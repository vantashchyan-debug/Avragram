import Foundation

/// The Avragram badge (compatible with Android AvraBadge): when a private chat opens, an invisible
/// "seen emoji 🅰" typing action is sent (at most every 6 h per person); an Avragram on the other side
/// answers once (at most every 10 min) and both mark each other as Avragram users. Nothing is shown in
/// other apps. Telegram UI shows the mark next to the name.
public final class AvraBadge {
    public static let shared = AvraBadge()
    public static let emoticon = "🅰"

    private let lock = NSLock()
    private var sender: ((Int64) -> Void)?
    private var selfId: Int64 = 0

    private init() {}

    public static var enabled: Bool {
        get { UserDefaults.standard.object(forKey: "avra.badge.enabled") as? Bool ?? true }
        set { UserDefaults.standard.set(newValue, forKey: "avra.badge.enabled") }
    }

    /// Set by the app: sends the handshake to a user id.
    public func configure(selfId: Int64, sender: @escaping (Int64) -> Void) {
        lock.lock()
        self.selfId = selfId
        self.sender = sender
        lock.unlock()
    }

    public func isAvragram(_ userId: Int64) -> Bool {
        guard AvraBadge.enabled else { return false }
        lock.lock()
        let me = selfId
        lock.unlock()
        if userId == me && me != 0 { return true }
        return (UserDefaults.standard.array(forKey: "avra.badge.known") as? [String] ?? []).contains(String(userId))
    }

    /// The mark to put after a name, or "".
    public func suffix(_ userId: Int64) -> String {
        return isAvragram(userId) ? " " + AvraBadge.emoticon : ""
    }

    private func lastSent(_ userId: Int64) -> Double {
        return UserDefaults.standard.double(forKey: "avra.badge.sent.\(userId)")
    }

    private func send(_ userId: Int64) {
        lock.lock()
        let s = sender
        lock.unlock()
        guard let s = s else { return }
        UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: "avra.badge.sent.\(userId)")
        s(userId)
    }

    public func chatOpened(_ userId: Int64) {
        guard AvraBadge.enabled, userId > 0, userId != selfId else { return }
        if Date().timeIntervalSince1970 - lastSent(userId) > 6 * 3600 { send(userId) }
    }

    /// The other side uses Avragram.
    public func received(_ userId: Int64) {
        guard AvraBadge.enabled, userId > 0 else { return }
        var known = UserDefaults.standard.array(forKey: "avra.badge.known") as? [String] ?? []
        if !known.contains(String(userId)) {
            known.append(String(userId))
            UserDefaults.standard.set(Array(known.suffix(5000)), forKey: "avra.badge.known")
        }
        if Date().timeIntervalSince1970 - lastSent(userId) > 600 { send(userId) }
    }
}
