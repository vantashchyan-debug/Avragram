import Foundation

/// AvraBot (port of the Android ChessAi): iterative deepening alpha-beta with quiescence search,
/// MVV-LVA ordering and piece-square tables. Four levels from ~600 to ~1800.
public final class AvraChessAi {
    public static let levelNames = ["Новичок", "Любитель", "Разрядник", "Мастер"]
    public static let levelRating = [600, 1000, 1400, 1800]
    private static let depth = [1, 2, 3, 6]
    private static let timeMs = [300.0, 600.0, 1200.0, 2200.0]
    private static let noise = [220, 60, 15, 0]
    private static let blunderPct = [30, 8, 0, 0]
    private static let inf = 1_000_000, mateScore = 100_000
    private static let value = [0, 100, 320, 330, 500, 900, 0]

    private static let pstP = [0, 0, 0, 0, 0, 0, 0, 0, 50, 50, 50, 50, 50, 50, 50, 50, 10, 10, 20, 30, 30, 20, 10, 10, 5, 5, 10, 25, 25, 10, 5, 5, 0, 0, 0, 20, 20, 0, 0, 0, 5, -5, -10, 0, 0, -10, -5, 5, 5, 10, 10, -20, -20, 10, 10, 5, 0, 0, 0, 0, 0, 0, 0, 0]
    private static let pstN = [-50, -40, -30, -30, -30, -30, -40, -50, -40, -20, 0, 0, 0, 0, -20, -40, -30, 0, 10, 15, 15, 10, 0, -30, -30, 5, 15, 20, 20, 15, 5, -30, -30, 0, 15, 20, 20, 15, 0, -30, -30, 5, 10, 15, 15, 10, 5, -30, -40, -20, 0, 5, 5, 0, -20, -40, -50, -40, -30, -30, -30, -30, -40, -50]
    private static let pstB = [-20, -10, -10, -10, -10, -10, -10, -20, -10, 0, 0, 0, 0, 0, 0, -10, -10, 0, 5, 10, 10, 5, 0, -10, -10, 5, 5, 10, 10, 5, 5, -10, -10, 0, 10, 10, 10, 10, 0, -10, -10, 10, 10, 10, 10, 10, 10, -10, -10, 5, 0, 0, 0, 0, 5, -10, -20, -10, -10, -10, -10, -10, -10, -20]
    private static let pstR = [0, 0, 0, 0, 0, 0, 0, 0, 5, 10, 10, 10, 10, 10, 10, 5, -5, 0, 0, 0, 0, 0, 0, -5, -5, 0, 0, 0, 0, 0, 0, -5, -5, 0, 0, 0, 0, 0, 0, -5, -5, 0, 0, 0, 0, 0, 0, -5, -5, 0, 0, 0, 0, 0, 0, -5, 0, 0, 0, 5, 5, 0, 0, 0]
    private static let pstQ = [-20, -10, -10, -5, -5, -10, -10, -20, -10, 0, 0, 0, 0, 0, 0, -10, -10, 0, 5, 5, 5, 5, 0, -10, -5, 0, 5, 5, 5, 5, 0, -5, 0, 0, 5, 5, 5, 5, 0, -5, -10, 5, 5, 5, 5, 5, 0, -10, -10, 0, 5, 0, 0, 0, 0, -10, -20, -10, -10, -5, -5, -10, -10, -20]
    private static let pstKMid = [-30, -40, -40, -50, -50, -40, -40, -30, -30, -40, -40, -50, -50, -40, -40, -30, -30, -40, -40, -50, -50, -40, -40, -30, -30, -40, -40, -50, -50, -40, -40, -30, -20, -30, -30, -40, -40, -30, -30, -20, -10, -20, -20, -20, -20, -20, -20, -10, 20, 20, 0, 0, 0, 0, 20, 20, 20, 30, 10, 0, 0, 10, 30, 20]
    private static let pstKEnd = [-50, -40, -30, -20, -20, -30, -40, -50, -30, -20, -10, 0, 0, -10, -20, -30, -30, -10, 20, 30, 30, 20, -10, -30, -30, -10, 30, 40, 40, 30, -10, -30, -30, -10, 30, 40, 40, 30, -10, -30, -30, -10, 20, 30, 30, 20, -10, -30, -30, -30, 0, 0, 0, 0, -30, -30, -50, -30, -30, -30, -30, -30, -30, -50]

    private var deadline: TimeInterval = 0
    private var stopped = false
    private var nodes = 0

    public init() {}

    /// The bot's move for the side to move, 0 if none. Call on a background queue.
    public func think(_ src: AvraChessBoard, level rawLevel: Int) -> Int {
        let level = max(0, min(3, rawLevel))
        let b = src.copy()
        var moves = b.legal()
        if moves.isEmpty { return 0 }
        if moves.count == 1 { return moves[0] }
        if Int.random(in: 0..<100) < Self.blunderPct[level] { return moves.randomElement() ?? moves[0] }
        deadline = Date().timeIntervalSince1970 + Self.timeMs[level] / 1000
        stopped = false
        var best = moves[0]
        order(b, &moves)
        for d in 1...Self.depth[level] {
            var alpha = -Self.inf, bestHere = 0, bestScore = -Self.inf
            var scored = [(Int, Int)]()
            for m in moves {
                b.make(m)
                var score = -search(b, d - 1, -Self.inf, Self.noise[level] > 0 ? Self.inf : -alpha, 1)
                b.unmake()
                if stopped { break }
                if Self.noise[level] > 0 { score += Int.random(in: -Self.noise[level]...Self.noise[level]) }
                scored.append((m, score))
                if score > bestScore { bestScore = score; bestHere = m }
                if score > alpha { alpha = score }
            }
            if bestHere != 0 { best = bestHere }
            if stopped { break }
            scored.sort { $0.1 > $1.1 }
            moves = scored.map { $0.0 }
            if bestScore > Self.mateScore - 1000 { break }
        }
        return best
    }

