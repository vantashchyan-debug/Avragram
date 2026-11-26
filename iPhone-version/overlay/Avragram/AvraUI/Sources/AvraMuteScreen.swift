import Foundation
import SwiftUI
import SGSwiftUI
import Postbox
import TelegramCore
import AccountContext

/// Mute (port of Android AvraMute): new private messages of a muted person are deleted for both
/// sides the moment they arrive and never show up here; a local copy goes to "Said behind your back".
struct AvraMuteScreen: View {
    let context: AccountContext
    @Environment(\.sgTheme) var theme
    @Environment(\.sgNavigation) var navigation
    @State var muted: [EnginePeer] = []

    var body: some View {
        Form {
            Section {
                Button("Замутить человека") { pick() }
            }
            Section(header: Text("Замученные · Сказано за спиной")) {
                if muted.isEmpty { Text("Никого").foregroundColor(theme.secondaryTextColor) }
                ForEach(muted, id: \.id) { peer in
                    HStack {
                        Button(action: {
                            navigation?.push(title: peer.debugDisplayTitle) { AvraBehindScreen(peerId: peer.id) }
                        }) {
                            VStack(alignment: .leading) {
                                Text(peer.debugDisplayTitle).foregroundColor(theme.primaryTextColor)
                                Text("Сообщений за спиной: \(AvraMute.behind(peer.id).count)").font(.footnote).foregroundColor(theme.secondaryTextColor)
                            }
                        }
                        Spacer()
                        Button("Размутить") {
                            _ = AvraMute.toggle(peer.id)
                            load()
                        }.buttonStyle(.bordered)
                    }
                }
            }
            Section {
                Text("Мут работает в личных чатах. Новое сообщение замученного удаляется у вас обоих сразу, как приходит: у тебя оно не появляется ни в чате, ни в уведомлениях. Копия текста хранится только на этом телефоне. Старые сообщения остаются.")
                    .font(.footnote).foregroundColor(theme.secondaryTextColor)
            }
        }
        .onAppear { load() }
        .sgNavigationTitle("Мут")
    }

    private func load() {
        let ids = Array(AvraMessageHook.shared.mutedPeers)
        let _ = (context.engine.data.get(EngineDataMap(ids.map { TelegramEngine.EngineData.Item.Peer.Peer(id: $0) }))
        |> deliverOnMainQueue).start(next: { map in
            muted = ids.compactMap { map[$0] ?? nil }
        })
    }

    private func pick() {
        let controller = context.sharedContext.makePeerSelectionController(PeerSelectionControllerParams(context: context, filter: [.onlyPrivateChats], hasChatListSelector: true, hasContactSelector: true, hasGlobalSearch: false, title: "Кого замутить"))
        controller.peerSelected = { [weak controller] peer, _ in
            controller?.dismiss()
            guard case .user = peer, peer.id != context.account.peerId, !AvraMute.isMuted(peer.id) else { return }
            _ = AvraMute.toggle(peer.id)
            load()
        }
        navigation?.push(controller)
    }
}

struct AvraBehindScreen: View {
    let peerId: PeerId
    @Environment(\.sgTheme) var theme

    var body: some View {
        let items = AvraMute.behind(peerId)
        List {
            if items.isEmpty { Text("Пока ничего").foregroundColor(theme.secondaryTextColor) }
            ForEach(Array(items.enumerated().reversed()), id: \.offset) { item in
                Text(item.element).textSelection(.enabled)
            }
        }
        .sgNavigationTitle("Сказано за спиной")
    }
}
