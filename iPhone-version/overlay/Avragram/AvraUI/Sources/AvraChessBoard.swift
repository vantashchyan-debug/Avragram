import Foundation

/// Full chess rules (port of the Android com.avragramm.chess.ChessBoard): legal moves with castling,
/// en passant and promotion, check / mate / stalemate, repetition, 50 moves, insufficient material,
/// UCI and figurine SAN. Squares 0...63 (a1 = 0, h8 = 63); pieces 1...6 = P N B R Q K, white > 0.
/// A move is packed into an Int: from | to << 6 | promo << 12 | flags << 16.
public final class AvraChessBoard {
    public static let empty = 0, pawn = 1, knight = 2, bishop = 3, rook = 4, queen = 5, king = 6
    public static let white = 1, black = -1
    public static let fCapture = 1, fEp = 2, fCastle = 4, fDouble = 8
    public enum Status { case ongoing, mate, stalemate, repetition, fifty, material }

    public var sq = [Int](repeating: 0, count: 64)
    public var side = 1
    public var castling = 15
    public var ep = -1
    public var halfmove = 0
    public var fullmove = 1
    public var key: UInt64 = 0

    private var undoCaptured = [Int](repeating: 0, count: 1024)
    private var undoCastling = [Int](repeating: 0, count: 1024)
    private var undoEp = [Int](repeating: 0, count: 1024)
    private var undoHalf = [Int](repeating: 0, count: 1024)
    private var undoMove = [Int](repeating: 0, count: 1024)
    private var history = [UInt64](repeating: 0, count: 1025)
    private(set) public var ply = 0

    private static let zobrist: (piece: [[UInt64]], castle: [UInt64], ep: [UInt64], side: UInt64) = {
        var state: UInt64 = 0xA77A6E55
        func next() -> UInt64 {
            state &+= 0x9E3779B97F4A7C15
            var z = state
            z = (z ^ (z >> 30)) &* 0xBF58476D1CE4E5B9
            z = (z ^ (z >> 27)) &* 0x94D049BB133111EB
            return z ^ (z >> 31)
        }
        var piece = [[UInt64]]()
        for _ in 0..<13 { piece.append((0..<64).map { _ in next() }) }
        let castle = (0..<16).map { _ in next() }
        let ep = (0..<8).map { _ in next() }
        return (piece, castle, ep, next())
    }()

    public init() { reset() }

    public func copy() -> AvraChessBoard {
        let b = AvraChessBoard()
        b.sq = sq; b.side = side; b.castling = castling; b.ep = ep
        b.halfmove = halfmove; b.fullmove = fullmove; b.key = key; b.ply = ply
        b.undoCaptured = undoCaptured; b.undoCastling = undoCastling; b.undoEp = undoEp
        b.undoHalf = undoHalf; b.undoMove = undoMove; b.history = history
        return b
    }

    public func reset() {
        let back = [4, 2, 3, 5, 6, 3, 2, 4]
        sq = [Int](repeating: 0, count: 64)
        for f in 0..<8 {
            sq[f] = back[f]; sq[8 + f] = 1; sq[48 + f] = -1; sq[56 + f] = -back[f]
        }
        side = 1; castling = 15; ep = -1; halfmove = 0; fullmove = 1; ply = 0
        key = computeKey(); history[0] = key
    }

    public static func file(_ s: Int) -> Int { s & 7 }
    public static func rank(_ s: Int) -> Int { s >> 3 }
    public static func from(_ m: Int) -> Int { m & 63 }
    public static func to(_ m: Int) -> Int { (m >> 6) & 63 }
    public static func promo(_ m: Int) -> Int { (m >> 12) & 15 }
    public static func flags(_ m: Int) -> Int { (m >> 16) & 15 }
    static func pack(_ from: Int, _ to: Int, _ promo: Int, _ flags: Int) -> Int { from | to << 6 | promo << 12 | flags << 16 }

    private func computeKey() -> UInt64 {
        var k: UInt64 = 0
        for s in 0..<64 where sq[s] != 0 { k ^= Self.zobrist.piece[sq[s] + 6][s] }
        k ^= Self.zobrist.castle[castling]
        if ep >= 0 { k ^= Self.zobrist.ep[Self.file(ep)] }
        if side == -1 { k ^= Self.zobrist.side }
        return k
    }

    public var lastMove: Int { ply == 0 ? 0 : undoMove[ply - 1] }
    public func moveAt(_ i: Int) -> Int { undoMove[i] }

