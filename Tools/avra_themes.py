#!/usr/bin/env python3
"""
Avragram theme generator.

Uses Telegram's Day (light) / Night (dark) .attheme files as the base so every screen keeps its
Telegram look, then sets structural colors from a style palette, re-hues Telegram's blue accents
to the style accent, tints neutral dark greys toward the style background (dark styles) and gives
each style a 4-color gradient chat wallpaper (looks best under the glass panels).

Writes TMessagesProj/src/main/assets/avra_*.attheme and com/avragramm/AvraStyles.java.
Run from the repo root:  python3 Tools/avra_themes.py
"""
import colorsys
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "TMessagesProj/src/main/assets")
JAVA_OUT = os.path.join(ROOT, "TMessagesProj/src/main/java/com/avragramm/AvraStyles.java")

# id, theme name (key, English), Russian title, dark, accent, bg, surface, header, text, muted, inBubble, outBubble, outText, onAccent, wallpaper[4], glass
STYLES = [
    ("avra_bglass", "B Glass", "B Glass", False, "#5457EE", "#EEEFFB", "#FFFFFF", "#F5F5FD", "#14151F", "#6B6E84", "#FFFFFF", "#5457EE", "#FFFFFF", "#FFFFFF", ["#EDEEFC", "#A8AAF6", "#F7D2C4", "#CFF0E4"], True),
    ("avra_bglass_dark", "B Glass Dark", "B Glass Тёмная", True, "#5B5FF5", "#0A0B1C", "#15172E", "#111326", "#F1F2FF", "#9497B5", "#1B1D38", "#5457EE", "#FFFFFF", "#FFFFFF", ["#090A1E", "#2E33A6", "#5B2A2C", "#0E3A34"], True),
    ("avra_light", "Avragram", "Avragram", False, "#4F5BF0", "#EEF0F6", "#FFFFFF", "#FFFFFF", "#11131A", "#6B7085", "#FFFFFF", "#4F5BF0", "#FFFFFF", "#FFFFFF", ["#B9C3FF", "#FFD3C2", "#BDEFE0", "#E3D5FF"], False),
    ("avra_dark", "Avragram Dark", "Avragram Тёмная", True, "#7C86FF", "#0B0C12", "#14151D", "#14151D", "#F2F3F8", "#8D92A8", "#1E2030", "#4F5BF0", "#FFFFFF", "#FFFFFF", ["#1B1F4A", "#3A1E2A", "#0F3A33", "#241A45"], False),
    ("avra_gothic", "Gothic", "Готика", True, "#B3122E", "#0A0708", "#140E10", "#120B0D", "#E8DCCB", "#8C7B74", "#1C1316", "#5A0F1E", "#F3E6D3", "#F3E6D3", ["#0A0708", "#2A0A12", "#120A14", "#1E0E0A"], False),
    ("avra_miami", "Miami Nights", "Неоновый Майами", True, "#FF3E9A", "#160A2A", "#22103D", "#1E0E36", "#FFF3E6", "#C9A8D9", "#2B1650", "#FF6A3D", "#FFFFFF", "#FFFFFF", ["#FF9F45", "#FF3E9A", "#7B2FF7", "#1FD1C9"], True),
    ("avra_air", "Air", "Воздух", False, "#3F8CFF", "#EAF3FF", "#FFFFFF", "#FFFFFF", "#0F1B2D", "#62738C", "#FFFFFF", "#3F8CFF", "#FFFFFF", "#FFFFFF", ["#BFDCFF", "#FFE0CC", "#CFF3E4", "#F6D6F2"], True),
    ("avra_air_dark", "Air Dark", "Воздух Тёмный", True, "#6EA8FF", "#08111F", "#0F1A2B", "#0F1A2B", "#EEF4FF", "#8A9AB5", "#18263B", "#2F6FE0", "#FFFFFF", "#FFFFFF", ["#0E2547", "#3A2430", "#0E3A3A", "#2A1E4A"], True),
    ("avra_obsidian", "Obsidian", "Обсидиан", True, "#C6F432", "#0B0B0C", "#141416", "#141416", "#EDEDED", "#8A8A8F", "#1C1C1F", "#C6F432", "#0B0B0C", "#0B0B0C", ["#0B0B0C", "#151A0A", "#0E0E10", "#1A1F0C"], False),
    ("avra_signal", "Signal", "Сигнал", True, "#FF6B3D", "#15122B", "#1D1938", "#1D1938", "#F4F2FF", "#A7A3C7", "#2A2550", "#FF6B3D", "#15122B", "#15122B", ["#15122B", "#2A1440", "#401C24", "#0F2A3A"], False),
    ("avra_neon_sunset", "Neon Sunset", "Неоновый закат", True, "#FF2A85", "#120A26", "#1C1036", "#1C1036", "#FFF1F8", "#B9A6D6", "#2A1A4A", "#FF2A85", "#FFFFFF", "#FFFFFF", ["#FF7A45", "#FF2A85", "#6A2BD8", "#00C2D1"], False),
    ("avra_sakura", "Sakura", "Сакура", False, "#E0567F", "#FBF0F3", "#FFFFFF", "#FFFFFF", "#2A1A20", "#8C6F79", "#FFFFFF", "#E0567F", "#FFFFFF", "#FFFFFF", ["#FFD1DC", "#FFE8D6", "#F5D0F0", "#FFF0F5"], True),
    ("avra_ocean", "Ocean", "Океан", True, "#22C3D6", "#071820", "#0D2430", "#0D2430", "#E6F6FA", "#86A9B5", "#133241", "#0F8FA1", "#FFFFFF", "#04161D", ["#06283A", "#0B3B4A", "#052030", "#0E4A5A"], False),
    ("avra_matrix", "Matrix", "Матрица", True, "#00E676", "#000000", "#0A0F0B", "#0A0F0B", "#C8FACC", "#5E8F66", "#0F1A12", "#00C853", "#001A0B", "#001A0B", ["#000000", "#021A0A", "#000A04", "#03240E"], False),
    ("avra_sand", "Sand", "Песок", False, "#C7772E", "#F5EFE6", "#FFFBF5", "#FFFBF5", "#2B2117", "#8A7A68", "#FFFBF5", "#C7772E", "#FFFFFF", "#FFFFFF", ["#F1DEC4", "#EAD2B5", "#F6E9D7", "#E5C9A8"], False),
    ("avra_neon", "Neon", "Неон", True, "#00F0FF", "#0A0A1A", "#121229", "#121229", "#F5F0FF", "#9C95C2", "#1C1C3D", "#7A3DFF", "#FFFFFF", "#0A0A1A", ["#1A0A2E", "#0A1A2E", "#2E0A24", "#0A2A2E"], False),
    ("avra_forest", "Forest", "Лес", True, "#4CC38A", "#0D1712", "#13211A", "#13211A", "#E6F2EA", "#8FA89A", "#1B2E24", "#2E8B5E", "#FFFFFF", "#08130D", ["#0D1F16", "#1A2E1A", "#0A1A14", "#233320"], False),
    ("avra_graphite", "Graphite", "Графит", False, "#1F1F24", "#EFEFF1", "#FFFFFF", "#FFFFFF", "#111114", "#6E6E76", "#FFFFFF", "#1F1F24", "#FFFFFF", "#FFFFFF", ["#DCDCE0", "#EFEFF1", "#D0D0D6", "#E6E6EA"], False),
    ("avra_sunset", "Sunset", "Закат", False, "#F0643C", "#FFF3EC", "#FFFFFF", "#FFFFFF", "#24150F", "#8F7266", "#FFFFFF", "#F0643C", "#FFFFFF", "#FFFFFF", ["#FFC9A8", "#FFB3B3", "#FFE0A8", "#F7B8D4"], False),
    ("avra_aurora", "Aurora", "Аврора", True, "#2DD4BF", "#0B1020", "#121A2E", "#0F1628", "#E8F7FF", "#8BA3B8", "#18223A", "#2DD4BF", "#062421", "#062421", ["#0B1020", "#123A4A", "#2A1B4A", "#0F2E2A"], True),
    ("avra_lavender", "Lavender", "Лаванда", False, "#8B5CF6", "#F4F0FF", "#FFFFFF", "#FFFFFF", "#1E1530", "#6E6488", "#FFFFFF", "#8B5CF6", "#FFFFFF", "#FFFFFF", ["#E9DDFF", "#FBE3F3", "#DDE8FF", "#F3E8FF"], True),
    ("avra_mint", "Mint", "Мята", False, "#10B981", "#EEFBF5", "#FFFFFF", "#FFFFFF", "#0F241B", "#5F7A6E", "#FFFFFF", "#10B981", "#FFFFFF", "#FFFFFF", ["#CFF5E4", "#E6FFF4", "#D4F0FF", "#F0FFE0"], True),
    ("avra_coffee", "Coffee", "Кофе", True, "#D4A373", "#17110D", "#211813", "#1D1511", "#F3E9DF", "#A8957F", "#2A1F18", "#8B5E3C", "#FFF7EE", "#17110D", ["#17110D", "#2A1C12", "#1F1610", "#33241A"], False),
    ("avra_paper", "Paper", "Бумага", False, "#9C4221", "#F6F1E7", "#FFFCF5", "#FFFCF5", "#2B2118", "#7D6E5C", "#FFFCF5", "#E9DFC9", "#2B2118", "#FFFFFF", ["#F3ECDD", "#EFE6D3", "#F7F1E4", "#EDE3CE"], False),
    ("avra_terminal", "Terminal", "Терминал", True, "#FFB000", "#0A0800", "#120F05", "#120F05", "#FFD27A", "#9C8040", "#17130A", "#3D2E00", "#FFD27A", "#0A0800", ["#0A0800", "#140F00", "#0A0800", "#1A1400"], False),
    ("avra_pixel", "8-bit", "8 бит", True, "#FF4FD8", "#1B1036", "#241547", "#241547", "#FFFFFF", "#B5A8E0", "#2E1D5C", "#FF4FD8", "#1B1036", "#1B1036", ["#1B1036", "#0F3460", "#3A0F5C", "#16213E"], False),
    ("avra_cyber", "Cyberpunk", "Киберпанк", True, "#FCEE0A", "#0B0B12", "#14141F", "#14141F", "#EAFBFF", "#7FA7B5", "#1A1A2A", "#FCEE0A", "#0B0B12", "#0B0B12", ["#0B0B12", "#00262E", "#2A0B2E", "#0B1A2E"], False),
    ("avra_minimal", "Minimal", "Минимал", False, "#111111", "#FFFFFF", "#FFFFFF", "#FFFFFF", "#111111", "#777777", "#F2F2F2", "#111111", "#FFFFFF", "#FFFFFF", ["#FFFFFF", "#F7F7F7", "#FFFFFF", "#F2F2F2"], False),
    ("avra_caramel", "Caramel", "Карамель", False, "#E08A00", "#FFF7EA", "#FFFFFF", "#FFFFFF", "#2A1A05", "#8C7350", "#FFFFFF", "#F5A524", "#2A1A05", "#2A1A05", ["#FFE7C2", "#FFD6A5", "#FFF1D6", "#FDE2B8"], True),
    ("avra_midnight", "Midnight", "Полночь", True, "#60A5FA", "#050B1A", "#0C1428", "#0A1124", "#E6EEFF", "#8597BC", "#131D36", "#2563EB", "#FFFFFF", "#050B1A", ["#050B1A", "#0B1E44", "#120B33", "#06213A"], False),
    ("avra_notebook", "Notebook", "Тетрадь", False, "#2563EB", "#FDFBF3", "#FFFFFF", "#FFFFFF", "#1F2937", "#6B7280", "#FFFFFF", "#DBEAFE", "#1E3A8A", "#FFFFFF", ["#FDFBF3", "#F5F1E3", "#FDFBF3", "#EEF2FF"], False),
    ("avra_royal", "Royal", "Роял", True, "#D4AF37", "#0E0B07", "#18130C", "#15110A", "#F5ECD7", "#A8977A", "#221A10", "#D4AF37", "#0E0B07", "#0E0B07", ["#0E0B07", "#2A1F0C", "#1A140A", "#33260E"], False),
    ("avra_ice", "Ice", "Лёд", False, "#0EA5E9", "#EEF8FF", "#FFFFFF", "#FFFFFF", "#0B2233", "#5C7A8F", "#FFFFFF", "#0EA5E9", "#FFFFFF", "#FFFFFF", ["#D6F0FF", "#EAF6FF", "#CDEBFF", "#F2FBFF"], True),
    ("avra_cherry", "Cherry", "Вишня", True, "#F43F5E", "#14070B", "#1F0C12", "#1B0A10", "#FFE4EA", "#B88A96", "#2A1119", "#E11D48", "#FFFFFF", "#FFFFFF", ["#14070B", "#3A0A18", "#1F0C24", "#2E0A10"], False),
]

