import Foundation
import SwiftUI
import SGSwiftUI
import Postbox
import TelegramCore
import AccountContext

/// AvraChess online lobby: challenge a friend (time control, color), your games, ratings.
struct AvraChessOnlineScreen: View {
    @Environment(\.sgTheme) private var theme
    @Environment(\.sgNavigation) private var navigation
    @State private var games = AvraChessNet.list()
    @State private var tc = 2
    @State private var color = 0

    var body: some View {
        Form {
            Section(header: Text("Вызвать друга")) {
                Picker("Контроль времени", selection: $tc) {
                    ForEach(0..<AvraChessNet.tcs.count, id: \.self) { Text(AvraChessNet.tcTitle($0)).tag($0) }
                }
                Picker("Цвет", selection: $color) {
                    Text("Случайно").tag(0)
                    Text("Белые").tag(1)
                    Text("Чёрные").tag(-1)
                }
                .pickerStyle(.segmented)
                Button("Выбрать друга и бросить вызов") { pickFriend() }
            }
            Section(header: Text("Рейтинг Эло")) {
                ForEach(0..<4, id: \.self) { c in
                    HStack {
                        Text(AvraChessNet.categoryEmoji[c] + " " + AvraChessNet.categories[c])
                        Spacer()
                        Text("\(AvraChessNet.rating(c))").bold()
                        Text("· \(AvraChessNet.gamesPlayed(c)) парт.").foregroundColor(theme.secondaryTextColor)
                    }
                }
            }
            Section(header: Text("Партии")) {
                if games.isEmpty {
                    Text("Пока нет онлайн-партий").foregroundColor(theme.secondaryTextColor)
                }
                ForEach(games) { g in
                    Button(action: {
                        navigation?.push(title: "AvraChess") { AvraChessOnlineGameScreen(gameId: g.id, cardKind: "") }
                    }) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(g.oppName.isEmpty ? "Соперник" : g.oppName).foregroundColor(theme.primaryTextColor)
                            Text(stateText(g) + " · " + AvraChessNet.tcTitle(g.tc)).font(.footnote).foregroundColor(theme.secondaryTextColor)
                        }
                    }
                }
            }
            Section {
                Text("Партия идёт через вашу личку скрытыми сообщениями: они не видны и удаляются после прочтения, в том числе у Android-версии Avragram. В чате остаются только карточки вызова и итога.")
                    .font(.footnote).foregroundColor(theme.secondaryTextColor)
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: AvraOnline.changed)) { _ in games = AvraChessNet.list() }
        .sgNavigationTitle("AvraChess онлайн")
    }

    private func stateText(_ g: AvraChessOnlineGame) -> String {
        switch g.state {
        case AvraChessNet.inviteIn: return "📨 Тебе бросили вызов"
        case AvraChessNet.inviteOut: return "⏳ Ждём ответа"
        case AvraChessNet.declined: return "Отклонён"
        case AvraChessNet.finished: return g.result == 0 ? "🤝 Ничья" : g.result == g.myColor ? "🏆 Победа" : "💀 Поражение"
        default: return g.myTurn ? "🎯 Твой ход" : "Ход соперника"
        }
    }

    private func pickFriend() {
        guard let context = AvraOnline.context else { return }
        let controller = context.sharedContext.makePeerSelectionController(PeerSelectionControllerParams(context: context, filter: [.onlyPrivateChats, .onlyWriteable, .excludeDisabled], hasChatListSelector: true, hasContactSelector: true, hasGlobalSearch: false, title: "Кого вызвать"))
        let tcValue = tc, colorValue = color
        controller.peerSelected = { [weak controller] peer, _ in
            controller?.dismiss()
            guard case let .user(user) = peer, user.botInfo == nil, peer.id != context.account.peerId else { return }
            let g = AvraChessNet.invite(peer: AvraOnline.userId(peer.id), name: user.addressName.map { "@" + $0 } ?? peer.debugDisplayTitle, tc: tcValue, color: colorValue)
            navigation?.push(title: "AvraChess") { AvraChessOnlineGameScreen(gameId: g.id, cardKind: "") }
        }
        navigation?.push(controller)
    }
}

/// One online game: the board, clocks, draw / resign, accept / decline for a challenge.
struct AvraChessOnlineGameScreen: View {
    let gameId: String
    let cardKind: String
    @StateObject private var game: AvraChessGame
    @State private var tick = Date()
    @Environment(\.sgTheme) private var theme
    @Environment(\.sgNavigation) private var navigation
    private let timer = Timer.publish(every: 0.5, on: .main, in: .common).autoconnect()

