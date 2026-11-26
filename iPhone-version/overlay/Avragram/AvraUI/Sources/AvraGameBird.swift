import Foundation
import SwiftUI
import SGSwiftUI

/// AvraBird: tap to flap through the gaps.
final class AvraBirdModel: ObservableObject {
    struct Pipe: Identifiable { let id: Int; var x: CGFloat; let gapY: CGFloat; var passed = false }
    @Published var phase: AvraGamePhase = .menu { didSet { phaseChanged() } }
    @Published var difficulty: AvraDifficulty = .normal
    @Published private(set) var y: CGFloat = 0.5
    @Published private(set) var pipes: [Pipe] = []
    @Published private(set) var score = 0
    private var vy: CGFloat = 0
    private var nextId = 1
    private var sinceSpawn: CGFloat = 0
    private let ticker = AvraTicker()
    var gap: CGFloat { [0.34, 0.28, 0.23][difficulty.rawValue] }
    private var speed: CGFloat { [0.0045, 0.0058, 0.0072][difficulty.rawValue] }

    func start() {
        y = 0.5; vy = 0; pipes = []; score = 0; sinceSpawn = 0.6
        phase = .playing
    }

    private func phaseChanged() {
        if phase == .playing { ticker.start(1.0 / 60.0) { [weak self] in self?.tick() } } else { ticker.stop() }
    }

    func flap() {
        guard phase == .playing else { return }
        vy = -0.0125
        AvraHaptics.tap()
    }

    private func tick() {
        vy += 0.0006
        y += vy
        sinceSpawn += speed
        if sinceSpawn > 0.55 {
            sinceSpawn = 0
            pipes.append(Pipe(id: nextId, x: 1.1, gapY: CGFloat.random(in: 0.25...0.75)))
            nextId += 1
        }
        for i in pipes.indices { pipes[i].x -= speed }
        pipes.removeAll { $0.x < -0.2 }
        let birdX: CGFloat = 0.25, r: CGFloat = 0.03
        if y < 0 || y > 1 { phase = .over; AvraHaptics.tap(true); return }
        for i in pipes.indices {
            let p = pipes[i]
            if abs(p.x - birdX) < 0.07 + r && (y - r < p.gapY - gap / 2 || y + r > p.gapY + gap / 2) {
                phase = .over; AvraHaptics.tap(true); return
            }
            if !p.passed && p.x < birdX {
                pipes[i].passed = true
                score += 1
            }
        }
    }
}

struct AvraBirdScreen: View {
    @StateObject var m = AvraBirdModel()
    @Environment(\.sgTheme) var theme
    @Environment(\.sgIsVisible) var visible

    var body: some View {
        AvraGameShell(kind: .bird, phase: $m.phase, difficulty: $m.difficulty, score: m.score,
                      onStart: { m.start() }, onPause: { m.phase = .paused }, onResume: { m.phase = .playing }) {
            GeometryReader { geo in
                let w = geo.size.width, h = geo.size.height
                ZStack(alignment: .topLeading) {
                    LinearGradient(colors: [theme.accentColor.opacity(0.25), theme.secondaryBackgroundColor], startPoint: .top, endPoint: .bottom)
                    ForEach(m.pipes) { p in
                        let top = (p.gapY - m.gap / 2) * h, bottom = (p.gapY + m.gap / 2) * h
                        RoundedRectangle(cornerRadius: 8).fill(theme.accentColor)
                            .frame(width: w * 0.14, height: max(0, top))
                            .offset(x: (p.x - 0.07) * w, y: 0)
                        RoundedRectangle(cornerRadius: 8).fill(theme.accentColor)
                            .frame(width: w * 0.14, height: max(0, h - bottom))
                            .offset(x: (p.x - 0.07) * w, y: bottom)
                    }
                    Text("🐦").font(.system(size: h * 0.06))
                        .rotationEffect(.degrees(Double(max(-30, min(60, 0)))))
                        .position(x: 0.25 * w, y: m.y * h)
                }
                .clipShape(RoundedRectangle(cornerRadius: 14))
                .contentShape(Rectangle())
                .onTapGesture { m.flap() }
            }
            .aspectRatio(0.62, contentMode: .fit)
        }
        .onChange(of: visible) { v in if !v && m.phase == .playing { m.phase = .paused } }
    }
}
