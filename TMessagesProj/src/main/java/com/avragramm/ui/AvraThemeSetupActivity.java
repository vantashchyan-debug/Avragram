package com.avragramm.ui;

import android.view.View;

import com.avragramm.AvraDesign;
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
 * The gear of a ready-made theme: apply it whole or only a part (palette / style), see what it is made
 * of and jump to the screen where each part can be chosen separately.
 */
public class AvraThemeSetupActivity extends AvraDesignFragment {

    private static final int ID_WHOLE = 10, ID_PALETTE = 11, ID_LAYOUT = 12;
    private static final int ID_FONT = 20, ID_PALETTES = 21, ID_BUTTONS = 22, ID_BUBBLES = 23, ID_AVATARS = 24, ID_LISTS = 25, ID_ANIM = 26;
    private static final int PAD = 64, HEIGHT = 60;

    /** Index in {@link AvraStyles}, -1 = Telegram. */
    private final int style;

    public AvraThemeSetupActivity(int style) {
        this.style = style >= 0 && style < AvraStyles.NAMES.length ? style : -1;
    }

    @Override
    protected CharSequence getTitle() {
        return AvraLayoutsActivity.title(style);
    }

    @Override
    protected AvraDesign.Field[] ownFields() {
        return new AvraDesign.Field[0];
    }

    private String name() {
        return style < 0 ? "" : AvraStyles.NAMES[style];
    }

    /** The palette (Telegram theme) of this ready-made theme. */
    static Theme.ThemeInfo palette(int style) {
        return style < 0 ? Theme.getTheme("Blue") : Theme.getTheme(AvraStyles.NAMES[style]);
    }

    static boolean paletteDark(int style) {
        return style >= 0 && AvraStyles.DARK[style];
    }

    /** Whole theme: layout first (no extra sync on the theme switch), then the palette. Returns font change. */
    static boolean applyWhole(int style) {
        boolean font = AvraDesign.setLayout(style < 0 ? "" : AvraStyles.NAMES[style]);
        AvraPalettesActivity.apply(palette(style), paletteDark(style));
        return font;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        String n = name();
        items.add(UItem.asHeader(getString(R.string.AvraThemeApply)));
        items.add(UItem.asButtonWithSubtext(ID_WHOLE, R.drawable.msg_theme, getString(R.string.AvraApplyWhole), getString(R.string.AvraApplyWholeInfo), PAD, HEIGHT));
        items.add(UItem.asButtonWithSubtext(ID_PALETTE, R.drawable.msg_palette, getString(R.string.AvraApplyPalette), getString(R.string.AvraApplyPaletteInfo), PAD, HEIGHT));
        items.add(UItem.asButtonWithSubtext(ID_LAYOUT, R.drawable.msg_customize, getString(R.string.AvraApplyLayout), getString(R.string.AvraApplyLayoutInfo), PAD, HEIGHT));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.AvraThemeParts)));
        items.add(UItem.asButton(ID_FONT, R.drawable.msg_text_outlined, getString(R.string.AvraFont), AvraLayoutsActivity.fontTitle(AvraDesign.baseFont(n))));
        items.add(UItem.asButton(ID_PALETTES, R.drawable.msg_colors, getString(R.string.AvraPalette),
                getString(style < 0 ? R.string.ThemeClassic : AvraStyles.TITLES[style])));
        items.add(UItem.asButton(ID_BUTTONS, R.drawable.msg_customize, getString(R.string.AvraButtons),
                getString(AvraLayoutsActivity.BUTTON_TITLES[AvraDesign.baseInt(AvraDesign.Field.BUTTON, n)])));
        items.add(UItem.asButton(ID_BUBBLES, R.drawable.msg_discussion, getString(R.string.AvraBubbleRadius),
                AvraDesign.baseInt(AvraDesign.Field.BUBBLE, n) + " dp"));
        items.add(UItem.asButton(ID_AVATARS, R.drawable.msg_contacts, getString(R.string.AvraAvatars),
                AvraDesign.baseInt(AvraDesign.Field.AVATAR, n) + " dp"));
        int divider = AvraDesign.baseInt(AvraDesign.Field.DIVIDER, n);
        int dividerTitle = divider == AvraDesign.DIVIDER_CARDS ? R.string.DividerStyleSegments
                : divider == AvraDesign.DIVIDER_HIDDEN ? R.string.DividerStyleHidden : R.string.DividerStyleLine;
        items.add(UItem.asButton(ID_LISTS, R.drawable.msg_list, getString(R.string.AvraSections), getString(dividerTitle)));
        items.add(UItem.asButton(ID_ANIM, R.drawable.msg_speed, getString(R.string.AvraTabAnim),
                AvraAnimActivity.transitionTitle(AvraDesign.baseInt(AvraDesign.Field.TRANSITION, n))));
        items.add(UItem.asShadow(getString(R.string.AvraThemePartsInfo)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_WHOLE: {
                boolean font = applyWhole(style);
                Theme.applyCommonTheme();
                done(font);
                break;
            }
            case ID_PALETTE:
                AvraPalettesActivity.apply(palette(style), paletteDark(style));
                done(false);
                break;
            case ID_LAYOUT: {
                boolean font = AvraDesign.setLayout(name());
                Theme.applyCommonTheme();
                done(font);
                break;
            }
            case ID_FONT: presentFragment(new AvraTextActivity()); break;
            case ID_PALETTES: presentFragment(new AvraPalettesActivity()); break;
            case ID_BUBBLES: presentFragment(new AvraBubblesActivity()); break;
            case ID_ANIM: presentFragment(new AvraAnimActivity()); break;
            case ID_BUTTONS:
            case ID_AVATARS:
            case ID_LISTS: presentFragment(new AvraShapesActivity()); break;
            default: super.onClick(item, view, position, x, y);
        }
    }

    private void done(boolean font) {
        BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, formatString(R.string.AvraThemeApplied, getTitle())).show();
        changed(font);
    }
}
