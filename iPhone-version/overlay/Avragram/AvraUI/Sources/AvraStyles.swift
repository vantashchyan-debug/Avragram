import Foundation

/// The Avragram themes (generated from Tools/avra_themes.py, the same table as on Android):
/// palette, chat wallpaper gradient, font and bubble roundness.
public struct AvraStyle: Identifiable {
    public let id: String
    public let title: String
    public let dark: Bool
    public let accent: UInt32
    public let background: UInt32
    public let outBubble: UInt32
    public let wallpaper: [UInt32]
    public let font: String
    public let bubbleRadius: Int32

    public static let all: [AvraStyle] = [
        AvraStyle(id: "avra_bglass", title: "B Glass", dark: false, accent: 0x5457EE, background: 0xEEEFFB, outBubble: 0x5457EE, wallpaper: [0xEDEEFC, 0xA8AAF6, 0xF7D2C4, 0xCFF0E4], font: "Manrope", bubbleRadius: 17),
        AvraStyle(id: "avra_bglass_dark", title: "B Glass Тёмная", dark: true, accent: 0x5B5FF5, background: 0x0A0B1C, outBubble: 0x5457EE, wallpaper: [0x090A1E, 0x2E33A6, 0x5B2A2C, 0x0E3A34], font: "Manrope", bubbleRadius: 17),
        AvraStyle(id: "avra_light", title: "Avragram", dark: false, accent: 0x4F5BF0, background: 0xEEF0F6, outBubble: 0x4F5BF0, wallpaper: [0xB9C3FF, 0xFFD3C2, 0xBDEFE0, 0xE3D5FF], font: "Inter", bubbleRadius: 16),
        AvraStyle(id: "avra_dark", title: "Avragram Тёмная", dark: true, accent: 0x7C86FF, background: 0x0B0C12, outBubble: 0x4F5BF0, wallpaper: [0x1B1F4A, 0x3A1E2A, 0x0F3A33, 0x241A45], font: "Inter", bubbleRadius: 16),
        AvraStyle(id: "avra_gothic", title: "Готика", dark: true, accent: 0xB3122E, background: 0x0A0708, outBubble: 0x5A0F1E, wallpaper: [0x0A0708, 0x2A0A12, 0x120A14, 0x1E0E0A], font: "Kurale", bubbleRadius: 6),
        AvraStyle(id: "avra_miami", title: "Неоновый Майами", dark: true, accent: 0xFF3E9A, background: 0x160A2A, outBubble: 0xFF6A3D, wallpaper: [0xFF9F45, 0xFF3E9A, 0x7B2FF7, 0x1FD1C9], font: "Russo One", bubbleRadius: 12),
        AvraStyle(id: "avra_air", title: "Воздух", dark: false, accent: 0x3F8CFF, background: 0xEAF3FF, outBubble: 0x3F8CFF, wallpaper: [0xBFDCFF, 0xFFE0CC, 0xCFF3E4, 0xF6D6F2], font: "Nunito", bubbleRadius: 17),
        AvraStyle(id: "avra_air_dark", title: "Воздух Тёмный", dark: true, accent: 0x6EA8FF, background: 0x08111F, outBubble: 0x2F6FE0, wallpaper: [0x0E2547, 0x3A2430, 0x0E3A3A, 0x2A1E4A], font: "Nunito", bubbleRadius: 17),
        AvraStyle(id: "avra_obsidian", title: "Обсидиан", dark: true, accent: 0xC6F432, background: 0x0B0B0C, outBubble: 0xC6F432, wallpaper: [0x0B0B0C, 0x151A0A, 0x0E0E10, 0x1A1F0C], font: "Onest", bubbleRadius: 4),
        AvraStyle(id: "avra_signal", title: "Сигнал", dark: true, accent: 0xFF6B3D, background: 0x15122B, outBubble: 0xFF6B3D, wallpaper: [0x15122B, 0x2A1440, 0x401C24, 0x0F2A3A], font: "Unbounded", bubbleRadius: 14),
        AvraStyle(id: "avra_neon_sunset", title: "Неоновый закат", dark: true, accent: 0xFF2A85, background: 0x120A26, outBubble: 0xFF2A85, wallpaper: [0xFF7A45, 0xFF2A85, 0x6A2BD8, 0x00C2D1], font: "Exo 2", bubbleRadius: 14),
        AvraStyle(id: "avra_sakura", title: "Сакура", dark: false, accent: 0xE0567F, background: 0xFBF0F3, outBubble: 0xE0567F, wallpaper: [0xFFD1DC, 0xFFE8D6, 0xF5D0F0, 0xFFF0F5], font: "Comfortaa", bubbleRadius: 17),
        AvraStyle(id: "avra_ocean", title: "Океан", dark: true, accent: 0x22C3D6, background: 0x071820, outBubble: 0x0F8FA1, wallpaper: [0x06283A, 0x0B3B4A, 0x052030, 0x0E4A5A], font: "Rubik", bubbleRadius: 12),
        AvraStyle(id: "avra_matrix", title: "Матрица", dark: true, accent: 0x00E676, background: 0x000000, outBubble: 0x00C853, wallpaper: [0x000000, 0x021A0A, 0x000A04, 0x03240E], font: "JetBrains Mono", bubbleRadius: 0),
        AvraStyle(id: "avra_sand", title: "Песок", dark: false, accent: 0xC7772E, background: 0xF5EFE6, outBubble: 0xC7772E, wallpaper: [0xF1DEC4, 0xEAD2B5, 0xF6E9D7, 0xE5C9A8], font: "Lora", bubbleRadius: 10),
        AvraStyle(id: "avra_neon", title: "Неон", dark: true, accent: 0x00F0FF, background: 0x0A0A1A, outBubble: 0x7A3DFF, wallpaper: [0x1A0A2E, 0x0A1A2E, 0x2E0A24, 0x0A2A2E], font: "Oswald", bubbleRadius: 6),
        AvraStyle(id: "avra_forest", title: "Лес", dark: true, accent: 0x4CC38A, background: 0x0D1712, outBubble: 0x2E8B5E, wallpaper: [0x0D1F16, 0x1A2E1A, 0x0A1A14, 0x233320], font: "Golos Text", bubbleRadius: 12),
        AvraStyle(id: "avra_graphite", title: "Графит", dark: false, accent: 0x1F1F24, background: 0xEFEFF1, outBubble: 0x1F1F24, wallpaper: [0xDCDCE0, 0xEFEFF1, 0xD0D0D6, 0xE6E6EA], font: "Inter", bubbleRadius: 8),
        AvraStyle(id: "avra_sunset", title: "Закат", dark: false, accent: 0xF0643C, background: 0xFFF3EC, outBubble: 0xF0643C, wallpaper: [0xFFC9A8, 0xFFB3B3, 0xFFE0A8, 0xF7B8D4], font: "Montserrat", bubbleRadius: 15),
        AvraStyle(id: "avra_aurora", title: "Аврора", dark: true, accent: 0x2DD4BF, background: 0x0B1020, outBubble: 0x2DD4BF, wallpaper: [0x0B1020, 0x123A4A, 0x2A1B4A, 0x0F2E2A], font: "Onest", bubbleRadius: 16),
        AvraStyle(id: "avra_lavender", title: "Лаванда", dark: false, accent: 0x8B5CF6, background: 0xF4F0FF, outBubble: 0x8B5CF6, wallpaper: [0xE9DDFF, 0xFBE3F3, 0xDDE8FF, 0xF3E8FF], font: "Manrope", bubbleRadius: 17),
        AvraStyle(id: "avra_mint", title: "Мята", dark: false, accent: 0x10B981, background: 0xEEFBF5, outBubble: 0x10B981, wallpaper: [0xCFF5E4, 0xE6FFF4, 0xD4F0FF, 0xF0FFE0], font: "Nunito", bubbleRadius: 17),
        AvraStyle(id: "avra_coffee", title: "Кофе", dark: true, accent: 0xD4A373, background: 0x17110D, outBubble: 0x8B5E3C, wallpaper: [0x17110D, 0x2A1C12, 0x1F1610, 0x33241A], font: "Lora", bubbleRadius: 12),
        AvraStyle(id: "avra_paper", title: "Бумага", dark: false, accent: 0x9C4221, background: 0xF6F1E7, outBubble: 0xE9DFC9, wallpaper: [0xF3ECDD, 0xEFE6D3, 0xF7F1E4, 0xEDE3CE], font: "Lora", bubbleRadius: 8),
        AvraStyle(id: "avra_terminal", title: "Терминал", dark: true, accent: 0xFFB000, background: 0x0A0800, outBubble: 0x3D2E00, wallpaper: [0x0A0800, 0x140F00, 0x0A0800, 0x1A1400], font: "JetBrains Mono", bubbleRadius: 2),
        AvraStyle(id: "avra_pixel", title: "8 бит", dark: true, accent: 0xFF4FD8, background: 0x1B1036, outBubble: 0xFF4FD8, wallpaper: [0x1B1036, 0x0F3460, 0x3A0F5C, 0x16213E], font: "Press Start 2P", bubbleRadius: 0),
        AvraStyle(id: "avra_cyber", title: "Киберпанк", dark: true, accent: 0xFCEE0A, background: 0x0B0B12, outBubble: 0xFCEE0A, wallpaper: [0x0B0B12, 0x00262E, 0x2A0B2E, 0x0B1A2E], font: "Oswald", bubbleRadius: 2),
        AvraStyle(id: "avra_minimal", title: "Минимал", dark: false, accent: 0x111111, background: 0xFFFFFF, outBubble: 0x111111, wallpaper: [0xFFFFFF, 0xF7F7F7, 0xFFFFFF, 0xF2F2F2], font: "Inter", bubbleRadius: 6),
        AvraStyle(id: "avra_caramel", title: "Карамель", dark: false, accent: 0xE08A00, background: 0xFFF7EA, outBubble: 0xF5A524, wallpaper: [0xFFE7C2, 0xFFD6A5, 0xFFF1D6, 0xFDE2B8], font: "Nunito", bubbleRadius: 17),
        AvraStyle(id: "avra_midnight", title: "Полночь", dark: true, accent: 0x60A5FA, background: 0x050B1A, outBubble: 0x2563EB, wallpaper: [0x050B1A, 0x0B1E44, 0x120B33, 0x06213A], font: "Montserrat", bubbleRadius: 14),
        AvraStyle(id: "avra_notebook", title: "Тетрадь", dark: false, accent: 0x2563EB, background: 0xFDFBF3, outBubble: 0xDBEAFE, wallpaper: [0xFDFBF3, 0xF5F1E3, 0xFDFBF3, 0xEEF2FF], font: "Caveat", bubbleRadius: 12),
        AvraStyle(id: "avra_royal", title: "Роял", dark: true, accent: 0xD4AF37, background: 0x0E0B07, outBubble: 0xD4AF37, wallpaper: [0x0E0B07, 0x2A1F0C, 0x1A140A, 0x33260E], font: "Playfair Display", bubbleRadius: 10),
        AvraStyle(id: "avra_ice", title: "Лёд", dark: false, accent: 0x0EA5E9, background: 0xEEF8FF, outBubble: 0x0EA5E9, wallpaper: [0xD6F0FF, 0xEAF6FF, 0xCDEBFF, 0xF2FBFF], font: "Comfortaa", bubbleRadius: 17),
        AvraStyle(id: "avra_cherry", title: "Вишня", dark: true, accent: 0xF43F5E, background: 0x14070B, outBubble: 0xE11D48, wallpaper: [0x14070B, 0x3A0A18, 0x1F0C24, 0x2E0A10], font: "Raleway", bubbleRadius: 14),
        AvraStyle(id: "avra_neuroslop", title: "Нейрослоп", dark: false, accent: 0xFF00E5, background: 0xC6FF00, outBubble: 0x7C4DFF, wallpaper: [0xFF0080, 0x00FFEA, 0xFFE600, 0x8A00FF], font: "Caveat", bubbleRadius: 17)
    ]
}

/// Fonts shipped with Avragram (family names inside res avra_*.ttf).
public enum AvraFonts {
    public static let families = ["Caveat", "Comfortaa", "Exo 2", "Golos Text", "Inter", "JetBrains Mono", "Kurale", "Lora", "Manrope",
                                  "Montserrat", "Nunito", "Onest", "Oswald", "Playfair Display", "Press Start 2P", "Raleway", "Rubik",
                                  "Ruslan Display", "Russo One", "Unbounded"]
}
