import Foundation
import UIKit
import SwiftUI
import SGSwiftUI

/// Sea battle (port of the Android com.avragramm.sea): a 10×10 sea, the classic fleet
/// 4, 3, 3, 2, 2, 2, 1, 1, 1, 1, ships never touch, a hit gives another shot.
struct AvraShip: Equatable {
    var x: Int, y: Int, len: Int, vertical: Bool
    func cell(_ i: Int) -> Int { vertical ? (y + i) * 10 + x : y * 10 + x + i }
    func contains(_ c: Int) -> Bool { (0..<len).contains { cell($0) == c } }
    var inside: Bool { x >= 0 && y >= 0 && (vertical ? y + len <= 10 && x < 10 : x + len <= 10 && y < 10) }
}

struct AvraSeaBoard {
    static let fleet = [4, 3, 3, 2, 2, 2, 1, 1, 1, 1]
    var ships: [AvraShip] = []

    func shipAt(_ c: Int) -> Int? { ships.firstIndex { $0.contains(c) } }

    func fits(_ s: AvraShip, skip: Int?) -> Bool {
        guard s.inside else { return false }
        for (i, o) in ships.enumerated() where i != skip {
            for a in 0..<s.len {
                let ca = s.cell(a)
                for b in 0..<o.len {
                    let cb = o.cell(b)
                    if abs(ca % 10 - cb % 10) <= 1 && abs(ca / 10 - cb / 10) <= 1 { return false }
                }
            }
        }
        return true
    }

    static func random() -> AvraSeaBoard {
        while true {
            var b = AvraSeaBoard()
            var ok = true
            for len in fleet {
                var placed = false
                for _ in 0..<300 where !placed {
                    let s = AvraShip(x: Int.random(in: 0..<10), y: Int.random(in: 0..<10), len: len, vertical: Bool.random())
                    if b.fits(s, skip: nil) { b.ships.append(s); placed = true }
                }
                if !placed { ok = false; break }
            }
            if ok { return b }
        }
    }

    /// "m" miss, "h" hit, "k" sunk.
    func resolve(_ cell: Int, hitBefore: Set<Int>) -> Character {
        guard let i = shipAt(cell) else { return "m" }
        let s = ships[i]
        for j in 0..<s.len where s.cell(j) != cell && !hitBefore.contains(s.cell(j)) { return "h" }
        return "k"
    }
}

enum AvraSeaMark: Int { case unknown = 0, miss, hit, sunk, near }

enum AvraSeaKnowledge {
    /// What a shooter knows: misses, hits, sunk ships and the water around them.
    static func marks(_ shots: [(Int, Character)]) -> [AvraSeaMark] {
        var k = [AvraSeaMark](repeating: .unknown, count: 100)
        for s in shots { k[s.0] = s.1 == "m" ? .miss : .hit }
        for s in shots where s.1 == "k" {
            var part: [Int] = []
            var stack = [s.0]
            var seen = Set<Int>()
            while let c = stack.popLast() {
                if seen.contains(c) { continue }
                seen.insert(c)
                if k[c] != .hit && k[c] != .sunk { continue }
                part.append(c)
                let x = c % 10, y = c / 10
                if x > 0 { stack.append(c - 1) }
                if x < 9 { stack.append(c + 1) }
                if y > 0 { stack.append(c - 10) }
                if y < 9 { stack.append(c + 10) }
            }
            for c in part {
                k[c] = .sunk
                let x = c % 10, y = c / 10
                for dy in -1...1 { for dx in -1...1 {
                    let nx = x + dx, ny = y + dy
                    if nx < 0 || ny < 0 || nx > 9 || ny > 9 { continue }
                    if k[ny * 10 + nx] == .unknown { k[ny * 10 + nx] = .near }
                } }
            }
        }
        return k
    }

    static func sunkLengths(_ k: [AvraSeaMark]) -> [Int] {
        var seen = Set<Int>(), out: [Int] = []
        for c0 in 0..<100 where k[c0] == .sunk && !seen.contains(c0) {
            var n = 0
            var stack = [c0]
            while let c = stack.popLast() {
                if seen.contains(c) || k[c] != .sunk { continue }
                seen.insert(c); n += 1
                let x = c % 10, y = c / 10
                if x > 0 { stack.append(c - 1) }
                if x < 9 { stack.append(c + 1) }
                if y > 0 { stack.append(c - 10) }
                if y < 9 { stack.append(c + 10) }
            }
            out.append(n)
        }
        return out
    }
}

