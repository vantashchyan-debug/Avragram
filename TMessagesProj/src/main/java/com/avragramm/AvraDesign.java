package com.avragramm;

import com.exteragram.messenger.DividerStyle;
import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;

/**
 * One source of truth for the shape of the UI. A style sets all of it at once; every value can then
 * be changed separately in Settings → Appearance (single merged control per mechanic).
 */
public final class AvraDesign {

    public static final int BUTTON_TELEGRAM = 0, BUTTON_PILL = 1, BUTTON_ROUNDED = 2, BUTTON_SHARP = 3, BUTTON_TILE = 4;
    public static final int DIVIDER_HIDDEN = 0, DIVIDER_LINE = 1, DIVIDER_CARDS = 2;

    private static int buttonShape = -1;

    private AvraDesign() {}

    public static int buttonShape() {
        if (buttonShape < 0) buttonShape = AvraConfig.prefs().getInt("buttonShape", BUTTON_TELEGRAM);
        return buttonShape;
    }

    public static void setButtonShape(int shape) {
        buttonShape = shape;
        AvraConfig.prefs().edit().putInt("buttonShape", shape).apply();
        try {
            ExteraConfig.setSquareFab(shape == BUTTON_SHARP || shape == BUTTON_TILE);
        } catch (Throwable ignore) {
        }
    }

    /** Corner radius (dp) for large buttons, given the radius the screen asked for. */
    public static int buttonRadius(int defaultDp) {
        switch (buttonShape()) {
            case BUTTON_PILL: return Math.max(defaultDp, 28);
            case BUTTON_ROUNDED: return 16;
            case BUTTON_SHARP: return 4;
            case BUTTON_TILE: return 20;
            default: return defaultDp;
        }
    }

    public static void setBubbleRadius(int r) {
        SharedConfig.bubbleRadius = r;
        MessagesController.getGlobalMainSettings().edit().putInt("bubbleRadius", r).apply();
    }

    public static int dividerStyle() {
        try {
            DividerStyle d = ExteraConfig.getDividerStyle();
            return d == DividerStyle.HIDDEN ? DIVIDER_HIDDEN : d == DividerStyle.SEGMENTS ? DIVIDER_CARDS : DIVIDER_LINE;
        } catch (Throwable e) {
            return DIVIDER_LINE;
        }
    }

    public static void setDividerStyle(int d) {
        ExteraConfig.setDividerStyle(d == DIVIDER_HIDDEN ? DividerStyle.HIDDEN : d == DIVIDER_CARDS ? DividerStyle.SEGMENTS : DividerStyle.LINE);
    }

    /** Applies the full design of an Avragram style (by theme name); null = plain Telegram. Returns true if the font changed. */
    public static boolean apply(String themeName) {
        int i = AvraStyles.indexOfName(themeName);
        if (i < 0) {
            return apply("system", BUTTON_TELEGRAM, 17, 28f, DIVIDER_LINE, 20, false, false, false, true, true, false);
        }
        return apply(AvraStyles.FONT[i], AvraStyles.BUTTON[i], AvraStyles.BUBBLE[i], AvraStyles.AVATAR[i], AvraStyles.DIVIDER[i],
                AvraStyles.SECTION[i], AvraStyles.CENTER[i], AvraStyles.SQUARE_FAB[i], AvraStyles.NEW_FAB[i],
                AvraStyles.NEW_SWITCH[i], AvraStyles.NEW_SLIDER[i], AvraStyles.FLOAT_NAV[i]);
    }

    public static void resetToTelegram() {
        try {
            apply(null);
        } catch (Throwable ignore) {
        }
    }

    private static boolean apply(String font, int button, int bubble, float avatar, int divider, int section, boolean center,
                                 boolean squareFab, boolean newFab, boolean newSwitch, boolean newSlider, boolean floatNav) {
        setBubbleRadius(bubble);
        setButtonShape(button);
        ExteraConfig.setAvatarCorners(avatar);
        setDividerStyle(divider);
        ExteraConfig.setSectionRadius(section);
        ExteraConfig.setCenterTitle(center);
        ExteraConfig.setSquareFab(squareFab);
        ExteraConfig.setNewFabStyle(newFab);
        ExteraConfig.setNewSwitchStyle(newSwitch);
        ExteraConfig.setNewSliderStyle(newSlider);
        ExteraConfig.setNewNavigationBarStyle(floatNav);
        return AvraFonts.set(font);
    }
}
