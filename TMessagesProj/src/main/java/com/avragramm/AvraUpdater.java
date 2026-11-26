package com.avragramm;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

/**
 * Avragram updates from GitHub releases (vantashchyan-debug/Avragram): once a day it checks the latest
 * release, downloads the APK built for this phone's processor (arm64-v8a or armeabi-v7a) and offers to
 * install it. Android always asks the user to confirm the installation itself.
 */
public final class AvraUpdater {

    private AvraUpdater() {
    }

    public static final String REPO = "vantashchyan-debug/Avragram";
    private static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";
    public static final String RELEASES_URL = "https://github.com/" + REPO + "/releases";
    private static final long DAY = 24L * 60 * 60 * 1000;

    public static final DispatchQueue queue = new DispatchQueue("avraUpdater");

    public static final class Release {
        public String version, tag, notes, assetName, url, pageUrl;
        public long size;
    }

    public interface Listener {
        /** Main thread. */
        void onState();
    }

    // ------------------------------------------------------------------ state (main thread)

    public static final int IDLE = 0, CHECKING = 1, UP_TO_DATE = 2, AVAILABLE = 3, DOWNLOADING = 4, READY = 5, ERROR = 6;

    public static int state = IDLE;
    public static Release release;
    public static int progress; // 0..100 while DOWNLOADING
    public static String error;
    private static final ArrayList<Listener> listeners = new ArrayList<>();
    private static boolean downloadCancelled;

    public static void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public static void removeListener(Listener l) {
        listeners.remove(l);
    }

    private static void setState(int s) {
        state = s;
        for (Listener l : new ArrayList<>(listeners)) l.onState();
    }

