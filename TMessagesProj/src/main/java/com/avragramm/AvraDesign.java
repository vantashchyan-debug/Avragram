package com.avragramm;

import android.content.SharedPreferences;

import com.exteragram.messenger.DividerStyle;
import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.TransitionAnimation;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;

/**
 * Two-layer geometry model of Avragram.
 *
 * <ol>
 *   <li><b>Base layer</b> — the layout preset ("style"): the geometry of one of the Avragram styles
 *       ({@link AvraStyles}) or plain Telegram geometry. Chosen on its own ({@link #setLayout}); a palette
 *       (colour theme) switch never changes it.</li>
 *   <li><b>Override layer</b> — values the user changed explicitly. Stored as {@code ov.<field>} in the
 *       "avragramm" preferences and never touched by a theme switch.</li>
 * </ol>
 *
 * Effective value = override if present, otherwise base. Every time a theme or layout is applied
 * ({@link #onThemeApplied}) the effective values are recomputed and written into the places the UI
 * reads them from ({@link SharedConfig#bubbleRadius}, {@link ExteraConfig}, {@link AvraFonts}). Nothing
 * else in the app writes those values any more: the old controls were moved to Avragram → Appearance
 * and write through {@link #setOverride}.
 */
public final class AvraDesign {

    public static final int BUTTON_TELEGRAM = 0, BUTTON_PILL = 1, BUTTON_ROUNDED = 2, BUTTON_SHARP = 3, BUTTON_TILE = 4;
    public static final int DIVIDER_HIDDEN = 0, DIVIDER_LINE = 1, DIVIDER_CARDS = 2;

    public static final int BUBBLE_MAX = 17, AVATAR_MAX = 28, SECTION_MAX = 28;

    /** Screen transitions. 0–2 are Telegram / the client's own engines, the rest are Avragram's ({@link AvraAnim}). */
    public static final int TR_TELEGRAM = 0, TR_ANDROID = 1, TR_SPRING = 2, TR_SLIDE = 3, TR_FADE = 4, TR_ZOOM = 5,
            TR_SLIDE_UP = 6, TR_FLIP = 7, TR_GLITCH = 8, TR_MIST = 9, TR_NONE = 10, TR_MAX = 10;
    /** New-message animations. */
    public static final int MSG_TELEGRAM = 0, MSG_FADE = 1, MSG_POP = 2, MSG_SLIDE = 3, MSG_SWING = 4, MSG_RISE = 5, MSG_MAX = 5;
    /** Speed in percent of the normal duration (more = slower). */
    public static final int SPEED_MIN = 25, SPEED_MAX = 300;

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
        SQUARE_FAB("squareFab"),
        TRANSITION("transition"),
        TRANSITION_SPEED("transitionSpeed"),
        MSG_ANIM("msgAnim"),
        MSG_SPEED("msgSpeed");

        public final String key;

