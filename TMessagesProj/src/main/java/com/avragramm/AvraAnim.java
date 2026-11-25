package com.avragramm;

import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;

/**
 * Avragram animations: screen transitions (driven by ActionBarLayout's classic frame loop) and the
 * appearance of new messages (ChatListItemAnimator). Values come from {@link AvraDesign} (style base +
 * user override). Everything here runs on the UI thread and allocates nothing per frame.
 */
public final class AvraAnim {

    private AvraAnim() {}

    /** Normal duration (ms) of each transition style at 100 % speed. Index = TR_* constant. */
    private static final float[] TRANSITION_MS = {150, 0, 0, 280, 230, 260, 300, 420, 320, 480, 1};

    private static final Interpolator OVERSHOOT = new OvershootInterpolator(1.6f);

    // ------------------------------------------------------------------------------------ transitions

    /** True when Avragram draws the transition itself (not Telegram / Android / Spring). */
    public static boolean customTransition() {
        return AvraDesign.transition() >= AvraDesign.TR_SLIDE;
    }

    /** Duration of a classic-loop transition; {@code telegramMs} is what Telegram would use. */
    public static float transitionDuration(float telegramMs) {
        int t = AvraDesign.transition();
        float base = t >= AvraDesign.TR_SLIDE ? TRANSITION_MS[t] : telegramMs;
        return Math.max(1f, base * AvraDesign.transitionSpeed() / 100f);
    }

    /**
     * Applies one frame of a custom transition.
     *
     * @param open     true when a screen opens (front = entering screen), false when it closes (front = leaving one)
     * @param front    the screen that enters (open) or leaves (close)
     * @param back     the screen underneath
     * @param progress interpolated animation progress 0..1
     */
    public static void applyTransition(View front, View back, boolean open, float progress) {
        final float e = open ? progress : 1f - progress; // how much of the front screen is "in"
        final int w = front.getWidth(), h = front.getHeight();
        reset(front);
        reset(back);
        switch (AvraDesign.transition()) {
            case AvraDesign.TR_SLIDE:
                front.setTranslationX((1f - e) * w);
                back.setTranslationX(-e * 0.3f * w);
                break;
            case AvraDesign.TR_FADE:
                front.setAlpha(e);
                break;
            case AvraDesign.TR_ZOOM:
                front.setAlpha(Math.min(1f, e * 1.4f));
                front.setScaleX(0.86f + 0.14f * e);
                front.setScaleY(0.86f + 0.14f * e);
                back.setScaleX(1f - 0.05f * e);
                back.setScaleY(1f - 0.05f * e);
                break;
            case AvraDesign.TR_SLIDE_UP:
                front.setTranslationY((1f - e) * h * 0.3f);
                front.setAlpha(Math.min(1f, e * 2f));
                break;
            case AvraDesign.TR_FLIP: {
                float cam = 9000 * AndroidUtilities.density;
                front.setCameraDistance(cam);
                back.setCameraDistance(cam);
                front.setPivotX(w / 2f);
                back.setPivotX(w / 2f);
                if (e < 0.5f) {
                    back.setRotationY(-180f * e);        // 0 → -90
                    front.setAlpha(0f);
                } else {
                    front.setRotationY(180f * (1f - e)); // 90 → 0
                    back.setAlpha(0f);
                }
                break;
            }
            case AvraDesign.TR_GLITCH: {
                float left = 1f - e;
                int step = (int) (e * 26);
                float jitter = ((step % 3) - 1) * AndroidUtilities.dp(14) * left;
                front.setTranslationX(jitter);
                back.setTranslationX(-jitter * 0.5f);
                float flicker = (step % 2 == 0) ? 1f : 0.45f;
                front.setAlpha(e >= 1f ? 1f : Math.min(1f, e * 1.6f) * flicker);
                front.setScaleX(1f + 0.04f * left * ((step % 4) == 0 ? 1 : 0));
                break;
            }
            case AvraDesign.TR_MIST:
                front.setAlpha(e * e);
                front.setScaleX(1.06f - 0.06f * e);
                front.setScaleY(1.06f - 0.06f * e);
                back.setAlpha(1f - 0.65f * e);
                break;
            case AvraDesign.TR_NONE:
            default:
                front.setAlpha(e > 0 ? 1f : 0f);
                break;
        }
    }

    /** Clears everything {@link #applyTransition} may have changed. */
    public static void reset(View v) {
        if (v == null) return;
        v.setAlpha(1f);
        v.setScaleX(1f);
        v.setScaleY(1f);
        v.setTranslationX(0f);
        v.setTranslationY(0f);
        v.setRotationY(0f);
        v.setRotation(0f);
    }

    // ------------------------------------------------------------------------------------ messages

    /** True when new messages get an Avragram entrance on top of Telegram's slide from the bottom. */
    public static boolean customMessages() {
        return AvraDesign.msgAnim() != AvraDesign.MSG_TELEGRAM;
    }

    public static long messageDuration(long telegramMs) {
        return Math.max(1L, telegramMs * AvraDesign.msgSpeed() / 100L + (AvraDesign.msgAnim() == AvraDesign.MSG_POP ? 80 : 0));
    }

    /** Start state of an entering message. {@code out} = own message (enters from the right). */
    public static void prepareMessage(View cell, boolean out) {
        switch (AvraDesign.msgAnim()) {
            case AvraDesign.MSG_FADE:
                cell.setAlpha(0f);
                break;
            case AvraDesign.MSG_POP:
                cell.setPivotY(cell.getHeight());
                cell.setScaleX(0.5f);
                cell.setScaleY(0.5f);
                cell.setAlpha(0f);
                break;
            case AvraDesign.MSG_SLIDE:
                cell.setTranslationX((out ? 1 : -1) * cell.getWidth() * 0.6f);
                cell.setAlpha(0.3f);
                break;
            case AvraDesign.MSG_SWING:
                cell.setPivotY(0f);
                cell.setRotation(out ? 9f : -9f);
                cell.setAlpha(0f);
                break;
            case AvraDesign.MSG_RISE:
                cell.setScaleX(0.92f);
                cell.setScaleY(0.92f);
                cell.setAlpha(0f);
                break;
            default:
                break;
        }
    }

    public static Interpolator messageInterpolator(Interpolator telegram) {
        int m = AvraDesign.msgAnim();
        if (m == AvraDesign.MSG_POP) return OVERSHOOT;
        if (m == AvraDesign.MSG_SWING || m == AvraDesign.MSG_SLIDE) return CubicBezierInterpolator.EASE_OUT_QUINT;
        return telegram;
    }

    /** Extra vertical offset (px) for "Rise": the message comes from a bit lower than Telegram's slide. */
    public static float extraRise() {
        return AvraDesign.msgAnim() == AvraDesign.MSG_RISE ? AndroidUtilities.dp(40) : 0f;
    }

    /** End state, also used when the animation is cancelled. */
    public static void finishMessage(View cell) {
        cell.setAlpha(1f);
        cell.setScaleX(1f);
        cell.setScaleY(1f);
        cell.setTranslationX(0f);
        cell.setRotation(0f);
        if (android.os.Build.VERSION.SDK_INT >= 28) cell.resetPivot();
    }
}
