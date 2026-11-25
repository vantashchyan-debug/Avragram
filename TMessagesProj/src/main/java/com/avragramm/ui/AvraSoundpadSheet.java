package com.avragramm.ui;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.avragramm.AvraSoundpad;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;

import java.util.List;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

/**
 * Soundpad sheet: built-in sounds, the user's own sounds and a "+" tile to add one. Opened from the
 * call screen, the voice chat menu and Avragram settings. In a call the sound goes to the other side;
 * without a call it is previewed on the speaker.
 */
public class AvraSoundpadSheet extends BottomSheet implements AvraSoundpad.Listener {

    private static final int COLUMNS = 3;

    private final boolean dark;
    private final int bg, tileBg, tileActive, text, subText, accent;
    private final LinearLayout grid;
    private final TextView status;
    private final TextView stopButton;

    public AvraSoundpadSheet(Context context, boolean dark) {
        super(context, false);
        this.dark = dark;
        bg = dark ? 0xFF1C1C1E : Theme.getColor(Theme.key_dialogBackground);
        tileBg = dark ? 0xFF2C2C2E : Theme.getColor(Theme.key_windowBackgroundGray);
        accent = dark ? 0xFF4CA3FF : Theme.getColor(Theme.key_featuredStickers_addButton);
        tileActive = (accent & 0x00FFFFFF) | 0x55000000;
        text = dark ? Color.WHITE : Theme.getColor(Theme.key_dialogTextBlack);
        subText = dark ? 0xFF9A9AA0 : Theme.getColor(Theme.key_dialogTextGray2);
        setBackgroundColor(bg);
        fixNavigationBar(bg);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(8));

