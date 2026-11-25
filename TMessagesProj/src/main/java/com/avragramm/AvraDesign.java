package com.avragramm;

import com.exteragram.messenger.DividerStyle;
import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;

/**
 * Per-style geometry: each Avragram style changes not only colours but the shape of the UI —
 * bubble radius, avatar shape, card ("segments") vs flat lists, centred titles, FAB and font.
 */
public final class AvraDesign {

    /** bubbleRadius (0..17), avatarCorners (0 square .. 28 circle), divider, centerTitle, squareFab, systemFont. */
    private static final class Spec {
        final int bubble; final float avatar; final DividerStyle divider; final boolean center, squareFab, systemFont;
        Spec(int bubble, float avatar, DividerStyle divider, boolean center, boolean squareFab, boolean systemFont) {
            this.bubble = bubble; this.avatar = avatar; this.divider = divider; this.center = center; this.squareFab = squareFab; this.systemFont = systemFont;
        }
    }

    /** Plain Telegram geometry (the default). */
    private static final Spec TELEGRAM = new Spec(17, 28f, DividerStyle.LINE, false, false, true);

    private AvraDesign() {}

    private static Spec spec(String name) {
        if (name == null) return TELEGRAM;
        switch (name) {
            case "B Glass":
            case "B Glass Тёмная": return new Spec(17, 18f, DividerStyle.SEGMENTS, false, false, true);   // soft glass cards, squircle avatars
            case "Avragram":
            case "Avragram Тёмная": return new Spec(16, 22f, DividerStyle.SEGMENTS, false, false, true);
            case "Воздух":
            case "Воздух Тёмный": return new Spec(17, 28f, DividerStyle.SEGMENTS, true, false, true);    // airy, round, centred
            case "Обсидиан": return new Spec(6, 8f, DividerStyle.HIDDEN, true, true, false);               // sharp, minimal
            case "Сигнал": return new Spec(3, 10f, DividerStyle.SEGMENTS, false, true, false);             // bold tiles
            case "Неоновый закат": return new Spec(14, 28f, DividerStyle.HIDDEN, true, false, false);
            case "Сакура": return new Spec(17, 28f, DividerStyle.SEGMENTS, true, false, true);
            case "Океан": return new Spec(12, 20f, DividerStyle.SEGMENTS, false, false, true);
            case "Матрица": return new Spec(0, 0f, DividerStyle.HIDDEN, true, true, false);               // terminal: square everything
            case "Песок": return new Spec(10, 16f, DividerStyle.LINE, false, false, false);
            case "Неон": return new Spec(6, 6f, DividerStyle.HIDDEN, false, true, false);
            case "Лес": return new Spec(12, 18f, DividerStyle.SEGMENTS, false, false, true);
            case "Графит": return new Spec(8, 12f, DividerStyle.LINE, false, true, true);
            case "Закат": return new Spec(15, 24f, DividerStyle.SEGMENTS, false, false, true);
            default: return TELEGRAM;
        }
    }

    /** Applies the geometry of a style; returns true if fonts changed (UI must be rebuilt). */
    public static boolean apply(String styleName) {
        return apply(spec(styleName));
    }

    public static void resetToTelegram() {
        try {
            apply(TELEGRAM);
        } catch (Throwable ignore) {
        }
    }

    private static boolean apply(Spec s) {
        SharedConfig.bubbleRadius = s.bubble;
        MessagesController.getGlobalMainSettings().edit().putInt("bubbleRadius", s.bubble).apply();
        ExteraConfig.setAvatarCorners(s.avatar);
        ExteraConfig.setDividerStyle(s.divider);
        ExteraConfig.setCenterTitle(s.center);
        ExteraConfig.setSquareFab(s.squareFab);
        boolean fontChanged = ExteraConfig.getUseSystemFonts() != s.systemFont;
        ExteraConfig.setUseSystemFonts(s.systemFont);
        if (fontChanged) AndroidUtilities.clearTypefaceCache();
        return fontChanged;
    }
}
