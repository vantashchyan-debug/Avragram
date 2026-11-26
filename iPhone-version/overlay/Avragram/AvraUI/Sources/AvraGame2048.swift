import Foundation
import SwiftUI
import SGSwiftUI

/// Avra48: 2048 on a 4×4 board (5×5 on Easy), "Keep going" after 2048.
final class Avra2048Model: ObservableObject {
    struct Tile: Identifiable, Equatable { let id: Int; var value: Int; var x: Int; var y: Int; var merged = false }
    @Published var phase: AvraGamePhase = .menu
    @Published var difficulty: AvraDifficulty = .normal
    @Published private(set) var tiles: [Tile] = []
    @Published private(set) var score = 0
    @Published private(set) var won = false
    private var nextId = 1
    var size: Int { difficulty == .easy ? 5 : 4 }

    func start() {
        tiles = []; score = 0; won = false
        spawn(); spawn()
        phase = .playing
    }

    private func spawn() {
        var free: [(Int, Int)] = []
        for y in 0..<size { for x in 0..<size where !tiles.contains(where: { $0.x == x && $0.y == y }) { free.append((x, y)) } }
        guard let p = free.randomElement() else { return }
        let four = Double.random(in: 0..<1) < (difficulty == .hard ? 0.25 : 0.1)
        tiles.append(Tile(id: nextId, value: four ? 4 : 2, x: p.0, y: p.1))
        nextId += 1
    }

    func swipe(_ d: AvraSwipe) {
        guard phase == .playing else { return }
        var moved = false
        var grid = [[Int?]](repeating: [Int?](repeating: nil, count: size), count: size)
        for (i, t) in tiles.enumerated() { grid[t.y][t.x] = i }
        var result = tiles.map { var t = $0; t.merged = false; return t }
        var removed = Set<Int>()
        for line in 0..<size {
            var cells: [(Int, Int)] = []
            for k in 0..<size {
                switch d {
                case .left: cells.append((k, line))
                case .right: cells.append((size - 1 - k, line))
                case .up: cells.append((line, k))
                case .down: cells.append((line, size - 1 - k))
                }
            }
            var target = 0
            var lastIndex: Int? = nil
            for (cx, cy) in cells {
                guard let i = grid[cy][cx] else { continue }
                if let li = lastIndex, result[li].value == result[i].value, !result[li].merged {
                    result[li].value *= 2
                    result[li].merged = true
                    score += result[li].value
                    if result[li].value == 2048 && !won { won = true }
                    result[i].x = result[li].x; result[i].y = result[li].y
                    removed.insert(result[i].id)
                    moved = true
                    lastIndex = nil
                    continue
                }
                let (tx, ty) = cells[target]
                if result[i].x != tx || result[i].y != ty { moved = true }
                result[i].x = tx; result[i].y = ty
                lastIndex = i
                target += 1
            }
        }
        guard moved else { return }
        withAnimation(.easeOut(duration: 0.12)) { tiles = result }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.12) {
            withAnimation(.spring(response: 0.25, dampingFraction: 0.6)) {
                self.tiles.removeAll { removed.contains($0.id) }
                self.spawn()
            }
            if !self.canMove() { self.phase = .over }
        }
        AvraHaptics.tap()
    }

    private func canMove() -> Bool {
        if tiles.count < size * size { return true }
        for a in tiles {
            for b in tiles where b.value == a.value && abs(a.x - b.x) + abs(a.y - b.y) == 1 { return true }
        }
        return false
    }
}

struct Avra2048Screen: View {
    @StateObject var m = Avra2048Model()
    @Environment(\.sgTheme) var theme

    var body: some View {
        AvraGameShell(kind: .g2048, phase: $m.phase, difficulty: $m.difficulty, score: m.score,
                      onStart: { m.start() }, onPause: { m.phase = .paused }, onResume: { m.phase = .playing }) {
            GeometryReader { geo in
                let side = min(geo.size.width, geo.size.height)
                let gap: CGFloat = 8
                let cell = (side - gap * CGFloat(m.size + 1)) / CGFloat(m.size)
                ZStack(alignment: .topLeading) {
                    RoundedRectangle(cornerRadius: 14).fill(theme.secondaryBackgroundColor)
                    ForEach(0..<(m.size * m.size), id: \.self) { i in
                        RoundedRectangle(cornerRadius: 8).fill(theme.separatorColor.opacity(0.5))
                            .frame(width: cell, height: cell)
                            .offset(x: gap + CGFloat(i % m.size) * (cell + gap), y: gap + CGFloat(i / m.size) * (cell + gap))
                    }
                    ForEach(m.tiles) { t in
                        RoundedRectangle(cornerRadius: 8)
                            .fill(color(t.value))
                            .overlay(Text("\(t.value)").font(.system(size: cell * (t.value >= 1000 ? 0.28 : 0.38), weight: .heavy, design: .rounded))
                                .foregroundColor(t.value <= 4 ? theme.primaryTextColor : .white))
                            .frame(width: cell, height: cell)
                            .scaleEffect(t.merged ? 1.12 : 1)
                            .offset(x: gap + CGFloat(t.x) * (cell + gap), y: gap + CGFloat(t.y) * (cell + gap))
                            .transition(.scale)
                    }
                }
                .frame(width: side, height: side)
                .contentShape(Rectangle())
                .avraSwipe { m.swipe($0) }
            }
            .aspectRatio(1, contentMode: .fit)
            if m.won {
                Text("🎉 2048! Играем дальше").font(.headline).foregroundColor(theme.accentColor)
            }
        }
    }

    private func color(_ v: Int) -> Color {
        let steps = max(0, Int(log2(Double(v))) - 1)
        let hue = (0.12 - Double(steps) * 0.035).truncatingRemainder(dividingBy: 1)
        return v <= 4 ? theme.accentColor.opacity(v == 2 ? 0.2 : 0.35) : Color(hue: hue < 0 ? hue + 1 : hue, saturation: 0.75, brightness: 0.92)
    }
}
