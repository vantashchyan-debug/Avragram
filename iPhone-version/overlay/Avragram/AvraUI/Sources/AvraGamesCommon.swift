import Foundation
import UIKit
import SwiftUI
import SGSwiftUI

/// Shared parts of the Fun mini-games: difficulty, best scores (on this device), the game frame with
/// Start / Pause / Restart and the result card. Games take the colors of the current Telegram theme.
enum AvraDifficulty: Int, CaseIterable, Identifiable {
    case easy = 0, normal, hard
    var id: Int { rawValue }
    var title: String { ["Легко", "Нормально", "Сложно"][rawValue] }
    var stars: String { String(repeating: "★", count: rawValue + 1) + String(repeating: "☆", count: 2 - rawValue) }
}

enum AvraGameKind: Int, CaseIterable, Identifiable {
    case blast = 0, snake, drop, run, bird, g2048
    var id: Int { rawValue }
    var title: String { ["AvraBlast", "AvraSnake", "AvraDrop", "AvraRun", "AvraBird", "Avra48"][rawValue] }
    var emoji: String { ["🧩", "🐍", "🧱", "🏃", "🐦", "🔢"][rawValue] }
    var info: String {
        ["Ставь блоки и очищай ряды и столбцы", "Классическая змейка", "Падающие блоки", "Раннер: прыжок и присед", "Пролетай через просветы", "2048 с режимом «Играть дальше»"][rawValue]
    }
}

enum AvraScores {
    private static func key(_ g: AvraGameKind, _ d: AvraDifficulty) -> String { "avra.best.\(g.rawValue).\(d.rawValue)" }
    static func best(_ g: AvraGameKind, _ d: AvraDifficulty) -> Int { UserDefaults.standard.integer(forKey: key(g, d)) }
    static func bestAny(_ g: AvraGameKind) -> Int { AvraDifficulty.allCases.map { best(g, $0) }.max() ?? 0 }
    /// Saves the score; returns true for a new record.
    @discardableResult
    static func submit(_ g: AvraGameKind, _ d: AvraDifficulty, _ score: Int) -> Bool {
        if score > best(g, d) {
            UserDefaults.standard.set(score, forKey: key(g, d))
            return true
        }
        return false
    }
}

/// A repeating game clock on the main run loop (paused when the screen is not visible).
final class AvraTicker {
    private var timer: Timer?
    func start(_ interval: TimeInterval, _ tick: @escaping () -> Void) {
        stop()
        let t = Timer(timeInterval: interval, repeats: true) { _ in tick() }
        RunLoop.main.add(t, forMode: .common)
        timer = t
    }
    func stop() {
        timer?.invalidate()
        timer = nil
    }
    deinit { timer?.invalidate() }
}

enum AvraGamePhase { case menu, playing, paused, over }

/// Start screen → difficulty → game → result; the game view is given the difficulty.
struct AvraGameShell<Game: View>: View {
    let kind: AvraGameKind
    @Binding var phase: AvraGamePhase
    @Binding var difficulty: AvraDifficulty
    let score: Int
    let onStart: () -> Void
    let onPause: () -> Void
    let onResume: () -> Void
    @ViewBuilder let game: () -> Game
    @Environment(\.sgTheme) var theme
    @Environment(\.sgNavigation) var navigation
    @State var choosing = false
    @State var record = false

    var body: some View {
        ZStack {
            theme.backgroundColor.ignoresSafeArea()
            VStack(spacing: 10) {
                HStack {
                    Text("\(kind.emoji) \(kind.title)").font(.headline).foregroundColor(theme.primaryTextColor)
                    Spacer()
                    Text("Счёт: \(score)").font(.system(.headline, design: .rounded)).foregroundColor(theme.accentColor)
                    if phase == .playing {
                        Button(action: onPause) { Image(systemName: "pause.fill") }.padding(.leading, 6)
                    }
                }
                .padding(.horizontal, 16)
                game()
                    .padding(.horizontal, 8)
                    .disabled(phase != .playing)
                Spacer(minLength: 0)
            }
            .padding(.top, 8)
            if phase != .playing {
                overlay
                    .transition(.opacity.combined(with: .scale(scale: 0.92)))
            }
        }
        .animation(.spring(response: 0.35, dampingFraction: 0.8), value: phase)
        .onChange(of: phase) { p in
            if p == .over { record = AvraScores.submit(kind, difficulty, score) }
        }
        .sgNavigationTitle(kind.title)
    }

    private var overlay: some View {
        VStack(spacing: 14) {
            Text(kind.emoji).font(.system(size: 54))
            Text(phase == .paused ? "Пауза" : phase == .over ? (record ? "🏆 Новый рекорд!" : "Игра окончена") : kind.title)
                .font(.title.bold())
            if phase == .over {
                Text("Счёт: \(score)").font(.title3)
            }
            Text("Рекорд (\(difficulty.title.lowercased())): \(AvraScores.best(kind, difficulty))").font(.subheadline).foregroundColor(theme.secondaryTextColor)
            if choosing {
                ForEach(AvraDifficulty.allCases) { d in
                    Button(action: {
                        difficulty = d
                        choosing = false
                        onStart()
                    }) {
                        Text("\(d.stars)  \(d.title)").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                }
            } else if phase == .paused {
                Button(action: onResume) { Text("Продолжить").frame(maxWidth: .infinity) }.buttonStyle(.borderedProminent)
                Button(action: { choosing = true }) { Text("Заново").frame(maxWidth: .infinity) }.buttonStyle(.bordered)
            } else {
                Button(action: { choosing = true }) { Text(phase == .over ? "Ещё раз" : "Начать").frame(maxWidth: .infinity) }.buttonStyle(.borderedProminent)
            }
            Button(action: { navigation?.dismiss() }) { Text("Выход").frame(maxWidth: .infinity) }.buttonStyle(.bordered)
        }
        .padding(24)
        .frame(maxWidth: 320)
        .background(RoundedRectangle(cornerRadius: 22).fill(theme.secondaryBackgroundColor).shadow(color: .black.opacity(0.25), radius: 20, y: 8))
        .padding(24)
    }
}

/// Swipe directions for Snake, Drop and 2048.
enum AvraSwipe { case up, down, left, right }

extension View {
    func avraSwipe(_ action: @escaping (AvraSwipe) -> Void) -> some View {
        self.gesture(DragGesture(minimumDistance: 18).onEnded { v in
            let dx = v.translation.width, dy = v.translation.height
            if abs(dx) > abs(dy) { action(dx > 0 ? .right : .left) } else { action(dy > 0 ? .down : .up) }
        })
    }
}
