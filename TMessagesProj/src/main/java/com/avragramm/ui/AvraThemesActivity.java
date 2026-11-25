package com.avragramm.ui;

import android.view.View;

import com.avragramm.AvraDesign;
import com.avragramm.AvraFonts;
import com.avragramm.AvraStyles;

import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.ThemeActivity;

import java.util.ArrayList;

import static org.telegram.messenger.LocaleController.getString;

/**
 * Themes and styles: plain Telegram + the 32 Avragram styles, day and night. Picking a style only
 * switches the theme — the user's geometry overrides are never touched (see {@link AvraDesign}).
 */
public class AvraThemesActivity extends AvraDesignFragment {

    private static final int ID_NIGHT_MODE = 10;
    private static final int ID_TELEGRAM_DAY = 11, ID_TELEGRAM_NIGHT = 12;
    private static final int ID_DAY_BASE = 1000, ID_NIGHT_BASE = 2000;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.AvraTabThemes);
    }

    @Override
    protected AvraDesign.Field[] ownFields() {
        return new AvraDesign.Field[0];
    }

    private static boolean autoNight() {
        return Theme.selectedAutoNightType != Theme.AUTO_NIGHT_TYPE_NONE;
    }

    private static Theme.ThemeInfo telegramNight() {
        Theme.ThemeInfo n = Theme.getTheme("Night");
        return n != null ? n : Theme.getTheme("Dark Blue");
    }

    private static String subtitle(int i) {
        String font = AvraFonts.FONTS[AvraFonts.indexOf(AvraStyles.FONT[i])][1];
        return (font == null ? getString(R.string.AvraFontSystem) : font) + " · " + getString(AvraStyles.DARK[i] ? R.string.AvraDark : R.string.AvraLight);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final boolean auto = autoNight();
        final Theme.ThemeInfo active = Theme.getActiveTheme();
        final Theme.ThemeInfo day = Theme.getCurrentTheme();
        final Theme.ThemeInfo night = Theme.getCurrentNightTheme();

        items.add(UItem.asButton(ID_NIGHT_MODE, R.drawable.menu_night_mode_24, getString(R.string.AvraNightMode), getString(R.string.AvraNightModeInfo)));
        items.add(UItem.asShadow(null));

        // With auto night: the day theme can be any style, the night theme only a dark one.
        items.add(UItem.asHeader(getString(auto ? R.string.AvraDayStyles : R.string.AvraStyle)));
        Theme.ThemeInfo dayTarget = auto ? day : active;
        boolean telegramDay = dayTarget == null || !AvraStyles.isAvra(dayTarget.assetName);
        items.add(UItem.asRadio(ID_TELEGRAM_DAY, getString(R.string.AvraStyleTelegram), getString(R.string.AvraStyleDefault)).setChecked(telegramDay));
        for (int i = 0; i < AvraStyles.NAMES.length; i++) {
            Theme.ThemeInfo t = Theme.getTheme(AvraStyles.NAMES[i]);
            if (t == null) continue;
            items.add(UItem.asRadio(ID_DAY_BASE + i, getString(AvraStyles.TITLES[i]), subtitle(i)).setChecked(t == dayTarget));
        }

        if (auto) {
            items.add(UItem.asShadow(null));
            items.add(UItem.asHeader(getString(R.string.AvraNightStyles)));
            boolean telegramNightChecked = night == null || !AvraStyles.isAvra(night.assetName);
            items.add(UItem.asRadio(ID_TELEGRAM_NIGHT, getString(R.string.AvraStyleTelegramNight)).setChecked(telegramNightChecked));
            for (int i = 0; i < AvraStyles.NAMES.length; i++) {
                if (!AvraStyles.DARK[i]) continue;
                Theme.ThemeInfo t = Theme.getTheme(AvraStyles.NAMES[i]);
                if (t == null) continue;
                items.add(UItem.asRadio(ID_NIGHT_BASE + i, getString(AvraStyles.TITLES[i]), subtitle(i)).setChecked(t == night));
            }
        }
        items.add(UItem.asShadow(getString(R.string.AvraStyleInfo)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;
        if (id == ID_NIGHT_MODE) {
            presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_NIGHT));
            return;
        }
        if (id == ID_TELEGRAM_DAY) {
            applyDay(Theme.getTheme("Blue"));
        } else if (id == ID_TELEGRAM_NIGHT) {
            applyNight(telegramNight());
        } else if (id >= ID_DAY_BASE && id < ID_DAY_BASE + AvraStyles.NAMES.length) {
            applyDay(Theme.getTheme(AvraStyles.NAMES[id - ID_DAY_BASE]));
        } else if (id >= ID_NIGHT_BASE && id < ID_NIGHT_BASE + AvraStyles.NAMES.length) {
            applyNight(Theme.getTheme(AvraStyles.NAMES[id - ID_NIGHT_BASE]));
        } else {
            super.onClick(item, view, position, x, y);
            return;
        }
        listView.adapter.update(true);
    }

    /** Day theme (or the only theme without auto night). Geometry overrides stay as they are. */
    private void applyDay(Theme.ThemeInfo theme) {
        if (theme == null) return;
        if (autoNight() && Theme.isCurrentThemeNight()) {
            // Night is on screen: remember the day theme, Telegram switches to it when day comes.
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, theme, false, null, -1);
            Theme.checkAutoNightThemeConditions(true);
            return;
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, theme, false, null, -1);
    }

    private void applyNight(Theme.ThemeInfo theme) {
        if (theme == null) return;
        Theme.setCurrentNightTheme(theme);
        Theme.saveAutoNightThemeConfig();
        if (Theme.isCurrentThemeNight()) {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, theme, true, null, -1);
        }
    }
}
