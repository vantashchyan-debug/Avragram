package com.avragramm.games;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import com.avragramm.AvraDesign;
import com.avragramm.AvraFonts;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * Base of every Avragram mini-game.
 *
 * Look: there are no game-specific colours or fonts. Every frame reads the active theme
 * (Theme.getColor — this already includes the Avragram override layer, palettes and auto-night),
 * the active app font (AvraFonts / AndroidUtilities.bold) and the corner radius of the active
 * layout (AvraDesign.buttonRadius). Switching theme or font while a game is open therefore
 * repaints it on the next frame, without restarting the round.
 *
 * Loop: while a round is running and not paused, onDraw advances the game by the real elapsed
 * time (clamped to 50 ms) and requests the next frame with postInvalidateOnAnimation, so it runs
 * at the display rate (60/90/120 Hz) and stops by itself when the view is paused or detached —
 * there is no thread, timer or handler that could leak the Activity.
 */
public abstract class AvraGameView extends View {

    public interface Listener {
        void onScore(int score);

        void onGameOver(int score);

        /** Optional milestone (2048 reached). */
        default void onWin(int score) {
        }
    }

    public static final int UP = 0, RIGHT = 1, DOWN = 2, LEFT = 3;

    protected final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    protected final RectF rect = new RectF();

    protected Listener listener;
    protected int difficulty;
    protected int score;
    protected boolean running, paused, over;
    private long lastFrameNs;

    // colours of the current frame (refreshed from the theme in onDraw)
    protected int cBg, cCard, cText, cGray, cAccent, cAccentText, cDanger, cDivider;

    private float downX, downY;
    private long downTime;
    private boolean swiped;

    protected AvraGameView(Context context) {
        super(context);
        stroke.setStyle(Paint.Style.STROKE);
        text.setTextAlign(Paint.Align.CENTER);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public int getScore() {
        return score;
    }

    public boolean isRunning() {
        return running && !over;
    }

    public boolean isPaused() {
        return paused;
    }

    /** New round at the given difficulty. */
    public void start(int difficulty) {
        this.difficulty = difficulty;
        score = 0;
        over = false;
        paused = false;
        running = true;
        lastFrameNs = 0;
        reset();
        if (listener != null) listener.onScore(0);
        invalidate();
    }

    public void pause() {
        if (running && !over) paused = true;
        invalidate();
    }

    public void resume() {
        if (!running || over) return;
        paused = false;
        lastFrameNs = 0;
        invalidate();
    }

    /** Stops the round without a result (exit). */
    public void stop() {
        running = false;
        paused = false;
        invalidate();
    }

    protected void addScore(int n) {
        if (n == 0) return;
        score += n;
        if (listener != null) listener.onScore(score);
    }

    protected void gameOver() {
        if (over) return;
        over = true;
        running = false;
        final int s = score;
        post(() -> {
            if (listener != null) listener.onGameOver(s);
        });
        invalidate();
    }

    // ---- to implement ----------------------------------------------------------------------

    /** Sets up a fresh round (sizes may still be 0 here; use {@link #onSizeChanged} for layout). */
    protected abstract void reset();

    /** Advances the game by dt seconds. */
    protected abstract void step(float dt);

    /** Draws the board. Called every frame, also when paused or over. */
    protected abstract void render(Canvas canvas);

    /** Event-driven games (2048, block puzzle) only need frames while animating. */
    protected boolean needsFrames() {
        return true;
    }

    protected void onSwipe(int direction) {
    }

    protected void onTap(float x, float y) {
    }

    // ---- frame ------------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        refreshStyle();
        canvas.drawColor(cBg);
        boolean active = running && !paused && !over;
        if (active) {
            long now = System.nanoTime();
            float dt = lastFrameNs == 0 ? 0f : Math.min(0.05f, (now - lastFrameNs) / 1e9f);
            lastFrameNs = now;
            if (dt > 0) step(dt);
        } else {
            lastFrameNs = 0;
        }
        render(canvas);
        if (running && !paused && !over && needsFrames()) {
            postInvalidateOnAnimation();
        } else {
            lastFrameNs = 0;
        }
    }