        Field(String key) {
            this.key = "ov." + key;
        }
    }

    private static final String KEY_BASE = "design.base";
    private static final String KEY_MIGRATED = "design.v3";
    private static final String KEY_MIGRATED_ANIM = "design.anim1";
    private static final String TELEGRAM = "";

    /** Effective values cached for hot paths (bubble drawing, button creation). */
    private static volatile int buttonShape = -1;
    private static volatile int nearRadius = -1;
    private static volatile int transition = -1, transitionSpeed = -1, msgAnim = -1, msgSpeed = -1;
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
            case TRANSITION: return i < 0 ? TR_SPRING : AvraStyles.TRANSITION[i];
            case TRANSITION_SPEED:
            case MSG_SPEED: return i < 0 ? 100 : AvraStyles.ANIM_SPEED[i];
            case MSG_ANIM: return i < 0 ? MSG_TELEGRAM : AvraStyles.MSG_ANIM[i];
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
            case TRANSITION: return Math.max(TR_TELEGRAM, Math.min(TR_MAX, v));
            case MSG_ANIM: return Math.max(MSG_TELEGRAM, Math.min(MSG_MAX, v));
            case TRANSITION_SPEED:
            case MSG_SPEED: return Math.max(SPEED_MIN, Math.min(SPEED_MAX, v));
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
            // Since 1.4 the theme (palette) no longer decides the layout: the style is chosen on its own
            // (Themes and styles → Styles, or a whole ready-made theme). Only the very first run of a
            // pre-1.3 install takes the layout from the active theme, so the look does not jump.
            if (!prefs().getBoolean(KEY_MIGRATED, false)) {
                ensureMigrated(AvraStyles.isAvra(theme.assetName) ? theme.name : TELEGRAM);
            }
            ensureAnimMigrated();
            if (sync()) fontChangedByTheme = true;
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /**
     * 1.5: the client's transition setting (Telegram / Android / Spring) moved here. A value the user
     * picked before becomes an override if it differs from the active style, so nothing changes visibly.
     */
    private static void ensureAnimMigrated() {
        SharedPreferences p = prefs();
        if (p.getBoolean(KEY_MIGRATED_ANIM, false)) return;
        SharedPreferences.Editor e = p.edit();
        TransitionAnimation t = ExteraConfig.getTransitionAnimation();
        int current = t == TransitionAnimation.AOSP ? TR_ANDROID : t == TransitionAnimation.SPRING ? TR_SPRING : TR_TELEGRAM;
        // Spring is the client's default: only a value the user actually changed is kept, so styles show their own animation.
        if (!p.contains(Field.TRANSITION.key) && current != TR_SPRING && current != baseInt(Field.TRANSITION)) {
            e.putInt(Field.TRANSITION.key, current);
        }
        e.putBoolean(KEY_MIGRATED_ANIM, true);
        e.apply();
    }

    // ------------------------------------------------------------------------------------ layout presets

    /**
     * Switches the layout preset (the base layer: buttons, bubbles, avatars, lists, title, FAB, font)
     * without touching colours. {@code styleName} is an Avragram style name, "" or unknown = Telegram.
     * User overrides stay on top. Returns true if the effective font changed.
     */
    public static boolean setLayout(String styleName) {
        ensureMigrated(baseStyle());
        String base = styleIndex(styleName) >= 0 ? styleName : TELEGRAM;
        if (!base.equals(baseStyle())) prefs().edit().putString(KEY_BASE, base).apply();
        return sync();
    }

    /** Index of the active layout preset in {@link AvraStyles}, -1 for Telegram. */
    public static int layoutIndex() {
        return styleIndex(baseStyle());
    }

    /** True if two styles have identical geometry (e.g. the light and dark variant of a concept). */
    public static boolean sameLayout(int a, int b) {
        if (a == b) return true;
        if (a < 0 || b < 0) return false;
        return AvraStyles.FONT[a].equals(AvraStyles.FONT[b]) && AvraStyles.BUTTON[a] == AvraStyles.BUTTON[b]
                && AvraStyles.BUBBLE[a] == AvraStyles.BUBBLE[b] && AvraStyles.AVATAR[a] == AvraStyles.AVATAR[b]
                && AvraStyles.DIVIDER[a] == AvraStyles.DIVIDER[b] && AvraStyles.SECTION[a] == AvraStyles.SECTION[b]
                && AvraStyles.CENTER[a] == AvraStyles.CENTER[b] && AvraStyles.SQUARE_FAB[a] == AvraStyles.SQUARE_FAB[b]
                && AvraStyles.TRANSITION[a] == AvraStyles.TRANSITION[b] && AvraStyles.MSG_ANIM[a] == AvraStyles.MSG_ANIM[b]
                && AvraStyles.ANIM_SPEED[a] == AvraStyles.ANIM_SPEED[b];
    }

    /** True if the active layout is the one of style {@code i} (-1 = Telegram). */
    public static boolean isLayout(int i) {
        return sameLayout(layoutIndex(), i);
    }

    private static int[] layouts;

    /** Distinct layout presets: the first style of every group with identical geometry. */
    public static int[] layouts() {
        int[] l = layouts;
        if (l == null) {
            int[] tmp = new int[AvraStyles.NAMES.length];
            int n = 0;
            outer:
            for (int i = 0; i < AvraStyles.NAMES.length; i++) {
                for (int k = 0; k < n; k++) if (sameLayout(tmp[k], i)) continue outer;
                tmp[n++] = i;
            }
            l = java.util.Arrays.copyOf(tmp, n);
            layouts = l;
        }
        return l;
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
        transition = value(Field.TRANSITION);
        transitionSpeed = value(Field.TRANSITION_SPEED);
        msgAnim = value(Field.MSG_ANIM);
        msgSpeed = value(Field.MSG_SPEED);
        // Telegram / Android / Spring are the client's engines; Avragram's own styles run on the classic frame loop.
        TransitionAnimation engine = transition == TR_ANDROID ? TransitionAnimation.AOSP
                : transition == TR_SPRING ? TransitionAnimation.SPRING : TransitionAnimation.DEFAULT;
        if (ExteraConfig.getTransitionAnimation() != engine) ExteraConfig.setTransitionAnimation(engine);

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

    /** Active screen transition ({@link #TR_TELEGRAM} …). */
    public static int transition() {
        int v = transition;
        if (v < 0) transition = v = value(Field.TRANSITION);
        return v;
    }

    public static int transitionSpeed() {
        int v = transitionSpeed;
        if (v < 0) transitionSpeed = v = value(Field.TRANSITION_SPEED);
        return v;
    }

    public static int msgAnim() {
        int v = msgAnim;
        if (v < 0) msgAnim = v = value(Field.MSG_ANIM);
        return v;
    }

    public static int msgSpeed() {
        int v = msgSpeed;
        if (v < 0) msgSpeed = v = value(Field.MSG_SPEED);
        return v;
    }

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
