import Foundation
import CryptoKit
import SwiftUI
import SGSwiftUI
import Postbox
import TelegramCore
import AccountContext

/// AvraSea online (port of Android com.avragramm.sea.SeaNet), same wire format: commitment
/// SHA-256(layout:salt) before the battle, a shot log in every message, answers computed from your own
/// layout, layouts revealed and checked at the end.
struct AvraSeaOnlineGame: Codable, Identifiable {
    var id: String
    var peer: Int64
    var starter: Bool
    var state: Int
    var myLayout = "", mySalt = "", myCommit = "", oppCommit = "", oppLayout = ""
    var log = ""
    var winner = 0
    var reason = ""
    var cheat = false, verified = false, revealSent = false, cardSent = false
    var oppName = ""
    var created: Int64 = 0
    var inIds: [Int32] = []

    var me: Character { starter ? "a" : "b" }
    var them: Character { starter ? "b" : "a" }
    var entries: [String] { log.isEmpty ? [] : log.split(separator: ".").map(String.init) }

    func shots(_ who: Character) -> [(Int, Character)] {
        entries.compactMap { e in
            let c = Array(e)
            guard c.count == 4, c[0] == who, let cell = Int(String(c[1...2])), cell >= 0, cell < 100 else { return nil }
            return (cell, c[3])
        }
    }

    /// "a" / "b" to shoot, "?" while a shot waits, nil when not active.
    var turn: Character? {
        guard state == AvraSeaNet.active else { return nil }
        guard let last = entries.last, last.count == 4 else { return "a" }
        let c = Array(last)
        if c[3] == "?" { return "?" }
        return c[3] == "m" ? (c[0] == "a" ? "b" : "a") : c[0]
    }

    var myTurn: Bool { turn == me }
    func kills(_ who: Character) -> Int { shots(who).filter { $0.1 == "k" }.count }
}

enum AvraSeaNet {
    static let inviteOut = 0, inviteIn = 1, active = 2, finished = 3, declined = 4
    static let tag = "#avrasea"
    static let letters = Array("АБВГДЕЖЗИК")

    static func isProtocol(_ text: String) -> Bool { text.contains(tag + " v=") }

    static func encode(_ b: AvraSeaBoard) -> String {
        b.ships.map { "\($0.x)\($0.y)\($0.len)\($0.vertical ? "v" : "h")" }.joined(separator: ",")
    }

    static func decode(_ s: String) -> AvraSeaBoard {
        var b = AvraSeaBoard()
        for p in s.split(separator: ",") {
            let c = Array(p)
            guard c.count == 4, let x = c[0].wholeNumberValue, let y = c[1].wholeNumberValue, let l = c[2].wholeNumberValue else { return AvraSeaBoard() }
            b.ships.append(AvraShip(x: x, y: y, len: l, vertical: c[3] == "v"))
        }
        return b
    }

    static func commit(_ layout: String, _ salt: String) -> String {
        let digest = SHA256.hash(data: Data((layout + ":" + salt).utf8))
        return digest.prefix(12).map { String(format: "%02x", $0) }.joined()
    }

    static func valid(_ b: AvraSeaBoard) -> Bool {
        guard b.ships.count == AvraSeaBoard.fleet.count, b.ships.map({ $0.len }).sorted() == AvraSeaBoard.fleet.sorted() else { return false }
        for (i, s) in b.ships.enumerated() where !b.fits(s, skip: i) { return false }
        return true
    }

    private static func newSalt() -> String {
        let a = Array("abcdefghijkmnpqrstuvwxyz23456789")
        var rng = SystemRandomNumberGenerator()
        return String((0..<12).map { _ in a.randomElement(using: &rng)! })
    }

    // MARK: storage

    private static let storeKey = "avra.sea.online"
    private(set) static var games: [String: AvraSeaOnlineGame] = {
        guard let data = UserDefaults.standard.data(forKey: storeKey),
              let list = try? JSONDecoder().decode([AvraSeaOnlineGame].self, from: data) else { return [:] }
        return Dictionary(uniqueKeysWithValues: list.map { ($0.id, $0) })
    }()

    static func save() {
        var done = 0
        let list = games.values.sorted { $0.created > $1.created }.filter { g in
            if g.state == finished || g.state == declined { done += 1; return done <= 20 }
            return true
        }
        games = Dictionary(uniqueKeysWithValues: list.map { ($0.id, $0) })
        if let data = try? JSONEncoder().encode(list) { UserDefaults.standard.set(data, forKey: storeKey) }
        AvraOnline.notify()
    }

