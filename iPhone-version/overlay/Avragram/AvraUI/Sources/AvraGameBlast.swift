import Foundation
import SwiftUI
import SGSwiftUI

/// AvraBlast: drag pieces from the tray onto the 8×8 board; full rows and columns are cleared.
final class AvraBlastModel: ObservableObject {
    struct Piece: Identifiable { let id: Int; let cells: [(Int, Int)]; let hue: Double
        var w: Int { (cells.map { $0.0 }.max() ?? 0) + 1 }
        var h: Int { (cells.map { $0.1 }.max() ?? 0) + 1 }
    }
    static let shapes: [[(Int, Int)]] = [
        [(0, 0)], [(0, 0), (1, 0)], [(0, 0), (0, 1)], [(0, 0), (1, 0), (2, 0)], [(0, 0), (0, 1), (0, 2)],
        [(0, 0), (1, 0), (0, 1), (1, 1)], [(0, 0), (1, 0), (2, 0), (3, 0)], [(0, 0), (0, 1), (0, 2), (0, 3)],
        [(0, 0), (0, 1), (1, 1)], [(1, 0), (0, 1), (1, 1)], [(0, 0), (1, 0), (1, 1)], [(0, 0), (1, 0), (0, 1)],
        [(0, 0), (1, 0), (2, 0), (0, 1), (1, 1), (2, 1), (0, 2), (1, 2), (2, 2)], [(0, 0), (1, 0), (2, 0), (1, 1)],
        [(0, 0), (0, 1), (0, 2), (1, 2), (2, 2)], [(0, 0), (1, 0), (2, 0), (3, 0), (4, 0)]
    ]
    @Published var phase: AvraGamePhase = .menu
    @Published var difficulty: AvraDifficulty = .normal
    @Published private(set) var grid = [Double?](repeating: nil, count: 64)
    @Published private(set) var tray: [Piece?] = [nil, nil, nil]
    @Published private(set) var score = 0
    @Published private(set) var flash = Set<Int>()
    private var nextId = 1

    func start() {
        grid = [Double?](repeating: nil, count: 64); score = 0
        refill()
        phase = .playing
    }

    private func refill() {
        let pool = difficulty == .easy ? Array(Self.shapes.prefix(12)) : Self.shapes
        tray = (0..<3).map { _ in
            nextId += 1
            return Piece(id: nextId, cells: pool.randomElement() ?? [(0, 0)], hue: Double.random(in: 0..<1))
        }
    }

    func fits(_ p: Piece, _ x: Int, _ y: Int) -> Bool {
        for (cx, cy) in p.cells {
            let gx = cx + x, gy = cy + y
            if gx < 0 || gy < 0 || gx >= 8 || gy >= 8 || grid[gy * 8 + gx] != nil { return false }
        }
        return true
    }

    @discardableResult
    func place(_ index: Int, _ x: Int, _ y: Int) -> Bool {
        guard phase == .playing, let p = tray[index], fits(p, x, y) else { return false }
        var g = grid
        for (cx, cy) in p.cells { g[(cy + y) * 8 + cx + x] = p.hue }
        score += p.cells.count
        var clear = Set<Int>()
        for r in 0..<8 where (0..<8).allSatisfy({ g[r * 8 + $0] != nil }) { for c in 0..<8 { clear.insert(r * 8 + c) } }
        for c in 0..<8 where (0..<8).allSatisfy({ g[$0 * 8 + c] != nil }) { for r in 0..<8 { clear.insert(r * 8 + c) } }
        withAnimation(.spring(response: 0.25, dampingFraction: 0.7)) {
            grid = g
            tray[index] = nil
        }
        AvraHaptics.tap()
        if !clear.isEmpty {
            let lines = clear.count / 8
            score += clear.count * 2 * max(1, lines) * (difficulty == .hard ? 2 : 1)
            flash = clear
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) {
                withAnimation(.easeOut(duration: 0.2)) {
                    for i in clear { self.grid[i] = nil }
                    self.flash = []
                }
                self.afterPlace()
            }
            AvraHaptics.tap(true)
        } else {
            afterPlace()
        }
        return true
    }

    private func afterPlace() {
        if tray.allSatisfy({ $0 == nil }) { withAnimation(.spring()) { refill() } }
        let canPlace = tray.contains { p in
            guard let p else { return false }
            for y in 0..<8 { for x in 0..<8 where fits(p, x, y) { return true } }
            return false
        }
        if !canPlace { phase = .over; AvraHaptics.tap(true) }
    }
}