enum AvraSeaAi {
    static let names = ["Юнга", "Боцман", "Адмирал"]

    static func pick(level: Int, _ k: [AvraSeaMark]) -> Int {
        let free = (0..<100).filter { k[$0] == .unknown }
        guard !free.isEmpty else { return -1 }
        let wounded = (0..<100).filter { k[$0] == .hit }
        if level <= 0 {
            if !wounded.isEmpty && Int.random(in: 0..<100) < 35, let t = target(k, wounded) { return t }
            let any = free + (0..<100).filter { k[$0] == .near }
            return any.randomElement() ?? free[0]
        }
        if level == 1 {
            if !wounded.isEmpty, let t = target(k, wounded) { return t }
            let parity = free.filter { ($0 % 10 + $0 / 10) % 2 == 0 }
            return (parity.isEmpty ? free : parity).randomElement() ?? free[0]
        }
        var left = AvraSeaBoard.fleet
        for l in AvraSeaKnowledge.sunkLengths(k) { if let i = left.firstIndex(of: l) { left.remove(at: i) } }
        var score = [Int](repeating: 0, count: 100)
        for len in left {
            for o in 0..<2 where !(len == 1 && o == 1) {
                for y in 0..<10 { for x in 0..<10 {
                    if o == 0 ? x + len > 10 : y + len > 10 { continue }
                    var ok = true, hits = 0
                    for i in 0..<len {
                        let c = o == 0 ? y * 10 + x + i : (y + i) * 10 + x
                        switch k[c] {
                        case .miss, .sunk, .near: ok = false
                        case .hit: hits += 1
                        case .unknown: break
                        }
                        if !ok { break }
                    }
                    if !ok || (!wounded.isEmpty && hits == 0) { continue }
                    let w = wounded.isEmpty ? 1 : 1 + hits * 40
                    for i in 0..<len {
                        let c = o == 0 ? y * 10 + x + i : (y + i) * 10 + x
                        if k[c] == .unknown { score[c] += w }
                    }
                } }
            }
        }
        return free.max { score[$0] * 8 + ($0 * 7919 % 8) < score[$1] * 8 + ($1 * 7919 % 8) } ?? free[0]
    }

    private static func target(_ k: [AvraSeaMark], _ wounded: [Int]) -> Int? {
        var options: [Int] = []
        let line = wounded.count >= 2
        let vertical = line && wounded[0] % 10 == wounded[1] % 10
        for c in wounded {
            let x = c % 10, y = c / 10
            for (dx, dy) in [(1, 0), (-1, 0), (0, 1), (0, -1)] {
                if line && (vertical ? dx != 0 : dy != 0) { continue }
                let nx = x + dx, ny = y + dy
                if nx < 0 || ny < 0 || nx > 9 || ny > 9 { continue }
                let n = ny * 10 + nx
                if k[n] == .unknown && !options.contains(n) { options.append(n) }
            }
        }
        return options.randomElement()
    }
}

final class AvraSeaGame: ObservableObject {
    @Published var level: Int
    @Published private(set) var mine = AvraSeaBoard.random()
    @Published private(set) var bot = AvraSeaBoard.random()
    @Published private(set) var myShots: [(Int, Character)] = []
    @Published private(set) var botShots: [(Int, Character)] = []
    @Published private(set) var placing = true
    @Published private(set) var botTurn = false
    @Published private(set) var winner = 0
    @Published var banner: String? = nil

    init(level: Int) { self.level = level }

    var enemyMarks: [AvraSeaMark] { AvraSeaKnowledge.marks(myShots) }
    var ownMarks: [AvraSeaMark] { AvraSeaKnowledge.marks(botShots) }

