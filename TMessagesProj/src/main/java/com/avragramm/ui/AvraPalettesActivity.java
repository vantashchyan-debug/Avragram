package com.avragramm.ui;

import android.content.Context;
import android.view.View;

import com.avragramm.AvraDesign;
import com.avragramm.AvraStyles;

import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

import static org.telegram.messenger.LocaleController.getString;

/**
 * Palettes: colours only. Applying a palette switches the Telegram theme (the .attheme colours);
 * since 1.4 a theme switch never touches the layout (buttons, bubbles, corners, font), see
 * {@link AvraDesign#setLayout}. With automatic night mode there is a day and a night palette.
 */
public class AvraPalettesActivity extends AvraDesignFragment {

    /** Telegram's own palettes: theme name, title, swatch (background, incoming, outgoing), dark. */
    static final String[] TG_NAMES = {"Blue", "Day", "Arctic Blue", "Dark Blue", "Night"};
    static final int[] TG_TITLES = {R.string.ThemeClassic, R.string.ThemeDay, R.string.ThemeArcticBlue, R.string.ThemeDark, R.string.ThemeNight};
    static final int[][] TG_SWATCH = {
            {0xFFCFDEEA, 0xFFFFFFFF, 0xFFEFFEDD},
            {0xFFFFFFFF, 0xFFF1F1F4, 0xFF4FA9F6},
            {0xFFFFFFFF, 0xFFF1F1F4, 0xFF3E88F7},
            {0xFF18222D, 0xFF213040, 0xFF2B5278},
            {0xFF0F0F0F, 0xFF222222, 0xFF2F6EA5},
    };
    static final boolean[] TG_DARK = {false, false, false, true, true};

    private AvraPresetRow[] dayRows, nightRows;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.AvraPalettes);
    }

    @Override
    protected AvraDesign.Field[] ownFields() {
        return new AvraDesign.Field[0];
    }

    static boolean autoNight() {
        return Theme.selectedAutoNightType != Theme.AUTO_NIGHT_TYPE_NONE;
    }

    /** Number of palettes: Telegram's first, then one per Avragram style. */
    static int count() {
        return TG_NAMES.length + AvraStyles.NAMES.length;
    }

    static Theme.ThemeInfo theme(int p) {
        return Theme.getTheme(p < TG_NAMES.length ? TG_NAMES[p] : AvraStyles.NAMES[p - TG_NAMES.length]);
    }

    static String title(int p) {
        return getString(p < TG_NAMES.length ? TG_TITLES[p] : AvraStyles.TITLES[p - TG_NAMES.length]);
    }

    static boolean dark(int p) {
        return p < TG_NAMES.length ? TG_DARK[p] : AvraStyles.DARK[p - TG_NAMES.length];
    }

    static void swatch(AvraPresetRow row, int p) {
        if (p < TG_NAMES.length) {
            row.setSwatch(TG_SWATCH[p][0], TG_SWATCH[p][1], TG_SWATCH[p][2]);
        } else {
            int i = p - TG_NAMES.length;
            row.setSwatch(AvraStyles.PREVIEW_BG[i], AvraStyles.PREVIEW_IN[i], AvraStyles.PREVIEW_OUT[i]);
        }
    }

    /** Title of the palette on screen right now. */
    static String activeTitle() {
        Theme.ThemeInfo t = Theme.getActiveTheme();
        if (t == null) return getString(R.string.AvraStyleTelegram);
        int i = AvraStyles.indexOfAsset(t.assetName);
        if (i >= 0) return getString(AvraStyles.TITLES[i]);
        for (int p = 0; p < TG_NAMES.length; p++) if (TG_NAMES[p].equals(t.name)) return getString(TG_TITLES[p]);
        return t.getName();
    }

    // ------------------------------------------------------------------------------------ applying

    /** Applies a palette where it belongs: a dark one goes to the night slot when auto night is on. */
    static void apply(Theme.ThemeInfo theme, boolean dark) {
        if (dark && autoNight()) applyNight(theme);
        else applyDay(theme);
    }

    /** Day palette (or the only one without auto night). */
    static void applyDay(Theme.ThemeInfo theme) {
        if (theme == null) return;
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, theme, false, null, -1);
        if (autoNight() && Theme.isCurrentThemeNight()) {
            // Night is on screen: the day palette is remembered, Telegram switches back to night now.
            Theme.checkAutoNightThemeConditions(true);
        }
    }

    static void applyNight(Theme.ThemeInfo theme) {
        if (theme == null) return;
        Theme.setCurrentNightTheme(theme);
        Theme.saveAutoNightThemeConfig();
        if (Theme.isCurrentThemeNight()) {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, theme, true, null, -1);
        }
    }

    // ------------------------------------------------------------------------------------ UI

    @Override
    public View createView(Context context) {
        int n = count();
        dayRows = new AvraPresetRow[n];
        nightRows = new AvraPresetRow[n];
        for (int p = 0; p < n; p++) {
            final int fp = p;
            dayRows[p] = row(context, p, v -> {
                applyDay(theme(fp));
                refresh();
            });
            if (dark(p)) {
                nightRows[p] = row(context, p, v -> {
                    applyNight(theme(fp));
                    refresh();
                });
            }
        }
        return super.createView(context);
    }

    private static AvraPresetRow row(Context context, int p, View.OnClickListener click) {
        AvraPresetRow r = new AvraPresetRow(context, false, false, true);
        r.setOnClickListener(click);
        swatch(r, p);
        return r;
    }

    private final Runnable refreshLater = this::refreshNow;

    /** Telegram may finish the switch after an animation, so the checks are refreshed once more later. */
    private void refresh() {
        refreshNow();
        org.telegram.messenger.AndroidUtilities.cancelRunOnUIThread(refreshLater);
        org.telegram.messenger.AndroidUtilities.runOnUIThread(refreshLater, 450);
    }

    private void refreshNow() {
        if (listView != null && listView.adapter != null) listView.adapter.update(true);
    }

    @Override
    public void onFragmentDestroy() {
        org.telegram.messenger.AndroidUtilities.cancelRunOnUIThread(refreshLater);
        super.onFragmentDestroy();
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (dayRows == null) return;
        boolean auto = autoNight();
        Theme.ThemeInfo day = auto ? Theme.getCurrentTheme() : Theme.getActiveTheme();
        Theme.ThemeInfo night = Theme.getCurrentNightTheme();

        items.add(UItem.asHeader(getString(auto ? R.string.AvraDayStyles : R.string.AvraPalette)));
        addRows(items, dayRows, day, 1000);
        if (auto) {
            items.add(UItem.asShadow(null));
            items.add(UItem.asHeader(getString(R.string.AvraNightStyles)));
            addRows(items, nightRows, night, 2000);
        }
        items.add(UItem.asShadow(getString(R.string.AvraPaletteInfo)));
    }

    private void addRows(ArrayList<UItem> items, AvraPresetRow[] rows, Theme.ThemeInfo selected, int idBase) {
        for (int p = 0; p < rows.length; p++) {
            AvraPresetRow r = rows[p];
            if (r == null) continue;
            Theme.ThemeInfo t = theme(p);
            if (t == null) continue;
            r.updateColors();
            r.setTitle(title(p)).setChecked(t == selected, true);
            items.add(UItem.asCustom(idBase + p, r));
        }
    }
}
