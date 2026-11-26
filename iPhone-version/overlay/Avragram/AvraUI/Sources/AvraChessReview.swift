import Foundation
import SwiftUI
import SGSwiftUI

/// Game review (port of Android ChessReview): every move is analysed and labelled — brilliant, great,
/// best, good, okay, book, inaccuracy, mistake, blunder — by the winning chance it lost; accuracy per side.
enum AvraChessReview {
    static let names = ["Бриллиантовый", "Отличный", "Лучший", "Хороший", "Базовый", "По книге", "Неточность", "Плохой", "Зевок"]
    static let symbols = ["!!", "!", "★", "✓", "•", "≡", "?!", "?", "??"]
    static let colors: [UInt32] = [0x1BADA6, 0x5C8BB0, 0x81B64C, 0x77A35A, 0x97AF8B, 0xA88865, 0xF0C02F, 0xFF8F3C, 0xE8382B]
    static let openings: [String] = [
"e2e4.e7e5.g1f3.b8c6.f1b5.a7a6.b5a4.g8f6.e1g1.f8e7.f1e1.b7b5.a4b3.d7d6.c2c3.e8g8",
        "e2e4.e7e5.g1f3.b8c6.f1b5.g8f6.e1g1.f6e4.d2d4.e4d6.b5c6.d7c6.d4e5.d6f5",
        "e2e4.e7e5.g1f3.b8c6.f1c4.f8c5.c2c3.g8f6.d2d3.d7d6.e1g1.e8g8",
        "e2e4.e7e5.g1f3.b8c6.f1c4.g8f6.f3g5.d7d5.e4d5.c6a5.c4b5.c7c6.d5c6.b7c6",
        "e2e4.e7e5.g1f3.b8c6.d2d4.e5d4.f3d4.g8f6.d4c6.b7c6.e4e5.d8e7",
        "e2e4.e7e5.g1f3.g8f6.f3e5.d7d6.e5f3.f6e4.d2d4.d6d5.f1d3",
        "e2e4.e7e5.b1c3.g8f6.f2f4.d7d5.f4e5.f6e4",
        "e2e4.e7e5.f2f4.e5f4.g1f3.g7g5",
        "e2e4.c7c5.g1f3.d7d6.d2d4.c5d4.f3d4.g8f6.b1c3.a7a6.c1e3.e7e5.d4b3.c8e6",
        "e2e4.c7c5.g1f3.b8c6.d2d4.c5d4.f3d4.g8f6.b1c3.e7e5.d4b5.d7d6",
        "e2e4.c7c5.g1f3.e7e6.d2d4.c5d4.f3d4.b8c6.b1c3.d8c7",
        "e2e4.c7c5.b1c3.b8c6.g2g3.g7g6.f1g2.f8g7.d2d3.d7d6",
        "e2e4.c7c5.c2c3.g8f6.e4e5.f6d5.d2d4.c5d4.g1f3",
        "e2e4.c7c5.g1f3.d7d6.d2d4.c5d4.f3d4.g8f6.b1c3.g7g6.c1e3.f8g7.f2f3.e8g8",
        "e2e4.e7e6.d2d4.d7d5.b1c3.f8b4.e4e5.c7c5.a2a3.b4c3.b2c3.g8e7",
        "e2e4.e7e6.d2d4.d7d5.e4e5.c7c5.c2c3.b8c6.g1f3.d8b6",
        "e2e4.c7c6.d2d4.d7d5.e4e5.c8f5.g1f3.e7e6.f1e2",
        "e2e4.c7c6.d2d4.d7d5.b1c3.d5e4.c3e4.c8f5.e4g3.f5g6.h2h4.h7h6",
        "e2e4.d7d5.e4d5.d8d5.b1c3.d5a5.d2d4.g8f6.g1f3.c7c6",
        "e2e4.g8f6.e4e5.f6d5.d2d4.d7d6.g1f3",
        "e2e4.d7d6.d2d4.g8f6.b1c3.g7g6.f2f4.f8g7.g1f3",
        "e2e4.g7g6.d2d4.f8g7.b1c3.d7d6",
        "d2d4.d7d5.c2c4.e7e6.b1c3.g8f6.c1g5.f8e7.e2e3.e8g8.g1f3.h7h6",
        "d2d4.d7d5.c2c4.d5c4.g1f3.g8f6.e2e3.e7e6.f1c4.c7c5.e1g1.a7a6",
        "d2d4.d7d5.c2c4.c7c6.g1f3.g8f6.b1c3.d5c4.a2a4.c8f5",
        "d2d4.g8f6.c2c4.g7g6.b1c3.f8g7.e2e4.d7d6.g1f3.e8g8.f1e2.e7e5.e1g1.b8c6",
        "d2d4.g8f6.c2c4.e7e6.b1c3.f8b4.e2e3.e8g8.f1d3.d7d5.g1f3.c7c5",
        "d2d4.g8f6.c2c4.e7e6.g1f3.b7b6.g2g3.c8a6.b2b3.f8b4.c1d2.b4e7",
        "d2d4.g8f6.c2c4.g7g6.b1c3.d7d5.c4d5.f6d5.e2e4.d5c3.b2c3.f8g7",
        "d2d4.g8f6.c2c4.c7c5.d4d5.e7e6.b1c3.e6d5.c4d5.d7d6",
        "d2d4.f7f5.g2g3.g8f6.f1g2.g7g6.g1f3.f8g7.e1g1.e8g8.c2c4.d7d6",
        "d2d4.d7d5.c1f4.g8f6.e2e3.e7e6.g1f3.c7c5.c2c3.b8c6.b1d2.f8d6.f4g3.e8g8",
        "d2d4.g8f6.c1f4.d7d5.e2e3.c7c5.c2c3.b8c6.b1d2.e7e6.g1f3.f8d6.f4g3.e8g8",
        "d2d4.d7d5.c2c4.e7e6.g1f3.g8f6.g2g3.f8e7.f1g2.e8g8.e1g1.d5c4",
        "c2c4.e7e5.b1c3.g8f6.g2g3.d7d5.c4d5.f6d5.f1g2.d5b6",
        "c2c4.g8f6.b1c3.e7e6.e2e4.d7d5.e4e5",
        "c2c4.c7c5.g1f3.b8c6.b1c3.g7g6.g2g3.f8g7.f1g2.g8f6.e1g1.e8g8",
        "g1f3.d7d5.g2g3.g8f6.f1g2.c7c6.e1g1.c8g4.d2d3.b8d7",
        "g1f3.g8f6.c2c4.g7g6.b1c3.f8g7.e2e4.d7d6.d2d4.e8g8",
        "e2e4.e7e5.g1f3.d7d6.d2d4.e5d4.f3d4.g8f6.b1c3.f8e7",
        "e2e4.e7e5.g1f3.b8c6.b1c3.g8f6.d2d4.e5d4.f3d4.f8b4.d4c6.b7c6.f1d3.d7d5",
        "e2e4.e7e5.f1c4.g8f6.d2d3.c7c6.g1f3.d7d5.c4b3",
        "b2b3.e7e5.c1b2.b8c6.e2e3.d7d5.f1b5.f8d6",
        "g2g3.d7d5.f1g2.e7e5.d2d3.g8f6.g1f3.b8c6.e1g1.f8e7",
        "e2e4.e7e5.g1f3.b8c6.f1c4.f8c5.b2b4.c5b4.c2c3.b4a5.d2d4.e5d4.e1g1",
        "d2d4.d7d5.c2c4.e7e6.b1c3.c7c6.e2e4.d5e4.c3e4.f8b4",
        "d2d4.g8f6.c2c4.e7e6.g2g3.d7d5.f1g2.f8e7.g1f3.e8g8.e1g1.d5c4.d1c2.a7a6",
        "d2d4.g8f6.g1f3.e7e6.e2e3.c7c5.f1d3.d7d5.b2b3.b8c6.e1g1.f8d6.c1b2.e8g8",
        "e2e4.c7c5.g1f3.d7d6.f1b5.c8d7.b5d7.d8d7.e1g1.b8c6.c2c3.g8f6",
        "e2e4.c7c5.g1f3.e7e6.d2d4.c5d4.f3d4.a7a6.f1d3.g8f6.e1g1.d8c7",
        "e2e4.e7e5.g1f3.b8c6.f1b5.a7a6.b5c6.d7c6.e1g1.f7f6.d2d4",
        "d2d4.d7d5.g1f3.g8f6.c2c4.e7e6.b1c3.c7c5.c4d5.f6d5",
        "e2e4.c7c5.g1f3.b8c6.f1b5.g7g6.e1g1.f8g7.f1e1.e7e5",
        "d2d4.g8f6.c2c4.e7e6.g1f3.d7d5.b1c3.f8e7.c1f4.e8g8.e2e3.c7c5"
    ]

