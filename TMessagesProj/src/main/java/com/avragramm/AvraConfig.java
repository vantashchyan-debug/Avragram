package com.avragramm;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/**
 * AvraGramm feature switches (prefs file "avragramm"), plus per-chat voice changer presets.
 * Based on exteraGram — https://github.com/exteraSquad/exteraGram
 */
public final class AvraConfig {

    private static final String PREFS = "avragramm";

    public static boolean saveDeleted = true;
    public static boolean saveViewOnce = true;
    /** 0 off, 1 frosted blur, 2 liquid glass (Android 13+, frosted below). */
    public static int glassMode = 2;
    /** Secret (debug menu): no cap on the number of spam messages. */
    public static boolean spamUnlimited = false;
    /** Chat header: "online · with @user in «Group»" from data shared with you (see AvraPresence). */
    public static boolean presenceBar = true;
    /** Secret (debug menu): show a sample presence bar for every online user, to check the layout. */
    public static boolean presenceDemo = false;
    public static final int GLASS_OFF = 0, GLASS_FROSTED = 1, GLASS_LIQUID = 2;

    private static volatile boolean loaded;

    private AvraConfig() {}

    public static void ensureLoaded() {
        if (!loaded && ApplicationLoader.applicationContext != null) {
            load();
        }
    }

    public static synchronized void load() {
        if (loaded) return;
        SharedPreferences p = prefs();
        saveDeleted = p.getBoolean("saveDeleted", true);
        saveViewOnce = p.getBoolean("saveViewOnce", true);
        // Weak devices (e.g. Galaxy Note 10.1) lag with real-time blur: default to off there.
        int defaultGlass = org.telegram.messenger.SharedConfig.getDevicePerformanceClass() <= org.telegram.messenger.SharedConfig.PERFORMANCE_CLASS_LOW ? GLASS_OFF : GLASS_LIQUID;
        glassMode = p.getInt("glassMode", defaultGlass);
        spamUnlimited = p.getBoolean("spamUnlimited", false);
        presenceBar = p.getBoolean("presenceBar", true);
        presenceDemo = p.getBoolean("presenceDemo", false);
        loaded = true;
    }

    public static void save() {
        prefs().edit()
                .putBoolean("saveDeleted", saveDeleted)
                .putBoolean("saveViewOnce", saveViewOnce)
                .putInt("glassMode", glassMode)
                .putBoolean("spamUnlimited", spamUnlimited)
                .putBoolean("presenceBar", presenceBar)
                .putBoolean("presenceDemo", presenceDemo)
                .apply();
    }

    // ---- per-chat voice changer -------------------------------------------------------------

    /** Preset for a chat; 0 = off. */
    public static int voicePreset(long dialogId) {
        if (dialogId == 0 || ApplicationLoader.applicationContext == null) return 0;
        return prefs().getInt("voice_" + dialogId, 0);
    }

    /** Extra pitch shift (semitones) for a chat. */
    public static int voicePitch(long dialogId) {
        if (dialogId == 0 || ApplicationLoader.applicationContext == null) return 0;
        return prefs().getInt("pitch_" + dialogId, 0);
    }

    public static void setVoice(long dialogId, int preset, int pitch) {
        prefs().edit().putInt("voice_" + dialogId, preset).putInt("pitch_" + dialogId, pitch).apply();
    }

    static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
