package com.avragramm.ui;

import android.content.Context;
import android.view.View;

import com.avragramm.AvraDesign;
import com.avragramm.AvraStyles;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.ThemeActivity;

import java.util.ArrayList;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

/**
 * Themes and styles. Three separate things:
 *
 * <ul>
 *   <li><b>Styles</b> — layout presets: buttons, bubbles, corners, lists, font. No colours.</li>
 *   <li><b>Palettes</b> — colours only.</li>
 *   <li><b>Ready-made themes</b> — a palette, a style and a font together. A row applies the whole
 *       theme; its gear opens {@link AvraThemeSetupActivity} (apply a part, pick font / palette).</li>
 * </ul>
 * The user's own changes (override layer) stay on top of all of them.
 */
public class AvraThemesActivity extends AvraDesignFragment {

    private static final int ID_NIGHT_MODE = 10, ID_LAYOUTS = 11, ID_PALETTES = 12;
    private static final int ID_TELEGRAM = 99, ID_THEME_BASE = 1000;
    private static final int PAD = 64, HEIGHT = 60;

    private AvraPresetRow telegramRow;
    private AvraPresetRow[] rows;

    private final Runnable refreshLater = () -> {
        if (listView != null && listView.adapter != null) listView.adapter.update(true);
    };

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.AvraTabThemes);
    }

    @Override
    protected AvraDesign.Field[] ownFields() {
        return new AvraDesign.Field[0];
    }

    @Override
    public View createView(Context context) {
        telegramRow = row(context, -1);
        rows = new AvraPresetRow[AvraStyles.NAMES.length];
        for (int i = 0; i < rows.length; i++) rows[i] = row(context, i);
        return super.createView(context);
    }

    private AvraPresetRow row(Context context, int style) {
        AvraPresetRow r = new AvraPresetRow(context, false, true, false);
        r.setOnClickListener(v -> {
            boolean font = AvraThemeSetupActivity.applyWhole(style);
            Theme.applyCommonTheme();
            changed(font);
            AndroidUtilities.cancelRunOnUIThread(refreshLater);
            AndroidUtilities.runOnUIThread(refreshLater, 450);
        });
        r.setOnGearClick(v -> presentFragment(new AvraThemeSetupActivity(style)));
        return r;
    }

    /** A ready-made theme is "on" when both its palette and its layout are active. */
    private static boolean isActive(int style, Theme.ThemeInfo active) {
        if (!AvraDesign.isLayout(style)) return false;
        if (style < 0) return active == null || !AvraStyles.isAvra(active.assetName);
        return active != null && AvraStyles.NAMES[style].equals(active.name);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (rows == null) return;
        Theme.ThemeInfo active = Theme.getActiveTheme();

        items.add(UItem.asButton(ID_NIGHT_MODE, R.drawable.menu_night_mode_24, getString(R.string.AvraNightMode), getString(R.string.AvraNightModeInfo)));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.AvraMixOwn)));
        items.add(UItem.asButtonWithSubtext(ID_LAYOUTS, R.drawable.msg_customize, getString(R.string.AvraLayouts),
                formatString(R.string.AvraLayoutsSub, AvraLayoutsActivity.activeTitle()), PAD, HEIGHT));
        items.add(UItem.asButtonWithSubtext(ID_PALETTES, R.drawable.msg_palette, getString(R.string.AvraPalettes),
                formatString(R.string.AvraPalettesSub, AvraPalettesActivity.activeTitle()), PAD, HEIGHT));
        items.add(UItem.asShadow(getString(R.string.AvraMixInfo)));

        items.add(UItem.asHeader(getString(R.string.AvraReadyThemes)));
        telegramRow.updateColors();
        telegramRow.setTitle(getString(R.string.AvraStyleTelegram)).setChecked(isActive(-1, active), true);
        items.add(UItem.asCustom(ID_TELEGRAM, telegramRow));
        for (int i = 0; i < rows.length; i++) {
            if (Theme.getTheme(AvraStyles.NAMES[i]) == null) continue;
            rows[i].updateColors();
            rows[i].setTitle(getString(AvraStyles.TITLES[i])).setChecked(isActive(i, active), true);
            items.add(UItem.asCustom(ID_THEME_BASE + i, rows[i]));
        }
        items.add(UItem.asShadow(getString(R.string.AvraReadyThemesInfo)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_NIGHT_MODE: presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_NIGHT)); break;
            case ID_LAYOUTS: presentFragment(new AvraLayoutsActivity()); break;
            case ID_PALETTES: presentFragment(new AvraPalettesActivity()); break;
            default: super.onClick(item, view, position, x, y);
        }
    }

    @Override
    public void onFragmentDestroy() {
        AndroidUtilities.cancelRunOnUIThread(refreshLater);
        super.onFragmentDestroy();
    }
}