    struct Result {
        var kind: [Int] = []
        var best: [Int] = []
        var eval: [Int] = []
        var accWhite: Double = 0
        var accBlack: Double = 0
        var counts = [[Int]](repeating: [Int](repeating: 0, count: 9), count: 2)
    }

    static func winChance(_ cp: Int) -> Double {
        if AvraChessAi.isMate(cp) { return cp > 0 ? 100 : 0 }
        let c = Double(max(-1500, min(1500, cp)))
        return 50 + 50 * (2 / (1 + exp(-0.00368208 * c)) - 1)
    }

    private static func inBook(_ moves: [String], _ upto: Int) -> Bool {
        let prefix = moves[0...upto].joined(separator: ".")
        return openings.contains { $0 == prefix || $0.hasPrefix(prefix + ".") }
    }

    private static func sacrifice(_ b: AvraChessBoard, _ m: Int) -> Bool {
        let from = AvraChessBoard.from(m), to = AvraChessBoard.to(m)
        let t = abs(b.sq[from])
        guard t >= 2 && t <= 5 && AvraChessBoard.promo(m) == 0 else { return false }
        let values = [0, 1, 3, 3, 5, 9, 100]
        let gain = b.sq[to] == 0 ? 0 : values[abs(b.sq[to])]
        let worth = values[t]
        if worth - gain < 2 { return false }
        let us = b.side
        b.make(m)
        defer { b.unmake() }
        let defended = b.attacked(to, by: us)
        var caps: [Int] = []
        b.pseudo(&caps, capturesOnly: true)
        var lowest = 99
        for c in caps where AvraChessBoard.to(c) == to {
            let attacker = abs(b.sq[AvraChessBoard.from(c)])
            b.make(c)
            let legal = !b.inCheck(-us)
            b.unmake()
            if legal { lowest = min(lowest, values[attacker]) }
        }
        if lowest == 99 { return false }
        return !defended || lowest < worth
    }

