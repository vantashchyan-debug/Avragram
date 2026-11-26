import Foundation
import SwiftUI
import SGSwiftUI
import TelegramCore

/// Voice changer settings: applied to every voice message you record.
struct AvraVoiceScreen: View {
    @Environment(\.sgTheme) var theme
    @State var preset = AvraVoiceFx.preset
    @State var pitch = Double(AvraVoiceFx.pitch)

    var body: some View {
        Form {
            Section(header: Text("Эффект")) {
                Picker("Эффект", selection: $preset) {
                    ForEach(0..<AvraVoiceFx.titles.count, id: \.self) { Text(AvraVoiceFx.titles[$0]).tag($0) }
                }
                .pickerStyle(.inline)
                .labelsHidden()
                .onChange(of: preset) { AvraVoiceFx.preset = $0 }
            }
            Section(header: Text("Высота голоса: \(Int(pitch) > 0 ? "+" : "")\(Int(pitch))")) {
                Slider(value: $pitch, in: -12...12, step: 1)
                    .onChange(of: pitch) { AvraVoiceFx.pitch = Int($0) }
            }
            Section {
                Text("Эффект накладывается на все голосовые сообщения, которые ты записываешь, прямо во время записи. Собеседник слышит уже изменённый голос.")
                    .font(.footnote).foregroundColor(theme.secondaryTextColor)
            }
        }
        .sgNavigationTitle("Voice changer")
    }
}