    // ------------------------------------------------------------------ settings

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("avra_updater", Activity.MODE_PRIVATE);
    }

    public static boolean autoCheck() {
        return prefs().getBoolean("auto", true);
    }

    public static void setAutoCheck(boolean v) {
        prefs().edit().putBoolean("auto", v).apply();
    }

    public static boolean autoDownload() {
        return prefs().getBoolean("autoDownload", true);
    }

    public static void setAutoDownload(boolean v) {
        prefs().edit().putBoolean("autoDownload", v).apply();
    }

    public static long lastCheck() {
        return prefs().getLong("last", 0);
    }

    // ------------------------------------------------------------------ processor

    /** "arm64-v8a" or "armeabi-v7a" (null on other processors, e.g. x86). */
    public static String abi() {
        for (String a : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(a)) return a;
        }
        for (String a : Build.SUPPORTED_ABIS) {
            if ("armeabi-v7a".equals(a)) return a;
        }
        return null;
    }

    /** Release file for this phone: Avragram-arm64.apk / Avragram-armv7.apk. */
    public static String assetName() {
        String abi = abi();
        if ("arm64-v8a".equals(abi)) return "Avragram-arm64.apk";
        if ("armeabi-v7a".equals(abi)) return "Avragram-armv7.apk";
        return null;
    }

    public static String abiTitle() {
        String abi = abi();
        if ("arm64-v8a".equals(abi)) return "arm64-v8a (64-bit)";
        if ("armeabi-v7a".equals(abi)) return "armeabi-v7a (32-bit)";
        return Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?";
    }

    // ------------------------------------------------------------------ versions

    /** Compares "1.10.1" style versions; a leading "v" is ignored. */
    public static int compare(String a, String b) {
        String[] x = clean(a).split("\\."), y = clean(b).split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? num(x[i]) : 0, q = i < y.length ? num(y[i]) : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    private static String clean(String v) {
        if (v == null) return "0";
        v = v.trim();
        if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
        return v;
    }

    private static int num(String s) {
        int n = 0;
        for (int i = 0; i < s.length() && Character.isDigit(s.charAt(i)); i++) n = n * 10 + (s.charAt(i) - '0');
        return n;
    }

    // ------------------------------------------------------------------ check

    /** Called on app start / resume: checks once a day, downloads in the background, then offers to install. */
    public static void autoCheck(Activity activity) {
        if (!autoCheck() || state == CHECKING || state == DOWNLOADING) return;
        if (state == READY && release != null && activity != null) {
            return; // already offered in this session; the settings screen still has the button
        }
        if (Math.abs(System.currentTimeMillis() - lastCheck()) < DAY) return;
        check(false, () -> {
            if (state == AVAILABLE && autoDownload()) {
                download(() -> {
                    Activity a = currentActivity();
                    if (state == READY && a != null) offerInstall(a);
                });
            } else if (state == AVAILABLE) {
                Activity a = currentActivity();
                if (a != null) offerDownload(a);
            }
        });
    }

    private static Activity currentActivity() {
        return org.telegram.ui.LaunchActivity.instance;
    }

    /** Checks the latest release. {@code done} runs on the main thread with {@link #state} set. */
    public static void check(boolean manual, Runnable done) {
        if (state == CHECKING || state == DOWNLOADING) return;
        setState(CHECKING);
        queue.postRunnable(() -> {
            Release r = null;
            String err = null;
            try {
                r = fetchLatest();
            } catch (Exception e) {
                FileLog.e(e);
                err = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            final Release fr = r;
            final String fe = err;
            AndroidUtilities.runOnUIThread(() -> {
                prefs().edit().putLong("last", System.currentTimeMillis()).apply();
                if (fr == null) {
                    error = fe;
                    setState(ERROR);
                } else {
                    release = fr;
                    boolean newer = compare(fr.version, AvraBrand.VERSION) > 0;
                    if (!newer) {
                        setState(UP_TO_DATE);
                    } else if (fr.url == null) {
                        error = LocaleController.getString(R.string.AvraUpdateNoAsset);
                        setState(ERROR);
                    } else {
                        File f = apkFile(fr);
                        setState(f.exists() && f.length() == fr.size ? READY : AVAILABLE);
                    }
                }
                if (done != null) done.run();
            });
        });
    }

    private static Release fetchLatest() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "Avragram-Updater");
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new Exception("GitHub HTTP " + code);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = c.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            JSONObject o = new JSONObject(out.toString("UTF-8"));
            Release r = new Release();
            r.tag = o.optString("tag_name");
            r.version = clean(r.tag);
            r.notes = o.optString("body", "");
            r.pageUrl = o.optString("html_url", RELEASES_URL);
            String want = assetName();
            JSONArray assets = o.optJSONArray("assets");
            if (want != null && assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject a = assets.getJSONObject(i);
                    if (want.equals(a.optString("name"))) {
                        r.assetName = want;
                        r.url = a.optString("browser_download_url");
                        r.size = a.optLong("size");
                    }
                }
            }
            return r;
        } finally {
            c.disconnect();
        }
    }

    // ------------------------------------------------------------------ download

    private static File apkFile(Release r) {
        File dir = new File(ApplicationLoader.applicationContext.getCacheDir(), "avra_update");
        return new File(dir, "Avragram-" + r.version + "-" + (r.assetName == null ? "apk" : r.assetName));
    }

    public static void download(Runnable done) {
        final Release r = release;
        if (r == null || r.url == null || state == DOWNLOADING) return;
        downloadCancelled = false;
        progress = 0;
        setState(DOWNLOADING);
        queue.postRunnable(() -> {
            File f = apkFile(r);
            String err = null;
            try {
                File dir = f.getParentFile();
                if (dir != null) {
                    File[] old = dir.listFiles();
                    if (old != null) for (File o : old) if (!o.equals(f)) o.delete();
                    dir.mkdirs();
                }
                File tmp = new File(f.getPath() + ".part");
                HttpURLConnection c = (HttpURLConnection) new URL(r.url).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent", "Avragram-Updater");
                try {
                    int code = c.getResponseCode();
                    if (code != 200) throw new Exception("HTTP " + code);
                    long total = r.size > 0 ? r.size : c.getContentLength();
                    long got = 0;
                    int lastP = -1;
                    try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
                        byte[] buf = new byte[64 * 1024];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            if (downloadCancelled) throw new InterruptedException();
                            out.write(buf, 0, n);
                            got += n;
                            int p = total > 0 ? (int) (got * 100 / total) : 0;
                            if (p != lastP) {
                                lastP = p;
                                AndroidUtilities.runOnUIThread(() -> {
                                    progress = p;
                                    if (state == DOWNLOADING) setState(DOWNLOADING);
                                });
                            }
                        }
                    }
                    if (r.size > 0 && tmp.length() != r.size) throw new Exception("size " + tmp.length() + " != " + r.size);
                    if (!tmp.renameTo(f)) throw new Exception("rename");
                } finally {
                    c.disconnect();
                    if (tmp.exists() && !f.exists()) tmp.delete();
                }
            } catch (InterruptedException e) {
                err = "";
            } catch (Exception e) {
                FileLog.e(e);
                err = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            final String fe = err;
            AndroidUtilities.runOnUIThread(() -> {
                if (fe == null) {
                    setState(READY);
                } else if (fe.isEmpty()) {
                    setState(AVAILABLE);
                } else {
                    error = fe;
                    setState(ERROR);
                }
                if (done != null) done.run();
            });
        });
    }

    public static void cancelDownload() {
        downloadCancelled = true;
    }

    // ------------------------------------------------------------------ install

    public static void install(Activity activity) {
        Release r = release;
        if (activity == null || r == null) return;
        File f = apkFile(r);
        if (!f.exists()) return;
        AndroidUtilities.openForView(f, f.getName(), "application/vnd.android.package-archive", activity, null, false);
    }

    private static CharSequence notesText(Release r) {
        String notes = r.notes == null ? "" : r.notes;
        int files = notes.indexOf("### Файлы");
        if (files > 0) notes = notes.substring(0, files);
        notes = notes.replace("**", "").replace("`", "").trim();
        if (notes.length() > 1500) notes = notes.substring(0, 1500) + "…";
        return notes;
    }

    public static void offerInstall(Activity activity) {
        Release r = release;
        if (r == null || activity == null || activity.isFinishing()) return;
        AlertDialog.Builder b = new AlertDialog.Builder(activity);
        b.setTitle(LocaleController.formatString(R.string.AvraUpdateReadyTitle, r.version));
        CharSequence notes = notesText(r);
        b.setMessage(TextUtils.isEmpty(notes) ? LocaleController.getString(R.string.AvraUpdateReadyInfo) : notes);
        b.setPositiveButton(LocaleController.getString(R.string.AvraUpdateInstall), (d, w) -> install(activity));
        b.setNegativeButton(LocaleController.getString(R.string.AvraUpdateLater), null);
        b.show();
    }

    public static void offerDownload(Activity activity) {
        Release r = release;
        if (r == null || activity == null || activity.isFinishing()) return;
        AlertDialog.Builder b = new AlertDialog.Builder(activity);
        b.setTitle(LocaleController.formatString(R.string.AvraUpdateAvailableTitle, r.version));
        CharSequence notes = notesText(r);
        if (!TextUtils.isEmpty(notes)) b.setMessage(notes);
        b.setPositiveButton(LocaleController.getString(R.string.AvraUpdateDownload), (d, w) -> download(() -> {
            Activity a = currentActivity();
            if (state == READY && a != null) offerInstall(a);
        }));
        b.setNegativeButton(LocaleController.getString(R.string.AvraUpdateLater), null);
        b.show();
    }
}