# Per-style geometry: font, button (0 telegram, 1 pill, 2 rounded, 3 sharp, 4 tile), bubble radius 0..17,
# avatar corners 0..28, divider (0 hidden, 1 line, 2 cards), card radius 0..28, centred title, square FAB,
# new FAB style, new switch style, new slider style, floating navigation bar
DESIGN = {
    "avra_bglass":      ("manrope",        1, 17, 20, 2, 24, False, False, True,  True,  True,  True),
    "avra_bglass_dark": ("manrope",        1, 17, 20, 2, 24, False, False, True,  True,  True,  True),
    "avra_light":       ("inter",          2, 16, 22, 2, 20, False, False, False, True,  True,  False),
    "avra_dark":        ("inter",          2, 16, 22, 2, 20, False, False, False, True,  True,  False),
    "avra_gothic":      ("gothic",         3,  6,  8, 1,  4, True,  True,  False, False, False, False),
    "avra_miami":       ("russo",          4, 12, 14, 2, 16, False, True,  True,  True,  True,  True),
    "avra_air":         ("nunito",         1, 17, 28, 2, 26, True,  False, True,  True,  True,  True),
    "avra_air_dark":    ("nunito",         1, 17, 28, 2, 26, True,  False, True,  True,  True,  True),
    "avra_obsidian":    ("onest_mono",     3,  4,  4, 1,  4, False, True,  False, False, False, False),
    "avra_signal":      ("unbounded_inter",4, 14, 20, 2, 24, False, True,  True,  True,  True,  False),
    "avra_neon_sunset": ("exo2",           1, 14, 28, 0, 20, True,  False, True,  True,  True,  True),
    "avra_sakura":      ("comfortaa",      1, 17, 28, 2, 26, True,  False, True,  True,  True,  True),
    "avra_ocean":       ("rubik",          2, 12, 20, 2, 18, False, False, False, True,  True,  False),
    "avra_matrix":      ("jetbrains",      3,  0,  0, 0,  0, True,  True,  False, False, False, False),
    "avra_sand":        ("lora",           2, 10, 16, 1, 12, False, False, False, True,  True,  False),
    "avra_neon":        ("oswald",         3,  6,  6, 0,  6, False, True,  False, False, True,  False),
    "avra_forest":      ("golos",          2, 12, 18, 2, 18, False, False, False, True,  True,  False),
    "avra_graphite":    ("inter",          3,  8, 12, 1,  8, False, True,  False, False, False, False),
    "avra_sunset":      ("montserrat",     1, 15, 24, 2, 22, False, False, True,  True,  True,  True),
    "avra_aurora":      ("onest",          1, 16, 24, 2, 22, False, False, True,  True,  True,  True),
    "avra_lavender":    ("manrope",        1, 17, 28, 2, 28, True,  False, True,  True,  True,  True),
    "avra_mint":        ("nunito",         1, 17, 28, 2, 24, False, False, True,  True,  True,  True),
    "avra_coffee":      ("lora",           2, 12, 18, 1, 14, False, False, False, True,  True,  False),
    "avra_paper":       ("lora",           2,  8, 12, 1,  6, True,  False, False, False, False, False),
    "avra_terminal":    ("jetbrains",      3,  2,  0, 0,  0, False, True,  False, False, False, False),
    "avra_pixel":       ("pressstart",     3,  0,  0, 0,  0, True,  True,  False, False, False, False),
    "avra_cyber":       ("oswald",         3,  2,  4, 0,  2, False, True,  False, False, True,  False),
    "avra_minimal":     ("inter",          3,  6, 28, 0,  0, True,  True,  False, True,  True,  False),
    "avra_caramel":     ("nunito",         1, 17, 24, 2, 26, False, False, True,  True,  True,  True),
    "avra_midnight":    ("montserrat",     2, 14, 22, 2, 18, False, False, False, True,  True,  True),
    "avra_notebook":    ("caveat",         2, 12, 28, 1, 10, False, False, False, True,  True,  False),
    "avra_royal":       ("playfair",       2, 10, 28, 2, 14, True,  False, False, True,  True,  False),
    "avra_ice":         ("comfortaa",      1, 17, 28, 2, 28, False, False, True,  True,  True,  True),
    "avra_cherry":      ("raleway",        2, 14, 20, 2, 18, False, False, False, True,  True,  False),
}