struct AvraBlastScreen: View {
    @StateObject private var m = AvraBlastModel()
    @Environment(\.sgTheme) private var theme
    @State private var dragging: Int? = nil
    @State private var dragPoint: CGPoint = .zero
    @State private var boardFrame: CGRect = .zero

    var body: some View {
        AvraGameShell(kind: .blast, phase: $m.phase, difficulty: $m.difficulty, score: m.score,
                      onStart: { m.start() }, onPause: { m.phase = .paused }, onResume: { m.phase = .playing }) {
            GeometryReader { geo in
                let side = geo.size.width
                let cell = side / 8
                VStack(spacing: 18) {
                    ZStack(alignment: .topLeading) {
                        RoundedRectangle(cornerRadius: 12).fill(theme.secondaryBackgroundColor)
                        ForEach(0..<64, id: \.self) { i in
                            RoundedRectangle(cornerRadius: cell * 0.18)
                                .fill(m.grid[i].map { Color(hue: $0, saturation: 0.65, brightness: 0.92) } ?? theme.separatorColor.opacity(0.35))
                                .overlay(RoundedRectangle(cornerRadius: cell * 0.18).fill(Color.white.opacity(m.flash.contains(i) ? 0.8 : 0)))
                                .frame(width: cell - 3, height: cell - 3)
                                .offset(x: CGFloat(i % 8) * cell + 1.5, y: CGFloat(i / 8) * cell + 1.5)
                        }
                    }
                    .frame(width: side, height: side)
                    .background(GeometryReader { g in Color.clear.onAppear { boardFrame = g.frame(in: .global) }
                        .onChange(of: g.frame(in: .global)) { boardFrame = $0 } })

                    HStack(spacing: 0) {
                        ForEach(0..<3, id: \.self) { idx in
                            ZStack {
                                if let p = m.tray[idx] {
                                    pieceView(p, cell: dragging == idx ? cell : cell * 0.55)
                                        .offset(dragging == idx ? CGSize(width: 0, height: -cell * 1.5) : .zero)
                                        .gesture(DragGesture(coordinateSpace: .global)
                                            .onChanged { v in dragging = idx; dragPoint = v.location }
                                            .onEnded { v in
                                                let origin = CGPoint(x: v.location.x - CGFloat(p.w) * cell / 2 - boardFrame.minX,
                                                                     y: v.location.y - cell * 1.5 - CGFloat(p.h) * cell / 2 - boardFrame.minY)
                                                let x = Int((origin.x / cell).rounded()), y = Int((origin.y / cell).rounded())
                                                m.place(idx, x, y)
                                                withAnimation(.spring()) { dragging = nil }
                                            })
                                        .position(dragging == idx ? CGPoint(x: dragPoint.x - geo.frame(in: .global).minX - CGFloat(idx) * side / 3, y: dragPoint.y - geo.frame(in: .global).minY - side - 18) : CGPoint(x: side / 6, y: cell * 1.3))
                                }
                            }
                            .frame(width: side / 3, height: cell * 2.6)
                            .zIndex(dragging == idx ? 5 : 0)
                        }
                    }
                }
            }
            .aspectRatio(0.72, contentMode: .fit)
        }
    }

    private func pieceView(_ p: AvraBlastModel.Piece, cell: CGFloat) -> some View {
        ZStack(alignment: .topLeading) {
            ForEach(Array(p.cells.enumerated()), id: \.offset) { item in
                RoundedRectangle(cornerRadius: cell * 0.18)
                    .fill(Color(hue: p.hue, saturation: 0.65, brightness: 0.92))
                    .frame(width: cell - 3, height: cell - 3)
                    .offset(x: CGFloat(item.element.0) * cell, y: CGFloat(item.element.1) * cell)
            }
        }
        .frame(width: CGFloat(p.w) * cell, height: CGFloat(p.h) * cell, alignment: .topLeading)
    }
}
