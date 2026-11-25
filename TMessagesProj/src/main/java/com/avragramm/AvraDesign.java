package com.avragramm;

import android.content.SharedPreferences;

import com.exteragram.messenger.DividerStyle;
import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;

/**
 * Two-layer geometry model of Avragram.
 *
 * <ol>
 *   <li><b>Base layer</b> — the geometry of the active theme: one of the 32 Avragram styles
 *       ({@link AvraStyles}) or, for any other theme (Telegram, accents, custom files), plain Telegram geometry.</li>
 *   <li><b>Override layer</b> — values the user changed explicitly. Stored as {@code ov.<field>} in the
 *       "avragramm" preferences and never touched by a theme switch.</li>
 * </ol>
 *
 * Effective value = override if present, otherwise base. Every time a theme is applied
 * ({@link #onThemeApplied}) the effective values are recomputed and written into the places the UI
 * reads them from ({@link SharedConfig#bubbleRadius}, {@link ExteraConfig}, {@link AvraFonts}). Nothing
 * else in the app writes those values any more: the old controls were moved to Avragram → Appearance
 * and write through {@link #setOverride}.
 */
public final class AvraDesign {

    public static final int BUTTON_TELEGRAM = 0, BUTTON_PILL = 1, BUTTON_ROUNDED = 2, BUTTON_SHARP = 3, BUTTON_TILE = 4;
    public static final int DIVIDER_HIDDEN = 0, DIVIDER_LINE = 1, DIVIDER_CARDS = 2;

    public static final int BUBBLE_MAX = 17, AVATAR_MAX = 28, SECTION_MAX = 28;

    /** Every mechanic the layers manage. The order is part of nothing persistent; keys use {@link #key}. */
    public enum Field {
        FONT("font"),
        BUTTON("button"),
        BUBBLE("bubble"),
        BUBBLE_NEAR("bubbleNear"),
        TAIL("tail"),
        AVATAR("avatar"),
        SINGLE_CORNER("singleCorner"),
        DIVIDER("divider"),
        SECTION("section"),
        SEPARATE_HEADERS("separateHeaders"),
        CENTER_TITLE("centerTitle"),
        SQUARE_FAB("squareFab");

        public final String key;

        Field(String key) {
            this.key = "ov." + key;
        }
    }

    private static final String KEY_BASE = "design.base";
    private static final String KEY_MIGRATED = "design.v3";
    private static final String TELEGRAM = "";

    /** Effective values cached for hot paths (bubble drawing, button creation). */
    private static volatile int buttonShape = -1;
    private static volatile int nearRadius = -1;
    /** Set when a theme switch changed the effective font; the Appearance screens offer a restart. */
    public static volatile boolean fontChangedByTheme;

    private AvraDesign() {}

    private static SharedPreferences prefs() {
        return AvraConfig.prefs();
    }

    // ------------------------------------------------------------------------------------ base layer

    /** Name of the Avragram style the base layer comes from, or "" for Telegram geometry. */
    public static String baseStyle() {
        try {
            return prefs().getString(KEY_BASE, TELEGRAM);
        } catch (Throwable e) {
            return TELEGRAM;
        }
    }

    private static int styleIndex(String name) {
        return name == null || name.isEmpty() ? -1 : AvraStyles.indexOfName(name);
    }

    /** Base value of an int field for a style (by theme name; "" or unknown = Telegram). */
    public static int baseInt(Field f, String styleName) {
        int i = styleIndex(styleName);
        switch (f) {
            case BUTTON: return i < 0 ? BUTTON_TELEGRAM : AvraStyles.BUTTON[i];
            case BUBBLE: return i < 0 ? 17 : AvraStyles.BUBBLE[i];
            case BUBBLE_NEAR: return Math.min(6, i < 0 ? 17 : AvraStyles.BUBBLE[i]);
            case TAIL: return i < 0 ? 1 : (AvraStyles.BUBBLE[i] > 4 ? 1 : 0);
            case AVATAR: return i < 0 ? 28 : AvraStyles.AVATAR[i];
            case SINGLE_CORNER: return 0;
            case DIVIDER: return i < 0 ? DIVIDER_LINE : AvraStyles.DIVIDER[i];
            case SECTION: return i < 0 ? 20 : AvraStyles.SECTION[i];
            case SEPARATE_HEADERS: return 1;
            case CENTER_TITLE: return i < 0 ? 0 : (AvraStyles.CENTER[i] ? 1 : 0);
            case SQUARE_FAB: return i < 0 ? 0 : (AvraStyles.SQUARE_FAB[i] ? 1 : 0);
            default: throw new IllegalArgumentException(f.name());
        }
    }

    public static String baseFont(String styleName) {
        int i = styleIndex(styleName);
        return i < 0 ? "system" : AvraStyles.FONT[i];
    }

