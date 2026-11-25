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

# id, name, dark, accent, bg, surface, header, text, muted, inBubble, outBubble, outText, onAccent, wallpaper[4]
STYLES = [
    ("avra_bglass", "B Glass", False, "#5457EE", "#EEEFFB", "#FFFFFF", "#F5F5FD", "#14151F", "#6B6E84", "#FFFFFF", "#5457EE", "#FFFFFF", "#FFFFFF", ["#EDEEFC", "#A8AAF6", "#F7D2C4", "#CFF0E4"]),
    ("avra_bglass_dark", "B Glass Тёмная", True, "#5B5FF5", "#0A0B1C", "#15172E", "#111326", "#F1F2FF", "#9497B5", "#1B1D38", "#5457EE", "#FFFFFF", "#FFFFFF", ["#090A1E", "#2E33A6", "#5B2A2C", "#0E3A34"]),
    ("avra_light", "Avragram", False, "#4F5BF0", "#EEF0F6", "#FFFFFF", "#FFFFFF", "#11131A", "#6B7085", "#FFFFFF", "#4F5BF0", "#FFFFFF", "#FFFFFF", ["#B9C3FF", "#FFD3C2", "#BDEFE0", "#E3D5FF"]),
    ("avra_dark", "Avragram Тёмная", True, "#7C86FF", "#0B0C12", "#14151D", "#14151D", "#F2F3F8", "#8D92A8", "#1E2030", "#4F5BF0", "#FFFFFF", "#FFFFFF", ["#1B1F4A", "#3A1E2A", "#0F3A33", "#241A45"]),
    ("avra_air", "Воздух", False, "#3F8CFF", "#EAF3FF", "#FFFFFF", "#FFFFFF", "#0F1B2D", "#62738C", "#FFFFFF", "#3F8CFF", "#FFFFFF", "#FFFFFF", ["#BFDCFF", "#FFE0CC", "#CFF3E4", "#F6D6F2"]),
    ("avra_air_dark", "Воздух Тёмный", True, "#6EA8FF", "#08111F", "#0F1A2B", "#0F1A2B", "#EEF4FF", "#8A9AB5", "#18263B", "#2F6FE0", "#FFFFFF", "#FFFFFF", ["#0E2547", "#3A2430", "#0E3A3A", "#2A1E4A"]),
    ("avra_obsidian", "Обсидиан", True, "#C6F432", "#0B0B0C", "#141416", "#141416", "#EDEDED", "#8A8A8F", "#1C1C1F", "#C6F432", "#0B0B0C", "#0B0B0C", ["#0B0B0C", "#151A0A", "#0E0E10", "#1A1F0C"]),
    ("avra_signal", "Сигнал", True, "#FF6B3D", "#15122B", "#1D1938", "#1D1938", "#F4F2FF", "#A7A3C7", "#2A2550", "#FF6B3D", "#15122B", "#15122B", ["#15122B", "#2A1440", "#401C24", "#0F2A3A"]),
    ("avra_neon_sunset", "Неоновый закат", True, "#FF2A85", "#120A26", "#1C1036", "#1C1036", "#FFF1F8", "#B9A6D6", "#2A1A4A", "#FF2A85", "#FFFFFF", "#FFFFFF", ["#FF7A45", "#FF2A85", "#6A2BD8", "#00C2D1"]),
    ("avra_sakura", "Сакура", False, "#E0567F", "#FBF0F3", "#FFFFFF", "#FFFFFF", "#2A1A20", "#8C6F79", "#FFFFFF", "#E0567F", "#FFFFFF", "#FFFFFF", ["#FFD1DC", "#FFE8D6", "#F5D0F0", "#FFF0F5"]),
    ("avra_ocean", "Океан", True, "#22C3D6", "#071820", "#0D2430", "#0D2430", "#E6F6FA", "#86A9B5", "#133241", "#0F8FA1", "#FFFFFF", "#04161D", ["#06283A", "#0B3B4A", "#052030", "#0E4A5A"]),
    ("avra_matrix", "Матрица", True, "#00E676", "#000000", "#0A0F0B", "#0A0F0B", "#C8FACC", "#5E8F66", "#0F1A12", "#00C853", "#001A0B", "#001A0B", ["#000000", "#021A0A", "#000A04", "#03240E"]),
    ("avra_sand", "Песок", False, "#C7772E", "#F5EFE6", "#FFFBF5", "#FFFBF5", "#2B2117", "#8A7A68", "#FFFBF5", "#C7772E", "#FFFFFF", "#FFFFFF", ["#F1DEC4", "#EAD2B5", "#F6E9D7", "#E5C9A8"]),
    ("avra_neon", "Неон", True, "#00F0FF", "#0A0A1A", "#121229", "#121229", "#F5F0FF", "#9C95C2", "#1C1C3D", "#7A3DFF", "#FFFFFF", "#0A0A1A", ["#1A0A2E", "#0A1A2E", "#2E0A24", "#0A2A2E"]),
    ("avra_forest", "Лес", True, "#4CC38A", "#0D1712", "#13211A", "#13211A", "#E6F2EA", "#8FA89A", "#1B2E24", "#2E8B5E", "#FFFFFF", "#08130D", ["#0D1F16", "#1A2E1A", "#0A1A14", "#233320"]),
    ("avra_graphite", "Графит", False, "#1F1F24", "#EFEFF1", "#FFFFFF", "#FFFFFF", "#111114", "#6E6E76", "#FFFFFF", "#1F1F24", "#FFFFFF", "#FFFFFF", ["#DCDCE0", "#EFEFF1", "#D0D0D6", "#E6E6EA"]),
    ("avra_sunset", "Закат", False, "#F0643C", "#FFF3EC", "#FFFFFF", "#FFFFFF", "#24150F", "#8F7266", "#FFFFFF", "#F0643C", "#FFFFFF", "#FFFFFF", ["#FFC9A8", "#FFB3B3", "#FFE0A8", "#F7B8D4"]),
]


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
    (_, _, dark, accent, bg, surface, header, text, muted, inb, outb, outt, onacc, wp) = style
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
    return out