    static func list() -> [AvraSeaOnlineGame] { games.values.sorted { $0.created > $1.created } }

    // MARK: actions

    private static func setLayout(_ g: inout AvraSeaOnlineGame, _ fleet: AvraSeaBoard) {
        g.myLayout = encode(fleet)
        g.mySalt = newSalt()
        g.myCommit = commit(g.myLayout, g.mySalt)
    }

    @discardableResult
    static func invite(peer: Int64, name: String, fleet: AvraSeaBoard) -> AvraSeaOnlineGame {
        let a = Array("abcdefghijkmnpqrstuvwxyz23456789")
        var g = AvraSeaOnlineGame(id: String((0..<6).map { _ in a.randomElement()! }), peer: peer, starter: true, state: inviteOut)
        g.created = AvraOnline.now()
        g.oppName = name
        setLayout(&g, fleet)
        games[g.id] = g
        save()
        send(g, status: "inv", human: "Вызов на морской бой")
        let head = "🚢 Вам бросили вызов!"
        let game = "Морской бой"
        let text = head + "\nИгра: " + game + "\nРасставь корабли и жми «Принять бой»"
        if let r = text.range(of: game) {
            AvraOnline.sendCard(userId: peer, text: text, linkRange: r, url: AvraOnline.cardUrl + "avrasea-\(g.id)-inv")
        }
        return g
    }

    static func accept(_ id: String, fleet: AvraSeaBoard) {
        guard var g = games[id], g.state == inviteIn else { return }
        setLayout(&g, fleet)
        g.state = active
        games[id] = g
        save()
        send(g, status: "acc", human: "Вызов принят, бой начался!")
    }

    static func decline(_ id: String) {
        guard var g = games[id], g.state == inviteIn || g.state == inviteOut else { return }
        g.state = declined
        games[id] = g
        save()
        send(g, status: "dec", human: "Вызов отклонён")
    }

    static func fire(_ id: String, cell: Int) {
        guard var g = games[id], g.myTurn, cell >= 0, cell < 100, !g.shots(g.me).contains(where: { $0.0 == cell }) else { return }
        g.log = (g.log.isEmpty ? "" : g.log + ".") + String(g.me) + String(format: "%02d", cell) + "?"
        games[id] = g
        save()
        send(g, status: "sh", human: "Выстрел: \(letters[cell % 10])\(cell / 10 + 1)")
    }

    static func resign(_ id: String) {
        guard var g = games[id], g.state == active else { return }
        finish(&g, winner: -1, reason: "resign")
        games[id] = g
        save()
        send(g, status: "res", human: "Сдался")
    }

    private static func send(_ g: AvraSeaOnlineGame, status: String, human: String) {
        let me = AvraOnline.selfId
        let a = g.starter ? me : g.peer, b = g.starter ? g.peer : me
        var t = "🚢 AvraSea\n\(human)\n\(tag) v=1 g=\(g.id) a=\(a) b=\(b) s=\(status) l=\(g.log.isEmpty ? "-" : g.log) c=\(g.myCommit)"
        if g.state == finished {
            t += " rv=\(g.myLayout):\(g.mySalt)"
            var updated = g
            updated.revealSent = true
            games[g.id] = updated
            save()
        }
        if !AvraOnline.selfName.isEmpty { t += " n=" + AvraOnline.selfName.replacingOccurrences(of: " ", with: "_") }
        AvraOnline.sendHidden(userId: g.peer, text: t)
    }