# Per-style animations: screen transition (0 Telegram, 1 Android, 2 Spring, 3 Slide, 4 Fade, 5 Zoom,
# 6 Slide up, 7 Flip, 8 Glitch, 9 Mist, 10 None), new message (0 Telegram, 1 Fade, 2 Pop, 3 Slide,
# 4 Swing, 5 Rise), speed in percent of the normal duration (more = slower)
ANIM = {
    "avra_bglass": (2, 2, 100), "avra_bglass_dark": (2, 2, 100),
    "avra_light": (2, 0, 100), "avra_dark": (2, 0, 100),
    "avra_gothic": (9, 1, 140), "avra_miami": (5, 3, 90),
    "avra_air": (5, 5, 110), "avra_air_dark": (5, 5, 110),
    "avra_obsidian": (3, 0, 85), "avra_signal": (6, 2, 100), "avra_neon_sunset": (5, 3, 100),
    "avra_sakura": (4, 4, 120), "avra_ocean": (6, 5, 115), "avra_matrix": (8, 1, 100),
    "avra_sand": (4, 1, 120), "avra_neon": (8, 2, 90), "avra_forest": (3, 5, 110),
    "avra_graphite": (1, 0, 100), "avra_sunset": (5, 2, 100), "avra_aurora": (9, 1, 110),
    "avra_lavender": (4, 4, 115), "avra_mint": (2, 2, 100), "avra_coffee": (3, 1, 120),
    "avra_paper": (7, 1, 110), "avra_terminal": (3, 0, 60), "avra_pixel": (7, 2, 80),
    "avra_cyber": (8, 3, 90), "avra_minimal": (0, 0, 100), "avra_caramel": (5, 2, 110),
    "avra_midnight": (6, 1, 110), "avra_notebook": (7, 4, 110), "avra_royal": (9, 1, 120),
    "avra_ice": (4, 5, 110), "avra_cherry": (5, 4, 100),
}


