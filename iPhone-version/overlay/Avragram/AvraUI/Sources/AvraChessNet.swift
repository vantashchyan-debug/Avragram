import Foundation
import Postbox

/// AvraChess online (port of Android com.avragramm.chess.ChessNet), same wire format.
struct AvraChessOnlineGame: Codable, Identifiable, Equatable {
    var id: String
    var peer: Int64
    var myColor: Int
    var tc: Int
    var moves: [String] = []
    var wms: Int64 = 0
    var bms: Int64 = 0
    var lastMs: Int64 = 0
    var state: Int = AvraChessNet.inviteOut
    var result: Int = 2
    var reason: String = ""
    var applied = false
    var oppName: String = ""
    var oppRating: Int = 1200
    var drawOfferIn = false
    var drawOfferOut = false
    var created: Int64 = 0
    var starter = false
    var cardSent = false
    var inIds: [Int32] = []
    var ratingDelta = 0

    var sideToMove: Int { moves.count % 2 == 0 ? 1 : -1 }
    var myTurn: Bool { state == AvraChessNet.active && sideToMove == myColor }

    func board() -> AvraChessBoard {
        let b = AvraChessBoard()
        for u in moves {
            let m = b.parseUci(u)
            if m == 0 { break }
            b.make(m)
        }
        return b
    }

    func remaining(_ color: Int) -> Int64 {
        let now = AvraOnline.now()
        if AvraChessNet.isDaily(tc) {
            if state != AvraChessNet.active || color != sideToMove { return Int64(AvraChessNet.tcs[tc].0) * 1000 }
            return Int64(AvraChessNet.tcs[tc].0) * 1000 - (now - lastMs)
        }
        var stored = color == 1 ? wms : bms
        if state == AvraChessNet.active && color == sideToMove && lastMs > 0 { stored -= now - lastMs }
        return stored
    }
}

enum AvraChessNet {
    static let inviteOut = 0, inviteIn = 1, active = 2, finished = 3, declined = 4
    static let tag = "#avrachess"
    static let categories = ["Пуля", "Блиц", "Рапид", "3 дня"]
    static let categoryEmoji = ["⚡", "🔥", "🕒", "📅"]
    /// (base seconds, increment, category), the same table as on Android.
    static let tcs: [(Int, Int, Int)] = [(60, 0, 0), (120, 1, 0), (180, 2, 1), (300, 0, 1), (600, 0, 2), (900, 10, 2), (3 * 86400, 0, 3)]
    static let dailyTc = 6

    static func isDaily(_ tc: Int) -> Bool { tc == dailyTc }

    static func tcTitle(_ tc: Int) -> String {
        guard tc >= 0 && tc < tcs.count else { return "?" }
        let t = tcs[tc]
        if t.2 == 3 { return categoryEmoji[3] + " " + categories[3] }
        return "\(categoryEmoji[t.2]) \(categories[t.2]) \(t.0 / 60)+\(t.1)"
    }

    static func isProtocol(_ text: String) -> Bool {
        return text.contains(tag + " v=")
    }

    // MARK: storage

    private static let storeKey = "avra.chess.online"
    private(set) static var games: [String: AvraChessOnlineGame] = {
        guard let data = UserDefaults.standard.data(forKey: storeKey),
              let list = try? JSONDecoder().decode([AvraChessOnlineGame].self, from: data) else { return [:] }
        var map: [String: AvraChessOnlineGame] = [:]
        for g in list { map[g.id] = g }
        return map
    }()

    static func save() {
        var list = Array(games.values).sorted { $0.created > $1.created }
        var finishedCount = 0
        list = list.filter { g in
            if g.state == finished || g.state == declined {
                finishedCount += 1
                return finishedCount <= 30
            }
            return true
        }
        games = Dictionary(uniqueKeysWithValues: list.map { ($0.id, $0) })
        if let data = try? JSONEncoder().encode(list) { UserDefaults.standard.set(data, forKey: storeKey) }
        AvraOnline.notify()
    }

    static func list() -> [AvraChessOnlineGame] {
        return games.values.sorted { a, b in
            func prio(_ g: AvraChessOnlineGame) -> Int {
                if g.state == inviteIn { return 0 }
                if g.myTurn { return 1 }
                if g.state == active { return 2 }
                if g.state == inviteOut { return 3 }
                return 4
            }
            if prio(a) != prio(b) { return prio(a) < prio(b) }
            return max(a.lastMs, a.created) > max(b.lastMs, b.created)
        }
    }