    private static func finish(_ g: inout AvraSeaOnlineGame, winner: Int, reason: String) {
        if g.state == finished { return }
        g.state = finished
        g.winner = winner
        g.reason = reason
        let key = winner > 0 ? "avra.sea.wins.online" : "avra.sea.losses.online"
        UserDefaults.standard.set(UserDefaults.standard.integer(forKey: key) + 1, forKey: key)
        let id = g.id
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            guard var current = games[id] else { return }
            if current.starter && !current.cardSent {
                current.cardSent = true
                games[id] = current
                save()
                let mine = current.winner > 0
                let who = "Победил(а) " + (mine ? (AvraOnline.selfName.isEmpty ? "ты" : AvraOnline.selfName) : (current.oppName.isEmpty ? "соперник" : current.oppName))
                let shots = current.shots(mine ? current.me : current.them).filter { $0.1 != "?" }
                let hits = shots.filter { $0.1 != "m" }.count
                let over = "Игра завершена!"
                let text = "🏁 " + over + " " + who + "\n🚢 Морской бой · " + (current.reason == "resign" ? "сдача" : "весь флот потоплен") + " · выстрелов: \(shots.count) · точность \(shots.isEmpty ? 0 : hits * 100 / shots.count)%"
                if let r = text.range(of: over) {
                    AvraOnline.sendCard(userId: current.peer, text: text, linkRange: r, url: AvraOnline.cardUrl + "avrasea-\(current.id)-end")
                }
            }
            AvraOnline.deleteForBoth(current.inIds)
        }
    }

    // MARK: protocol in

    static func receive(peerId: PeerId, messageId: Int32, outgoing: Bool, text: String) {
        if outgoing { return }
        if var g = process(from: AvraOnline.userId(peerId), text: text) {
            if !g.inIds.contains(messageId) { g.inIds.append(messageId) }
            games[g.id] = g
            save()
        }
        AvraOnline.deleteForBoth([messageId])
    }

    private static func extendsLog(_ ours: [String], _ theirs: [String]) -> Bool {
        if theirs.count < ours.count { return false }
        for i in 0..<ours.count {
            let o = ours[i], t = theirs[i]
            if o == t { continue }
            if o.count == 4 && t.count == 4 && o.prefix(3) == t.prefix(3) && o.last == "?" { continue }
            return false
        }
        return true
    }

    private static func process(from sender: Int64, text: String) -> AvraSeaOnlineGame? {
        guard let f = AvraOnline.fields(text, tag: tag), let id = f["g"], id.count <= 16,
              let a = f["a"].flatMap(Int64.init), let b = f["b"].flatMap(Int64.init) else { return nil }
        let me = AvraOnline.selfId
        guard a == me || b == me else { return nil }
        let peer = a == me ? b : a
        guard sender == peer else { return nil }
        let status = f["s"] ?? ""
        var g: AvraSeaOnlineGame
        if let existing = games[id] {
            g = existing
        } else {
            guard status == "inv" else { return nil } // without our own layout this game cannot go on
            g = AvraSeaOnlineGame(id: id, peer: peer, starter: false, state: inviteIn)
            g.created = AvraOnline.now()
        }
        guard g.peer == peer else { return nil }
        if let n = f["n"] { g.oppName = String(n.replacingOccurrences(of: "_", with: " ").prefix(64)) }
        if let c = f["c"], !c.isEmpty, g.oppCommit.isEmpty, c.count <= 64 { g.oppCommit = c }

        var theirs: [String] = []
        if let l = f["l"], l != "-", !l.isEmpty, l.count <= 2000 { theirs = l.split(separator: ".").map(String.init) }
        var event: Character? = nil
        if extendsLog(g.entries, theirs) && theirs.joined(separator: ".") != g.log {
            g.log = theirs.joined(separator: ".")
            if let last = theirs.last, last.first == g.me { event = last.last }
        }
        switch status {
        case "acc":
            if g.state == inviteOut { g.state = active }
        case "dec":
            if g.state == inviteOut || g.state == inviteIn { g.state = declined }
        case "res":
            if g.state == active { finish(&g, winner: 1, reason: "resign") }
        default:
            break
        }
        if let rv = f["rv"], g.oppLayout.isEmpty, let colon = rv.firstIndex(of: ":") {
            verify(&g, layout: String(rv[..<colon]), salt: String(rv[rv.index(after: colon)...]))
        }
        if event != nil { AvraHaptics.tap(true) }
        if g.state == active && g.kills(g.me) >= AvraSeaBoard.fleet.count { finish(&g, winner: 1, reason: "fleet") }

        // a shot at you: answer from your own layout
        if g.state == active, g.turn == "?", var e = Optional(g.entries), let last = e.last, last.first == g.them {
            let c = Array(last)
            if let cell = Int(String(c[1...2])), cell >= 0, cell < 100 {
                let mine = decode(g.myLayout)
                let hit = Set(g.shots(g.them).filter { $0.1 != "m" && $0.1 != "?" }.map { $0.0 })
                let r = mine.resolve(cell, hitBefore: hit)
                e[e.count - 1] = String(last.prefix(3)) + String(r)
                g.log = e.joined(separator: ".")
                AvraHaptics.tap(r != "m")
                if g.kills(g.them) >= AvraSeaBoard.fleet.count { finish(&g, winner: -1, reason: "fleet") }
                games[id] = g
                let name = "\(letters[cell % 10])\(cell / 10 + 1)"
                send(g, status: "rs", human: r == "m" ? "\(name): мимо" : r == "k" ? "\(name): убит" : "\(name): ранен")
                g = games[id] ?? g
            }
        }
        if g.state == finished && !g.revealSent {
            games[id] = g
            send(g, status: "rev", human: "Флот раскрыт")
            g = games[id] ?? g
        }
        games[id] = g
        save()
        return g
    }

    private static func verify(_ g: inout AvraSeaOnlineGame, layout: String, salt: String) {
        g.oppLayout = layout
        let b = decode(layout)
        var ok = valid(b) && commit(layout, salt) == g.oppCommit
        if ok {
            var hit = Set<Int>()
            for s in g.shots(g.me) where s.1 != "?" {
                let real = b.resolve(s.0, hitBefore: hit)
                if real != s.1 { ok = false; break }
                if real != "m" { hit.insert(s.0) }
            }
        }
        g.verified = ok
        g.cheat = !ok
    }
}

