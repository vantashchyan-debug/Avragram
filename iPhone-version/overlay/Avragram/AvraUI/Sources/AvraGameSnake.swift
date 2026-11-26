import Foundation
import SwiftUI
import SGSwiftUI

/// AvraSnake: swipe to turn, eat to grow; Hard is faster and the walls kill.
final class AvraSnakeModel: ObservableObject {
    static let w = 16, h = 20
    @Published var phase: AvraGamePhase = .menu { didSet { phaseChanged() } }
    @Published var difficulty: AvraDifficulty = .normal
    @Published private(set) var body: [(Int, Int)] = []
    @Published private(set) var food = (5, 5)
    @Published private(set) var score = 0
    private var dir = (1, 0), nextDir = (1, 0)
    private let ticker = AvraTicker()

    func start() {
        body = [(6, 10), (5, 10), (4, 10)]
        dir = (1, 0); nextDir = (1, 0); score = 0
        placeFood()
        phase = .playing
    }

    private func phaseChanged() {
        if phase == .playing {
            let interval = [0.16, 0.11, 0.075][difficulty.rawValue]
            ticker.start(interval) { [weak self] in self?.step() }
        } else {
            ticker.stop()
        }
    }

    func turn(_ s: AvraSwipe) {
        let d: (Int, Int)
        switch s {
        case .up: d = (0, -1)
        case .down: d = (0, 1)
        case .left: d = (-1, 0)
        case .right: d = (1, 0)
        }
        if d.0 != -dir.0 || d.1 != -dir.1 { nextDir = d }
    }

    private func placeFood() {
        var free: [(Int, Int)] = []
        for y in 0..<Self.h { for x in 0..<Self.w where !body.contains(where: { $0 == (x, y) }) { free.append((x, y)) } }
        food = free.randomElement() ?? (0, 0)
    }

    private func step() {
        guard phase == .playing, let head = body.first else { return }
        dir = nextDir
        var nx = head.0 + dir.0, ny = head.1 + dir.1
        if difficulty == .hard {
            if nx < 0 || ny < 0 || nx >= Self.w || ny >= Self.h { phase = .over; AvraHaptics.tap(true); return }
        } else {
            nx = (nx + Self.w) % Self.w; ny = (ny + Self.h) % Self.h
        }
        if body.dropLast().contains(where: { $0 == (nx, ny) }) { phase = .over; AvraHaptics.tap(true); return }
        var b = body
        b.insert((nx, ny), at: 0)
        if (nx, ny) == food {
            score += 10 * (difficulty.rawValue + 1)
            AvraHaptics.tap()
            body = b
            placeFood()
        } else {
            b.removeLast()
            body = b
        }
    }
}

struct AvraSnakeScreen: View {
    @StateObject private var m = AvraSnakeModel()
    @Environment(\.sgTheme) private var theme
    @Environment(\.sgIsVisible) private var visible

    var body: some View {
        AvraGameShell(kind: .snake, phase: $m.phase, difficulty: $m.difficulty, score: m.score,
                      onStart: { m.start() }, onPause: { m.phase = .paused }, onResume: { m.phase = .playing }) {
            GeometryReader { geo in
                let cell = min(geo.size.width / CGFloat(AvraSnakeModel.w), geo.size.height / CGFloat(AvraSnakeModel.h))
                ZStack(alignment: .topLeading) {
                    RoundedRectangle(cornerRadius: 12).fill(theme.secondaryBackgroundColor)
                        .frame(width: cell * CGFloat(AvraSnakeModel.w), height: cell * CGFloat(AvraSnakeModel.h))
                    Circle().fill(Color.red)
                        .frame(width: cell * 0.8, height: cell * 0.8)
                        .offset(x: CGFloat(m.food.0) * cell + cell * 0.1, y: CGFloat(m.food.1) * cell + cell * 0.1)
                    ForEach(Array(m.body.enumerated()), id: \.offset) { item in
                        RoundedRectangle(cornerRadius: cell * 0.3)
                            .fill(theme.accentColor.opacity(item.offset == 0 ? 1 : 0.75))
                            .frame(width: cell * 0.92, height: cell * 0.92)
                            .offset(x: CGFloat(item.element.0) * cell + cell * 0.04, y: CGFloat(item.element.1) * cell + cell * 0.04)
                    }
                }
                .frame(maxWidth: .infinity)
            }
            .aspectRatio(CGFloat(AvraSnakeModel.w) / CGFloat(AvraSnakeModel.h), contentMode: .fit)
            .contentShape(Rectangle())
            .avraSwipe { m.turn($0) }
        }
        .onChange(of: visible) { v in if !v && m.phase == .playing { m.phase = .paused } }
    }
}
