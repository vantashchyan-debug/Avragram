import Foundation
import SwiftUI
import SGSwiftUI

/// AvraRun: endless runner. Tap the upper half to jump, the lower half to duck.
final class AvraRunModel: ObservableObject {
    struct Obstacle: Identifiable { let id: Int; var x: CGFloat; let high: Bool }
    @Published var phase: AvraGamePhase = .menu { didSet { phaseChanged() } }
    @Published var difficulty: AvraDifficulty = .normal
    @Published private(set) var y: CGFloat = 0
    @Published private(set) var ducking = false
    @Published private(set) var obstacles: [Obstacle] = []
    @Published private(set) var score = 0
    private var vy: CGFloat = 0
    private var distance: CGFloat = 0
    private var nextSpawn: CGFloat = 0.8
    private var nextId = 1
    private let ticker = AvraTicker()
    private var duckUntil = Date.distantPast

    func start() {
        y = 0; vy = 0; obstacles = []; score = 0; distance = 0; nextSpawn = 0.8; ducking = false
        phase = .playing
    }

    private func phaseChanged() {
        if phase == .playing { ticker.start(1.0 / 60.0) { [weak self] in self?.tick() } } else { ticker.stop() }
    }

    private var speed: CGFloat { [0.009, 0.012, 0.015][difficulty.rawValue] + min(0.01, distance / 4000) }

    func jump() {
        guard phase == .playing, y == 0 else { return }
        vy = 0.03
        AvraHaptics.tap()
    }

    func duck() {
        guard phase == .playing else { return }
        ducking = true
        duckUntil = Date().addingTimeInterval(0.6)
    }

    private func tick() {
        if y > 0 || vy > 0 {
            y += vy; vy -= 0.0016
            if y < 0 { y = 0; vy = 0 }
        }
        if ducking && Date() > duckUntil { ducking = false }
        distance += speed * 100
        score = Int(distance / 10)
        nextSpawn -= speed
        if nextSpawn <= 0 {
            obstacles.append(Obstacle(id: nextId, x: 1.1, high: Bool.random() && difficulty != .easy))
            nextId += 1
            nextSpawn = CGFloat.random(in: 0.45...0.9)
        }
        for i in obstacles.indices { obstacles[i].x -= speed }
        obstacles.removeAll { $0.x < -0.1 }
        for o in obstacles where abs(o.x - 0.2) < 0.05 {
            let hit = o.high ? !ducking : y < 0.12
            if hit { phase = .over; AvraHaptics.tap(true); return }
        }
    }
}

struct AvraRunScreen: View {
    @StateObject var m = AvraRunModel()
    @Environment(\.sgTheme) var theme
    @Environment(\.sgIsVisible) var visible

    var body: some View {
        AvraGameShell(kind: .run, phase: $m.phase, difficulty: $m.difficulty, score: m.score,
                      onStart: { m.start() }, onPause: { m.phase = .paused }, onResume: { m.phase = .playing }) {
            GeometryReader { geo in
                let w = geo.size.width, h = geo.size.height
                let ground = h * 0.8
                ZStack(alignment: .topLeading) {
                    RoundedRectangle(cornerRadius: 14).fill(theme.secondaryBackgroundColor)
                    Rectangle().fill(theme.separatorColor).frame(width: w, height: 3).offset(y: ground)
                    ForEach(m.obstacles) { o in
                        RoundedRectangle(cornerRadius: 4).fill(theme.destructiveColor)
                            .frame(width: w * 0.06, height: o.high ? h * 0.12 : h * 0.1)
                            .offset(x: o.x * w - w * 0.03, y: o.high ? ground - h * 0.34 : ground - h * 0.1)
                    }
                    Text(m.ducking ? "🦔" : "🏃").font(.system(size: h * (m.ducking ? 0.09 : 0.14)))
                        .position(x: 0.2 * w, y: ground - h * (m.ducking ? 0.045 : 0.07) - m.y * h * 1.6)
                }
                .clipShape(RoundedRectangle(cornerRadius: 14))
                .contentShape(Rectangle())
                .gesture(DragGesture(minimumDistance: 0).onEnded { v in
                    if v.location.y < h / 2 { m.jump() } else { m.duck() }
                })
            }
            .aspectRatio(1.4, contentMode: .fit)
            Text("Верх — прыжок, низ — присесть").font(.footnote).foregroundColor(theme.secondaryTextColor)
        }
        .onChange(of: visible) { v in if !v && m.phase == .playing { m.phase = .paused } }
    }
}