    /** Wakes the loop of an event-driven game (e.g. after a move starts an animation). */
    protected void kick() {
        lastFrameNs = 0;
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        paused = running && !over;
        lastFrameNs = 0;
    }

    // ---- theme & font -----------------------------------------------------------------------

    private void refreshStyle() {
        cBg = Theme.getColor(Theme.key_windowBackgroundWhite);
        cCard = Theme.getColor(Theme.key_windowBackgroundGray);
        if (ColorUtils.calculateContrast(withAlpha(cCard, 255), withAlpha(cBg, 255)) < 1.08) {
            // some palettes use the same colour for both: derive a visible card colour
            cCard = ColorUtils.blendARGB(cBg, Theme.isCurrentThemeDark() ? 0xFFFFFFFF : 0xFF000000, 0.07f);
        }
        cText = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        cGray = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
        cAccent = Theme.getColor(Theme.key_featuredStickers_addButton);
        cAccentText = Theme.getColor(Theme.key_featuredStickers_buttonText);
        cDanger = Theme.getColor(Theme.key_text_RedBold);
        cDivider = ColorUtils.blendARGB(cCard, cText, 0.12f);
        Typeface bold = AndroidUtilities.bold();
        text.setTypeface(bold);
    }

    protected static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /** A theme-derived shade between the card colour and the accent (t = 0..1). */
    protected int shade(float t) {
        return ColorUtils.blendARGB(cCard, cAccent, Math.max(0f, Math.min(1f, t)));
    }

    /** Readable text colour on a background. */
    protected int onColor(int bg) {
        return ColorUtils.calculateContrast(cText | 0xFF000000, bg | 0xFF000000) >= 3 ? cText
                : ColorUtils.calculateContrast(cAccentText | 0xFF000000, bg | 0xFF000000) >= 3 ? cAccentText
                : (ColorUtils.calculateLuminance(bg | 0xFF000000) > 0.5 ? 0xFF000000 : 0xFFFFFFFF);
    }

    /** Regular app font (the Avragram font if one is chosen). */
    public static Typeface regularFont() {
        Typeface t = AvraFonts.isCustom() ? AvraFonts.regular() : null;
        return t != null ? t : Typeface.DEFAULT;
    }

    /** Corner radius for a cell of the given size, following the active layout's button shape. */
    protected float cellRadius(float cell) {
        float r = AndroidUtilities.dp(AvraDesign.buttonRadius(8)) * 0.5f;
        return Math.min(r, cell * 0.32f);
    }

    protected void roundRect(Canvas c, float l, float t, float r, float b, float radius, int color) {
        fill.setColor(color);
        rect.set(l, t, r, b);
        c.drawRoundRect(rect, radius, radius, fill);
    }

    protected void centerText(Canvas c, String s, float cx, float cy, float size, int color) {
        text.setTextSize(size);
        text.setColor(color);
        Paint.FontMetrics fm = text.getFontMetrics();
        c.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2f, text);
    }

    // ---- input ------------------------------------------------------------------------------

    /**
     * The game owns every touch that starts on it: without this, a horizontal drag (moving a piece,
     * steering) is taken by the parent — e.g. the swipe-back gesture of the screen — and the game
     * only gets ACTION_CANCEL, so pieces snap back and taps near the left edge do nothing.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN && getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        return super.dispatchTouchEvent(e);
    }

    /** Default input: swipe (4 directions) and tap. Games with dragging override onTouchEvent. */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                downTime = SystemClock.uptimeMillis();
                swiped = false;
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (swiped || !isRunning() || paused) return true;
                float dx = e.getX() - downX, dy = e.getY() - downY;
                float th = AndroidUtilities.dp(28);
                if (Math.abs(dx) > th || Math.abs(dy) > th) {
                    swiped = true;
                    if (Math.abs(dx) > Math.abs(dy)) onSwipe(dx > 0 ? RIGHT : LEFT);
                    else onSwipe(dy > 0 ? DOWN : UP);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                if (!swiped && isRunning() && !paused && SystemClock.uptimeMillis() - downTime < 500) {
                    onTap(e.getX(), e.getY());
                }
                return true;
            default:
                return true;
        }
    }

    protected static float dp(float v) {
        return AndroidUtilities.dpf2(v);
    }
}