    // MARK: attacks

    private static let knightD = [(1, 2), (2, 1), (2, -1), (1, -2), (-1, -2), (-2, -1), (-2, 1), (-1, 2)]
    private static let kingD = [(1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0), (-1, -1), (0, -1), (1, -1)]
    private static let bishopD = [(1, 1), (1, -1), (-1, 1), (-1, -1)]
    private static let rookD = [(1, 0), (-1, 0), (0, 1), (0, -1)]

    public func attacked(_ s: Int, by: Int) -> Bool {
        let f = s & 7, r = s >> 3
        let pr = r - by
        if pr >= 0 && pr < 8 {
            if f > 0 && sq[pr * 8 + f - 1] == by { return true }
            if f < 7 && sq[pr * 8 + f + 1] == by { return true }
        }
        for d in Self.knightD {
            let nf = f + d.0, nr = r + d.1
            if nf >= 0 && nf < 8 && nr >= 0 && nr < 8 && sq[nr * 8 + nf] == by * 2 { return true }
        }
        for d in Self.kingD {
            let nf = f + d.0, nr = r + d.1
            if nf >= 0 && nf < 8 && nr >= 0 && nr < 8 && sq[nr * 8 + nf] == by * 6 { return true }
        }
        for (dirs, a, b) in [(Self.bishopD, 3, 5), (Self.rookD, 4, 5)] {
            for d in dirs {
                var nf = f + d.0, nr = r + d.1
                while nf >= 0 && nf < 8 && nr >= 0 && nr < 8 {
                    let p = sq[nr * 8 + nf]
                    if p != 0 {
                        if p == by * a || p == by * b { return true }
                        break
                    }
                    nf += d.0; nr += d.1
                }
            }
        }
        return false
    }

    public func kingSquare(_ color: Int) -> Int {
        for s in 0..<64 where sq[s] == color * 6 { return s }
        return -1
    }

    public func inCheck(_ color: Int) -> Bool {
        let k = kingSquare(color)
        return k >= 0 && attacked(k, by: -color)
    }

    public var inCheck: Bool { inCheck(side) }

    // MARK: generation

    public func pseudo(_ out: inout [Int], capturesOnly: Bool) {
        let us = side
        for s in 0..<64 {
            let p = sq[s]
            if p == 0 || (p > 0 ? 1 : -1) != us { continue }
            let t = abs(p), f = s & 7, r = s >> 3
            if t == 1 {
                let dir = us, start = us == 1 ? 1 : 6, last = us == 1 ? 7 : 0
                let nr = r + dir
                if nr < 0 || nr > 7 { continue }
                let one = nr * 8 + f
                if sq[one] == 0 {
                    if nr == last { addPromos(&out, s, one, 0) }
                    else if !capturesOnly {
                        out.append(Self.pack(s, one, 0, 0))
                        let two = (r + 2 * dir) * 8 + f
                        if r == start && sq[two] == 0 { out.append(Self.pack(s, two, 0, Self.fDouble)) }
                    }
                }
                for df in [-1, 1] {
                    let nf = f + df
                    if nf < 0 || nf > 7 { continue }
                    let c = nr * 8 + nf
                    if sq[c] != 0 && (sq[c] > 0 ? 1 : -1) != us {
                        if nr == last { addPromos(&out, s, c, Self.fCapture) } else { out.append(Self.pack(s, c, 0, Self.fCapture)) }
                    } else if c == ep {
                        out.append(Self.pack(s, c, 0, Self.fCapture | Self.fEp))
                    }
                }
            } else if t == 2 || t == 6 {
                for d in (t == 2 ? Self.knightD : Self.kingD) {
                    let nf = f + d.0, nr = r + d.1
                    if nf < 0 || nf > 7 || nr < 0 || nr > 7 { continue }
                    let c = nr * 8 + nf
                    if sq[c] == 0 {
                        if !capturesOnly { out.append(Self.pack(s, c, 0, 0)) }
                    } else if (sq[c] > 0 ? 1 : -1) != us {
                        out.append(Self.pack(s, c, 0, Self.fCapture))
                    }
                }
                if t == 6 && !capturesOnly { addCastles(&out, s) }
            } else {
                let dirs = t == 3 ? Self.bishopD : t == 4 ? Self.rookD : Self.bishopD + Self.rookD
                for d in dirs {
                    var nf = f + d.0, nr = r + d.1
                    while nf >= 0 && nf < 8 && nr >= 0 && nr < 8 {
                        let c = nr * 8 + nf
                        if sq[c] == 0 {
                            if !capturesOnly { out.append(Self.pack(s, c, 0, 0)) }
                        } else {
                            if (sq[c] > 0 ? 1 : -1) != us { out.append(Self.pack(s, c, 0, Self.fCapture)) }
                            break
                        }
                        nf += d.0; nr += d.1
                    }
                }
            }
        }
    }