        TextView title = new TextView(context);
        title.setText(getString(R.string.AvraSoundpad));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(text);
        root.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 0, 4, 2));

        status = new TextView(context);
        status.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        status.setTextColor(subText);
        root.addView(status, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 0, 4, 12));

        ScrollView scroll = new ScrollView(context) {
            @Override
            protected void onMeasure(int w, int h) {
                int max = (int) (AndroidUtilities.displaySize.y * 0.55f);
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST));
            }
        };
        grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(grid);
        root.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // ---- volume
        TextView volLabel = new TextView(context);
        volLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        volLabel.setTextColor(text);
        volLabel.setText(formatString(R.string.AvraSoundpadVolume, AvraSoundpad.volume()));
        root.addView(volLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 12, 4, 0));
        SeekBar volume = new SeekBar(context);
        volume.setMax(190);
        volume.setProgress(AvraSoundpad.volume() - 10);
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (!user) return;
                AvraSoundpad.setVolume(p + 10);
                volLabel.setText(formatString(R.string.AvraSoundpadVolume, p + 10));
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        root.addView(volume, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40));

        stopButton = new TextView(context);
        stopButton.setText(getString(R.string.AvraSoundpadStop));
        stopButton.setGravity(Gravity.CENTER);
        stopButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        stopButton.setTypeface(AndroidUtilities.bold());
        stopButton.setTextColor(Color.WHITE);
        stopButton.setBackground(round(accent, 12));
        stopButton.setOnClickListener(v -> AvraSoundpad.stop());
        root.addView(stopButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 46, 0, 8, 0, 8));

        setCustomView(root);
        rebuild();
    }

    private static GradientDrawable round(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private void rebuild() {
        Context ctx = getContext();
        status.setText(getString(AvraSoundpad.inCall() ? R.string.AvraSoundpadInCall : R.string.AvraSoundpadNoCall));
        stopButton.setAlpha(AvraSoundpad.isPlaying() ? 1f : 0.4f);
        stopButton.setEnabled(AvraSoundpad.isPlaying());

        grid.removeAllViews();
        List<AvraSoundpad.Sound> sounds = AvraSoundpad.all();
        int total = sounds.size() + 1; // + the "add" tile
        LinearLayout row = null;
        for (int i = 0; i < total; i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 84, 0, 0, 0, 8));
            }
            View tile = i < sounds.size() ? tile(ctx, sounds.get(i)) : addTile(ctx);
            row.addView(tile, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f, i % COLUMNS == 0 ? 0 : 4, 0, i % COLUMNS == COLUMNS - 1 ? 0 : 4, 0));
        }
        // Keep the last row's tiles the same width.
        int rest = total % COLUMNS;
        if (rest != 0 && row != null) {
            for (int k = rest; k < COLUMNS; k++) {
                row.addView(new View(ctx), LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f, 4, 0, 4, 0));
            }
        }
    }

    private View tile(Context ctx, AvraSoundpad.Sound s, String emoji, String name, boolean active) {
        LinearLayout t = new LinearLayout(ctx);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(6), dp(6), dp(6), dp(6));
        t.setBackground(round(active ? tileActive : tileBg, 16));
        TextView e = new TextView(ctx);
        e.setText(emoji);
        e.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26);
        e.setGravity(Gravity.CENTER);
        t.addView(e, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        TextView n = new TextView(ctx);
        n.setText(name);
        n.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        n.setTextColor(text);
        n.setGravity(Gravity.CENTER);
        n.setMaxLines(2);
        n.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(n, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        return t;
    }

    private View tile(Context ctx, AvraSoundpad.Sound s) {
        View t = tile(ctx, s, s.emoji, s.title(), AvraSoundpad.isPlaying(s));
        t.setOnClickListener(v -> AvraSoundpad.toggle(s));
        if (s.custom) {
            t.setOnLongClickListener(v -> {
                editCustom(s);
                return true;
            });
        }
        return t;
    }

    private View addTile(Context ctx) {
        View t = tile(ctx, null, "➕", getString(R.string.AvraSoundpadAdd), false);
        t.setOnClickListener(v -> {
            Activity a = activity(ctx);
            AvraSoundpad.setImportCallback((sound, error) -> {
                if (sound == null) {
                    Toast.makeText(getContext(), getString(R.string.AvraSoundpadImportFailed), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), formatString(R.string.AvraSoundpadAdded, sound.title()), Toast.LENGTH_SHORT).show();
                }
                if (isShowing()) rebuild();
            });
            AvraSoundpad.pick(a);
        });
        return t;
    }

    /** The picker result is handled in LaunchActivity, so it starts from there whenever it exists. */
    private static Activity activity(Context ctx) {
        if (LaunchActivity.instance != null) return LaunchActivity.instance;
        while (ctx instanceof ContextWrapper) {
            if (ctx instanceof Activity) return (Activity) ctx;
            ctx = ((ContextWrapper) ctx).getBaseContext();
        }
        return null;
    }

    /** Long press on a custom sound: rename or delete. */
    private void editCustom(AvraSoundpad.Sound s) {
        Context ctx = getContext();
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(s.title());
        b.setItems(new CharSequence[]{getString(R.string.AvraSoundpadRename), getString(R.string.AvraSoundpadDelete)}, (d, which) -> {
            if (which == 0) rename(s);
            else {
                AvraSoundpad.delete(s);
                rebuild();
            }
        });
        b.setNegativeButton(getString(R.string.AvraCancel), null);
        b.show();
    }

    private void rename(AvraSoundpad.Sound s) {
        Context ctx = getContext();
        EditTextBoldCursor field = new EditTextBoldCursor(ctx);
        field.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        field.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        field.setText(s.title());
        field.setBackground(null);
        field.setLineColors(Theme.getColor(Theme.key_dialogInputField), Theme.getColor(Theme.key_dialogInputFieldActivated), Theme.getColor(Theme.key_text_RedBold));
        FrameLayout box = new FrameLayout(ctx);
        box.addView(field, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 24, 4, 24, 0));
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(getString(R.string.AvraSoundpadRename));
        b.setView(box);
        b.setPositiveButton(getString(R.string.OK), (d, w) -> {
            AvraSoundpad.rename(s, field.getText() == null ? "" : field.getText().toString());
            rebuild();
        });
        b.setNegativeButton(getString(R.string.AvraCancel), null);
        b.show();
    }

    @Override
    public void onSoundpadChanged() {
        if (isShowing()) rebuild();
    }

    @Override
    public void show() {
        super.show();
        AvraSoundpad.setListener(this);
    }

    @Override
    public void dismiss() {
        AvraSoundpad.setListener(null);
        // Sounds keep playing into the call after the sheet closes; only the import result needs the sheet.
        AvraSoundpad.setImportCallback(null);
        super.dismiss();
    }
}
