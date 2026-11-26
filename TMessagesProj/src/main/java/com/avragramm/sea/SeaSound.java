package com.avragramm.sea;

import android.media.AudioAttributes;
import android.media.SoundPool;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.util.HashSet;

/** AvraSea sounds (res/raw/sea_*.ogg, synthesized by Tools/chess-art/make_sea_sounds.py). */
public final class SeaSound {

    private SeaSound() {
    }

    public static final int SHOT = 0, SPLASH = 1, HIT = 2, SUNK = 3, PLACE = 4, START = 5, WIN = 6, LOSE = 7, TURN = 8;
    private static final String[] FILES = {"shot", "splash", "hit", "sunk", "place", "start", "win", "lose", "turn"};
    private static SoundPool pool;
    private static final int[] ids = new int[FILES.length];
    private static final HashSet<Integer> ready = new HashSet<>();

    public static boolean enabled() {
        return SeaNet.prefs().getBoolean("sound", true);
    }

    public static void setEnabled(boolean on) {
        SeaNet.prefs().edit().putBoolean("sound", on).apply();
    }

    public static synchronized void preload() {
        if (pool != null) return;
        try {
            pool = new SoundPool.Builder().setMaxStreams(4).setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
            pool.setOnLoadCompleteListener((sp, id, status) -> {
                synchronized (SeaSound.class) {
                    if (status == 0) ready.add(id);
                }
            });
            android.content.Context ctx = ApplicationLoader.applicationContext;
            for (int i = 0; i < FILES.length; i++) {
                int res = ctx.getResources().getIdentifier("sea_" + FILES[i], "raw", ctx.getPackageName());
                ids[i] = res == 0 ? 0 : pool.load(ctx, res, 1);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public static void play(int sound) {
        if (!enabled()) return;
        Utilities.globalQueue.postRunnable(() -> {
            try {
                preload();
                synchronized (SeaSound.class) {
                    int id = ids[sound];
                    if (id != 0 && ready.contains(id)) pool.play(id, 1f, 1f, 1, 0, 1f);
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
    }
}