    func shuffle() {
        withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) { mine = AvraSeaBoard.random() }
        AvraHaptics.tap()
    }

    func rotate(_ index: Int) {
        var s = mine.ships[index]
        s.vertical.toggle()
        if s.len > 1 && mine.fits(s, skip: index) {
            withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) { mine.ships[index] = s }
            AvraHaptics.tap()
        }
    }

    func move(_ index: Int, toX x: Int, y: Int) -> Bool {
        var s = mine.ships[index]
        s.x = x; s.y = y
        guard mine.fits(s, skip: index) else { return false }
        withAnimation(.spring(response: 0.3, dampingFraction: 0.75)) { mine.ships[index] = s }
        AvraHaptics.tap()
        return true
    }

    func start() {
        AvraChessStyle.playSea("start")
        withAnimation(.easeInOut(duration: 0.35)) { placing = false }
        AvraHaptics.tap(true)
    }

    func fire(_ cell: Int) {
        guard !placing, !botTurn, winner == 0, enemyMarks[cell] == .unknown else { return }
        let hit = Set(myShots.filter { $0.1 != "m" }.map { $0.0 })
        let r = bot.resolve(cell, hitBefore: hit)
        withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) { myShots.append((cell, r)) }
        show(r == "m" ? "💧 Мимо" : r == "k" ? "🔥 Убил!" : "💥 Попал!")
        AvraChessStyle.playSea(r == "m" ? "splash" : r == "k" ? "sunk" : "hit")
        AvraHaptics.tap(r != "m")
        if myShots.filter({ $0.1 == "k" }).count >= AvraSeaBoard.fleet.count {
            winner = 1
            show("🏆 Победа!")
            AvraChessStyle.playSea("win")
        } else if r == "m" {
            botTurn = true
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.9) { [weak self] in self?.botShoot() }
        }
    }

    private func botShoot() {
        guard winner == 0 else { return }
        let cell = AvraSeaAi.pick(level: level, ownMarks)
        guard cell >= 0 else { return }
        let hit = Set(botShots.filter { $0.1 != "m" }.map { $0.0 })
        let r = mine.resolve(cell, hitBefore: hit)
        withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) { botShots.append((cell, r)) }
        show(r == "m" ? "😮‍💨 Соперник промахнулся" : r == "k" ? "💀 Твой корабль потоплен" : "💢 По тебе попали!")
        AvraChessStyle.playSea(r == "m" ? "splash" : r == "k" ? "sunk" : "hit")
        if botShots.filter({ $0.1 == "k" }).count >= AvraSeaBoard.fleet.count {
            winner = -1
            show("💀 Поражение")
            AvraChessStyle.playSea("lose")
        } else if r == "m" {
            botTurn = false
        } else {
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { [weak self] in self?.botShoot() }
        }
    }

    func restart() {
        mine = AvraSeaBoard.random(); bot = AvraSeaBoard.random()
        myShots = []; botShots = []; placing = true; botTurn = false; winner = 0
    }

    private func show(_ text: String) {
        withAnimation(.spring(response: 0.3, dampingFraction: 0.6)) { banner = text }
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.1) { [weak self] in
            guard let self, self.banner == text else { return }
            withAnimation(.easeOut(duration: 0.3)) { self.banner = nil }
        }
    }
}

struct AvraSeaGrid: View {
    let marks: [AvraSeaMark]
    let ships: [AvraShip]
    let showShips: Bool
    let active: Bool
    var onCell: ((Int) -> Void)? = nil
    var onShipTap: ((Int) -> Void)? = nil
    var onShipDrop: ((Int, Int, Int) -> Bool)? = nil
    @State var dragIndex: Int? = nil
    @State var dragOffset: CGSize = .zero

    private static let letters = Array("АБВГДЕЖЗИК")

