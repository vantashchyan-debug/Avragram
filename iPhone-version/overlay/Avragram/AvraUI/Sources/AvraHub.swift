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
                    row("🚢", "Морской бой", "Против бота: Юнга, Боцман, Адмирал") {
                        navigation?.push(title: "Морской бой") { AvraSeaScreen(level: 1) }
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
