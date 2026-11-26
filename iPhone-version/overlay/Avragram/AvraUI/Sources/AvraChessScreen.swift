import Foundation
import UIKit
import SwiftUI
import SGSwiftUI

struct AvraPiece: Identifiable {
    let id: Int
    let square: Int
    let piece: Int
}

/// AvraChess against AvraBot: tap or drag pieces, smooth spring animations, legal move dots,
/// check glow, undo and a new game with switched colors.
final class AvraChessGame: ObservableObject {
    @Published private(set) var squares: [Int] = AvraChessBoard().sq
    /// Stable identity of each piece so SwiftUI can animate it from square to square.
    @Published private(set) var ids: [Int] = []
    @Published var selected: Int? = nil
    @Published private(set) var targets: [Int] = []
    @Published private(set) var thinking = false
    @Published private(set) var lastMove = 0
    @Published private(set) var statusText = ""
    @Published var level: Int
    @Published private(set) var humanColor = 1

    private(set) var board = AvraChessBoard()
    private let ai = AvraChessAi()
    private let queue = DispatchQueue(label: "avra.chess.ai", qos: .userInitiated)
    /// Online game id (moves go to AvraChessNet instead of the bot).
    let onlineId: String?
    private var observer: NSObjectProtocol?

    init(level: Int) {
        self.level = level
        self.onlineId = nil
        resetIds()
        refresh()
    }

    init(online id: String) {
        self.level = 0
        self.onlineId = id
        if let g = AvraChessNet.games[id] {
            humanColor = g.myColor
            board = g.board()
        }
        resetIds()
        squares = board.sq
        lastMove = board.lastMove
        refresh()
        observer = NotificationCenter.default.addObserver(forName: AvraOnline.changed, object: nil, queue: .main) { [weak self] _ in
            self?.syncOnline()
        }
    }

    deinit {
        if let observer = observer { NotificationCenter.default.removeObserver(observer) }
    }

    var online: AvraChessOnlineGame? { onlineId.flatMap { AvraChessNet.games[$0] } }

    /// New moves of an online game: play the new ones with animation.
    private func syncOnline() {
        guard let g = online else { return }
        if g.moves.count > board.ply {
            let known = (0..<board.ply).map { AvraChessBoard.uci(board.moveAt($0)) }
            if Array(g.moves.prefix(board.ply)) == known {
                for u in g.moves[board.ply...] {
                    let m = board.parseUci(u)
                    if m == 0 { break }
                    apply(m, local: false)
                }
                return
            }
            board = g.board()
            resetIds()
            squares = board.sq
            lastMove = board.lastMove
        }
        refresh()
    }

    private func resetIds() {
        var next = 1
        ids = board.sq.map { p in
            if p == 0 { return 0 }
            next += 1
            return next
        }
    }

    var over: Bool { board.status() != .ongoing }

    func pieces() -> [AvraPiece] {
        var out: [AvraPiece] = []
        for s in 0..<64 where squares[s] != 0 { out.append(AvraPiece(id: ids[s], square: s, piece: squares[s])) }
        return out
    }

    func canMove(_ s: Int) -> Bool {
        let p = squares[s]
        if onlineId != nil && !(online?.myTurn ?? false) { return false }
        return !thinking && !over && p != 0 && (p > 0 ? 1 : -1) == board.side && board.side == humanColor
    }

    func tap(_ s: Int) {
        if let sel = selected, sel != s, !canMove(s) {
            if !tryMove(from: sel, to: s) { select(nil) }
            return
        }
        if canMove(s) { select(selected == s ? nil : s) } else { select(nil) }
    }

    func select(_ s: Int?) {
        selected = s
        if let s = s { targets = board.legal().filter { AvraChessBoard.from($0) == s }.map { AvraChessBoard.to($0) } } else { targets = [] }
    }

    @discardableResult
    func tryMove(from: Int, to: Int) -> Bool {
        let options = board.legal().filter { AvraChessBoard.from($0) == from && AvraChessBoard.to($0) == to }
        guard !options.isEmpty else { return false }
        let m = options.first { AvraChessBoard.promo($0) == 5 } ?? options[0]
        if let id = onlineId {
            let uci = AvraChessBoard.uci(m)
            apply(m, local: true)
            AvraChessNet.move(id, uci: uci)
            return true
        }
        apply(m)
        if !over {
            thinking = true
            refresh()
            let snapshot = board.copy()
            let lvl = level
            queue.asyncAfter(deadline: .now() + 0.25) { [weak self] in
                guard let self else { return }
                let reply = self.ai.think(snapshot, level: lvl)
                DispatchQueue.main.async {
                    self.thinking = false
                    if reply != 0 && snapshot.key == self.board.key { self.apply(reply) } else { self.refresh() }
                }
            }
        }
        return true
    }

