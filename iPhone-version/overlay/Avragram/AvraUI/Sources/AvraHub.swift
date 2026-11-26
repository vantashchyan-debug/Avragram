import Foundation
import UIKit
import SwiftUI
import SGSwiftUI
import SGSettingsUI
import Display
import TelegramPresentationData
import AccountContext

/// Avragram version of the iPhone app (shown in the hub).
public let avragramIOSVersion = "1.0.0"

struct AvraHubView: View {
    let context: AccountContext
    @Environment(\.sgTheme) private var theme
    @Environment(\.sgNavigation) private var navigation

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                VStack(spacing: 6) {
                    Text("Avragram").font(.system(size: 30, weight: .heavy))
                    Text("для iPhone · \(avragramIOSVersion)").font(.subheadline).foregroundColor(theme.secondaryTextColor)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)

                section("Развлекуха") {
                    row("♟", "AvraChess", "Шахматы против AvraBot, 4 уровня") {
                        navigation?.push(title: "AvraChess") { AvraChessScreen(level: 1) }
                    }
                    row("🌐", "AvraChess онлайн", "С другом через личку, рейтинг Эло, играет и с Android") {
                        navigation?.push(title: "AvraChess онлайн") { AvraChessOnlineScreen() }
                    }
                    row("🚢", "Морской бой", "Против бота: Юнга, Боцман, Адмирал") {
                        navigation?.push(title: "Морской бой") { AvraSeaScreen(level: 1) }
                    }
                    row("⚓", "Морской бой онлайн", "С другом, честная игра с проверкой расстановки") {
                        navigation?.push(title: "Морской бой онлайн") { AvraSeaOnlineLobby() }
                    }
                    ForEach(AvraGameKind.allCases) { g in
                        let best = AvraScores.bestAny(g)
                        row(g.emoji, g.title, (best > 0 ? "Рекорд \(best) · " : "") + g.info) {
                            navigation?.push(title: g.title) { AvraGameScreenFor(kind: g) }
                        }
                    }
                    row("🧮", "Калькулятор", "Инженерный: скобки, память, x!, √, sin, ln") {
                        navigation?.push(title: "Калькулятор") { AvraCalculatorScreen() }
                    }
                    row("📨", "Спам", "Отправить сообщение несколько раз в выбранный чат") {
                        navigation?.push(title: "Спам") { AvraSpamScreen(context: context) }
                    }
                    row("🎛", "Саундпад", "12 звуков и свои, слышно собеседнику в звонке") {
                        navigation?.push(title: "Саундпад") { AvraSoundpadScreen() }
                    }
                    row("🎙", "Voice changer", "Бас, хелий, робот, эхо, рация, демон и другие") {
                        navigation?.push(title: "Voice changer") { AvraVoiceScreen() }
                    }
                }

                section("Оформление") {
                    row("🎨", "Темы, шрифты, скругления", "35 тем Avragram, 20 шрифтов, форма пузырей") {
                        navigation?.push(title: "Оформление") { AvraAppearanceScreen(context: context) }
                    }
                    row("🎬", "Видео при запуске", "Видео Avragram вперемешку, своё видео или фото") {
                        navigation?.push(title: "Видео при запуске") { AvraIntroSettingsScreen() }
                    }
                }

                section("Приватность") {
                    row("🔇", "Мут и «Сказано за спиной»", "Сообщения замученного удаляются у обоих сразу") {
                        navigation?.push(title: "Мут") { AvraMuteScreen(context: context) }
                    }
                }

                section("Настройки") {
                    row("⚙️", "Swiftgram", "Настройки, на которых основан Avragram") {
                        navigation?.push(sgSettingsController(context: context))
                    }
                }

                Text("Avragram для iPhone основан на Swiftgram (GPL). Онлайн-игры, темы, спам, саундпад и voice changer переносятся с Android по этапам.")
                    .font(.footnote)
                    .foregroundColor(theme.secondaryTextColor)
                    .padding(.horizontal, 4)
            }
            .padding(16)
        }
        .background(theme.backgroundColor.ignoresSafeArea())
        .sgNavigationTitle("Avragram")
    }

    private func section<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title.uppercased()).font(.caption.bold()).foregroundColor(theme.secondaryTextColor).padding(.leading, 6)
            VStack(spacing: 0) { content() }
                .background(theme.secondaryBackgroundColor)
                .cornerRadius(14)
        }
    }

    private func row(_ icon: String, _ title: String, _ subtitle: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Text(icon).font(.system(size: 26)).frame(width: 36)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.body.weight(.semibold)).foregroundColor(theme.primaryTextColor)
                    Text(subtitle).font(.footnote).foregroundColor(theme.secondaryTextColor)
                }
                Spacer()
                Image(systemName: "chevron.right").foregroundColor(theme.secondaryTextColor)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// Settings → Avragram.
public func avragramSettingsController(context: AccountContext) -> ViewController {
    let presentationData = context.sharedContext.currentPresentationData.with { $0 }
    return SGSwiftUIController(
        title: "Avragram",
        presentationData: presentationData,
        presentationDataSignal: context.sharedContext.presentationData
    ) {
        AvraHubView(context: context)
    }
}

struct AvraGameScreenFor: View {
    let kind: AvraGameKind
    var body: some View {
        switch kind {
        case .blast: AvraBlastScreen()
        case .snake: AvraSnakeScreen()
        case .drop: AvraDropScreen()
        case .run: AvraRunScreen()
        case .bird: AvraBirdScreen()
        case .g2048: Avra2048Screen()
        }
    }
}