    private func addPromos(_ out: inout [Int], _ from: Int, _ to: Int, _ flags: Int) {
        for p in [5, 2, 4, 3] { out.append(Self.pack(from, to, p, flags)) }
    }

    private func addCastles(_ out: inout [Int], _ k: Int) {
        if side == 1 && k == 4 {
            if castling & 1 != 0 && sq[5] == 0 && sq[6] == 0 && sq[7] == 4 && !attacked(4, by: -1) && !attacked(5, by: -1) && !attacked(6, by: -1) {
                out.append(Self.pack(4, 6, 0, Self.fCastle))
            }
            if castling & 2 != 0 && sq[3] == 0 && sq[2] == 0 && sq[1] == 0 && sq[0] == 4 && !attacked(4, by: -1) && !attacked(3, by: -1) && !attacked(2, by: -1) {
                out.append(Self.pack(4, 2, 0, Self.fCastle))
            }
        } else if side == -1 && k == 60 {
            if castling & 4 != 0 && sq[61] == 0 && sq[62] == 0 && sq[63] == -4 && !attacked(60, by: 1) && !attacked(61, by: 1) && !attacked(62, by: 1) {
                out.append(Self.pack(60, 62, 0, Self.fCastle))
            }
            if castling & 8 != 0 && sq[59] == 0 && sq[58] == 0 && sq[57] == 0 && sq[56] == -4 && !attacked(60, by: 1) && !attacked(59, by: 1) && !attacked(58, by: 1) {
                out.append(Self.pack(60, 58, 0, Self.fCastle))
            }
        }
    }

    public func legal() -> [Int] {
        var list = [Int]()
        list.reserveCapacity(48)
        pseudo(&list, capturesOnly: false)
        var out = [Int]()
        let us = side
        for m in list {
            make(m)
            if !inCheck(us) { out.append(m) }
            unmake()
        }
        return out
    }

    // MARK: make / unmake

    private static let castleMask: [Int] = {
        var m = [Int](repeating: 15, count: 64)
        m[4] = 15 & ~3; m[7] = 15 & ~1; m[0] = 15 & ~2
        m[60] = 15 & ~12; m[63] = 15 & ~4; m[56] = 15 & ~8
        return m
    }()

    private static func rookMove(_ to: Int) -> (Int, Int) {
        switch to {
        case 6: return (7, 5)
        case 2: return (0, 3)
        case 62: return (63, 61)
        default: return (56, 59)
        }
    }

    public func make(_ m: Int) {
        let from = Self.from(m), to = Self.to(m), fl = Self.flags(m), pr = Self.promo(m)
        let p = sq[from]
        undoMove[ply] = m; undoCastling[ply] = castling; undoEp[ply] = ep; undoHalf[ply] = halfmove
        var k = key
        let z = Self.zobrist
        k ^= z.castle[castling]
        if ep >= 0 { k ^= z.ep[ep & 7] }
        var captured: Int
        if fl & Self.fEp != 0 {
            let cs = to - 8 * side
            captured = sq[cs]
            sq[cs] = 0
            k ^= z.piece[captured + 6][cs]
        } else {
            captured = sq[to]
            if captured != 0 { k ^= z.piece[captured + 6][to] }
        }
        undoCaptured[ply] = captured
        k ^= z.piece[p + 6][from]
        sq[from] = 0
        let placed = pr != 0 ? pr * side : p
        sq[to] = placed
        k ^= z.piece[placed + 6][to]
        if fl & Self.fCastle != 0 {
            let (rf, rt) = Self.rookMove(to)
            let rook = sq[rf]
            sq[rf] = 0; sq[rt] = rook
            k ^= z.piece[rook + 6][rf] ^ z.piece[rook + 6][rt]
        }
        castling &= Self.castleMask[from] & Self.castleMask[to]
        ep = fl & Self.fDouble != 0 ? (from + to) / 2 : -1
        halfmove = (abs(p) == 1 || captured != 0) ? 0 : halfmove + 1
        if side == -1 { fullmove += 1 }
        side = -side
        k ^= z.castle[castling]
        if ep >= 0 { k ^= z.ep[ep & 7] }
        k ^= z.side
        key = k
        ply += 1
        history[ply] = key
    }

