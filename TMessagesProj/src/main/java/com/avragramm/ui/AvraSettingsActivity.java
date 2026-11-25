package com.avragramm.ui;

import android.view.View;

import com.avragramm.AvraDesign;
import com.avragramm.AvraFonts;
import com.avragramm.AvraStyles;
import com.exteragram.messenger.appicons.AppIconController;
import com.exteragram.messenger.appicons.ui.AppIconsActivity;
import com.exteragram.messenger.preferences.appearance.AppearancePreferencesActivity;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.ThemeActivity;

import java.util.ArrayList;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

/**
 * Avragram → Appearance. One entry point, split into tabs so every option lives in exactly one place:
 *
 * <pre>
 *  Themes and styles      Telegram + 32 styles, day / night, night mode (Telegram's screen)
 *  Font and text          font (override), message text size (moved from Telegram), live preview
 *  Bubbles and messages   bubble radius (moved from Telegram), grouped corners (new),
 *                         tail (moved from the client's Chats), chat background (moved from Telegram)
 *  Shapes and geometry    buttons, avatars + single radius, cards / dividers / radius / headers,
 *                         centred title, floating button (moved from the client's Appearance)
 *  Glass and effects      Avragram glass + the client's blur options
 *  App icon               the client's icon screen
 *  Also in Telegram / client: what is unique there and stays there (accents, name colour, chat
 *                         list, swipes, media, stickers / chat list, folders, navigation, icon packs)
 * </pre>
 */
public class AvraSettingsActivity extends AvraDesignFragment {

    private static final int ID_THEMES = 10, ID_TEXT = 11, ID_BUBBLES = 12, ID_SHAPES = 13, ID_GLASS = 14, ID_ICON = 15, ID_ANIM = 16;
    private static final int ID_TELEGRAM = 20, ID_CLIENT = 21;
    private static final int ID_RESET_ALL = 30;
    /** Same padding / height the client uses for two-line buttons (BasePreferencesActivity). */
    private static final int PAD = 64, HEIGHT = 60;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.AvraSectionAppearance);
    }

    @Override
    protected AvraDesign.Field[] ownFields() {
        return new AvraDesign.Field[0];
    }

    private static String activeStyleName() {
        Theme.ThemeInfo t = Theme.getActiveTheme();
        int i = t == null ? -1 : AvraStyles.indexOfAsset(t.assetName);
        return i >= 0 ? getString(AvraStyles.TITLES[i]) : getString(R.string.AvraStyleTelegram);
    }

    private static String fontName() {
        String n = AvraFonts.FONTS[AvraFonts.indexOf(AvraDesign.font())][1];
        return n == null ? getString(R.string.AvraFontSystem) : n;
    }

    private static UItem tab(int id, int icon, int title, CharSequence subtitle) {
        return UItem.asButtonWithSubtext(id, icon, getString(title), subtitle, PAD, HEIGHT);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.AvraSectionAppearance)));
        items.add(tab(ID_THEMES, R.drawable.msg_theme, R.string.AvraTabThemes, activeStyleName() + " · " + getString(R.string.AvraTabThemesInfo)));
        items.add(tab(ID_TEXT, R.drawable.msg_text_outlined, R.string.AvraTabText, fontName() + " · " + getString(R.string.AvraTabTextInfo)));
        items.add(tab(ID_BUBBLES, R.drawable.msg_discussion, R.string.AvraTabBubbles, getString(R.string.AvraTabBubblesInfo)));
        items.add(tab(ID_SHAPES, R.drawable.msg_customize, R.string.AvraTabShapes, getString(R.string.AvraTabShapesInfo)));
        items.add(tab(ID_ANIM, R.drawable.msg_speed, R.string.AvraTabAnim, AvraAnimActivity.transitionTitle(AvraDesign.transition()) + " · " + getString(R.string.AvraTabAnimInfo)));
        items.add(tab(ID_GLASS, R.drawable.msg_blur_radial, R.string.AvraTabGlass, getString(R.string.AvraTabGlassInfo)));
        items.add(tab(ID_ICON, R.drawable.msg_photos, R.string.AvraAppIcon, AppIconController.getSelectedIcon().getTitle()));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.AvraTabUnique)));
        items.add(tab(ID_TELEGRAM, R.drawable.msg_discussion, R.string.AvraMoreTelegramChats, getString(R.string.AvraMoreTelegramChatsInfo)));
        items.add(tab(ID_CLIENT, R.drawable.msg_palette, R.string.AvraMoreClientTitle, getString(R.string.AvraMoreClient)));
        items.add(UItem.asShadow(null));

        items.add(UItem.asButton(ID_RESET_ALL, R.drawable.msg_reset, formatString(R.string.AvraResetAll, AvraDesign.overriddenCount())).red());
        items.add(UItem.asShadow(getString(R.string.AvraOverridesInfo)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_THEMES: presentFragment(new AvraThemesActivity()); break;
            case ID_TEXT: presentFragment(new AvraTextActivity()); break;
            case ID_BUBBLES: presentFragment(new AvraBubblesActivity()); break;
            case ID_SHAPES: presentFragment(new AvraShapesActivity()); break;
            case ID_GLASS: presentFragment(new AvraGlassActivity()); break;
            case ID_ANIM: presentFragment(new AvraAnimActivity()); break;
            case ID_ICON: presentFragment(new AppIconsActivity()); break;
            case ID_TELEGRAM: presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC)); break;
            case ID_CLIENT: presentFragment(new AppearancePreferencesActivity()); break;
            case ID_RESET_ALL: {
                if (AvraDesign.overriddenCount() == 0) {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.AvraNothingToReset)).show();
                    return;
                }
                boolean font = AvraDesign.resetAll();
                Theme.applyCommonTheme();
                BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.AvraResetDone)).show();
                changed(font);
                break;
            }
            default: super.onClick(item, view, position, x, y);
        }
    }
}
