package com.avragramm.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import com.avragramm.AvraDesign;
import com.avragramm.games.AvraGameView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;

/**
 * Shared look of the "Fun" section (games, calculator): buttons and panels take their colours from
 * the active theme, their corners from the active layout's button shape, their font from the app
 * font, and every animation follows the animation tab (speed, "no animation").
 * {@link #restyle} re-applies colours after a theme switch without recreating anything.
 */
public final class AvraFunUi {

    public static final int PRIMARY = 0, SECONDARY = 1, DANGER = 2, CARD = 3;

    private AvraFunUi() {}

    /** Duration scaled by the animation speed of the active style; 0 when animations are off. */
    public static long duration(long baseMs) {
        if (AvraDesign.transition() == AvraDesign.TR_NONE) return 0;
        return Math.max(1, baseMs * AvraDesign.transitionSpeed() / 100);
    }

    public static float radius() {
        return AndroidUtilities.dp(AvraDesign.buttonRadius(10));
    }

    public static int bgColor() {
        return Theme.getColor(Theme.key_windowBackgroundWhite);
    }

    public static int cardColor() {
        int bg = bgColor();
        int card = Theme.getColor(Theme.key_windowBackgroundGray);
        if (ColorUtils.calculateContrast(card | 0xFF000000, bg | 0xFF000000) < 1.08) {
            card = ColorUtils.blendARGB(bg, Theme.isCurrentThemeDark() ? 0xFFFFFFFF : 0xFF000000, 0.07f);
        }
        return card;
    }

    public static int textColor() {
        return Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
    }

    public static int grayColor() {
        return Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
    }

    public static int accentColor() {
        return Theme.getColor(Theme.key_featuredStickers_addButton);
    }

    public static int accentTextColor() {
        return Theme.getColor(Theme.key_featuredStickers_buttonText);
    }

    public static GradientDrawable rounded(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    /** A themed button; {@code kind} is PRIMARY / SECONDARY / DANGER / CARD. */
    @SuppressLint("ClickableViewAccessibility")
    public static TextView button(Context context, CharSequence text, int kind) {
        TextView b = new TextView(context);
        b.setText(text);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        b.setSingleLine(true);
        b.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        b.setTag(kind);
        restyle(b);
        b.setOnTouchListener((v, e) -> {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(duration(90)).start();
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(duration(140)).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
            }
            return false;
        });
        return b;
    }

    /** Re-applies theme colours, radius and font to a view made by {@link #button}. */
    public static void restyle(TextView b) {
        Object tag = b.getTag();
        int kind = tag instanceof Integer ? (Integer) tag : PRIMARY;
        int bg, fg;
        switch (kind) {
            case SECONDARY:
                bg = cardColor();
                fg = textColor();
                break;
            case DANGER:
                bg = ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_text_RedBold), 36);
                fg = Theme.getColor(Theme.key_text_RedBold);
                break;
            case CARD:
                bg = cardColor();
                fg = grayColor();
                break;
            default:
                bg = accentColor();
                fg = accentTextColor();
        }
        b.setBackground(Theme.createSimpleSelectorRoundRectDrawable((int) radius(), bg, ColorUtils.blendARGB(bg, fg, 0.15f)));
        b.setTextColor(fg);
        b.setTypeface(AndroidUtilities.bold());
    }

    /** Themed body text. */
    public static TextView text(Context context, float sizeDp, boolean bold, boolean gray) {
        TextView t = new TextView(context);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(bold ? AndroidUtilities.bold() : AvraGameView.regularFont());
        t.setTextColor(gray ? grayColor() : textColor());
        t.setTag(gray ? "gray" : "text");
        return t;
    }

    public static void restyleText(TextView t) {
        t.setTextColor("gray".equals(t.getTag()) ? grayColor() : textColor());
    }

    /** Shows / hides a panel with the fade+scale of the active animation settings. */
    public static void show(View v, boolean show) {
        v.animate().cancel();
        long d = duration(show ? 220 : 160);
        if (show) {
            if (v.getVisibility() != View.VISIBLE) {
                v.setAlpha(0f);
                v.setScaleX(0.94f);
                v.setScaleY(0.94f);
                v.setVisibility(View.VISIBLE);
            }
            v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(d).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).withEndAction(null).start();
        } else {
            if (v.getVisibility() != View.VISIBLE) return;
            v.animate().alpha(0f).scaleX(0.96f).scaleY(0.96f).setDuration(d).setInterpolator(CubicBezierInterpolator.EASE_OUT)
                    .withEndAction(() -> v.setVisibility(View.GONE)).start();
        }
    }
}