    static func analyse(_ moves: [String], progress: @escaping (Double) -> Void) -> Result {
        var r = Result()
        let ai = AvraChessAi()
        let b = AvraChessBoard()
        var sums = [0.0, 0.0], counts = [0, 0]
        var book = true
        for i in 0..<moves.count {
            progress(Double(i) / Double(max(1, moves.count)))
            let m = b.parseUci(moves[i])
            if m == 0 { break }
            let mover = b.side
            let scores = ai.analyse(b, maxDepth: 5, ms: 320)
            let bestScore = scores.first?.1 ?? 0
            let second = scores.count > 1 ? scores[1].1 : bestScore
            let played = scores.first { $0.0 == m }?.1 ?? bestScore
            let bestMove = scores.first?.0 ?? 0
            let wBest = winChance(bestScore), wPlayed = winChance(played)
            var loss = max(0, wBest - wPlayed)
            book = book && inBook(moves, i)
            let kind: Int
            if book {
                kind = 5; loss = 0
            } else if scores.count <= 1 || m == bestMove || loss < 1.5 {
                if wPlayed >= 50 && wBest < 97 && sacrifice(b, m) { kind = 0 }
                else if scores.count > 1 && wBest - winChance(second) >= 18 && wBest > 15 && wBest < 95 { kind = 1 }
                else { kind = 2 }
            } else if loss < 5 { kind = 3 }
            else if loss < 10 { kind = 4 }
            else if loss < 18 { kind = 6 }
            else if loss < 28 { kind = 7 }
            else { kind = 8 }
            r.kind.append(kind)
            r.best.append(bestMove)
            r.eval.append(played * mover)
            let side = mover == 1 ? 0 : 1
            r.counts[side][kind] += 1
            sums[side] += max(0, min(100, 103.1668 * exp(-0.04354 * loss) - 3.1669))
            counts[side] += 1
            b.make(m)
        }
        r.accWhite = counts[0] == 0 ? 0 : sums[0] / Double(counts[0])
        r.accBlack = counts[1] == 0 ? 0 : sums[1] / Double(counts[1])
        progress(1)
        return r
    }
}

