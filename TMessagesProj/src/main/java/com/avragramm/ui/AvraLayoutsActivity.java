package com.avragramm.ui;

import android.content.Context;
import android.view.View;

import com.avragramm.AvraDesign;
import com.avragramm.AvraFonts;
import com.avragramm.AvraStyles;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

/**
 * Styles (layout presets): buttons, bubbles, avatars, lists, title, floating button and font —
 * without colours. Light and dark variants of a concept share one layout and are listed once.
 * The user's own changes stay on top of any style.
 */
public class AvraLayoutsActivity extends AvraDesignFragment {

    private static final int ID_TELEGRAM = 99, ID_BASE = 1000, ID_RESET_ALL = 30;
    static final int[] BUTTON_TITLES = {R.string.AvraButtonsTelegram, R.string.AvraButtonsPill, R.string.AvraButtonsRounded, R.string.AvraButtonsSharp, R.string.AvraButtonsTile};

    private AvraPresetRow telegramRow;
    private AvraPresetRow[] rows;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.AvraLayouts);
    }

    @Override
    protected AvraDesign.Field[] ownFields() {
        return new AvraDesign.Field[0];
    }

    /** Title of a layout (-1 = Telegram). */
    static String title(int i) {
        return getString(i < 0 ? R.string.AvraStyleTelegram : AvraStyles.TITLES[i]);
    }

    static String fontTitle(String id) {
        String n = AvraFonts.FONTS[AvraFonts.indexOf(id)][1];
        return n == null ? getString(R.string.AvraFontSystem) : n;
    }

    /** "Manrope · buttons: Pill · bubbles 17 dp · Zoom" for a layout (-1 = Telegram). */
    static String describe(int i) {
        String name = i < 0 ? "" : AvraStyles.NAMES[i];
        int button = AvraDesign.baseInt(AvraDesign.Field.BUTTON, name);
        return formatString(R.string.AvraLayoutDesc, fontTitle(AvraDesign.baseFont(name)),
                getString(BUTTON_TITLES[button]), AvraDesign.baseInt(AvraDesign.Field.BUBBLE, name),
                AvraAnimActivity.transitionTitle(AvraDesign.baseInt(AvraDesign.Field.TRANSITION, name)));
    }

    /** Title of the active layout. */
    static String activeTitle() {
        int[] l = AvraDesign.layouts();
        int active = AvraDesign.layoutIndex();
        if (active < 0) return title(-1);
        for (int i : l) if (AvraDesign.sameLayout(i, active)) return title(i);
        return title(active);
    }

    @Override
    public View createView(Context context) {
        telegramRow = row(context, -1);
        int[] l = AvraDesign.layouts();
        rows = new AvraPresetRow[l.length];
        for (int k = 0; k < l.length; k++) rows[k] = row(context, l[k]);
        return super.createView(context);
    }

    private AvraPresetRow row(Context context, int i) {
        AvraPresetRow r = new AvraPresetRow(context, true, false, false);
        r.setOnClickListener(v -> apply(i));
        return r;
    }

    private void apply(int i) {
        if (AvraDesign.isLayout(i)) return;
        boolean font = AvraDesign.setLayout(i < 0 ? "" : AvraStyles.NAMES[i]);
        Theme.applyCommonTheme();
        changed(font);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (rows == null) return;
        items.add(UItem.asHeader(getString(R.string.AvraLayouts)));
        bind(telegramRow, -1);
        items.add(UItem.asCustom(ID_TELEGRAM, telegramRow));
        int[] l = AvraDesign.layouts();
        for (int k = 0; k < l.length; k++) {
            bind(rows[k], l[k]);
            items.add(UItem.asCustom(ID_BASE + l[k], rows[k]));
        }
        items.add(UItem.asShadow(getString(R.string.AvraLayoutInfo)));

        int n = AvraDesign.overriddenCount();
        if (n > 0) {
            items.add(UItem.asButton(ID_RESET_ALL, R.drawable.msg_reset, formatString(R.string.AvraResetAll, n)).red());
            items.add(UItem.asShadow(getString(R.string.AvraOverridesInfo)));
        }
    }

    private static void bind(AvraPresetRow r, int i) {
        r.updateColors();
        r.setTitle(title(i)).setSubtitle(describe(i)).setChecked(AvraDesign.isLayout(i), true);
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_RESET_ALL) {
            boolean font = AvraDesign.resetAll();
            Theme.applyCommonTheme();
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.AvraResetDone)).show();
            changed(font);
            return;
        }
        super.onClick(item, view, position, x, y);
    }
}