    public static int baseInt(Field f) {
        return baseInt(f, baseStyle());
    }

    public static String baseFont() {
        return baseFont(baseStyle());
    }

    // ------------------------------------------------------------------------------------ override layer

    public static boolean isOverridden(Field f) {
        return prefs().contains(f.key);
    }

    public static int overriddenCount() {
        SharedPreferences p = prefs();
        int n = 0;
        for (Field f : Field.values()) if (p.contains(f.key)) n++;
        return n;
    }

    public static int overriddenCount(Field... fields) {
        SharedPreferences p = prefs();
        int n = 0;
        for (Field f : fields) if (p.contains(f.key)) n++;
        return n;
    }

    /** Effective int value: the user's override, or the base of the active style. */
    public static int value(Field f) {
        SharedPreferences p = prefs();
        if (p.contains(f.key)) {
            try {
                return clamp(f, p.getInt(f.key, baseInt(f)));
            } catch (ClassCastException e) {
                p.edit().remove(f.key).apply(); // corrupted / foreign type: fall back to the base
            }
        }
        return baseInt(f);
    }

    public static boolean flag(Field f) {
        return value(f) != 0;
    }

    public static String font() {
        SharedPreferences p = prefs();
        String v = null;
        try {
            v = p.getString(Field.FONT.key, null);
        } catch (ClassCastException e) {
            p.edit().remove(Field.FONT.key).apply();
        }
        return v != null && AvraFonts.isKnown(v) ? v : baseFont();
    }

    private static int clamp(Field f, int v) {
        switch (f) {
            case BUTTON: return Math.max(BUTTON_TELEGRAM, Math.min(BUTTON_TILE, v));
            case BUBBLE:
            case BUBBLE_NEAR: return Math.max(0, Math.min(BUBBLE_MAX, v));
            case AVATAR: return Math.max(0, Math.min(AVATAR_MAX, v));
            case SECTION: return Math.max(0, Math.min(SECTION_MAX, v));
            case DIVIDER: return Math.max(DIVIDER_HIDDEN, Math.min(DIVIDER_CARDS, v));
            default: return v != 0 ? 1 : 0;
        }
    }

    /** Stores an explicit user value on top of the theme and applies it. Returns true if the font changed. */
    public static boolean setOverride(Field f, int v) {
        if (f == Field.FONT) throw new IllegalArgumentException("use setFont");
        prefs().edit().putInt(f.key, clamp(f, v)).apply();
        return sync();
    }

    public static boolean setOverride(Field f, boolean v) {
        return setOverride(f, v ? 1 : 0);
    }

    public static boolean setFont(String id) {
        prefs().edit().putString(Field.FONT.key, AvraFonts.isKnown(id) ? id : "system").apply();
        return sync();
    }

    /** "Reset to theme": drops the overrides so the base of the active style shows again. */
    public static boolean reset(Field... fields) {
        SharedPreferences.Editor e = prefs().edit();
        for (Field f : fields) e.remove(f.key);
        e.apply();
        return sync();
    }

    public static boolean resetAll() {
        return reset(Field.values());
    }

    // ------------------------------------------------------------------------------------ applying