    static func pending() -> Int { games.values.filter { $0.state == inviteIn || $0.myTurn }.count }

    // MARK: ratings

    static func rating(_ cat: Int) -> Int { UserDefaults.standard.object(forKey: "avra.chess.rating.\(cat)") as? Int ?? 1200 }
    static func gamesPlayed(_ cat: Int) -> Int { UserDefaults.standard.integer(forKey: "avra.chess.games.\(cat)") }

    private static func applyRating(_ g: inout AvraChessOnlineGame) {
        if g.applied || g.result == 2 || g.moves.count < 2 { g.applied = true; return }
        g.applied = true
        let cat = tcs[g.tc].2
        let r = rating(cat), n = gamesPlayed(cat)
        let score: Double = g.result == 0 ? 0.5 : g.result == g.myColor ? 1 : 0
        let expected = 1.0 / (1 + pow(10, Double(g.oppRating - r) / 400))
        let delta = Int((Double(n < 20 ? 40 : 20) * (score - expected)).rounded())
        g.ratingDelta = delta
        UserDefaults.standard.set(max(100, r + delta), forKey: "avra.chess.rating.\(cat)")
        UserDefaults.standard.set(n + 1, forKey: "avra.chess.games.\(cat)")
    }

    // MARK: actions

    private static func newId() -> String {
        let a = Array("abcdefghijkmnpqrstuvwxyz23456789")
        return String((0..<6).map { _ in a.randomElement()! })
    }

    @discardableResult
    static func invite(peer: Int64, name: String, tc: Int, color: Int) -> AvraChessOnlineGame {
        var g = AvraChessOnlineGame(id: newId(), peer: peer, myColor: color == 0 ? (Bool.random() ? 1 : -1) : color, tc: tc)
        g.wms = Int64(tcs[tc].0) * 1000
        g.bms = g.wms
        g.created = AvraOnline.now()
        g.starter = true
        g.oppName = name
        games[g.id] = g
        save()
        send(g, status: "inv", human: "Вызов на партию")
        sendInviteCard(g)
        return g
    }

    static func accept(_ id: String) {
        guard var g = games[id], g.state == inviteIn else { return }
        g.state = active
        g.lastMs = AvraOnline.now()
        games[id] = g
        save()
        send(g, status: "acc", human: "Вызов принят")
    }

    static func decline(_ id: String) {
        guard var g = games[id], g.state == inviteIn || g.state == inviteOut else { return }
        let wasIn = g.state == inviteIn
        g.state = declined
        games[id] = g
        save()
        send(g, status: "dec", human: wasIn ? "Вызов отклонён" : "Вызов отменён")
    }

    /// Your move (legal on the board of the game).
    static func move(_ id: String, uci: String) {
        guard var g = games[id], g.myTurn else { return }
        let before = g.board()
        let m = before.parseUci(uci)
        guard m != 0 else { return }
        let san = before.san(m)
        if !isDaily(g.tc) {
            let left = g.remaining(g.myColor) + Int64(tcs[g.tc].1) * 1000
            if g.myColor == 1 { g.wms = max(0, left) } else { g.bms = max(0, left) }
        }
        g.lastMs = AvraOnline.now()
        g.moves.append(uci)
        g.drawOfferIn = false
        let status = g.drawOfferOut ? "dof" : "mv"
        checkEnd(&g)
        games[id] = g
        save()
        send(g, status: status, human: "Ход \((g.moves.count + 1) / 2): \(san)")
        if g.state == finished { onFinished(g) }
    }

    static func resign(_ id: String) {
        guard var g = games[id], g.state == active else { return }
        finish(&g, result: -g.myColor, reason: "resign")
        games[id] = g
        save()
        send(g, status: "res", human: "Сдался")
        onFinished(g)
    }

    static func offerDraw(_ id: String) {
        guard var g = games[id], g.state == active else { return }
        if g.drawOfferIn {
            finish(&g, result: 0, reason: "agreed")
            games[id] = g
            save()
            send(g, status: "dac", human: "Ничья по соглашению")
            onFinished(g)
        } else {
            g.drawOfferOut = true
            games[id] = g
            save()
            send(g, status: "dof", human: "Предлагаю ничью")
        }
    }

    static func declineDraw(_ id: String) {
        guard var g = games[id], g.drawOfferIn else { return }
        g.drawOfferIn = false
        games[id] = g
        save()
        send(g, status: "ddc", human: "Ничья отклонена")
    }

