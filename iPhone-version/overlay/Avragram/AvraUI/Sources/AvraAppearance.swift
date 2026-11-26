import Foundation
import UIKit
import SwiftUI
import SGSwiftUI
import SwiftSignalKit
import TelegramCore
import TelegramUIPreferences
import AccountContext

/// Avragram → Appearance: the 35 Avragram themes (palette + wallpaper + bubbles + font), the 20 fonts
/// and bubble roundness. Colors go through Telegram's own accent color / wallpaper settings, so they
/// survive restarts and sync like a normal Telegram theme; the font needs a restart.
enum AvraAppearance {
    static let fontKey = "avra.font.family"
    static let styleKey = "avra.style.id"

    static var currentFont: String? {
        get { UserDefaults.standard.string(forKey: fontKey) }
        set {
            if let v = newValue, !v.isEmpty { UserDefaults.standard.set(v, forKey: fontKey) } else { UserDefaults.standard.removeObject(forKey: fontKey) }
        }
    }

    static func apply(_ style: AvraStyle, context: AccountContext, font: Bool) {
        UserDefaults.standard.set(style.id, forKey: styleKey)
        if font { currentFont = style.font }
        let index = Int32(1000 + (AvraStyle.all.firstIndex { $0.id == style.id } ?? 0))
        let wallpaper = TelegramWallpaper.gradient(TelegramWallpaper.Gradient(id: nil, colors: style.wallpaper, settings: WallpaperSettings()))
        let accent = PresentationThemeAccentColor(index: index, baseColor: .custom, accentColor: style.accent, bubbleColors: [style.outBubble], wallpaper: wallpaper)
        let reference = PresentationThemeReference.builtin(style.dark ? .night : .day)
        let _ = updatePresentationThemeSettingsInteractively(accountManager: context.sharedContext.accountManager, { current in
            var updated = current
            updated.theme = reference
            updated.automaticThemeSwitchSetting.force = false
            updated.automaticThemeSwitchSetting.trigger = .explicitNone
            var accents = updated.themeSpecificAccentColors
            accents[reference.index] = accent
            updated.themeSpecificAccentColors = accents
            var wallpapers = updated.themeSpecificChatWallpapers
            wallpapers[coloredThemeIndex(reference: reference, accentColor: accent)] = wallpaper
            updated.themeSpecificChatWallpapers = wallpapers
            let r = max(0, min(17, style.bubbleRadius))
            updated.chatBubbleSettings = PresentationChatBubbleSettings(mainRadius: r, auxiliaryRadius: max(2, r / 2), mergeBubbleCorners: true)
            return updated
        }).start()
    }

    static func setBubbleRadius(_ r: Int32, context: AccountContext) {
        let _ = updatePresentationThemeSettingsInteractively(accountManager: context.sharedContext.accountManager, { current in
            var updated = current
            updated.chatBubbleSettings = PresentationChatBubbleSettings(mainRadius: r, auxiliaryRadius: max(2, r / 2), mergeBubbleCorners: true)
            return updated
        }).start()
    }

    static func color(_ v: UInt32) -> Color {
        Color(red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255, blue: Double(v & 0xFF) / 255)
    }
}

struct AvraAppearanceScreen: View {
    let context: AccountContext
    @Environment(\.sgTheme) private var theme
    @State private var selected: String? = UserDefaults.standard.string(forKey: AvraAppearance.styleKey)
    @State private var font: String? = AvraAppearance.currentFont
    @State private var radius: Double = 16
    @State private var restartNeeded = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if restartNeeded {
                    Text("Шрифт сменится после перезапуска Avragram: смахни приложение из списка открытых и открой снова.")
                        .font(.footnote).padding(12).frame(maxWidth: .infinity, alignment: .leading)
                        .background(RoundedRectangle(cornerRadius: 12).fill(theme.accentColor.opacity(0.15)))
                }
                Text("ГОТОВЫЕ ТЕМЫ").font(.caption.bold()).foregroundColor(theme.secondaryTextColor)
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                    ForEach(AvraStyle.all) { s in
                        Button(action: {
                            selected = s.id
                            AvraAppearance.apply(s, context: context, font: true)
                            if font != s.font { restartNeeded = true }
                            font = s.font
                            radius = Double(s.bubbleRadius)
                            AvraHaptics.tap()
                        }) { card(s) }.buttonStyle(.plain)
                    }
                }
                Text("ШРИФТ").font(.caption.bold()).foregroundColor(theme.secondaryTextColor)
                VStack(spacing: 0) {
                    fontRow(nil, "Системный (San Francisco)")
                    ForEach(AvraFonts.families, id: \.self) { f in fontRow(f, f) }
                }
                .background(theme.secondaryBackgroundColor).cornerRadius(14)
                Text("СКРУГЛЕНИЕ ПУЗЫРЕЙ: \(Int(radius))").font(.caption.bold()).foregroundColor(theme.secondaryTextColor)
                Slider(value: $radius, in: 0...17, step: 1, onEditingChanged: { editing in
                    if !editing { AvraAppearance.setBubbleRadius(Int32(radius), context: context) }
                })
                Text("Тема меняет цвета, обои чата, скругление пузырей и шрифт. Цвета применяются сразу через настройки тем Telegram.")
                    .font(.footnote).foregroundColor(theme.secondaryTextColor)
            }
            .padding(16)
        }
        .background(theme.backgroundColor.ignoresSafeArea())
        .sgNavigationTitle("Оформление")
    }

    private func card(_ s: AvraStyle) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            ZStack(alignment: .bottomTrailing) {
                LinearGradient(colors: s.wallpaper.map { AvraAppearance.color($0) }, startPoint: .topLeading, endPoint: .bottomTrailing)
                    .frame(height: 70)
                RoundedRectangle(cornerRadius: CGFloat(s.bubbleRadius))
                    .fill(AvraAppearance.color(s.outBubble))
                    .frame(width: 70, height: 24)
                    .padding(8)
            }
            .cornerRadius(10)
            Text(s.title).font(.subheadline.bold()).foregroundColor(theme.primaryTextColor).lineLimit(1)
            Text(s.font).font(.caption).foregroundColor(theme.secondaryTextColor)
        }
        .padding(8)
        .background(RoundedRectangle(cornerRadius: 14).fill(theme.secondaryBackgroundColor))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(selected == s.id ? theme.accentColor : Color.clear, lineWidth: 2))
    }

    private func fontRow(_ family: String?, _ title: String) -> some View {
        Button(action: {
            AvraAppearance.currentFont = family
            if font != family { restartNeeded = true }
            font = family
            AvraHaptics.tap()
        }) {
            HStack {
                Text(title).font(family.map { .custom($0, size: 17) } ?? .body).foregroundColor(theme.primaryTextColor)
                Spacer()
                if font == family { Image(systemName: "checkmark").foregroundColor(theme.accentColor) }
            }
            .padding(.horizontal, 14).padding(.vertical, 11)
            .contentShape(Rectangle())
        }.buttonStyle(.plain)
    }
}
