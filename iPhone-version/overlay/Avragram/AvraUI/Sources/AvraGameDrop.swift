import Foundation
import SwiftUI
import SGSwiftUI

/// AvraDrop: falling blocks. Tap rotates, swipe left/right moves, swipe down drops.
final class AvraDropModel: ObservableObject {
    static let w = 10, h = 20
    static let shapes: [[(Int, Int)]] = [
        [(0, 1), (1, 1), (2, 1), (3, 1)], [(0, 0), (1, 0), (0, 1), (1, 1)], [(1, 0), (0, 1), (1, 1), (2, 1)],
        [(0, 0), (0, 1), (1, 1), (2, 1)], [(2, 0), (0, 1), (1, 1), (2, 1)], [(1, 0), (2, 0), (0, 1), (1, 1)], [(0, 0), (1, 0), (1, 1), (2, 1)]
    ]
    @Published var phase: AvraGamePhase = .menu { didSet { phaseChanged() } }
    @Published var difficulty: AvraDifficulty = .normal
    @Published private(set) var grid = [Int](repeating: 0, count: 200)
    @Published private(set) var cells: [(Int, Int)] = []
    @Published private(set) var kind = 0
    @Published private(set) var score = 0
    private var px = 3, py = 0
    private var lines = 0
    private let ticker = AvraTicker()

    func start() {
        grid = [Int](repeating: 0, count: 200); score = 0; lines = 0
        spawn()
        phase = .playing
    }

    private var interval: TimeInterval { max(0.12, [0.7, 0.5, 0.33][difficulty.rawValue] - Double(lines / 10) * 0.05) }

    private func phaseChanged() {
        if phase == .playing { ticker.start(interval) { [weak self] in self?.fall() } } else { ticker.stop() }
    }

    private func spawn() {
        kind = Int.random(in: 0..<Self.shapes.count)
        cells = Self.shapes[kind]
        px = 3; py = 0
        if !fits(cells, px, py) { phase = .over; AvraHaptics.tap(true) }
    }

    private func fits(_ c: [(Int, Int)], _ x: Int, _ y: Int) -> Bool {
        for (cx, cy) in c {
            let gx = cx + x, gy = cy + y
            if gx < 0 || gx >= Self.w || gy >= Self.h { return false }
            if gy >= 0 && grid[gy * Self.w + gx] != 0 { return false }
        }
        return true
    }

    func absolute() -> [(Int, Int)] { cells.map { ($0.0 + px, $0.1 + py) } }

    func move(_ dx: Int) {
        guard phase == .playing, fits(cells, px + dx, py) else { return }
        px += dx
        objectWillChange.send()
    }

    func rotate() {
        guard phase == .playing, kind != 1 else { return }
        let r = cells.map { (2 - $0.1, $0.0) }
        for kick in [0, -1, 1, -2, 2] where fits(r, px + kick, py) {
            px += kick
            withAnimation(.easeOut(duration: 0.08)) { cells = r }
            AvraHaptics.tap()
            return
        }
    }

    func drop() {
        guard phase == .playing else { return }
        while fits(cells, px, py + 1) { py += 1; score += 1 }
        lock()
    }

    private func fall() {
        guard phase == .playing else { return }
        if fits(cells, px, py + 1) { py += 1; objectWillChange.send() } else { lock() }
    }

    private func lock() {
        for (x, y) in absolute() where y >= 0 { grid[y * Self.w + x] = kind + 1 }
        var cleared = 0
        var g = grid
        var y = Self.h - 1
        while y >= 0 {
            if (0..<Self.w).allSatisfy({ g[y * Self.w + $0] != 0 }) {
                g.removeSubrange(y * Self.w..<(y + 1) * Self.w)
                g.insert(contentsOf: [Int](repeating: 0, count: Self.w), at: 0)
                cleared += 1
            } else {
                y -= 1
            }
        }
        if cleared > 0 {
            withAnimation(.easeOut(duration: 0.15)) { grid = g }
            score += [0, 100, 300, 500, 800][cleared] * (difficulty.rawValue + 1)
            lines += cleared
            AvraHaptics.tap(true)
            if phase == .playing { phaseChanged() }
        }
        spawn()
    }
}

struct AvraDropScreen: View {
    @StateObject var m = AvraDropModel()
    @Environment(\.sgTheme) var theme
    @Environment(\.sgIsVisible) var visible

    var body: some View {
        AvraGameShell(kind: .drop, phase: $m.phase, difficulty: $m.difficulty, score: m.score,
                      onStart: { m.start() }, onPause: { m.phase = .paused }, onResume: { m.phase = .playing }) {
            GeometryReader { geo in
                let cell = min(geo.size.width / 10, geo.size.height / 20)
                ZStack(alignment: .topLeading) {
                    RoundedRectangle(cornerRadius: 10).fill(theme.secondaryBackgroundColor)
                        .frame(width: cell * 10, height: cell * 20)
                    ForEach(0..<200, id: \.self) { i in
                        if m.grid[i] != 0 {
                            block(m.grid[i] - 1, cell)
                                .offset(x: CGFloat(i % 10) * cell, y: CGFloat(i / 10) * cell)
                        }
                    }
                    ForEach(Array(m.absolute().enumerated()), id: \.offset) { item in
                        block(m.kind, cell)
                            .offset(x: CGFloat(item.element.0) * cell, y: CGFloat(item.element.1) * cell)
                    }
                }
                .frame(width: cell * 10, height: cell * 20)
                .frame(maxWidth: .infinity)
                .contentShape(Rectangle())
                .onTapGesture { m.rotate() }
                .gesture(DragGesture(minimumDistance: 14).onEnded { v in
                    if abs(v.translation.width) > abs(v.translation.height) {
                        let steps = max(1, Int(abs(v.translation.width) / cell))
                        for _ in 0..<steps { m.move(v.translation.width > 0 ? 1 : -1) }
                    } else if v.translation.height > 0 {
                        m.drop()
                    } else {
                        m.rotate()
                    }
                })
            }
            .aspectRatio(0.5, contentMode: .fit)
        }
        .onChange(of: visible) { v in if !v && m.phase == .playing { m.phase = .paused } }
    }

    private func block(_ k: Int, _ cell: CGFloat) -> some View {
        RoundedRectangle(cornerRadius: cell * 0.2)
            .fill(Color(hue: Double(k) / 7.0, saturation: 0.65, brightness: 0.9))
            .frame(width: cell - 2, height: cell - 2)
    }
}