    var body: some View {
        GeometryReader { geo in
            let band = geo.size.width / 10.6 * 0.6
            let cell = (geo.size.width - band) / 10
            ZStack(alignment: .topLeading) {
                ForEach(0..<10, id: \.self) { i in
                    Text(String(Self.letters[i])).font(.system(size: cell * 0.4, weight: .bold)).foregroundColor(.white.opacity(0.8))
                        .position(x: band + (CGFloat(i) + 0.5) * cell, y: band / 2)
                    Text("\(i + 1)").font(.system(size: cell * 0.4, weight: .bold)).foregroundColor(.white.opacity(0.8))
                        .position(x: band / 2, y: band + (CGFloat(i) + 0.5) * cell)
                }
                RoundedRectangle(cornerRadius: cell * 0.35)
                    .fill(LinearGradient(colors: [Color(red: 0.07, green: 0.51, blue: 0.71), Color(red: 0.03, green: 0.25, blue: 0.37)], startPoint: .top, endPoint: .bottom))
                    .overlay(RoundedRectangle(cornerRadius: cell * 0.35).stroke(active ? Color(red: 0.31, green: 0.76, blue: 0.97) : Color.white.opacity(0.3), lineWidth: 2))
                    .frame(width: cell * 10, height: cell * 10)
                    .offset(x: band, y: band)
                // grid
                Path { p in
                    for i in 1..<10 {
                        p.move(to: CGPoint(x: band + CGFloat(i) * cell, y: band)); p.addLine(to: CGPoint(x: band + CGFloat(i) * cell, y: band + 10 * cell))
                        p.move(to: CGPoint(x: band, y: band + CGFloat(i) * cell)); p.addLine(to: CGPoint(x: band + 10 * cell, y: band + CGFloat(i) * cell))
                    }
                }
                .stroke(Color.white.opacity(0.2), lineWidth: 1)
                // ships
                if showShips {
                    ForEach(Array(ships.enumerated()), id: \.offset) { item in
                        let s = item.element
                        let sunk = (0..<s.len).allSatisfy { marks[s.cell($0)] == .sunk }
                        shipShape(s, cell: cell, sunk: sunk)
                            .offset(dragIndex == item.offset ? dragOffset : .zero)
                            .position(x: band + (CGFloat(s.x) + CGFloat(s.vertical ? 1 : s.len) / 2) * cell,
                                      y: band + (CGFloat(s.y) + CGFloat(s.vertical ? s.len : 1) / 2) * cell)
                            .zIndex(dragIndex == item.offset ? 5 : 1)
                            .onTapGesture { onShipTap?(item.offset) }
                            .gesture(DragGesture(minimumDistance: 6)
                                .onChanged { v in
                                    guard onShipDrop != nil else { return }
                                    dragIndex = item.offset
                                    dragOffset = v.translation
                                }
                                .onEnded { v in
                                    guard let drop = onShipDrop else { return }
                                    let nx = s.x + Int((v.translation.width / cell).rounded())
                                    let ny = s.y + Int((v.translation.height / cell).rounded())
                                    _ = drop(item.offset, nx, ny)
                                    withAnimation(.spring(response: 0.3, dampingFraction: 0.75)) { dragIndex = nil; dragOffset = .zero }
                                })
                    }
                }
                // wrecks seen by the shooter
                if !showShips {
                    ForEach(0..<100, id: \.self) { c in
                        if marks[c] == .sunk {
                            RoundedRectangle(cornerRadius: cell * 0.2)
                                .fill(LinearGradient(colors: [Color(red: 0.36, green: 0.25, blue: 0.22), Color(white: 0.13)], startPoint: .top, endPoint: .bottom))
                                .frame(width: cell * 0.8, height: cell * 0.8)
                                .position(x: band + (CGFloat(c % 10) + 0.5) * cell, y: band + (CGFloat(c / 10) + 0.5) * cell)
                                .transition(.opacity)
                        }
                    }
                }
                // marks
                ForEach(0..<100, id: \.self) { c in
                    markView(marks[c], cell: cell)
                        .position(x: band + (CGFloat(c % 10) + 0.5) * cell, y: band + (CGFloat(c / 10) + 0.5) * cell)
                        .allowsHitTesting(false)
                }
                // tap targets
                if active, let onCell = onCell {
                    ForEach(0..<100, id: \.self) { c in
                        Color.white.opacity(0.001)
                            .frame(width: cell, height: cell)
                            .position(x: band + (CGFloat(c % 10) + 0.5) * cell, y: band + (CGFloat(c / 10) + 0.5) * cell)
                            .onTapGesture { onCell(c) }
                    }
                }
                if !active && onCell != nil {
                    Color.black.opacity(0.25).frame(width: cell * 10, height: cell * 10).offset(x: band, y: band).allowsHitTesting(false)
                }
            }
        }
        .aspectRatio(1, contentMode: .fit)
    }

    private func shipShape(_ s: AvraShip, cell: CGFloat, sunk: Bool) -> some View {
        let w = CGFloat(s.vertical ? 1 : s.len) * cell - cell * 0.26
        let h = CGFloat(s.vertical ? s.len : 1) * cell - cell * 0.26
        return Capsule()
            .fill(LinearGradient(colors: sunk ? [Color(red: 0.36, green: 0.25, blue: 0.22), Color(white: 0.13)] : [Color(white: 0.9), Color(red: 0.47, green: 0.56, blue: 0.61)],
                                 startPoint: s.vertical ? .leading : .top, endPoint: s.vertical ? .trailing : .bottom))
            .overlay(Capsule().stroke(sunk ? Color.red : Color(red: 0.15, green: 0.2, blue: 0.22), lineWidth: 1.5))
            .frame(width: w, height: h)
            .shadow(color: .black.opacity(0.35), radius: 3, x: 1, y: 2)
    }

