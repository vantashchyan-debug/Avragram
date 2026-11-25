package com.avragramm.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadioButton;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

/**
 * One row of a preset list: radio, title (one line, never overlapped), optional second line,
 * optional colour swatch and optional gear button. Clicks are handled by the row itself because
 * custom list items are not clickable through the adapter.
 */
@SuppressLint("ViewConstructor")
public class AvraPresetRow extends FrameLayout {

    private final RadioButton radio;
    private final TextView title;
    private final TextView subtitle;
    private final ImageView gear;
    private final boolean twoLine;
    private final boolean withSwatch;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int[] swatch;

    public AvraPresetRow(Context context, boolean twoLine, boolean withGear, boolean withSwatch) {
        super(context);
        this.twoLine = twoLine;
        this.withSwatch = withSwatch;
        setWillNotDraw(false);
        setForeground(Theme.getSelectorDrawable(false));

        radio = new RadioButton(context);
        radio.setSize(dp(20));
        addView(radio, LayoutHelper.createFrame(22, 22, Gravity.LEFT | Gravity.CENTER_VERTICAL, 20, 0, 0, 0));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        subtitle = new TextView(context);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        subtitle.setVisibility(twoLine ? VISIBLE : GONE);
        texts.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        int right = 16 + (withGear ? 44 : 0) + (withSwatch ? 44 : 0);
        addView(texts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 64, 0, right, 0));

        if (withGear) {
            gear = new ImageView(context);
            gear.setScaleType(ImageView.ScaleType.CENTER);
            gear.setImageResource(R.drawable.msg_settings);
            gear.setContentDescription(getString(R.string.AvraThemeSetup));
            addView(gear, LayoutHelper.createFrame(44, 44, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 10, 0));
        } else {
            gear = null;
        }
        updateColors();
    }

    /** Re-reads theme colours; cheap, called on every list fill so palette switches repaint the rows. */
    public void updateColors() {
        radio.setColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_radioBackgroundChecked));
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        if (gear != null) {
            gear.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon));
            gear.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 1));
        }
        invalidate();
    }

    public AvraPresetRow setTitle(CharSequence text) {
        if (!TextUtils.equals(title.getText(), text)) title.setText(text);
        return this;
    }

    public AvraPresetRow setSubtitle(CharSequence text) {
        if (!TextUtils.equals(subtitle.getText(), text)) subtitle.setText(text);
        return this;
    }

    /** Background, incoming bubble, outgoing bubble. */
    public AvraPresetRow setSwatch(int bg, int in, int out) {
        swatch = new int[]{bg, in, out};
        invalidate();
        return this;
    }

    public AvraPresetRow setChecked(boolean checked, boolean animated) {
        radio.setChecked(checked, animated && radio.isChecked() != checked && isAttachedToWindow());
        return this;
    }

    public AvraPresetRow setOnGearClick(OnClickListener l) {
        if (gear != null) gear.setOnClickListener(l);
        return this;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(twoLine ? 64 : 52), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!withSwatch || swatch == null) return;
        // Three overlapping dots: chat background, incoming and outgoing bubble.
        float r = dp(8), cy = getHeight() / 2f;
        float x = getWidth() - dp(16 + (gear != null ? 54 : 0)) - r;
        float ring = dp(1.5f);
        int window = Theme.getColor(Theme.key_windowBackgroundWhite);
        for (int k = 0; k < 3; k++) {
            float cx = x - (2 - k) * dp(11);
            paint.setColor(window);
            canvas.drawCircle(cx, cy, r + ring, paint);
            paint.setColor(swatch[k]);
            canvas.drawCircle(cx, cy, r, paint);
        }
    }
}