    init(gameId: String, cardKind: String) {
        self.gameId = gameId
        self.cardKind = cardKind
        _game = StateObject(wrappedValue: AvraChessGame(online: gameId))
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                if let g = AvraChessNet.games[gameId] {
                    clock(g, color: -g.myColor, name: g.oppName.isEmpty ? "Соперник" : g.oppName, rating: g.oppRating)
                    AvraChessBoardView(game: game)
                    clock(g, color: g.myColor, name: "Ты", rating: AvraChessNet.rating(AvraChessNet.tcs[g.tc].2))
                    Text(game.statusText).font(.headline)
                    actions(g)
                    Text(game.moveList()).font(.system(size: 14, design: .monospaced)).foregroundColor(theme.secondaryTextColor)
                        .frame(maxWidth: .infinity, alignment: .leading).padding(10)
                        .background(theme.secondaryBackgroundColor).cornerRadius(12)
                } else {
                    Text("Партия ещё не пришла. Если тебе бросили вызов только что, подожди пару секунд.")
                        .multilineTextAlignment(.center).padding(30)
                }
            }
            .padding(16)
        }
        .background(theme.backgroundColor.ignoresSafeArea())
        .onReceive(timer) { now in
            tick = now
            if let g = AvraChessNet.games[gameId], g.state == AvraChessNet.active, g.myTurn, g.remaining(g.myColor) <= 0 {
                AvraChessNet.claimTimeout(gameId)
            }
        }
        .sgNavigationTitle("AvraChess")
    }

    private func clock(_ g: AvraChessOnlineGame, color: Int, name: String, rating: Int) -> some View {
        let ms = max(0, g.remaining(color))
        let active = g.state == AvraChessNet.active && g.sideToMove == color
        return HStack {
            VStack(alignment: .leading) {
                Text(name).font(.headline)
                Text("\(rating)").font(.footnote).foregroundColor(theme.secondaryTextColor)
            }
            Spacer()
            Text(format(ms, daily: AvraChessNet.isDaily(g.tc)))
                .font(.system(size: 22, weight: .bold, design: .monospaced))
                .padding(.horizontal, 12).padding(.vertical, 4)
                .background(RoundedRectangle(cornerRadius: 8).fill(active ? (ms < 10_000 && !AvraChessNet.isDaily(g.tc) ? Color.red : theme.accentColor) : theme.secondaryBackgroundColor))
                .foregroundColor(active ? .white : theme.secondaryTextColor)
        }
        .id(tick)
    }

    private func format(_ ms: Int64, daily: Bool) -> String {
        if daily {
            let h = ms / 3_600_000
            return h >= 24 ? "\(h / 24) д \(h % 24) ч" : "\(h) ч \(ms / 60_000 % 60) мин"
        }
        if ms < 10_000 { return String(format: "%d.%d", ms / 1000, ms / 100 % 10) }
        let s = ms / 1000
        return String(format: "%d:%02d", s / 60, s % 60)
    }

    @ViewBuilder
    private func actions(_ g: AvraChessOnlineGame) -> some View {
        HStack(spacing: 10) {
            switch g.state {
            case AvraChessNet.inviteIn:
                Button("✅ Принять") { AvraChessNet.accept(gameId) }.buttonStyle(.borderedProminent)
                Button("❌ Отклонить") { AvraChessNet.decline(gameId) }.buttonStyle(.bordered)
            case AvraChessNet.inviteOut:
                Button("✖ Отменить вызов") { AvraChessNet.decline(gameId) }.buttonStyle(.bordered)
            case AvraChessNet.active:
                if g.drawOfferIn {
                    Button("Принять ничью") { AvraChessNet.offerDraw(gameId) }.buttonStyle(.borderedProminent)
                    Button("Отклонить") { AvraChessNet.declineDraw(gameId) }.buttonStyle(.bordered)
                } else {
                    Button(g.drawOfferOut ? "Ничья предложена" : "🤝 Ничья") { if !g.drawOfferOut { AvraChessNet.offerDraw(gameId) } }.buttonStyle(.bordered)
                }
                if !g.myTurn && g.remaining(g.sideToMove) <= 0 {
                    Button("Время вышло — победа") { AvraChessNet.claimTimeout(gameId) }.buttonStyle(.borderedProminent)
                }
                Button("🏳 Сдаться") { AvraChessNet.resign(gameId) }.buttonStyle(.bordered).tint(.red)
            default:
                Button("🔁 Реванш") {
                    AvraChessNet.invite(peer: g.peer, name: g.oppName, tc: g.tc, color: -g.myColor)
                }.buttonStyle(.borderedProminent)
                if !g.moves.isEmpty {
                    Button("🔍 Обзор игры") {
                        let moves = g.moves
                        navigation?.push(title: "Обзор игры") { AvraChessReviewScreen(moves: moves) }
                    }.buttonStyle(.bordered)
                }
            }
        }
    }
}
