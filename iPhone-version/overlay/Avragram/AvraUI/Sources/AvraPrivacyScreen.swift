import Foundation
import SwiftUI
import SGSwiftUI
import TelegramCore

/// Avragram → Privacy: keep messages that others delete.
struct AvraPrivacyScreen: View {
    @Environment(\.sgTheme) var theme
    @State var keep = AvraKeepDeleted.enabled
    @State var badge = AvraBadge.enabled

    var body: some View {
        Form {
            Section {
                Toggle("Сохранять удалённые сообщения", isOn: $keep)
                    .onChange(of: keep) { AvraKeepDeleted.enabled = $0 }
            }
            Section {
                Toggle("Значок 🅰 у пользователей Avragram", isOn: $badge)
                    .onChange(of: badge) { AvraBadge.enabled = $0 }
                Text("Когда открываешь личку, Avragram незаметно спрашивает, есть ли Avragram у собеседника. Если есть, рядом с его именем в шапке чата появится 🅰. Работает вместе с Android-версией.")
                    .font(.footnote).foregroundColor(theme.secondaryTextColor)
            }
            Section {
                Text("Когда собеседник удаляет сообщение, у тебя оно остаётся. Работает для сообщений, которые пришли после включения. Сохранено удалений: \(AvraKeepDeleted.count).")
                    .font(.footnote).foregroundColor(theme.secondaryTextColor)
            }
        }
        .sgNavigationTitle("Приватность")
    }
}
