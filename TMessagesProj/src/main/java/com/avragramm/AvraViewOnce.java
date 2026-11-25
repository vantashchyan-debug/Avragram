package com.avragramm;

import android.content.Context;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.secretmedia.EncryptedFileInputStream;
import org.telegram.ui.Components.BulletinFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Copies opened view-once / self-destructing media into the gallery before it disappears.
 * Telegram caches such media encrypted ("x.ext.enc" + key "x.ext.enc.key" in the internal cache)
 * and may still be downloading it when the viewer opens, so we wait and decrypt in the background.
 */
public final class AvraViewOnce {

    private static final int POLL_MS = 400;
    private static final int MAX_WAIT_MS = 30_000;

    private AvraViewOnce() {}

    public static void save(Context context, int account, MessageObject mo) {
        AvraConfig.ensureLoaded();
        if (!AvraConfig.saveViewOnce || context == null || mo == null || mo.messageOwner == null) return;
        final Context app = context.getApplicationContext();
        final boolean video = mo.isVideo() || mo.isRoundVideo();
        final String attachPath = mo.messageOwner.attachPath;
        final File base = FileLoader.getInstance(account).getPathToMessage(mo.messageOwner);
        Utilities.globalQueue.postRunnable(() -> tryCopy(app, base, attachPath, video, 0));
    }

    private static void tryCopy(Context app, File base, String attachPath, boolean video, int waited) {
        try {
            File toSave = null;
            if (attachPath != null && !attachPath.isEmpty() && new File(attachPath).exists()) {
                toSave = new File(attachPath);
            } else if (base != null && base.exists() && base.length() > 0) {
                toSave = base;
            } else if (base != null) {
                File enc = new File(base.getAbsolutePath() + ".enc");
                File key = new File(FileLoader.getInternalCacheDir(), enc.getName() + ".key");
                if (enc.exists() && enc.length() > 0 && key.exists()) toSave = decrypt(app, enc, key, base.getName());
            }
            if (toSave == null) {
                if (waited < MAX_WAIT_MS) {
                    Utilities.globalQueue.postRunnable(() -> tryCopy(app, base, attachPath, video, waited + POLL_MS), POLL_MS);
                }
                return;
            }
            MediaController.saveFile(toSave.getAbsolutePath(), app, video ? 1 : 0, null, null, uri -> AndroidUtilities.runOnUIThread(() -> {
                try {
                    BulletinFactory.global().createSimpleBulletin(org.telegram.messenger.R.raw.ic_download, "Сохранено в галерею (Avragram)").show();
                } catch (Throwable ignore) {
                }
            }), false);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static File decrypt(Context app, File enc, File key, String name) throws Exception {
        File dir = new File(app.getCacheDir(), "avra_view_once");
        if (!dir.exists() && !dir.mkdirs()) return null;
        File out = new File(dir, System.currentTimeMillis() + "_" + name);
        try (InputStream in = new EncryptedFileInputStream(enc, key); FileOutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        }
        return out;
    }
}