    /**
     * Called by {@link Theme} whenever a theme becomes current (startup, manual switch, auto night,
     * end of a preview). Previews themselves are ignored so a cancelled preview leaves nothing behind.
     */
    public static void onThemeApplied(Theme.ThemeInfo theme, boolean preview) {
        if (preview || theme == null) return;
        try {
            String base = AvraStyles.isAvra(theme.assetName) ? theme.name : TELEGRAM;
            ensureMigrated(base);
            if (!base.equals(baseStyle())) {
                prefs().edit().putString(KEY_BASE, base).apply();
            }
            if (sync()) fontChangedByTheme = true;
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /**
     * Writes the effective values where the UI reads them. Only values that actually differ are
     * written, so repeated or rapid theme switches cost a handful of comparisons.
     *
     * @return true if the effective font changed (text views need a rebuild, the process default a restart)
     */
    public static synchronized boolean sync() {
        buttonShape = value(Field.BUTTON);
        nearRadius = value(Field.BUBBLE_NEAR);

        int bubble = value(Field.BUBBLE);
        if (SharedConfig.bubbleRadius != bubble) {
            SharedConfig.bubbleRadius = bubble;
            MessagesController.getGlobalMainSettings().edit().putInt("bubbleRadius", bubble).apply();
        }
        boolean removeTail = !flag(Field.TAIL);
        if (ExteraConfig.getRemoveMessageTail() != removeTail) ExteraConfig.setRemoveMessageTail(removeTail);

        float avatar = value(Field.AVATAR);
        if (ExteraConfig.getAvatarCorners() != avatar) ExteraConfig.setAvatarCorners(avatar);
        boolean single = flag(Field.SINGLE_CORNER);
        if (ExteraConfig.getSingleCornerRadius() != single) ExteraConfig.setSingleCornerRadius(single);

        DividerStyle divider = toDivider(value(Field.DIVIDER));
        if (ExteraConfig.getDividerStyle() != divider) ExteraConfig.setDividerStyle(divider);
        float section = value(Field.SECTION);
        if (ExteraConfig.getSectionRadius() != section) ExteraConfig.setSectionRadius(section);
        // With cards the client always separates headers; the stored preference only matters for the other styles.
        if (divider != DividerStyle.SEGMENTS) {
            boolean separate = flag(Field.SEPARATE_HEADERS);
            if (ExteraConfig.getSectionsSeparatedHeaders() != separate) ExteraConfig.setSectionsSeparatedHeaders(separate);
        }

        boolean center = flag(Field.CENTER_TITLE);
        if (ExteraConfig.getCenterTitle() != center) ExteraConfig.setCenterTitle(center);
        boolean squareFab = flag(Field.SQUARE_FAB);
        if (ExteraConfig.getSquareFab() != squareFab) ExteraConfig.setSquareFab(squareFab);

        return AvraFonts.applyEffective(font());
    }

    // ------------------------------------------------------------------------------------ migration

    /**
     * Before 1.3 a style wrote its geometry straight into the config, mixed with what the user set by
     * hand. On the first run we keep the current look: every value that differs from the base of the
     * active theme becomes an explicit override. Fresh installs (no Avragram design keys) start clean.
     */
    private static void ensureMigrated(String base) {
        SharedPreferences p = prefs();
        if (p.getBoolean(KEY_MIGRATED, false)) return;
        SharedPreferences.Editor e = p.edit();
        boolean hadDesign = p.contains("buttonShape") || p.contains("font");
        if (hadDesign) {
            int legacyButton = p.getInt("buttonShape", BUTTON_TELEGRAM);
            putIfDiffers(e, Field.BUTTON, legacyButton, base);
            putIfDiffers(e, Field.BUBBLE, SharedConfig.bubbleRadius, base);
            putIfDiffers(e, Field.TAIL, ExteraConfig.getRemoveMessageTail() ? 0 : 1, base);
            putIfDiffers(e, Field.AVATAR, Math.round(ExteraConfig.getAvatarCorners()), base);
            putIfDiffers(e, Field.SINGLE_CORNER, ExteraConfig.getSingleCornerRadius() ? 1 : 0, base);
            putIfDiffers(e, Field.DIVIDER, fromDivider(ExteraConfig.getDividerStyle()), base);
            putIfDiffers(e, Field.SECTION, Math.round(ExteraConfig.getSectionRadius()), base);
            putIfDiffers(e, Field.CENTER_TITLE, ExteraConfig.getCenterTitle() ? 1 : 0, base);
            putIfDiffers(e, Field.SQUARE_FAB, ExteraConfig.getSquareFab() ? 1 : 0, base);
            String legacyFont = p.getString("font", null);
            if (legacyFont != null && AvraFonts.isKnown(legacyFont) && !legacyFont.equals(baseFont(base))) {
                e.putString(Field.FONT.key, legacyFont);
            }
        }
        e.remove("buttonShape");
        e.putString(KEY_BASE, base);
        e.putBoolean(KEY_MIGRATED, true);
        e.apply();
    }

    private static void putIfDiffers(SharedPreferences.Editor e, Field f, int current, String base) {
        current = clamp(f, current);
        if (current != baseInt(f, base)) e.putInt(f.key, current);
    }

    private static DividerStyle toDivider(int d) {
        return d == DIVIDER_HIDDEN ? DividerStyle.HIDDEN : d == DIVIDER_CARDS ? DividerStyle.SEGMENTS : DividerStyle.LINE;
    }

    private static int fromDivider(DividerStyle d) {
        return d == DividerStyle.HIDDEN ? DIVIDER_HIDDEN : d == DividerStyle.SEGMENTS ? DIVIDER_CARDS : DIVIDER_LINE;
    }

    // ------------------------------------------------------------------------------------ hot-path readers

    public static int buttonShape() {
        int s = buttonShape;
        if (s < 0) {
            s = value(Field.BUTTON);
            buttonShape = s;
        }
        return s;
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

    /**
     * Radius (dp) of bubble corners that touch a neighbouring message of the same group. Telegram
     * hard-codes min(6, bubbleRadius); Avragram lets the user pick it, never above the main radius.
     */
    public static int nearRadius() {
        int n = nearRadius;
        if (n < 0) {
            n = value(Field.BUBBLE_NEAR);
            nearRadius = n;
        }
        return Math.min(n, SharedConfig.bubbleRadius);
    }
}
