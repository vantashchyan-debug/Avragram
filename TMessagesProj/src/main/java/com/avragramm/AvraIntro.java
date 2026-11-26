package com.avragramm;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Launch screen: full screen over the app on a cold start from the launcher — the built-in
 * res/raw/avra_intro.mp4, your own video or your own photo for N seconds (Fun → Launch screen).
 * Tap skips it. Sound follows the ringer: silent / vibrate → muted.
 */
public final class AvraIntro {

    private AvraIntro() {
    }

    private static boolean shownThisProcess, fromLauncher;

    /** Built-in launch videos, mixed when "Shuffle" is on. */
    public static final int[] VIDEOS = {R.raw.avra_intro, R.raw.avra_intro2, R.raw.avra_intro3};

    /** Next built-in video: random, but not the one played last time. */
    private static int pickVideo() {
        AvraConfig.ensureLoaded();
        if (!AvraConfig.introShuffle || VIDEOS.length < 2) return VIDEOS[0];
        android.content.SharedPreferences p = ApplicationLoader.applicationContext.getSharedPreferences("avra_intro", Context.MODE_PRIVATE);
        int last = p.getInt("last", -1);
        int i = new java.util.Random().nextInt(VIDEOS.length - (last >= 0 && last < VIDEOS.length ? 1 : 0));
        if (last >= 0 && last < VIDEOS.length && i >= last) i++;
        p.edit().putInt("last", i).apply();
        return VIDEOS[i];
    }

    /**
     * LaunchActivity.handleIntent() sets the intent action to null once it is processed, so the
     * "opened from the launcher" check has to be taken at the very start of onCreate.
     */
    public static void captureLaunch(Intent intent) {
        fromLauncher = intent != null && Intent.ACTION_MAIN.equals(intent.getAction())
                && (intent.getCategories() == null || intent.hasCategory(Intent.CATEGORY_LAUNCHER));
    }

    public static boolean enabled() {
        AvraConfig.ensureLoaded();
        return AvraConfig.introVideo;
    }