    public func unmake() {
        if ply == 0 { return }
        ply -= 1
        let m = undoMove[ply]
        let from = Self.from(m), to = Self.to(m), fl = Self.flags(m), pr = Self.promo(m)
        side = -side
        if side == -1 { fullmove -= 1 }
        sq[from] = pr != 0 ? side : sq[to]
        if fl & Self.fEp != 0 {
            sq[to] = 0
            sq[to - 8 * side] = undoCaptured[ply]
        } else {
            sq[to] = undoCaptured[ply]
        }
        if fl & Self.fCastle != 0 {
            let (rf, rt) = Self.rookMove(to)
            sq[rf] = sq[rt]; sq[rt] = 0
        }
        castling = undoCastling[ply]; ep = undoEp[ply]; halfmove = undoHalf[ply]
        key = history[ply]
    }

    // MARK: result

    public func repetition(_ times: Int) -> Bool {
        var count = 1
        var i = ply - 2
        while i >= 0 && i >= ply - halfmove {
            if history[i] == key {
                count += 1
                if count >= times { return true }
            }
            i -= 2
        }
        return false
    }

    public var insufficient: Bool {
        var minors = 0
        for s in 0..<64 {
            let t = abs(sq[s])
            if t == 1 || t == 4 || t == 5 { return false }
            if t == 2 || t == 3 { minors += 1 }
        }
        return minors <= 1
    }

    public func status() -> Status {
        if legal().isEmpty { return inCheck ? .mate : .stalemate }
        if insufficient { return .material }
        if halfmove >= 100 { return .fifty }
        if repetition(3) { return .repetition }
        return .ongoing
    }

    // MARK: notation

    public static func square(_ s: Int) -> String {
        let files = Array("abcdefgh")
        return "\(files[s & 7])\((s >> 3) + 1)"
    }

    public static func uci(_ m: Int) -> String {
        var s = square(from(m)) + square(to(m))
        let p = promo(m)
        if p != 0 { s += p == 2 ? "n" : p == 3 ? "b" : p == 4 ? "r" : "q" }
        return s
    }

    public func parseUci(_ u: String) -> Int {
        let c = Array(u.utf8)
        if c.count < 4 { return 0 }
        let f = Int(c[0]) - 97 + (Int(c[1]) - 49) * 8
        let t = Int(c[2]) - 97 + (Int(c[3]) - 49) * 8
        var pr = 0
        if c.count > 4 {
            switch c[4] {
            case 110: pr = 2
            case 98: pr = 3
            case 114: pr = 4
            default: pr = 5
            }
        }
        for m in legal() where Self.from(m) == f && Self.to(m) == t && Self.promo(m) == pr { return m }
        return 0
    }

    private static let figurines = Array(" ♙♘♗♖♕♔")

    /// Figurine SAN ("♘f3", "exd5", "O-O", "e8=♕+"); call BEFORE making the move.
    public func san(_ m: Int) -> String {
        let from = Self.from(m), to = Self.to(m), fl = Self.flags(m)
        let t = abs(sq[from])
        var b = ""
        if fl & Self.fCastle != 0 {
            b = (to & 7) == 6 ? "O-O" : "O-O-O"
        } else {
            if t == 1 {
                if fl & Self.fCapture != 0 { b.append(Array("abcdefgh")[from & 7]) }
            } else {
                b.append(Self.figurines[t])
                var sameFile = false, sameRank = false, other = false
                for o in legal() where o != m && Self.to(o) == to && abs(sq[Self.from(o)]) == t {
                    other = true
                    if (Self.from(o) & 7) == (from & 7) { sameFile = true }
                    if (Self.from(o) >> 3) == (from >> 3) { sameRank = true }
                }
                if other {
                    if !sameFile { b.append(Array("abcdefgh")[from & 7]) }
                    else if !sameRank { b += "\((from >> 3) + 1)" }
                    else { b += Self.square(from) }
                }
            }
            if fl & Self.fCapture != 0 { b += "x" }
            b += Self.square(to)
            if Self.promo(m) != 0 { b += "="; b.append(Self.figurines[Self.promo(m)]) }
        }
        make(m)
        if inCheck { b += legal().isEmpty ? "#" : "+" }
        unmake()
        return b
    }
}
