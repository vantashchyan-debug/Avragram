import Foundation
import SwiftUI
import SGSwiftUI
import SwiftSignalKit
import TelegramCore
import AccountContext

/// Spam (port of the Android one): send a message N times with a pause between, to a chosen chat.
/// At most 100 messages and at least 0.3 s between them, so Telegram does not limit the account.
final class AvraSpamRunner: ObservableObject {
    @Published var text = ""
    @Published var count = 10.0
    @Published var interval = 1.0
    @Published var peer: EnginePeer? = nil
    @Published private(set) var sent = 0
    @Published private(set) var running = false
    private var timer: Timer?

    func start(context: AccountContext) {
        guard !running, let peer = peer else { return }
        let body = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty else { return }
        let total = max(1, min(100, Int(count)))
        let pause = max(0.3, interval)
        sent = 0
        running = true
        let send = { [weak self] in
            guard let self, self.running else { return }
            let _ = enqueueMessages(account: context.account, peerId: peer.id, messages: [.message(text: body, attributes: [], inlineStickers: [:], mediaReference: nil, threadId: nil, replyToMessageId: nil, replyToStoryId: nil, localGroupingKey: nil, correlationId: nil, bubbleUpEmojiOrStickersets: [])]).start()
            self.sent += 1
            if self.sent >= total { self.stop() }
        }
        send()
        if total > 1 {
            let t = Timer(timeInterval: pause, repeats: true) { _ in send() }
            RunLoop.main.add(t, forMode: .common)
            timer = t
        }
    }

    func stop() {
        timer?.invalidate()
        timer = nil
        running = false
    }

    deinit { timer?.invalidate() }
}

struct AvraSpamScreen: View {
    let context: AccountContext
    @StateObject var runner = AvraSpamRunner()
    @Environment(\.sgTheme) var theme
    @Environment(\.sgNavigation) var navigation

    var body: some View {
        Form {
            Section(header: Text("Кому")) {
                Button(action: pickChat) {
                    HStack {
                        Text(runner.peer.map { $0.debugDisplayTitle } ?? "Выбрать чат")
                        Spacer()
                        Image(systemName: "chevron.right")
                    }
                }
            }
            Section(header: Text("Сообщение")) {
                TextEditor(text: $runner.text).frame(minHeight: 90)
            }
            Section(header: Text("Сколько раз: \(Int(runner.count))")) {
                Slider(value: $runner.count, in: 1...100, step: 1)
            }
            Section(header: Text(String(format: "Пауза: %.1f с", runner.interval))) {
                Slider(value: $runner.interval, in: 0.3...10, step: 0.1)
            }
            Section {
                if runner.running {
                    Button(role: .destructive, action: { runner.stop() }) { Text("Стоп · отправлено \(runner.sent) из \(Int(runner.count))") }
                } else {
                    Button(action: { runner.start(context: context) }) { Text("Отправить") }
                        .disabled(runner.peer == nil || runner.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
            Section {
                Text("Не больше 100 сообщений за раз. Слишком частая отправка может временно ограничить аккаунт в Telegram.")
                    .font(.footnote).foregroundColor(theme.secondaryTextColor)
            }
        }
        .onDisappear { runner.stop() }
        .sgNavigationTitle("Спам")
    }

    private func pickChat() {
        let controller = context.sharedContext.makePeerSelectionController(PeerSelectionControllerParams(context: context, filter: [.onlyWriteable, .excludeDisabled], hasContactSelector: false, title: "Кому отправить"))
        controller.peerSelected = { [weak controller] peer, _ in
            runner.peer = peer
            controller?.dismiss()
        }
        navigation?.push(controller)
    }
}