/// One online sea battle: place the fleet (for a challenge you got), then shoot.
struct AvraSeaOnlineScreen: View {
    let gameId: String
    @State var fleet = AvraSeaBoard.random()
    @State var refresh = 0
    @Environment(\.sgTheme) var theme

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                if let g = AvraSeaNet.games[gameId] {
                    content(g)
                } else {
                    Text("Бой ещё не пришёл. Если тебе бросили вызов только что, подожди пару секунд.").foregroundColor(.white).padding(30)
                }
            }
            .padding(16)
            .id(refresh)
        }
        .background(Color(red: 0.02, green: 0.09, blue: 0.15).ignoresSafeArea())
        .onReceive(NotificationCenter.default.publisher(for: AvraOnline.changed)) { _ in refresh += 1 }
        .sgNavigationTitle("Морской бой")
    }

    @ViewBuilder
    private func content(_ g: AvraSeaOnlineGame) -> some View {
        if g.state == AvraSeaNet.inviteIn {
            Text("\(g.oppName.isEmpty ? "Соперник" : g.oppName) вызывает на бой").font(.headline).foregroundColor(.white)
            Text("Расставь флот: перетаскивай корабли, нажми, чтобы повернуть").font(.footnote).foregroundColor(.white.opacity(0.7))
            AvraSeaGrid(marks: [AvraSeaMark](repeating: .unknown, count: 100), ships: fleet.ships, showShips: true, active: false,
                        onShipTap: { i in
                            var s = fleet.ships[i]; s.vertical.toggle()
                            if s.len > 1 && fleet.fits(s, skip: i) { withAnimation(.spring()) { fleet.ships[i] = s } }
                        },
                        onShipDrop: { i, x, y in
                            var s = fleet.ships[i]; s.x = x; s.y = y
                            guard fleet.fits(s, skip: i) else { return false }
                            withAnimation(.spring()) { fleet.ships[i] = s }
                            return true
                        })
            HStack {
                Button("🎲 Случайно") { withAnimation(.spring()) { fleet = AvraSeaBoard.random() } }.buttonStyle(.bordered).tint(.white)
                Button("⚓ Принять бой") { AvraSeaNet.accept(gameId, fleet: fleet) }.buttonStyle(.borderedProminent)
                Button("Отклонить") { AvraSeaNet.decline(gameId) }.buttonStyle(.bordered).tint(.red)
            }
        } else {
            let mine = AvraSeaNet.decode(g.myLayout)
            let myShots = g.shots(g.me).filter { $0.1 != "?" }
            let theirShots = g.shots(g.them).filter { $0.1 != "?" }
            let over = g.state == AvraSeaNet.finished
            Text(title(g)).font(.title3.bold()).foregroundColor(over && g.winner > 0 ? .yellow : .white)
            if over {
                Text(g.cheat ? "⚠️ Ответы соперника не совпадают с его расстановкой — жульничество!" : g.verified ? "✅ Флот соперника проверен: всё честно" : "⏳ Ждём раскрытия флота соперника")
                    .font(.footnote).foregroundColor(.white.opacity(0.8))
            }
            Text("Воды: \(g.oppName.isEmpty ? "соперник" : g.oppName)").font(.caption.bold()).foregroundColor(.white.opacity(0.7)).frame(maxWidth: .infinity, alignment: .leading)
            AvraSeaGrid(marks: AvraSeaKnowledge.marks(myShots), ships: over ? AvraSeaNet.decode(g.oppLayout).ships : [], showShips: over && !g.oppLayout.isEmpty,
                        active: g.myTurn, onCell: { AvraSeaNet.fire(gameId, cell: $0) })
            Text("Твой флот").font(.caption.bold()).foregroundColor(.white.opacity(0.7)).frame(maxWidth: .infinity, alignment: .leading)
            AvraSeaGrid(marks: AvraSeaKnowledge.marks(theirShots), ships: mine.ships, showShips: true, active: false).padding(.horizontal, 40)
            if g.state == AvraSeaNet.inviteOut {
                Button("✖ Отменить вызов") { AvraSeaNet.decline(gameId) }.buttonStyle(.bordered).tint(.white)
            } else if g.state == AvraSeaNet.active {
                Button("🏳 Сдаться") { AvraSeaNet.resign(gameId) }.buttonStyle(.bordered).tint(.red)
            }
        }
    }

    private func title(_ g: AvraSeaOnlineGame) -> String {
        switch g.state {
        case AvraSeaNet.inviteOut: return "⏳ Ждём друга"
        case AvraSeaNet.declined: return "Вызов отклонён"
        case AvraSeaNet.finished: return g.winner > 0 ? "🏆 Победа!" : "💀 Поражение"
        default:
            if g.turn == "?" { return "Снаряд летит…" }
            return g.myTurn ? "🎯 Твой выстрел!" : "Ход соперника"
        }
    }
}

