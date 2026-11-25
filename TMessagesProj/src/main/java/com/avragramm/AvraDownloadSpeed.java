package com.avragramm;

import android.os.SystemClock;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * "Download speed & ETA": percent, speed and time left under a file name while it loads.
 * The speed is a smoothed average of the progress callbacks, kept per file.
 */
public final class AvraDownloadSpeed {

    private AvraDownloadSpeed() {
    }

    private static final long MIN_SAMPLE_MS = 300;
    private static final long FORGET_MS = 60_000;

    private static final class Track {
        long bytes, time, touched;
        double speed; // bytes per second, 0 until the first sample
    }

    private static final HashMap<Long, Track> tracks = new HashMap<>();

    public static boolean enabled() {
        AvraConfig.ensureLoaded();
        return AvraConfig.downloadSpeed;
    }

    /** Feeds a progress value and returns the smoothed speed in bytes/s (0 = not known yet). Main thread. */
    public static double update(long fileId, long loaded, long total) {
        long now = SystemClock.elapsedRealtime();
        Track t = tracks.get(fileId);
        if (t == null || loaded < t.bytes) {
            if (tracks.size() > 64) cleanup(now);
            t = new Track();
            t.bytes = loaded;
            t.time = now;
            tracks.put(fileId, t);
        } else {
            long dt = now - t.time;
            if (dt >= MIN_SAMPLE_MS) {
                double inst = (loaded - t.bytes) * 1000.0 / dt;
                t.speed = t.speed <= 0 ? inst : t.speed * 0.7 + inst * 0.3;
                t.bytes = loaded;
                t.time = now;
            }
        }
        t.touched = now;
        if (total > 0 && loaded >= total) {
            tracks.remove(fileId);
        }
        return t.speed;
    }

    private static void cleanup(long now) {
        Iterator<Map.Entry<Long, Track>> it = tracks.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().touched > FORGET_MS) it.remove();
        }
    }

    /** Full line: "42% · 1.2 MB / 5 MB · 850 KB/s · 0:12". */
    public static String full(long loaded, long total, double speed) {
        return percent(loaded, total) + " · " + AndroidUtilities.formatFileSize(loaded) + " / "
                + AndroidUtilities.formatFileSize(total) + tail(loaded, total, speed);
    }

    /** Short line without sizes: "42% · 850 KB/s · 0:12". */
    public static String compact(long loaded, long total, double speed) {
        return percent(loaded, total) + tail(loaded, total, speed);
    }

    public static String percent(long loaded, long total) {
        int p = total <= 0 ? 0 : (int) Math.min(100, loaded * 100 / total);
        return p + "%";
    }

    private static String tail(long loaded, long total, double speed) {
        if (speed < 1) return "";
        String s = " · " + LocaleController.formatString(R.string.AvraDownloadSpeedPerSec, AndroidUtilities.formatFileSize((long) speed));
        long left = (long) Math.ceil(Math.max(0, total - loaded) / speed);
        if (left > 0 && left < 100 * 3600) s += " · " + eta(left);
        return s;
    }

    private static String eta(long sec) {
        long h = sec / 3600, m = sec % 3600 / 60, s = sec % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, s) : String.format(Locale.US, "%d:%02d", m, s);
    }
}
