import Foundation
import UIKit
import SwiftUI
import SwiftSignalKit
import Postbox
import TelegramCore
import Display
import AccountContext
import SGSwiftUI
import AvraCore

/// Online games and mute for iPhone, wire-compatible with Avragram for Android:
///  - AvraChess: hidden "#avrachess v=1 g=… t=… w=… b=… m=… c=… s=…" messages (same fields, same
///    statuses inv/acc/dec/mv/dof/ddc/dac/res/flag), challenge and result cards with a link
///    "…/Avragram#avrachess-<id>-<inv|end>";
///  - mute: new messages of a muted person are deleted for both sides as they arrive.
/// Incoming protocol messages are dropped before they are stored (TelegramCore AvraMessageHook),
/// applied here and deleted for both sides. Text is only parsed as key=value pairs.
public final class AvraOnline {
    public static let changed = Notification.Name("AvraOnlineChanged")
    static let cardUrl = "https://github.com/vantashchyan-debug/Avragram#"

    private(set) static weak var context: AccountContext?

    /// Called by the app when an account becomes active.
    public static func attach(context: AccountContext) {
        Self.context = context
        AvraMute.load(account: context.account.peerId)
        loadSelfName(context: context)
        let account = context.account
        AvraBadge.shared.configure(selfId: account.peerId.id._internalGetInt64Value()) { userId in
            let _ = avraSendBadgeHandshake(account: account, peerId: AvraOnline.peerId(userId: userId)).start()
        }
        AvraMessageHook.shared.handler = { peerId, messageId, outgoing, text, date in
            if AvraChessNet.isProtocol(text) {
                DispatchQueue.main.async { AvraChessNet.receive(peerId: peerId, messageId: messageId, outgoing: outgoing, text: text, date: date) }
                return true
            }
            if AvraSeaNet.isProtocol(text) {
                DispatchQueue.main.async { AvraSeaNet.receive(peerId: peerId, messageId: messageId, outgoing: outgoing, text: text) }
                return true
            }
            if !outgoing && AvraMessageHook.shared.mutedPeers.contains(peerId) {
                DispatchQueue.main.async { AvraMute.deleteIncoming(peerId: peerId, messageId: messageId, text: text) }
                return true
            }
            return false
        }
    }

    static var selfId: Int64 {
        guard let context = context else { return 0 }
        return context.account.peerId.id._internalGetInt64Value()
    }

    static func peerId(userId: Int64) -> PeerId {
        return PeerId(namespace: Namespaces.Peer.CloudUser, id: PeerId.Id._internalFromInt64Value(userId))
    }

    static func userId(_ peerId: PeerId) -> Int64 {
        return peerId.id._internalGetInt64Value()
    }

    static func sendHidden(userId: Int64, text: String) {
        guard let context = context else { return }
        let _ = avraSendHiddenMessage(account: context.account, peerId: peerId(userId: userId), text: text).start()
    }

    static func deleteForBoth(_ ids: [Int32]) {
        guard let context = context, !ids.isEmpty else { return }
        let _ = avraDeleteHiddenMessages(account: context.account, ids: ids).start()
    }

    /// A visible card: text with a link under one word (the game id travels in the link).
    static func sendCard(userId: Int64, text: String, linkRange: Range<String.Index>, url: String) {
        guard let context = context else { return }
        let ns = NSRange(linkRange, in: text)
        let bold = text.firstIndex(of: "\n").map { NSRange(text.startIndex..<$0, in: text) } ?? NSRange(location: 0, length: (text as NSString).length)
        let entities = [
            MessageTextEntity(range: bold.location ..< bold.location + bold.length, type: .Bold),
            MessageTextEntity(range: ns.location ..< ns.location + ns.length, type: .TextUrl(url: url))
        ]
        let message = EnqueueMessage.message(text: text, attributes: [TextEntitiesMessageAttribute(entities: entities)], inlineStickers: [:], mediaReference: nil, threadId: nil, replyToMessageId: nil, replyToStoryId: nil, localGroupingKey: nil, correlationId: nil, bubbleUpEmojiOrStickersets: [])
        let _ = enqueueMessages(account: context.account, peerId: peerId(userId: userId), messages: [message]).start()
    }

    /// "@username" or the name of this account, remembered when the account is attached.
    static var selfName = ""

    static func loadSelfName(context: AccountContext) {
        let _ = (context.engine.data.get(TelegramEngine.EngineData.Item.Peer.Peer(id: context.account.peerId))
        |> deliverOnMainQueue).start(next: { peer in
            guard let peer = peer else { return }
            if let username = peer.addressName, !username.isEmpty {
                selfName = "@" + username
            } else {
                selfName = peer.debugDisplayTitle
            }
        })
    }