    @ViewBuilder
    private func markView(_ m: AvraSeaMark, cell: CGFloat) -> some View {
        switch m {
        case .miss:
            Circle().fill(Color.white.opacity(0.8)).frame(width: cell * 0.22, height: cell * 0.22).transition(.scale)
        case .hit:
            ZStack {
                Circle().fill(RadialGradient(colors: [Color.yellow, Color.orange, Color.red.opacity(0)], center: .center, startRadius: 1, endRadius: cell * 0.5))
                    .frame(width: cell, height: cell)
                Image(systemName: "xmark").font(.system(size: cell * 0.5, weight: .black)).foregroundColor(Color(red: 0.72, green: 0.11, blue: 0.11))
            }
            .transition(.scale(scale: 2).combined(with: .opacity))
        case .sunk:
            Image(systemName: "xmark").font(.system(size: cell * 0.5, weight: .black)).foregroundColor(.red)
        case .near:
            Circle().fill(Color.white.opacity(0.3)).frame(width: cell * 0.12, height: cell * 0.12)
        case .unknown:
            EmptyView()
        }
    }
}

struct AvraSeaScreen: View {
    @StateObject var game: AvraSeaGame

    init(level: Int) { _game = StateObject(wrappedValue: AvraSeaGame(level: level)) }

    var body: some View {
        AvraSeaScreenBody(game: game)
    }
}

struct AvraSeaScreenBody: View {
    @ObservedObject var game: AvraSeaGame
    @Environment(\.sgTheme) var theme

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                if game.placing {
                    Text("Расставь флот").font(.title2.bold()).foregroundColor(.white)
                    Text("Перетаскивай корабль, нажми на него, чтобы повернуть").font(.footnote).foregroundColor(.white.opacity(0.7))
                    AvraSeaGrid(marks: game.ownMarks, ships: game.mine.ships, showShips: true, active: false,
                                onShipTap: { game.rotate($0) }, onShipDrop: { game.move($0, toX: $1, y: $2) })
                    HStack {
                        Button(action: { game.shuffle() }) { Label("Случайно", systemImage: "dice").frame(maxWidth: .infinity) }
                            .buttonStyle(.bordered).tint(.white)
                        Button(action: { game.start() }) { Label("В бой!", systemImage: "flag.checkered").frame(maxWidth: .infinity) }
                            .buttonStyle(.borderedProminent)
                    }
                    Picker("Бот", selection: $game.level) {
                        ForEach(0..<3, id: \.self) { Text(AvraSeaAi.names[$0]).tag($0) }
                    }.pickerStyle(.segmented)
                } else {
                    Text(game.winner > 0 ? "🏆 Победа!" : game.winner < 0 ? "💀 Поражение" : game.botTurn ? "Ход соперника" : "🎯 Твой выстрел!")
                        .font(.title2.bold()).foregroundColor(game.winner > 0 ? .yellow : .white)
                    Text("Воды: 🤖 \(AvraSeaAi.names[game.level])").font(.caption.bold()).foregroundColor(.white.opacity(0.7)).frame(maxWidth: .infinity, alignment: .leading)
                    ZStack {
                        AvraSeaGrid(marks: game.enemyMarks, ships: game.bot.ships, showShips: game.winner != 0, active: !game.botTurn && game.winner == 0,
                                    onCell: { game.fire($0) })
                        if let b = game.banner {
                            Text(b).font(.system(size: 30, weight: .heavy)).foregroundColor(.white)
                                .shadow(color: .black.opacity(0.8), radius: 6, y: 2)
                                .transition(.scale(scale: 0.4).combined(with: .opacity))
                        }
                    }
                    Text("Твой флот").font(.caption.bold()).foregroundColor(.white.opacity(0.7)).frame(maxWidth: .infinity, alignment: .leading)
                    AvraSeaGrid(marks: game.ownMarks, ships: game.mine.ships, showShips: true, active: false)
                        .padding(.horizontal, 40)
                    Button(action: { game.restart() }) { Label(game.winner != 0 ? "Реванш" : "Заново", systemImage: "arrow.counterclockwise").frame(maxWidth: .infinity) }
                        .buttonStyle(.borderedProminent)
                }
            }
            .padding(16)
        }
        .background(Color(red: 0.02, green: 0.09, blue: 0.15).ignoresSafeArea())
        .sgNavigationTitle("Морской бой")
    }
}