    static func claimTimeout(_ id: String) {
        guard var g = games[id], g.state == active else { return }
        let side = g.sideToMove
        guard g.remaining(side) <= 0 else { return }
        let b = g.board()
        let canMate = b.sq.contains { $0 != 0 && ($0 > 0 ? 1 : -1) == -side && abs($0) != 6 }
        finish(&g, result: canMate ? -side : 0, reason: "time")
        games[id] = g
        save()
        send(g, status: "flag", human: resultText(g))
        onFinished(g)
    }

    // MARK: protocol out

    private static func send(_ g: AvraChessOnlineGame, status: String, human: String) {
        let me = AvraOnline.selfId
        let white = g.myColor == 1 ? me : g.peer, black = g.myColor == 1 ? g.peer : me
        var t = "♟ AvraChess · \(tcTitle(g.tc))\n\(human)\n"
        t += "\(tag) v=1 g=\(g.id) t=\(g.tc) w=\(white) b=\(black) m=\(g.moves.isEmpty ? "-" : g.moves.joined(separator: "."))"
        t += " c=\(g.wms),\(g.bms) s=\(status)"
        if g.state == finished { t += " res=\(g.result) why=\(g.reason)" }
        let cat = tcs[g.tc].2
        t += " r=" + (0..<4).map { c in String(gamesPlayed(c) > 0 || c == cat ? rating(c) : 0) }.joined(separator: ",")
        if !AvraOnline.selfName.isEmpty { t += " n=" + AvraOnline.enc(AvraOnline.selfName) }
        AvraOnline.sendHidden(userId: g.peer, text: t)
    }

    private static func sendInviteCard(_ g: AvraChessOnlineGame) {
        let head = "♟ Вам бросили вызов!"
        let game = "Шахматы"
        let text = head + "\nИгра: " + game + " · " + tcTitle(g.tc) + "\n" + (g.myColor == 1 ? "Ты играешь чёрными" : "Ты играешь белыми")
        if let r = text.range(of: game) {
            AvraOnline.sendCard(userId: g.peer, text: text, linkRange: r, url: AvraOnline.cardUrl + "avrachess-\(g.id)-inv")
        }
    }

    private static func sendResultCard(_ g: AvraChessOnlineGame) {
        let over = "Игра завершена!"
        let who: String
        if g.result == 0 {
            who = "Ничья"
        } else {
            let winnerName = g.result == g.myColor ? (AvraOnline.selfName.isEmpty ? "ты" : AvraOnline.selfName) : (g.oppName.isEmpty ? "соперник" : g.oppName)
            who = (g.result == 1 ? "Победили Белые" : "Победили Чёрные") + " (\(winnerName))"
        }
        let text = "🏁 " + over + " " + who + "\n" + reasonText(g.reason) + " · " + tcTitle(g.tc) + " · ходов: \((g.moves.count + 1) / 2)"
        if let r = text.range(of: over) {
            AvraOnline.sendCard(userId: g.peer, text: text, linkRange: r, url: AvraOnline.cardUrl + "avrachess-\(g.id)-end")
        }
    }

