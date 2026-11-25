package com.avragramm;

import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.text.TextPaint;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

/**
 * App-wide fonts (OFL fonts from Google Fonts, bundled in assets/fonts/avra, Latin + Cyrillic).
 * "system" and "roboto" are the two modes that used to be the separate "use system fonts" switch.
 */
public final class AvraFonts {

    /** id, display name, regular file, regular weight, bold file, bold weight. */
    public static final String[][] FONTS = {
            {"system", null, null, "0", null, "0"},
            {"roboto", "Roboto", null, "0", null, "0"},
            {"manrope", "Manrope", "manrope", "500", "manrope", "750"},
            {"inter", "Inter", "inter", "400", "inter", "600"},
            {"unbounded_inter", "Unbounded + Inter", "inter", "400", "unbounded", "600"},
            {"golos", "Golos", "golostext", "400", "golostext", "600"},
            {"onest", "Onest", "onest", "400", "onest", "600"},
            {"onest_mono", "Onest + JetBrains Mono", "onest", "400", "jetbrainsmono", "700"},
            {"rubik", "Rubik", "rubik", "400", "rubik", "600"},
            {"montserrat", "Montserrat", "montserrat", "500", "montserrat", "700"},
            {"nunito", "Nunito", "nunito", "500", "nunito", "800"},
            {"comfortaa", "Comfortaa", "comfortaa", "500", "comfortaa", "700"},
            {"exo2", "Exo 2", "exo2", "400", "exo2", "700"},
            {"oswald", "Oswald", "oswald", "400", "oswald", "600"},
            {"raleway", "Raleway", "raleway", "500", "raleway", "700"},
            {"jetbrains", "JetBrains Mono", "jetbrainsmono", "400", "jetbrainsmono", "700"},
            {"lora", "Lora", "lora", "400", "lora", "700"},
            {"playfair", "Playfair Display", "playfairdisplay", "400", "playfairdisplay", "800"},
            {"caveat", "Caveat", "caveat", "500", "caveat", "700"},
            {"pressstart", "Press Start 2P", "pressstart2p", "0", "pressstart2p", "0"},
    };

    private static volatile String current;
    private static Typeface regular, bold;

    private AvraFonts() {}

    public static String current() {
        if (current == null) {
            current = AvraConfig.prefs().getString("font", "system");
        }
        return current;
    }

    public static int indexOf(String id) {
        for (int i = 0; i < FONTS.length; i++) if (FONTS[i][0].equals(id)) return i;
        return 0;
    }

    public static boolean isCustom() {
        return indexOf(current()) >= 2;
    }

    /** Stores the font; system/roboto map onto the client's "use system fonts" option. Returns true if it changed. */
    public static boolean set(String id) {
        if (id == null) id = "system";
        boolean changed = !id.equals(current());
        current = id;
        regular = bold = null;
        AvraConfig.prefs().edit().putString("font", id).apply();
        try {
            if ("system".equals(id)) com.exteragram.messenger.ExteraConfig.setUseSystemFonts(true);
            else if ("roboto".equals(id)) com.exteragram.messenger.ExteraConfig.setUseSystemFonts(false);
        } catch (Throwable ignore) {
        }
        AndroidUtilities.clearTypefaceCache();
        return changed;
    }