    /// Opens a game from a card link; true when the link was an Avragram card.
    public static func openCard(url: String, context: AccountContext, navigationController: NavigationController?) -> Bool {
        // only the exact card address: https://github.com/vantashchyan-debug/Avragram#avra<kind>-<id>-<card>
        guard url.hasPrefix(cardUrl + "avra") else { return false }
        let tail = String(url.dropFirst(cardUrl.count + 4))
        let parts = tail.split(separator: "-").map(String.init)
        guard parts.count == 3, parts[1].count <= 16, parts[1].allSatisfy({ $0.isLetter || $0.isNumber }) else { return false }
        let kind = parts[0], id = parts[1], card = parts[2]
        let presentationData = context.sharedContext.currentPresentationData.with { $0 }
        if kind == "chess" {
            let controller = SGSwiftUIControllerFactory.make(title: "AvraChess", context: context, presentationData: presentationData) {
                AvraChessOnlineGameScreen(gameId: id, cardKind: card)
            }
            navigationController?.pushViewController(controller)
            return true
        }
        if kind == "sea" {
            let controller = SGSwiftUIControllerFactory.make(title: "Морской бой", context: context, presentationData: presentationData) {
                AvraSeaOnlineScreen(gameId: id)
            }
            navigationController?.pushViewController(controller)
            return true
        }
        return false
    }

    static func notify() {
        NotificationCenter.default.post(name: changed, object: nil)
    }

    static func now() -> Int64 {
        return Int64(Date().timeIntervalSince1970 * 1000)
    }

    /// Android-compatible URL encoding of names (URLEncoder: spaces as +).
    static func enc(_ s: String) -> String {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-_.*")
        return (s.addingPercentEncoding(withAllowedCharacters: allowed) ?? "").replacingOccurrences(of: "%20", with: "+")
    }

    static func dec(_ s: String) -> String {
        return s.replacingOccurrences(of: "+", with: " ").removingPercentEncoding ?? ""
    }

    /// key=value pairs of the protocol line ("#tag k=v k=v …" at the end of the text).
    static func fields(_ text: String, tag: String) -> [String: String]? {
        guard let r = text.range(of: tag + " ", options: .backwards) else { return nil }
        let line = text[r.upperBound...].split(separator: "\n").first.map(String.init) ?? ""
        var out: [String: String] = [:]
        for part in line.split(separator: " ") {
            guard let eq = part.firstIndex(of: "="), eq != part.startIndex else { continue }
            let key = String(part[..<eq])
            let value = String(part[part.index(after: eq)...])
            if key.count <= 8 && value.count <= 4000 { out[key] = value }
        }
        return out
    }
}

/// Builds Telegram screens hosting SwiftUI content (with the Telegram theme).
enum SGSwiftUIControllerFactory {
    static func make<Content: View>(title: String, context: AccountContext, presentationData: PresentationData, @ViewBuilder content: () -> Content) -> ViewController {
        return SGSwiftUIController(title: title, presentationData: presentationData, presentationDataSignal: context.sharedContext.presentationData, content: content)
    }
}

// MARK: - mute

enum AvraMute {
    private static var key: String = "avra.mute"

    static func load(account: PeerId) {
        key = "avra.mute.\(account.toInt64())"
        let ids = (UserDefaults.standard.array(forKey: key) as? [NSNumber]) ?? []
        AvraMessageHook.shared.mutedPeers = Set(ids.map { PeerId($0.int64Value) })
    }

    static func isMuted(_ peerId: PeerId) -> Bool {
        return AvraMessageHook.shared.mutedPeers.contains(peerId)
    }

    static func toggle(_ peerId: PeerId) -> Bool {
        var set = AvraMessageHook.shared.mutedPeers
        let muted: Bool
        if set.contains(peerId) {
            set.remove(peerId)
            muted = false
        } else {
            set.insert(peerId)
            muted = true
        }
        AvraMessageHook.shared.mutedPeers = set
        UserDefaults.standard.set(set.map { NSNumber(value: $0.toInt64()) }, forKey: key)
        return muted
    }

    /// "Said behind your back": a local copy of what the muted person wrote.
    static func deleteIncoming(peerId: PeerId, messageId: Int32, text: String) {
        var log = UserDefaults.standard.array(forKey: "avra.behind.\(peerId.toInt64())") as? [String] ?? []
        let date = DateFormatter.localizedString(from: Date(), dateStyle: .short, timeStyle: .short)
        log.append(date + " · " + (text.isEmpty ? "📎 Вложение" : String(text.prefix(2000))))
        UserDefaults.standard.set(Array(log.suffix(500)), forKey: "avra.behind.\(peerId.toInt64())")
        AvraOnline.deleteForBoth([messageId])
    }

    static func behind(_ peerId: PeerId) -> [String] {
        return UserDefaults.standard.array(forKey: "avra.behind.\(peerId.toInt64())") as? [String] ?? []
    }
}