    private func apply(_ m: Int, local: Bool = true) {
        let from = AvraChessBoard.from(m), to = AvraChessBoard.to(m), fl = AvraChessBoard.flags(m)
        var newIds = ids
        if fl & AvraChessBoard.fEp != 0 { newIds[to - 8 * board.side] = 0 }
        newIds[to] = newIds[from]
        newIds[from] = 0
        if fl & AvraChessBoard.fCastle != 0 {
            let (rf, rt): (Int, Int) = to == 6 ? (7, 5) : to == 2 ? (0, 3) : to == 62 ? (63, 61) : (56, 59)
            newIds[rt] = newIds[rf]
            newIds[rf] = 0
        }
        let capture = fl & AvraChessBoard.fCapture != 0
        board.make(m)
        withAnimation(.spring(response: 0.32, dampingFraction: 0.78)) {
            ids = newIds
            squares = board.sq
            lastMove = m
            selected = nil
            targets = []
        }
        AvraHaptics.tap(capture || !local)
        if board.inCheck { AvraChessStyle.play(.check) }
        else if fl & AvraChessBoard.fCastle != 0 { AvraChessStyle.play(.castle) }
        else if capture { AvraChessStyle.play(.capture) }
        else { AvraChessStyle.play(.move) }
        if board.status() != .ongoing { AvraChessStyle.play(.end) }
        refresh()
    }

    /// Shows a position (game review); no moves can be made.
    func show(_ b: AvraChessBoard) {
        board = b.copy()
        resetIds()
        withAnimation(.easeInOut(duration: 0.2)) {
            squares = board.sq
            lastMove = board.lastMove
            selected = nil
            targets = []
        }
        humanColor = 2
        statusText = ""
    }

    var uciMoves: [String] { (0..<board.ply).map { AvraChessBoard.uci(board.moveAt($0)) } }

    func undo() {
        guard !thinking else { return }
        let n = board.side == humanColor ? 2 : 1
        for _ in 0..<n where board.ply > 0 { board.unmake() }
        resetIds()
        withAnimation(.easeInOut(duration: 0.25)) {
            squares = board.sq
            lastMove = board.lastMove
            selected = nil
            targets = []
        }
        refresh()
    }

    func newGame() {
        board = AvraChessBoard()
        humanColor = -humanColor
        resetIds()
        withAnimation(.easeInOut(duration: 0.3)) {
            squares = board.sq
            lastMove = 0
            selected = nil
            targets = []
        }
        refresh()
        if board.side != humanColor {
            thinking = true
            let snapshot = board.copy()
            let lvl = level
            queue.asyncAfter(deadline: .now() + 0.4) { [weak self] in
                guard let self else { return }
                let reply = self.ai.think(snapshot, level: lvl)
                DispatchQueue.main.async {
                    self.thinking = false
                    if reply != 0 { self.apply(reply) }
                }
            }
        }
    }

    private func refresh() {
        if let g = online {
            switch g.state {
            case AvraChessNet.inviteOut: statusText = "⏳ Ждём, пока соперник примет вызов"
            case AvraChessNet.inviteIn: statusText = "📨 Тебе бросили вызов"
            case AvraChessNet.declined: statusText = "Вызов отклонён"
            case AvraChessNet.finished: statusText = (g.result == 0 ? "🤝 " : g.result == g.myColor ? "🏆 " : "💀 ") + AvraChessNet.resultText(g)
            default:
                if g.drawOfferIn { statusText = "🤝 Соперник предлагает ничью" }
                else { statusText = (g.myTurn ? "Твой ход" : "Ход соперника") + (board.inCheck ? " · Шах!" : "") }
            }
            return
        }
        let st = board.status()
        switch st {
        case .mate: statusText = (board.side == humanColor ? "💀 Мат. Ты проиграл" : "🏆 Мат! Ты победил")
        case .stalemate: statusText = "🤝 Пат — ничья"
        case .repetition: statusText = "🤝 Троекратное повторение — ничья"
        case .fifty: statusText = "🤝 Правило 50 ходов — ничья"
        case .material: statusText = "🤝 Недостаточно материала — ничья"
        case .ongoing:
            if thinking { statusText = "🤖 AvraBot думает…" }
            else { statusText = (board.side == humanColor ? "Твой ход" : "Ход бота") + (board.inCheck ? " · Шах!" : "") }
        }
    }

