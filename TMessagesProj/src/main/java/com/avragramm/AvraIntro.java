package com.avragramm;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Launch video: plays res/raw/avra_intro.mp4 full screen over the app on a cold start from the
 * launcher. Tap skips it. Sound follows the ringer: silent / vibrate → muted. Can be turned off
 * in the Fun settings.
 */
public final class AvraIntro {

    private AvraIntro() {
    }

    private static boolean shownThisProcess;

    public static boolean enabled() {
        AvraConfig.ensureLoaded();
        return AvraConfig.introVideo;
    }

    /** Called at the end of LaunchActivity.onCreate. */
    public static void maybeShow(Activity activity, boolean restored) {
        if (shownThisProcess || restored || activity == null || !enabled()) return;
        Intent intent = activity.getIntent();
        if (intent == null || !Intent.ACTION_MAIN.equals(intent.getAction())) return;
        shownThisProcess = true;
        try {
            show(activity);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private static void show(Activity activity) {
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        FrameLayout overlay = new FrameLayout(activity);
        overlay.setBackgroundColor(Color.BLACK);
        overlay.setClickable(true);

        TextureView texture = new TextureView(activity);
        overlay.addView(texture, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

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
        // Safety net: never keep the app covered if the player stalls.
        AndroidUtilities.runOnUIThread(close, 15000);

        texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
                if (closed[0]) return;
                try {
                    MediaPlayer p = new MediaPlayer();
                    player[0] = p;
                    Context ctx = activity.getApplicationContext();
                    p.setDataSource(ctx, Uri.parse("android.resource://" + ctx.getPackageName() + "/" + R.raw.avra_intro));
                    p.setSurface(new Surface(st));
                    AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
                    if (am != null && am.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) p.setVolume(0f, 0f);
                    p.setOnVideoSizeChangedListener((mp, vw, vh) -> centerCrop(texture, vw, vh));
                    p.setOnCompletionListener(mp -> close.run());
                    p.setOnErrorListener((mp, what, extra) -> {
                        close.run();
                        return true;
                    });
                    p.setOnPreparedListener(MediaPlayer::start);
                    p.prepareAsync();
                } catch (Throwable t) {
                    FileLog.e(t);
                    close.run();
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {
                MediaPlayer p = player[0];
                if (p != null) centerCrop(texture, p.getVideoWidth(), p.getVideoHeight());
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
        overlay.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                close.run();
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
