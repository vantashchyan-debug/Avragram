package com.avragramm;

import android.view.MotionEvent;
import android.widget.EditText;

import org.telegram.messenger.AndroidUtilities;

/**
 * Stylus-friendly text fields.
 *
 * Android only shows the on-screen keyboard after a "clean" tap. A stylus (especially on a tablet
 * with a broken touchscreen) jitters a few pixels while touching the glass, so TextView treats the
 * tap as a cursor drag: the cursor jumps and the keyboard never opens. Here every short tap on an
 * editable field — even with jitter — explicitly focuses it and opens the keyboard.
 */
public final class AvraInput {

    /** A press shorter than this, without a long-press selection, counts as a tap. */
    private static final long MAX_TAP_MS = 600;
    /** Generous movement allowance for stylus jitter. */
    private static final float MAX_TAP_MOVE_DP = 40;

    private AvraInput() {}

    public static void onTouch(EditText view, MotionEvent event, float downX, float downY, long downTime) {
        if (event.getActionMasked() != MotionEvent.ACTION_UP) return;
        if (!view.isEnabled() || !view.isFocusable() || !view.getShowSoftInputOnFocus()) return;
        if (view.hasSelection()) return; // a real long-press text selection
        long duration = event.getEventTime() - downTime;
        float dx = event.getX() - downX, dy = event.getY() - downY;
        float max = AndroidUtilities.dp(MAX_TAP_MOVE_DP);
        if (duration > MAX_TAP_MS || dx * dx + dy * dy > max * max) return;
        if (!view.isFocused()) view.requestFocus();
        view.postDelayed(() -> {
            if (view.isFocused() && view.isAttachedToWindow()) AndroidUtilities.showKeyboard(view);
        }, 60);
    }
}