    func moveList() -> String {
        let b = AvraChessBoard()
        var out = ""
        for i in 0..<board.ply {
            let m = board.moveAt(i)
            if i % 2 == 0 { out += "\(i / 2 + 1). " }
            out += b.san(m) + "  "
            b.make(m)
        }
        return out.isEmpty ? "Ходов пока нет" : out
    }
}

enum AvraHaptics {
    static func tap(_ strong: Bool = false) {
        let g = UIImpactFeedbackGenerator(style: strong ? .medium : .light)
        g.impactOccurred()
    }
}

struct AvraChessBoardView: View {
    @ObservedObject var game: AvraChessGame
    @State var dragId: Int? = nil
    @State var dragOffset: CGSize = .zero

    private var light: Color { AvraChessStyle.color(AvraChessStyle.boards[AvraChessStyle.board].0) }
    private var dark: Color { AvraChessStyle.color(AvraChessStyle.boards[AvraChessStyle.board].1) }

    var body: some View {
        GeometryReader { geo in
            let size = min(geo.size.width, geo.size.height)
            let cell = size / 8
            let flipped = game.humanColor == -1
            ZStack(alignment: .topLeading) {
                // squares
                ForEach(0..<64, id: \.self) { s in
                    let isLight = ((s & 7) + (s >> 3)) % 2 == 1
                    Rectangle()
                        .fill(isLight ? light : dark)
                        .overlay(highlight(s))
                        .frame(width: cell, height: cell)
                        .position(center(s, cell, flipped))
                        .onTapGesture { game.tap(s) }
                }
                // legal targets
                ForEach(game.targets, id: \.self) { t in
                    Circle()
                        .fill(Color.black.opacity(0.22))
                        .frame(width: game.squares[t] != 0 ? cell * 0.9 : cell * 0.3, height: game.squares[t] != 0 ? cell * 0.9 : cell * 0.3)
                        .position(center(t, cell, flipped))
                        .allowsHitTesting(false)
                        .transition(.scale.combined(with: .opacity))
                }
                // pieces
                ForEach(game.pieces()) { item in
                    AvraPieceView(piece: item.piece, size: cell)
                        .scaleEffect(dragId == item.id ? 1.3 : 1)
                        .shadow(color: .black.opacity(dragId == item.id ? 0.35 : 0), radius: 8, y: 6)
                        .position(center(item.square, cell, flipped))
                        .offset(dragId == item.id ? dragOffset : .zero)
                        .zIndex(dragId == item.id ? 10 : 1)
                        .gesture(
                            DragGesture(minimumDistance: 4)
                                .onChanged { v in
                                    guard game.canMove(item.square) else { return }
                                    if dragId != item.id {
                                        dragId = item.id
                                        game.select(item.square)
                                        AvraHaptics.tap()
                                    }
                                    dragOffset = CGSize(width: v.translation.width, height: v.translation.height - cell * 0.3)
                                }
                                .onEnded { v in
                                    defer {
                                        withAnimation(.spring(response: 0.25, dampingFraction: 0.8)) {
                                            dragId = nil
                                            dragOffset = .zero
                                        }
                                    }
                                    guard dragId == item.id else { return }
                                    let p = center(item.square, cell, flipped)
                                    let target = square(at: CGPoint(x: p.x + v.translation.width, y: p.y + v.translation.height - cell * 0.3), cell, flipped)
                                    if let t = target, t != item.square { game.tryMove(from: item.square, to: t) }
                                }
                        )
                        .simultaneousGesture(TapGesture().onEnded { game.tap(item.square) })
                }
            }
            .frame(width: size, height: size)
            .clipShape(RoundedRectangle(cornerRadius: 10))
        }
        .aspectRatio(1, contentMode: .fit)
    }

    @ViewBuilder
    private func highlight(_ s: Int) -> some View {
        let last = game.lastMove
        if game.selected == s {
            Color.yellow.opacity(0.55)
        } else if last != 0 && (AvraChessBoard.from(last) == s || AvraChessBoard.to(last) == s) {
            Color.yellow.opacity(0.32)
        } else if game.board.inCheck && game.board.kingSquare(game.board.side) == s {
            RadialGradient(colors: [Color.red.opacity(0.9), Color.red.opacity(0)], center: .center, startRadius: 2, endRadius: 30)
        } else {
            Color.clear
        }
    }