    private static Typeface load(String file, String weight) {
        AssetManager am = ApplicationLoader.applicationContext.getAssets();
        String path = "fonts/avra/" + file + ".ttf";
        try {
            Typeface.Builder b = new Typeface.Builder(am, path);
            int w = Integer.parseInt(weight);
            if (w > 0) {
                b.setFontVariationSettings("'wght' " + w);
                b.setWeight(Math.min(1000, Math.max(1, w)));
            }
            Typeface t = b.build();
            return t != null ? t : Typeface.createFromAsset(am, path);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    public static Typeface regular() {
        if (!isCustom()) return null;
        if (regular == null) {
            String[] f = FONTS[indexOf(current())];
            regular = load(f[2], f[3]);
        }
        return regular;
    }

    public static Typeface bold() {
        if (!isCustom()) return null;
        if (bold == null) {
            String[] f = FONTS[indexOf(current())];
            bold = load(f[4], f[5]);
        }
        return bold;
    }

    /** Replacement for Telegram's bundled font asset paths, or null to keep the default. */
    public static Typeface resolve(String assetPath) {
        if (!isCustom() || assetPath == null) return null;
        switch (assetPath) {
            case AndroidUtilities.TYPEFACE_ROBOTO_REGULAR:
                return regular();
            case AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM:
            case AndroidUtilities.TYPEFACE_ROBOTO_EXTRA_BOLD:
            case AndroidUtilities.TYPEFACE_ROBOTO_CONDENSED_BOLD:
                return bold();
            case AndroidUtilities.TYPEFACE_ROBOTO_ITALIC: {
                Typeface r = regular();
                return r == null ? null : Typeface.create(r, Typeface.ITALIC);
            }
            case AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM_ITALIC: {
                Typeface b = bold();
                return b == null ? null : Typeface.create(b, Typeface.ITALIC);
            }
            default:
                return null;
        }
    }

    /**
     * Makes the chosen font the process default, so plain TextViews and paints without an explicit
     * typeface (message text, dialog list) use it too. Best effort, called once at app start.
     */
    public static void installAsDefault() {
        try {
            Typeface r = regular(), b = bold();
            if (r == null) return;
            if (b == null) b = r;
            try {
                Method m = Typeface.class.getDeclaredMethod("setDefault", Typeface.class);
                m.setAccessible(true);
                m.invoke(null, r);
            } catch (Throwable ignore) {
            }
            setStatic("DEFAULT", r);
            setStatic("SANS_SERIF", r);
            setStatic("DEFAULT_BOLD", b);
            try {
                Field f = Typeface.class.getDeclaredField("sDefaults");
                f.setAccessible(true);
                Typeface[] arr = (Typeface[]) f.get(null);
                if (arr != null && arr.length >= 4) {
                    arr[0] = r;
                    arr[1] = b;
                    arr[2] = Typeface.create(r, Typeface.ITALIC);
                    arr[3] = Typeface.create(b, Typeface.ITALIC);
                }
            } catch (Throwable ignore) {
            }
            try {
                Field f = Typeface.class.getDeclaredField("sSystemFontMap");
                f.setAccessible(true);
                @SuppressWarnings("unchecked") Map<String, Typeface> map = (Map<String, Typeface>) f.get(null);
                if (map != null) {
                    map.put("sans-serif", r);
                    map.put("sans-serif-medium", b);
                }
            } catch (Throwable ignore) {
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /** For TextPaints created without a typeface (they would ignore the process default on some ROMs). */
    public static void apply(TextPaint paint, boolean boldText) {
        if (paint == null || !isCustom()) return;
        Typeface t = boldText ? bold() : regular();
        if (t != null) paint.setTypeface(t);
    }

    /** Gives every TextPaint in Theme that has no explicit typeface the chosen regular font. */
    public static void applyToTheme() {
        if (!isCustom()) return;
        Typeface r = regular();
        if (r == null) return;
        try {
            for (Field f : org.telegram.ui.ActionBar.Theme.class.getFields()) {
                if (!Modifier.isStatic(f.getModifiers())) continue;
                Class<?> t = f.getType();
                if (t == TextPaint.class) {
                    TextPaint p = (TextPaint) f.get(null);
                    if (p != null && (p.getTypeface() == null || p.getTypeface() == Typeface.DEFAULT)) p.setTypeface(r);
                } else if (t == TextPaint[].class) {
                    TextPaint[] arr = (TextPaint[]) f.get(null);
                    if (arr != null) for (TextPaint p : arr) {
                        if (p != null && (p.getTypeface() == null || p.getTypeface() == Typeface.DEFAULT)) p.setTypeface(r);
                    }
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static void setStatic(String name, Typeface value) {
        try {
            Field f = Typeface.class.getDeclaredField(name);
            f.setAccessible(true);
            try {
                Field mod = Field.class.getDeclaredField("accessFlags");
                mod.setAccessible(true);
                mod.setInt(f, f.getModifiers() & ~Modifier.FINAL);
            } catch (Throwable ignore) {
            }
            f.set(null, value);
        } catch (Throwable ignore) {
        }
    }
}