def main():
    day, night = load("day.attheme"), load("night.attheme")
    entries = []
    for st in STYLES:
        colors = build(st, night if st[2] else day)
        with open(os.path.join(ASSETS, st[0] + ".attheme"), "w", encoding="utf-8") as f:
            for k in sorted(colors):
                f.write("%s=%d\n" % (k, to_signed(colors[k])))
        entries.append((st[0], st[1], st[2], parse_hex(st[4]), parse_hex(st[9]), parse_hex(st[10])))
        print("wrote", st[0], len(colors), "keys")
    j = lambda xs: ", ".join(xs)
    with open(JAVA_OUT, "w", encoding="utf-8") as f:
        f.write("package com.avragramm;\n\n/** Generated by Tools/avra_themes.py — do not edit by hand. */\n")
        f.write("public final class AvraStyles {\n    private AvraStyles() {}\n\n")
        f.write('    public static final String DEFAULT_DAY = "B Glass";\n    public static final String DEFAULT_NIGHT = "B Glass Тёмная";\n\n')
        f.write("    public static final String[] ASSETS = {%s};\n" % j('"%s.attheme"' % e[0] for e in entries))
        f.write("    public static final String[] NAMES = {%s};\n" % j('"%s"' % e[1] for e in entries))
        f.write("    public static final boolean[] DARK = {%s};\n" % j("true" if e[2] else "false" for e in entries))
        f.write("    public static final int[] PREVIEW_BG = {%s};\n" % j("0x%08X" % e[3] for e in entries))
        f.write("    public static final int[] PREVIEW_IN = {%s};\n" % j("0x%08X" % e[4] for e in entries))
        f.write("    public static final int[] PREVIEW_OUT = {%s};\n" % j("0x%08X" % e[5] for e in entries))
        f.write('\n    public static boolean isAvra(String assetName) {\n        return assetName != null && assetName.startsWith("avra_");\n    }\n')
        f.write("\n    public static boolean isDarkAsset(String assetName) {\n        for (int i = 0; i < ASSETS.length; i++) if (ASSETS[i].equals(assetName)) return DARK[i];\n        return false;\n    }\n}\n")


if __name__ == "__main__":
    main()
