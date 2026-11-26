import Foundation
import Postbox
import SwiftSignalKit
internal import TelegramApi
import MtProtoKit
import AvraCore

/// Avragram: hidden service messages of the online games (AvraChess "#avrachess", AvraSea "#avrasea"),
/// the same protocol as the Android app, and the mute feature.
///
/// Every new private message passes `AvraMessageHook` before it is stored. The handler (set by the
/// Avragram UI module) applies game messages and returns true for them: such a message is never
/// stored, shown or notified. Messages are only inspected, never executed: the handler parses a fixed
/// key=value format.
public final class AvraMessageHook {
    public static let shared = AvraMessageHook()

    private let lock = NSLock()
    private var currentHandler: ((PeerId, Int32, Bool, String, Int32) -> Bool)?
    private var currentMuted: Set<PeerId> = Set()

    private init() {
    }

    /// (peer, message id, outgoing, text, date) -> drop the message
    public var handler: ((PeerId, Int32, Bool, String, Int32) -> Bool)? {
        get {
            self.lock.lock()
            defer { self.lock.unlock() }
            return self.currentHandler
        }
        set {
            self.lock.lock()
            self.currentHandler = newValue
            self.lock.unlock()
        }
    }

    /// People whose new messages go to the handler whatever they say (mute).
    public var mutedPeers: Set<PeerId> {
        get {
            self.lock.lock()
            defer { self.lock.unlock() }
            return self.currentMuted
        }
        set {
            self.lock.lock()
            self.currentMuted = newValue
            self.lock.unlock()
        }
    }
}

func avraFilterHiddenMessages(_ messages: [StoreMessage]) -> [StoreMessage] {
    guard let handler = AvraMessageHook.shared.handler else {
        return messages
    }
    let muted = AvraMessageHook.shared.mutedPeers
    return messages.filter { message in
        guard case let .Id(id) = message.id, id.peerId.namespace == Namespaces.Peer.CloudUser, id.namespace == Namespaces.Message.Cloud else {
            return true
        }
        let incoming = message.flags.contains(.Incoming)
        if !message.text.contains("#avra") && !(incoming && muted.contains(id.peerId)) {
            return true
        }
        return !handler(id.peerId, id.id, !incoming, message.text, message.timestamp)
    }
}

/// Sends a silent text message without creating a local copy (the game protocol).
public func avraSendHiddenMessage(account: Account, peerId: PeerId, text: String) -> Signal<Never, NoError> {
    return account.postbox.transaction { transaction -> Api.InputPeer? in
        return transaction.getPeer(peerId).flatMap(apiInputPeer)
    }
    |> mapToSignal { inputPeer -> Signal<Never, NoError> in
        guard let inputPeer = inputPeer else {
            return .complete()
        }
        var randomId: Int64 = 0
        arc4random_buf(&randomId, 8)
        // no_webpage (1 << 1), silent (1 << 5)
        let flags: Int32 = (1 << 1) | (1 << 5)
        return account.network.request(Api.functions.messages.sendMessage(flags: flags, peer: inputPeer, replyTo: nil, message: text, randomId: randomId, replyMarkup: nil, entities: nil, scheduleDate: nil, scheduleRepeatPeriod: nil, sendAs: nil, quickReplyShortcut: nil, effect: nil, allowPaidStars: nil, suggestedPost: nil, richMessage: nil))
        |> ignoreValues
        |> `catch` { _ -> Signal<Never, NoError> in
            return .complete()
        }
    }
}

/// Deletes private-chat messages for both sides by id (they were never stored here).
public func avraDeleteHiddenMessages(account: Account, ids: [Int32]) -> Signal<Never, NoError> {
    if ids.isEmpty {
        return .complete()
    }
    let stateManager = account.stateManager
    return account.network.request(Api.functions.messages.deleteMessages(flags: 1, id: ids))
    |> map { result -> Api.messages.AffectedMessages? in
        return result
    }
    |> `catch` { _ -> Signal<Api.messages.AffectedMessages?, NoError> in
        return .single(nil)
    }
    |> mapToSignal { result -> Signal<Never, NoError> in
        if let result = result {
            switch result {
            case let .affectedMessages(affectedMessagesData):
                stateManager?.addUpdateGroups([.updatePts(pts: affectedMessagesData.pts, ptsCount: affectedMessagesData.ptsCount)])
            }
        }
        return .complete()
    }
}

/// Avragram: keep messages that others delete (Avragram → Privacy). Deletions that arrive from the
/// server are skipped; the ids are remembered so they can be shown as deleted.
public enum AvraKeepDeleted {
    public static var enabled: Bool {
        get {
            return UserDefaults.standard.bool(forKey: "avra.keepDeleted")
        }
        set {
            UserDefaults.standard.set(newValue, forKey: "avra.keepDeleted")
        }
    }

    static func remember(globalIds: [Int32]) {
        var list = UserDefaults.standard.array(forKey: "avra.deleted.global") as? [Int] ?? []
        list.append(contentsOf: globalIds.map { Int($0) })
        UserDefaults.standard.set(Array(list.suffix(5000)), forKey: "avra.deleted.global")
    }

    static func remember(messageIds: [MessageId]) {
        var list = UserDefaults.standard.array(forKey: "avra.deleted.channel") as? [String] ?? []
        list.append(contentsOf: messageIds.map { "\($0.peerId.toInt64()):\($0.id)" })
        UserDefaults.standard.set(Array(list.suffix(5000)), forKey: "avra.deleted.channel")
    }

    public static var count: Int {
        return (UserDefaults.standard.array(forKey: "avra.deleted.global")?.count ?? 0) + (UserDefaults.standard.array(forKey: "avra.deleted.channel")?.count ?? 0)
    }
}

/// Avragram badge handshake: an invisible "seen emoji 🅰" typing action to a user.
public func avraSendBadgeHandshake(account: Account, peerId: PeerId) -> Signal<Never, NoError> {
    return account.postbox.transaction { transaction -> Api.InputPeer? in
        return transaction.getPeer(peerId).flatMap(apiInputPeer)
    }
    |> mapToSignal { inputPeer -> Signal<Never, NoError> in
        guard let inputPeer = inputPeer else {
            return .complete()
        }
        return account.network.request(Api.functions.messages.setTyping(flags: 0, peer: inputPeer, topMsgId: nil, action: .sendMessageEmojiInteractionSeen(.init(emoticon: AvraBadge.emoticon))))
        |> ignoreValues
        |> `catch` { _ -> Signal<Never, NoError> in
            return .complete()
        }
    }
}