    private static func onFinished(_ g: AvraChessOnlineGame) {
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            guard var current = games[g.id] else { return }
            if current.starter && !current.cardSent {
                current.cardSent = true
                games[g.id] = current
                save()
                sendResultCard(current)
            }
            AvraOnline.deleteForBoth(current.inIds)
        }
    }

    // MARK: protocol in

    static func receive(peerId: PeerId, messageId: Int32, outgoing: Bool, text: String, date: Int32) {
        if outgoing { return } // our own echo: nothing new
        let from = AvraOnline.userId(peerId)
        let applied = process(from: from, text: text, dateMs: Int64(date) * 1000)
        if var g = applied {
            if !g.inIds.contains(messageId) { g.inIds.append(messageId) }
            games[g.id] = g
            save()
        }
        AvraOnline.deleteForBoth([messageId])
    }

    private static func process(from sender: Int64, text: String, dateMs: Int64) -> AvraChessOnlineGame? {
        guard let f = AvraOnline.fields(text, tag: tag), let id = f["g"], id.count <= 16,
              let white = f["w"].flatMap(Int64.init), let black = f["b"].flatMap(Int64.init),
              let tc = f["t"].flatMap(Int.init), tc >= 0, tc < tcs.count else { return nil }
        let me = AvraOnline.selfId
        guard white == me || black == me else { return nil }
        let peer = white == me ? black : white
        guard sender == peer else { return nil }
        var status = f["s"] ?? ""
        var g: AvraChessOnlineGame
        var isNew = false
        if let existing = games[id] {
            g = existing
        } else {
            g = AvraChessOnlineGame(id: id, peer: peer, myColor: white == me ? 1 : -1, tc: tc)
            g.wms = Int64(tcs[tc].0) * 1000
            g.bms = g.wms
            g.state = status == "inv" ? inviteIn : active
            g.created = dateMs
            isNew = true
        }
        guard g.peer == peer else { return nil }
        if let n = f["n"] { g.oppName = String(AvraOnline.dec(n).prefix(64)) }
        if let r = f["r"] {
            let rs = r.split(separator: ",").compactMap { Int($0) }
            let cat = tcs[tc].2
            if cat < rs.count, rs[cat] > 0, rs[cat] < 4000, g.state != finished { g.oppRating = rs[cat] }
        }
        // the move list: adopt it when it continues ours
        var theirs: [String] = []
        if let m = f["m"], m != "-", !m.isEmpty { theirs = m.split(separator: ".").map(String.init) }
        var moved = false
        if theirs.count > g.moves.count && Array(theirs.prefix(g.moves.count)) == g.moves && legal(theirs) {
            g.moves = theirs
            moved = true
        }
        if let c = f["c"], moved || isNew {
            let cs = c.split(separator: ",").compactMap { Int64($0) }
            if cs.count == 2 { g.wms = cs[0]; g.bms = cs[1] }
        }
        if moved || isNew { g.lastMs = dateMs }
        if theirs.count != g.moves.count { status = "" } // an old message must not replay its offer / result
        switch status {
        case "acc":
            if g.state == inviteOut || g.state == inviteIn { g.state = active; g.lastMs = dateMs }
        case "dec":
            if g.state == inviteOut || g.state == inviteIn { g.state = declined }
        case "res":
            if g.state == active { finish(&g, result: g.myColor, reason: "resign") }
        case "dof":
            if g.state == active { g.drawOfferIn = true }
        case "ddc":
            g.drawOfferOut = false
        case "dac":
            if g.state == active { finish(&g, result: 0, reason: "agreed") }
        case "flag":
            if g.state == active, let res = f["res"].flatMap(Int.init), res >= -1, res <= 1 { finish(&g, result: res, reason: "time") }
        case "inv":
            break
        default:
            if g.state == inviteOut || g.state == inviteIn { g.state = active }
        }
        if moved {
            g.drawOfferOut = false
            if status == "dof" { g.drawOfferIn = true }
            AvraHaptics.tap()
        }
        if g.state == active { checkEnd(&g) }
        games[id] = g
        save()
        if g.state == finished { onFinished(g) }
        return g
    }

    private static func legal(_ moves: [String]) -> Bool {
        if moves.count > 600 { return false }
        let b = AvraChessBoard()
        for u in moves {
            if b.status() != .ongoing { return false }
            let m = b.parseUci(u)
            if m == 0 { return false }
            b.make(m)
        }
        return true
    }

    private static func checkEnd(_ g: inout AvraChessOnlineGame) {
        let b = g.board()
        switch b.status() {
        case .ongoing: return
        case .mate: finish(&g, result: -g.sideToMove, reason: "mate")
        case .stalemate: finish(&g, result: 0, reason: "stalemate")
        case .repetition: finish(&g, result: 0, reason: "repetition")
        case .fifty: finish(&g, result: 0, reason: "fifty")
        case .material: finish(&g, result: 0, reason: "material")
        }
    }

    private static func finish(_ g: inout AvraChessOnlineGame, result: Int, reason: String) {
        if g.state == finished { return }
        g.state = finished
        g.result = result
        g.reason = reason
        g.drawOfferIn = false
        g.drawOfferOut = false
        applyRating(&g)
    }

    static func reasonText(_ r: String) -> String {
        switch r {
        case "mate": return "Мат"
        case "resign": return "Сдача"
        case "time": return "Время вышло"
        case "agreed": return "По соглашению"
        case "stalemate": return "Пат"
        case "repetition": return "Троекратное повторение"
        case "fifty": return "Правило 50 ходов"
        default: return "Недостаточно материала"
        }
    }

    static func resultText(_ g: AvraChessOnlineGame) -> String {
        let who = g.result == 1 ? "Победили Белые" : g.result == -1 ? "Победили Чёрные" : "Ничья"
        return who + " · " + reasonText(g.reason)
    }
}