    /** Called at the end of LaunchActivity.onCreate. */
    public static void maybeShow(Activity activity, boolean restored) {
        if (shownThisProcess || restored || activity == null || !enabled()) return;
        if (!fromLauncher) return;
        fromLauncher = false;
        shownThisProcess = true;
        try {
            show(activity);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    /** Settings → "Preview". */
    public static void preview(Activity activity) {
        if (activity == null) return;
        try {
            show(activity);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    // ---- own video / photo --------------------------------------------------------------------

    private static File dir() {
        File d = new File(ApplicationLoader.applicationContext.getFilesDir(), "avra_intro");
        d.mkdirs();
        return d;
    }

    public static File customVideo() {
        return new File(dir(), "custom.mp4");
    }

    public static File customPhoto() {
        return new File(dir(), "custom.jpg");
    }

    /** Copies a picked file into the app's own storage. Background thread. */
    public static boolean importFile(Uri uri, boolean photo) {
        File target = photo ? customPhoto() : customVideo();
        File tmp = new File(target.getPath() + ".tmp");
        try (InputStream in = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(tmp)) {
            if (in == null) return false;
            byte[] buf = new byte[64 * 1024];
            int n;
            long total = 0;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                total += n;
            }
            if (total == 0) return false;
        } catch (Throwable t) {
            FileLog.e(t);
            tmp.delete();
            return false;
        }
        target.delete();
        return tmp.renameTo(target);
    }

    /** What will actually be shown: own photo / video only while its file exists. */
    private static int source() {
        AvraConfig.ensureLoaded();
        if (AvraConfig.introType == AvraConfig.INTRO_PHOTO && customPhoto().length() > 0) return AvraConfig.INTRO_PHOTO;
        if (AvraConfig.introType == AvraConfig.INTRO_VIDEO && customVideo().length() > 0) return AvraConfig.INTRO_VIDEO;
        return AvraConfig.INTRO_DEFAULT;
    }

    @SuppressLint("ClickableViewAccessibility")
    private static void show(Activity activity) {
        final int source = source();
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        FrameLayout overlay = new FrameLayout(activity);
        overlay.setBackgroundColor(Color.BLACK);
        overlay.setClickable(true);

        TextureView texture = null;
        ImageView photo = null;
        if (source == AvraConfig.INTRO_PHOTO) {
            int w = Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
            Bitmap bitmap = ImageLoader.loadBitmap(customPhoto().getAbsolutePath(), null, w, w, true);
            if (bitmap == null) return;
            photo = new ImageView(activity);
            photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            photo.setImageBitmap(bitmap);
            overlay.addView(photo, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        } else {
            texture = new TextureView(activity);
            overlay.addView(texture, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }

        TextView skip = new TextView(activity);
        skip.setText(LocaleController.getString(R.string.AvraIntroSkip));
        skip.setTextColor(0xCCFFFFFF);
        skip.setTextSize(13);
        skip.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(6), AndroidUtilities.dp(14), AndroidUtilities.dp(6));
        skip.setBackground(org.telegram.ui.ActionBar.Theme.createRoundRectDrawable(AndroidUtilities.dp(16), 0x66000000));
        FrameLayout.LayoutParams lp = LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0);
        lp.bottomMargin = AndroidUtilities.dp(32) + AndroidUtilities.navigationBarHeight;
        overlay.addView(skip, lp);

        decor.addView(overlay, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final MediaPlayer[] player = new MediaPlayer[1];
        final boolean[] closed = new boolean[1];
        final Runnable close = () -> {
            if (closed[0]) return;
            closed[0] = true;
            MediaPlayer p = player[0];
            player[0] = null;
            if (p != null) {
                try {
                    p.stop();
                } catch (Throwable ignore) {
                }
                p.release();
            }
            overlay.animate().alpha(0f).setDuration(250).withEndAction(() -> {
                if (overlay.getParent() != null) ((ViewGroup) overlay.getParent()).removeView(overlay);
            }).start();
        };
        overlay.setOnClickListener(v -> close.run());
        overlay.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                close.run();
            }
        });

        if (photo != null) {
            AvraConfig.ensureLoaded();
            int sec = Math.max(1, Math.min(10, AvraConfig.introPhotoSec));
            photo.setAlpha(0f);
            photo.animate().alpha(1f).setDuration(200).start();
            AndroidUtilities.runOnUIThread(close, sec * 1000L);
            return;
        }

        // Safety net: never keep the app covered if the player stalls (moved to the clip length once it is known).
        AndroidUtilities.runOnUIThread(close, 15000);
        final TextureView textureView = texture;

        texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
                if (closed[0]) return;
                try {
                    MediaPlayer p = new MediaPlayer();
                    player[0] = p;
                    Context ctx = activity.getApplicationContext();
                    if (source == AvraConfig.INTRO_VIDEO) {
                        p.setDataSource(customVideo().getAbsolutePath());
                    } else {
                        p.setDataSource(ctx, Uri.parse("android.resource://" + ctx.getPackageName() + "/" + pickVideo()));
                    }
                    p.setSurface(new Surface(st));
                    AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
                    if (am != null && am.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) p.setVolume(0f, 0f);
                    p.setOnVideoSizeChangedListener((mp, vw, vh) -> centerCrop(textureView, vw, vh));
                    p.setOnCompletionListener(mp -> close.run());
                    p.setOnErrorListener((mp, what, extra) -> {
                        close.run();
                        return true;
                    });
                    p.setOnPreparedListener(mp -> {
                        int duration = mp.getDuration();
                        if (duration > 0) {
                            AndroidUtilities.cancelRunOnUIThread(close);
                            AndroidUtilities.runOnUIThread(close, duration + 3000L);
                        }
                        mp.start();
                    });
                    p.prepareAsync();
                } catch (Throwable t) {
                    FileLog.e(t);
                    close.run();
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {
                MediaPlayer p = player[0];
                if (p != null) centerCrop(textureView, p.getVideoWidth(), p.getVideoHeight());
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                close.run();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture st) {
            }
        });
    }

    /** Fills the screen keeping the aspect ratio (edges are cut instead of black bars). */
    private static void centerCrop(TextureView view, int vw, int vh) {
        int w = view.getWidth(), h = view.getHeight();
        if (vw <= 0 || vh <= 0 || w <= 0 || h <= 0) return;
        float scale = Math.max(w / (float) vw, h / (float) vh);
        Matrix m = new Matrix();
        m.setScale(vw * scale / w, vh * scale / h, w / 2f, h / 2f);
        view.setTransform(m);
    }
}