    private func center(_ s: Int, _ cell: CGFloat, _ flipped: Bool) -> CGPoint {
        let f = s & 7, r = s >> 3
        let col = flipped ? 7 - f : f, row = flipped ? r : 7 - r
        return CGPoint(x: (CGFloat(col) + 0.5) * cell, y: (CGFloat(row) + 0.5) * cell)
    }

    private func square(at p: CGPoint, _ cell: CGFloat, _ flipped: Bool) -> Int? {
        let col = Int(floor(p.x / cell)), row = Int(floor(p.y / cell))
        guard col >= 0, col < 8, row >= 0, row < 8 else { return nil }
        let f = flipped ? 7 - col : col, r = flipped ? row : 7 - row
        return r * 8 + f
    }
}

struct AvraPieceView: View {
    let piece: Int
    let size: CGFloat
    private static let glyphs = ["", "♟\u{FE0E}", "♞\u{FE0E}", "♝\u{FE0E}", "♜\u{FE0E}", "♛\u{FE0E}", "♚\u{FE0E}"]

    var body: some View {
        if let img = AvraChessStyle.image(piece) {
            Image(uiImage: img).resizable().interpolation(.high).frame(width: size, height: size)
        } else {
            glyph
        }
    }

    private var glyph: some View {
        let white = piece > 0
        let g = Self.glyphs[abs(piece)]
        return ZStack {
            Text(g).font(.system(size: size * 0.8)).foregroundColor(white ? .black : .white).offset(x: 1, y: 1)
            Text(g).font(.system(size: size * 0.8)).foregroundColor(white ? .black : .white).offset(x: -1, y: -1)
            Text(g).font(.system(size: size * 0.8)).foregroundColor(white ? .white : Color(white: 0.08))
        }
        .frame(width: size, height: size)
        .shadow(color: .black.opacity(0.3), radius: 1.5, y: 1.5)
        .allowsHitTesting(true)
    }
}

struct AvraChessScreen: View {
    @StateObject var game: AvraChessGame
    @Environment(\.sgTheme) var theme
    @Environment(\.sgNavigation) var navigation

    init(level: Int) {
        _game = StateObject(wrappedValue: AvraChessGame(level: level))
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                HStack {
                    Text("🤖 AvraBot · \(AvraChessAi.levelNames[game.level]) (\(AvraChessAi.levelRating[game.level]))")
                        .font(.headline)
                    Spacer()
                }
                AvraChessBoardView(game: game)
                    .padding(.horizontal, 4)
                Text(game.statusText)
                    .font(.system(size: 17, weight: .bold))
                    .animation(.easeInOut, value: game.statusText)
                Text(game.moveList())
                    .font(.system(size: 14, design: .monospaced))
                    .foregroundColor(theme.secondaryTextColor)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(10)
                    .background(theme.secondaryBackgroundColor)
                    .cornerRadius(12)
                HStack(spacing: 10) {
                    Button(action: { game.undo() }) {
                        Label("Отменить ход", systemImage: "arrow.uturn.backward").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    Button(action: { game.newGame() }) {
                        Label("Новая партия", systemImage: "arrow.triangle.2.circlepath").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                }
                if game.board.ply > 0 {
                    Button(action: {
                        let moves = game.uciMoves
                        navigation?.push(title: "Обзор игры") { AvraChessReviewScreen(moves: moves) }
                    }) {
                        Label("Обзор игры", systemImage: "magnifyingglass").frame(maxWidth: .infinity)
                    }.buttonStyle(.bordered)
                }
                Button(action: { navigation?.push(title: "Оформление шахмат") { AvraChessSettingsScreen() } }) {
                    Label("Доска, фигуры, звуки", systemImage: "paintpalette").frame(maxWidth: .infinity)
                }.buttonStyle(.bordered)
                Picker("Уровень", selection: $game.level) {
                    ForEach(0..<4, id: \.self) { i in Text(AvraChessAi.levelNames[i]).tag(i) }
                }
                .pickerStyle(.segmented)
            }
            .padding(16)
        }
        .background(theme.backgroundColor.ignoresSafeArea())
        .sgNavigationTitle("AvraChess")
    }
}