final class AvraReviewModel: ObservableObject {
    let moves: [String]
    @Published var result: AvraChessReview.Result? = nil
    @Published var progress = 0.0
    @Published var ply: Int
    let positions: [AvraChessBoard]
    let sans: [String]

    init(moves: [String]) {
        var ok: [String] = []
        var pos: [AvraChessBoard] = []
        var sans: [String] = []
        let b = AvraChessBoard()
        pos.append(b.copy())
        for u in moves {
            let m = b.parseUci(u)
            if m == 0 { break }
            sans.append(b.san(m))
            b.make(m)
            pos.append(b.copy())
            ok.append(u)
        }
        self.moves = ok
        self.positions = pos
        self.sans = sans
        self.ply = ok.count
        let list = ok
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            let r = AvraChessReview.analyse(list) { p in DispatchQueue.main.async { self?.progress = p } }
            DispatchQueue.main.async { self?.result = r }
        }
    }
}

struct AvraChessReviewScreen: View {
    @StateObject private var model: AvraReviewModel
    @StateObject private var boardModel: AvraChessGame
    @Environment(\.sgTheme) private var theme

    init(moves: [String]) {
        _model = StateObject(wrappedValue: AvraReviewModel(moves: moves))
        _boardModel = StateObject(wrappedValue: AvraChessGame(level: 0))
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                if let r = model.result {
                    HStack {
                        Text(String(format: "%.1f", r.accWhite)).font(.title2.bold()).frame(maxWidth: .infinity).padding(8).background(Color.white).foregroundColor(.black).cornerRadius(10)
                        Text("Точность").font(.footnote).foregroundColor(theme.secondaryTextColor)
                        Text(String(format: "%.1f", r.accBlack)).font(.title2.bold()).frame(maxWidth: .infinity).padding(8).background(Color(white: 0.15)).foregroundColor(.white).cornerRadius(10)
                    }
                } else {
                    ProgressView(value: model.progress) { Text("Анализ партии… \(Int(model.progress * 100))%") }
                }
                AvraChessBoardView(game: boardModel).allowsHitTesting(false)
                Text(info()).font(.headline).multilineTextAlignment(.center)
                HStack {
                    Button(action: { go(0) }) { Image(systemName: "backward.end.fill").frame(maxWidth: .infinity) }
                    Button(action: { go(model.ply - 1) }) { Image(systemName: "chevron.left").frame(maxWidth: .infinity) }
                    Button(action: { go(model.ply + 1) }) { Image(systemName: "chevron.right").frame(maxWidth: .infinity) }
                    Button(action: { go(model.moves.count) }) { Image(systemName: "forward.end.fill").frame(maxWidth: .infinity) }
                }
                .buttonStyle(.bordered)
                if let r = model.result {
                    VStack(spacing: 4) {
                        ForEach(0..<9, id: \.self) { k in
                            HStack {
                                Text(AvraChessReview.symbols[k]).font(.caption.bold()).foregroundColor(.white).frame(width: 26, height: 22)
                                    .background(Capsule().fill(AvraChessStyle.color(AvraChessReview.colors[k])))
                                Text(AvraChessReview.names[k])
                                Spacer()
                                Text("\(r.counts[0][k])").bold().frame(width: 40)
                                Text("\(r.counts[1][k])").bold().frame(width: 40)
                            }
                        }
                    }
                    .padding(12).background(theme.secondaryBackgroundColor).cornerRadius(14)
                }
            }
            .padding(16)
        }
        .background(theme.backgroundColor.ignoresSafeArea())
        .onAppear { go(model.ply) }
        .sgNavigationTitle("Обзор игры")
    }

    private func go(_ target: Int) {
        let t = max(0, min(model.moves.count, target))
        model.ply = t
        boardModel.show(model.positions[t])
    }

    private func info() -> String {
        guard model.ply > 0 else { return "Начальная позиция" }
        let i = model.ply - 1
        var s = "\(i / 2 + 1)\(i % 2 == 0 ? "." : "…") \(model.sans[i])"
        if let r = model.result, i < r.kind.count {
            s += " — " + AvraChessReview.symbols[r.kind[i]] + " " + AvraChessReview.names[r.kind[i]]
            let best = r.best[i]
            if best != 0 && r.kind[i] >= 6 {
                s += "\nЛучше было " + model.positions[i].copy().san(best)
            }
        }
        return s
    }
}