def parse_hex(h):
    return 0xFF000000 | int(h.lstrip("#"), 16)


def to_signed(v):
    v &= 0xFFFFFFFF
    return v - (1 << 32) if v & 0x80000000 else v


def split(v):
    v &= 0xFFFFFFFF
    return (v >> 24) & 0xFF, (v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF


def join(a, r, g, b):
    return (a << 24) | (r << 16) | (g << 8) | b


def with_alpha(c, a):
    _, r, g, b = split(c)
    return join(a, r, g, b)


def mix(c1, c2, t):
    a1, r1, g1, b1 = split(c1)
    a2, r2, g2, b2 = split(c2)
    f = lambda x, y: int(round(x + (y - x) * t))
    return join(f(a1, a2), f(r1, r2), f(g1, g2), f(b1, b2))


def hls(c):
    _, r, g, b = split(c)
    return colorsys.rgb_to_hls(r / 255, g / 255, b / 255)


def from_hls(a, h, l, s):
    r, g, b = colorsys.hls_to_rgb(h, max(0, min(1, l)), max(0, min(1, s)))
    return join(a, int(round(r * 255)), int(round(g * 255)), int(round(b * 255)))


def load(name):
    out = {}
    with open(os.path.join(ASSETS, name), encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if "=" not in line or line.startswith("WPS"):
                continue
            k, v = line.split("=", 1)
            try:
                out[k] = int(v)
            except ValueError:
                pass
    return out


def build(style, base):
    (_, _, _, dark, accent, bg, surface, header, text, muted, inb, outb, outt, onacc, wp, glass) = style
    A, BG, SF, HD, TX, MU, IN, OUT, OT, ON = map(parse_hex, (accent, bg, surface, header, text, muted, inb, outb, outt, onacc))
    WP = [parse_hex(c) for c in wp]
    ah, al, as_ = hls(A)
    bh, _, bs = hls(BG)
    explicit = {
        "windowBackgroundWhite": SF, "windowBackgroundGray": BG,
        "windowBackgroundWhiteBlackText": TX, "windowBackgroundWhiteGrayText": MU,
        "windowBackgroundWhiteGrayText2": MU, "windowBackgroundWhiteGrayText3": MU,
        "windowBackgroundWhiteHintText": MU, "windowBackgroundWhiteValueText": A,
        "windowBackgroundWhiteBlueHeader": A, "windowBackgroundWhiteBlueText4": A,
        "windowBackgroundWhiteLinkText": A,
        "actionBarDefault": HD, "actionBarDefaultIcon": TX, "actionBarDefaultTitle": TX,
        "actionBarDefaultSubtitle": MU, "actionBarDefaultSearch": TX,
        "actionBarDefaultSearchPlaceholder": with_alpha(MU, 0x99),
        "actionBarDefaultSelector": with_alpha(A, 0x26), "actionBarWhiteSelector": with_alpha(A, 0x26),
        "actionBarDefaultSubmenuBackground": SF, "actionBarDefaultSubmenuItem": TX,
        "actionBarDefaultSubmenuItemIcon": MU,
        "avatar_backgroundActionBarBlue": HD, "avatar_actionBarIconBlue": TX,
        "chats_name": TX, "chats_message": MU, "chats_date": MU,
        "chats_menuBackground": SF, "chats_menuItemText": TX, "chats_menuItemIcon": MU,
        "chats_menuName": TX, "chats_menuPhone": MU, "chats_menuPhoneCats": MU,
        "chats_menuTopBackgroundCats": HD,
        "chats_unreadCounter": A, "chats_unreadCounterText": ON,
        "chats_actionBackground": A, "chats_actionPressedBackground": mix(A, 0xFF000000, 0.15),
        "chats_actionIcon": ON, "chats_pinnedOverlay": with_alpha(A, 0x0C),
        "chat_wallpaper": WP[0], "chat_wallpaper_gradient_to": WP[1],
        "key_chat_wallpaper_gradient_to2": WP[2], "key_chat_wallpaper_gradient_to3": WP[3],
        "chat_inBubble": IN, "chat_inBubbleSelected": mix(IN, A, 0.12),
        "chat_messageTextIn": TX, "chat_inTimeText": MU,
        "chat_outBubble": OUT, "chat_outBubbleSelected": mix(OUT, 0xFF000000, 0.12),
        "chat_messageTextOut": OT, "chat_messageLinkOut": OT,
        "chat_outTimeText": with_alpha(OT, 0xB3), "chat_outSentCheck": with_alpha(OT, 0xCC),
        "chat_outSentCheckRead": OT, "chat_outSentClock": with_alpha(OT, 0xB3),
        "chat_outViews": with_alpha(OT, 0xB3),
        "chat_messagePanelBackground": SF, "chat_messagePanelText": TX,
        "chat_messagePanelHint": MU, "chat_messagePanelIcons": MU, "chat_messagePanelSend": A,
        "chat_messagePanelVoiceBackground": A, "chat_recordedVoiceBackground": A,
        "chat_goDownButton": SF,
        "dialogBackground": SF, "dialogBackgroundGray": BG, "dialogTextBlack": TX,
        "dialogTextGray": MU, "dialogTextGray2": MU, "dialogTextGray3": MU, "dialogButton": A,
        "switchTrackChecked": A, "switch2TrackChecked": A, "checkboxSquareBackground": A,
        "checkbox": A, "radioBackgroundChecked": A,
        "featuredStickers_addButton": A, "featuredStickers_buttonText": ON,
        "featuredStickers_addButtonPressed": mix(A, 0xFF000000, 0.15),
        "windowBackgroundCheckText": ON,
    }
    out = {}
    for key, val in base.items():
        if key in explicit:
            continue
        a = split(val)[0]
        h, l, s = hls(val)
        if key.startswith("chat_out") and l > 0.72 and s < 0.5:
            out[key] = with_alpha(OT, a)
        elif 0.50 <= h <= 0.66 and s > 0.28:
            ns = min(1.0, s * (as_ / 0.75)) if as_ > 0.05 else 0.0
            out[key] = from_hls(a, ah, l + (al - 0.55) * 0.6, ns)
        elif dark and s <= 0.28 and l < 0.40:
            out[key] = from_hls(a, bh, l, min(0.5, bs * 0.9))
        else:
            out[key] = val & 0xFFFFFFFF
    out.update(explicit)
    if glass:  # translucent incoming bubbles over the gradient wallpaper
        out["chat_inBubble"] = with_alpha(IN, 0xC2 if dark else 0xD9)
    return out


def main():
    day, night = load("day.attheme"), load("night.attheme")
    entries = []
    for st in STYLES:
        colors = build(st, night if st[3] else day)
        with open(os.path.join(ASSETS, st[0] + ".attheme"), "w", encoding="utf-8") as f:
            for k in sorted(colors):
                f.write("%s=%d\n" % (k, to_signed(colors[k])))
        entries.append(st)
        print("wrote", st[0], len(colors), "keys")
    for d, idx in (("values", 1), ("values-ru", 2)):
        with open(os.path.join(ROOT, "TMessagesProj/src/main/res", d, "avra_styles.xml"), "w", encoding="utf-8") as f:
            f.write('<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by Tools/avra_themes.py -->\n<resources>\n')
            for st in STYLES:
                f.write('    <string name="AvraStyle_%s">%s</string>\n' % (st[0][5:], st[idx]))
            f.write("</resources>\n")
    j = lambda xs: ", ".join(xs)
    b = lambda v: "true" if v else "false"
    D = [DESIGN[e[0]] for e in entries]
    with open(JAVA_OUT, "w", encoding="utf-8") as f:
        f.write("package com.avragramm;\n\nimport org.telegram.messenger.R;\n\n/** Generated by Tools/avra_themes.py — do not edit by hand. */\n")
        f.write("public final class AvraStyles {\n    private AvraStyles() {}\n\n")
        f.write("    public static final String[] ASSETS = {%s};\n" % j('"%s.attheme"' % e[0] for e in entries))
        f.write("    public static final String[] NAMES = {%s};\n" % j('"%s"' % e[1] for e in entries))
        f.write("    public static final int[] TITLES = {%s};\n" % j("R.string.AvraStyle_%s" % e[0][5:] for e in entries))
        f.write("    public static final boolean[] DARK = {%s};\n" % j(b(e[3]) for e in entries))
        f.write("    public static final int[] PREVIEW_BG = {%s};\n" % j("0x%08X" % parse_hex(e[5]) for e in entries))
        f.write("    public static final int[] PREVIEW_IN = {%s};\n" % j("0x%08X" % parse_hex(e[10]) for e in entries))
        f.write("    public static final int[] PREVIEW_OUT = {%s};\n" % j("0x%08X" % parse_hex(e[11]) for e in entries))
        f.write("    public static final int[] ACCENT = {%s};\n\n" % j("0x%08X" % parse_hex(e[4]) for e in entries))
        f.write("    public static final String[] FONT = {%s};\n" % j('"%s"' % d[0] for d in D))
        for name, k in (("BUTTON", 1), ("BUBBLE", 2), ("AVATAR", 3), ("DIVIDER", 4), ("SECTION", 5)):
            f.write("    public static final int[] %s = {%s};\n" % (name, j(str(d[k]) for d in D)))
        for name, k in (("CENTER", 6), ("SQUARE_FAB", 7)):
            f.write("    public static final boolean[] %s = {%s};\n" % (name, j(b(d[k]) for d in D)))
        N = [ANIM[e[0]] for e in entries]
        for name, k in (("TRANSITION", 0), ("MSG_ANIM", 1), ("ANIM_SPEED", 2)):
            f.write("    public static final int[] %s = {%s};\n" % (name, j(str(n[k]) for n in N)))
        f.write('\n    public static boolean isAvra(String assetName) {\n        return assetName != null && assetName.startsWith("avra_");\n    }\n')
        f.write("\n    public static int indexOfAsset(String assetName) {\n        if (assetName != null) for (int i = 0; i < ASSETS.length; i++) if (ASSETS[i].equals(assetName)) return i;\n        return -1;\n    }\n")
        f.write("\n    public static int indexOfName(String name) {\n        if (name != null) for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equals(name)) return i;\n        return -1;\n    }\n")
        f.write("\n    public static boolean isDarkAsset(String assetName) {\n        int i = indexOfAsset(assetName);\n        return i >= 0 && DARK[i];\n    }\n}\n")


if __name__ == "__main__":
    main()