/// Lobby: challenge a friend to a sea battle and your battles.
struct AvraSeaOnlineLobby: View {
    @Environment(\.sgTheme) var theme
    @Environment(\.sgNavigation) var navigation
    @State var games = AvraSeaNet.list()

    var body: some View {
        Form {
            Section {
                Button("Вызвать друга") { pickFriend() }
                Text("Флот расставится случайно; поменять его можно в бою против бота. Друг расставит свой и примет бой.")
                    .font(.footnote).foregroundColor(theme.secondaryTextColor)
            }
            Section(header: Text("Бои")) {
                if games.isEmpty { Text("Пока нет онлайн-боёв").foregroundColor(theme.secondaryTextColor) }
                ForEach(games) { g in
                    Button(action: { navigation?.push(title: "Морской бой") { AvraSeaOnlineScreen(gameId: g.id) } }) {
                        VStack(alignment: .leading) {
                            Text(g.oppName.isEmpty ? "Соперник" : g.oppName).foregroundColor(theme.primaryTextColor)
                            Text(g.state == AvraSeaNet.inviteIn ? "📨 Тебе бросили вызов" : g.state == AvraSeaNet.finished ? (g.winner > 0 ? "🏆 Победа" : "💀 Поражение") : g.myTurn ? "🎯 Твой выстрел" : "Ход соперника")
                                .font(.footnote).foregroundColor(theme.secondaryTextColor)
                        }
                    }
                }
            }
            Section {
                Text("Онлайн: побед \(UserDefaults.standard.integer(forKey: "avra.sea.wins.online")), поражений \(UserDefaults.standard.integer(forKey: "avra.sea.losses.online"))")
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: AvraOnline.changed)) { _ in games = AvraSeaNet.list() }
        .sgNavigationTitle("Морской бой онлайн")
    }

    private func pickFriend() {
        guard let context = AvraOnline.context else { return }
        let controller = context.sharedContext.makePeerSelectionController(PeerSelectionControllerParams(context: context, filter: [.onlyPrivateChats, .onlyWriteable, .excludeDisabled], hasChatListSelector: true, hasContactSelector: true, hasGlobalSearch: false, title: "Кого вызвать"))
        controller.peerSelected = { [weak controller] peer, _ in
            controller?.dismiss()
            guard case let .user(user) = peer, user.botInfo == nil, peer.id != context.account.peerId else { return }
            let g = AvraSeaNet.invite(peer: AvraOnline.userId(peer.id), name: user.addressName.map { "@" + $0 } ?? peer.debugDisplayTitle, fleet: AvraSeaBoard.random())
            navigation?.push(title: "Морской бой") { AvraSeaOnlineScreen(gameId: g.id) }
        }
        navigation?.push(controller)
    }
}