    /// Game review: exact scores of every legal move for the side to move, best first.
    public func analyse(_ src: AvraChessBoard, maxDepth: Int, ms: Double) -> [(Int, Int)] {
        let b = src.copy()
        var moves = b.legal()
        var result: [(Int, Int)] = []
        if moves.isEmpty { return result }
        order(b, &moves)
        let until = Date().timeIntervalSince1970 + ms / 1000
        for d in 1...maxDepth {
            deadline = d == 1 ? Double.greatestFiniteMagnitude : until
            stopped = false
            var scored: [(Int, Int)] = []
            for m in moves {
                b.make(m)
                let score = -search(b, d - 1, -Self.inf, Self.inf, 1)
                b.unmake()
                if stopped { break }
                scored.append((m, score))
            }
            if stopped { break }
            scored.sort { $0.1 > $1.1 }
            result = scored
            moves = scored.map { $0.0 }
            if scored[0].1 > Self.mateScore - 1000 || Date().timeIntervalSince1970 > until { break }
        }
        return result
    }

    public static func isMate(_ score: Int) -> Bool { abs(score) > mateScore - 1000 }

    private func search(_ b: AvraChessBoard, _ depth: Int, _ alphaIn: Int, _ beta: Int, _ ply: Int) -> Int {
        nodes += 1
        if nodes & 1023 == 0 && Date().timeIntervalSince1970 > deadline { stopped = true }
        if stopped { return 0 }
        if ply > 0 && (b.halfmove >= 100 || b.repetition(2)) { return 0 }
        let check = b.inCheck
        if depth <= 0 && !check { return quiesce(b, alphaIn, beta, 0) }
        var alpha = alphaIn
        var list = [Int]()
        b.pseudo(&list, capturesOnly: false)
        order(b, &list)
        let us = b.side
        var legal = 0
        for m in list {
            b.make(m)
            if b.inCheck(us) { b.unmake(); continue }
            legal += 1
            let score = -search(b, depth - 1, -beta, -alpha, ply + 1)
            b.unmake()
            if stopped { return 0 }
            if score >= beta { return beta }
            if score > alpha { alpha = score }
        }
        if legal == 0 { return check ? -Self.mateScore + ply : 0 }
        return alpha
    }

    private func quiesce(_ b: AvraChessBoard, _ alphaIn: Int, _ beta: Int, _ qply: Int) -> Int {
        let stand = Self.evaluate(b)
        if stand >= beta { return beta }
        var alpha = max(alphaIn, stand)
        if qply > 6 { return alpha }
        var list = [Int]()
        b.pseudo(&list, capturesOnly: true)
        order(b, &list)
        let us = b.side
        for m in list {
            b.make(m)
            if b.inCheck(us) { b.unmake(); continue }
            let score = -quiesce(b, -beta, -alpha, qply + 1)
            b.unmake()
            if score >= beta { return beta }
            if score > alpha { alpha = score }
        }
        return alpha
    }

    private func order(_ b: AvraChessBoard, _ moves: inout [Int]) {
        moves.sort { Self.orderScore(b, $0) > Self.orderScore(b, $1) }
    }

    private static func orderScore(_ b: AvraChessBoard, _ m: Int) -> Int {
        var s = 0
        if AvraChessBoard.flags(m) & AvraChessBoard.fCapture != 0 {
            var victim = abs(b.sq[AvraChessBoard.to(m)])
            if victim == 0 { victim = 1 }
            s += 10 * value[victim] - value[abs(b.sq[AvraChessBoard.from(m)])] / 10 + 10000
        }
        if AvraChessBoard.promo(m) == 5 { s += 9000 }
        return s
    }

    /// Static evaluation from the side to move's point of view, centipawns.
    public static func evaluate(_ b: AvraChessBoard) -> Int {
        var material = 0
        for s in 0..<64 {
            let t = abs(b.sq[s])
            if t != 6 && t != 1 { material += value[t] }
        }
        let endgame = material <= 1300
        var score = 0
        for s in 0..<64 {
            let p = b.sq[s]
            if p == 0 { continue }
            let t = abs(p)
            let idx = p > 0 ? (7 - (s >> 3)) * 8 + (s & 7) : (s >> 3) * 8 + (s & 7)
            var v = value[t]
            switch t {
            case 1: v += pstP[idx]
            case 2: v += pstN[idx]
            case 3: v += pstB[idx]
            case 4: v += pstR[idx]
            case 5: v += pstQ[idx]
            default: v += endgame ? pstKEnd[idx] : pstKMid[idx]
            }
            score += p > 0 ? v : -v
        }
        return score * b.side
    }
}
