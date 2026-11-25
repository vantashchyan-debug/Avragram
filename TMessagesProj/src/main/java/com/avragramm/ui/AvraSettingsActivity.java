package com.avragramm.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.View;

import com.avragramm.AvraConfig;
import com.avragramm.AvraDesign;
import com.avragramm.AvraFonts;
import com.avragramm.AvraStyles;
import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.GlassOutlineStyle;
import com.exteragram.messenger.HeaderBlurStyle;
import com.exteragram.messenger.appicons.AppIconController;
import com.exteragram.messenger.appicons.ui.AppIconsActivity;
import com.exteragram.messenger.preferences.appearance.AppearancePreferencesActivity;
import com.exteragram.messenger.preferences.appearance.components.AvatarCornersPreviewCell;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.ThemePreviewMessagesCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.ThemeActivity;

import java.util.ArrayList;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

/**
 * Avragram → Appearance: the single merged place for every look-related mechanic that used to be
 * split between Telegram's chat settings, the client's appearance screen and Avragram styles.
 * Unique options of both stay here (live message and avatar previews included); the rest of
 * Telegram's and the client's screens are linked at the bottom.
 */
public class AvraSettingsActivity extends UniversalFragment {

    private static final int ID_STYLE_BASE = 1000, ID_TELEGRAM = 11, ID_FOLLOW_SYSTEM = 10;
    private static final int ID_FONT = 20;
    private static final int ID_BUTTON_BASE = 30;
    private static final int ID_SINGLE_CORNER = 40;
    private static final int ID_DIVIDER_BASE = 50, ID_SEPARATE_HEADERS = 55;
    private static final int ID_GLASS_OFF = 60, ID_GLASS_FROSTED = 61, ID_GLASS_LIQUID = 62, ID_GLASS_OUTLINE = 63, ID_GLASS_MENU = 64, ID_HEADER_BLUR = 65;
    private static final int ID_ICON = 70, ID_MORE_TELEGRAM = 71, ID_MORE_CLIENT = 72;
    private static final int ID_SAVE_DELETED = 80, ID_SAVE_VIEW_ONCE = 81;

    private ThemePreviewMessagesCell messagesPreview;
    private AvatarCornersPreviewCell avatarPreview;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.AvraSectionAppearance);
    }

    @Override
    public View createView(Context context) {
        messagesPreview = new ThemePreviewMessagesCell(context, getParentLayout(), 0);
        avatarPreview = new AvatarCornersPreviewCell(context, this, getResourceProvider());
        return super.createView(context);
    }

    private static int[] buttonTitles() {
        return new int[]{R.string.AvraButtonsTelegram, R.string.AvraButtonsPill, R.string.AvraButtonsRounded, R.string.AvraButtonsSharp, R.string.AvraButtonsTile};
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        AvraConfig.ensureLoaded();
        final boolean followSystem = Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SYSTEM;
        final Theme.ThemeInfo active = Theme.getActiveTheme();
        final Theme.ThemeInfo day = Theme.getCurrentTheme();
        final Theme.ThemeInfo night = Theme.getCurrentNightTheme();

        // ---- style
        items.add(UItem.asHeader(getString(R.string.AvraStyle)));
        items.add(UItem.asSwitchNoIcon(ID_FOLLOW_SYSTEM, getString(R.string.AvraFollowSystem)).setChecked(followSystem));
        items.add(UItem.asShadow(getString(followSystem ? R.string.AvraFollowSystemOn : R.string.AvraFollowSystemOff)));
        items.add(UItem.asHeader(getString(followSystem ? R.string.AvraLightStyles : R.string.AvraStyle)));
        boolean telegramActive = active == null || !AvraStyles.isAvra(active.assetName);
        items.add(UItem.asRadio(ID_TELEGRAM, getString(R.string.AvraStyleTelegram), getString(R.string.AvraStyleDefault)).setChecked(telegramActive));
        for (int i = 0; i < AvraStyles.NAMES.length; i++) {
            if (followSystem && AvraStyles.DARK[i]) continue;
            Theme.ThemeInfo t = Theme.getTheme(AvraStyles.NAMES[i]);
            boolean checked = t != null && (followSystem ? t == day : t == active);
            items.add(UItem.asRadio(ID_STYLE_BASE + i, getString(AvraStyles.TITLES[i]),
                    AvraFonts.FONTS[AvraFonts.indexOf(AvraStyles.FONT[i])][1] + " · " + getString(AvraStyles.DARK[i] ? R.string.AvraDark : R.string.AvraLight)).setChecked(checked));
        }
        if (followSystem) {
            items.add(UItem.asShadow(null));
            items.add(UItem.asHeader(getString(R.string.AvraDarkStyles)));
            for (int i = 0; i < AvraStyles.NAMES.length; i++) {
                if (!AvraStyles.DARK[i]) continue;
                Theme.ThemeInfo t = Theme.getTheme(AvraStyles.NAMES[i]);
                items.add(UItem.asRadio(ID_STYLE_BASE + i, getString(AvraStyles.TITLES[i]), AvraFonts.FONTS[AvraFonts.indexOf(AvraStyles.FONT[i])][1]).setChecked(t != null && t == night));
            }
        }
        items.add(UItem.asShadow(getString(R.string.AvraStyleInfo)));

        // ---- font (merges the client's "use system fonts")
        items.add(UItem.asHeader(getString(R.string.AvraFont)));
        items.add(UItem.asButton(ID_FONT, R.drawable.msg_text_outlined, getString(R.string.AvraFont), fontName(AvraFonts.current())));
        items.add(UItem.asShadow(getString(R.string.AvraFontInfo)));

        // ---- buttons
        items.add(UItem.asHeader(getString(R.string.AvraButtons)));
        int[] bt = buttonTitles();
        for (int i = 0; i < bt.length; i++) {
            items.add(UItem.asRadio(ID_BUTTON_BASE + i, getString(bt[i])).setChecked(AvraDesign.buttonShape() == i));
        }
        items.add(UItem.asShadow(getString(R.string.AvraButtonsInfo)));

        // ---- bubbles (Telegram's bubble radius, with the live message preview)
        items.add(UItem.asHeader(getString(R.string.AvraBubbles)));
        if (messagesPreview != null) items.add(UItem.asCustom(messagesPreview));
        items.add(UItem.asIntSlideView(1, 0, org.telegram.messenger.SharedConfig.bubbleRadius, 17, v -> v + " dp", v -> {
            AvraDesign.setBubbleRadius(v);
            refreshMessagesPreview();
        }));
        items.add(UItem.asShadow(getString(R.string.AvraBubblesInfo)));

        // ---- avatars (client avatar corners, with its live preview)
        items.add(UItem.asHeader(getString(R.string.AvraAvatars)));
        if (avatarPreview != null) items.add(UItem.asCustom(avatarPreview));
        items.add(UItem.asSwitchNoIcon(ID_SINGLE_CORNER, getString(R.string.SingleCornerRadius)).setChecked(ExteraConfig.getSingleCornerRadius()));
        items.add(UItem.asShadow(getString(R.string.SingleCornerRadiusInfo)));

        // ---- lists and cards (client sections)
        items.add(UItem.asHeader(getString(R.string.AvraSections)));
        int divider = AvraDesign.dividerStyle();
        items.add(UItem.asRadio(ID_DIVIDER_BASE + AvraDesign.DIVIDER_CARDS, getString(R.string.DividerStyleSegments)).setChecked(divider == AvraDesign.DIVIDER_CARDS));
        items.add(UItem.asRadio(ID_DIVIDER_BASE + AvraDesign.DIVIDER_LINE, getString(R.string.DividerStyleLine)).setChecked(divider == AvraDesign.DIVIDER_LINE));
        items.add(UItem.asRadio(ID_DIVIDER_BASE + AvraDesign.DIVIDER_HIDDEN, getString(R.string.DividerStyleHidden)).setChecked(divider == AvraDesign.DIVIDER_HIDDEN));
        UItem slider = UItem.asIntSlideView(1, 0, (int) ExteraConfig.getSectionRadius(), 28, v -> v == 0 ? getString(R.string.BlurOff) : v + " dp", v -> {
            ExteraConfig.setSectionRadius(v);
            rebuild();
        });
        slider.text = getString(R.string.AvraSectionRadius);
        items.add(slider);
        items.add(UItem.asSwitchNoIcon(ID_SEPARATE_HEADERS, getString(R.string.SeparateHeaders)).setChecked(ExteraConfig.getSectionsSeparatedHeaders()).setEnabled(divider != AvraDesign.DIVIDER_CARDS));
        items.add(UItem.asShadow(null));

        // ---- glass (Avragram glass + the client's blur options)
        items.add(UItem.asHeader(getString(R.string.AvraGlass)));
        items.add(UItem.asRadio(ID_GLASS_LIQUID, getString(R.string.AvraGlassLiquid), "Android 13+").setChecked(AvraConfig.glassMode == AvraConfig.GLASS_LIQUID));
        items.add(UItem.asRadio(ID_GLASS_FROSTED, getString(R.string.AvraGlassFrosted)).setChecked(AvraConfig.glassMode == AvraConfig.GLASS_FROSTED));
        items.add(UItem.asRadio(ID_GLASS_OFF, getString(R.string.AvraGlassOff)).setChecked(AvraConfig.glassMode == AvraConfig.GLASS_OFF));
        String[] outlines = {getString(R.string.GlassOutlineGlare), getString(R.string.GlassOutlineSolid), getString(R.string.GlassOutlineHidden)};
        items.add(UItem.asButton(ID_GLASS_OUTLINE, getString(R.string.GlassOutlineStyle), outlines[ExteraConfig.getGlassOutlineStyle().ordinal()]));
        items.add(UItem.asSwitchNoIcon(ID_GLASS_MENU, getString(R.string.GlassMessageMenu)).setChecked(ExteraConfig.getGlassMessageMenu()));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            items.add(UItem.asButton(ID_HEADER_BLUR, getString(R.string.HeaderBlurStyle), headerBlurNames()[ExteraConfig.getHeaderBlurStyle().ordinal()]));
        }
        items.add(UItem.asShadow(getString(R.string.AvraGlassInfo)));

        // ---- icon + the rest
        items.add(UItem.asHeader(getString(R.string.AvraMore)));
        items.add(UItem.asButton(ID_ICON, R.drawable.msg_photos, getString(R.string.AvraAppIcon), AppIconController.getSelectedIcon().getTitle()));
        items.add(UItem.asButton(ID_MORE_TELEGRAM, R.drawable.msg_theme, getString(R.string.AvraMoreTelegram)));
        items.add(UItem.asButton(ID_MORE_CLIENT, R.drawable.msg_palette, getString(R.string.AvraMoreClient)));
        items.add(UItem.asShadow(null));

        // ---- privacy
        items.add(UItem.asHeader(getString(R.string.AvraPrivacy)));
        items.add(UItem.asSwitchNoIcon(ID_SAVE_DELETED, getString(R.string.AvraShowDeleted)).setChecked(AvraConfig.saveDeleted));
        items.add(UItem.asSwitchNoIcon(ID_SAVE_VIEW_ONCE, getString(R.string.AvraSaveViewOnce)).setChecked(AvraConfig.saveViewOnce));
        items.add(UItem.asShadow(getString(R.string.AvraPrivacyInfo)));
    }

    private String[] headerBlurNames() {
        return new String[]{getString(R.string.HeaderBlurDefault), getString(R.string.HeaderBlurProgressive), getString(R.string.HeaderBlurFade)};
    }

    private static String fontName(String id) {
        String n = AvraFonts.FONTS[AvraFonts.indexOf(id)][1];
        return n == null ? getString(R.string.AvraFontSystem) : n;
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        final int id = item.id;
        if (id >= ID_STYLE_BASE && id < ID_STYLE_BASE + AvraStyles.NAMES.length) {
            applyStyle(id - ID_STYLE_BASE);
        } else if (id == ID_TELEGRAM) {
            applyTelegram();
        } else if (id == ID_FOLLOW_SYSTEM) {
            Theme.selectedAutoNightType = Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SYSTEM
                    ? Theme.AUTO_NIGHT_TYPE_NONE : Theme.AUTO_NIGHT_TYPE_SYSTEM;
            Theme.saveAutoNightThemeConfig();
            Theme.checkAutoNightThemeConditions(true);
        } else if (id == ID_FONT) {
            showFontPicker();
            return;
        } else if (id >= ID_BUTTON_BASE && id < ID_BUTTON_BASE + 5) {
            AvraDesign.setButtonShape(id - ID_BUTTON_BASE);
            rebuild();
        } else if (id == ID_SINGLE_CORNER) {
            ExteraConfig.setSingleCornerRadius(!ExteraConfig.getSingleCornerRadius());
            if (avatarPreview != null) avatarPreview.invalidate();
            rebuild();
        } else if (id >= ID_DIVIDER_BASE && id <= ID_DIVIDER_BASE + 2) {
            AvraDesign.setDividerStyle(id - ID_DIVIDER_BASE);
            if (id - ID_DIVIDER_BASE == AvraDesign.DIVIDER_CARDS) ExteraConfig.setSectionsSeparatedHeaders(true);
            Theme.applyCommonTheme();
            rebuild();
        } else if (id == ID_SEPARATE_HEADERS) {
            ExteraConfig.setSectionsSeparatedHeaders(!ExteraConfig.getSectionsSeparatedHeaders());
            rebuild();
        } else if (id == ID_GLASS_OFF || id == ID_GLASS_FROSTED || id == ID_GLASS_LIQUID) {
            AvraConfig.glassMode = id == ID_GLASS_OFF ? AvraConfig.GLASS_OFF : id == ID_GLASS_FROSTED ? AvraConfig.GLASS_FROSTED : AvraConfig.GLASS_LIQUID;
            AvraConfig.save();
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewTheme, false, false);
        } else if (id == ID_GLASS_OUTLINE) {
            String[] outlines = {getString(R.string.GlassOutlineGlare), getString(R.string.GlassOutlineSolid), getString(R.string.GlassOutlineHidden)};
            pick(getString(R.string.GlassOutlineStyle), outlines, ExteraConfig.getGlassOutlineStyle().ordinal(), i -> {
                ExteraConfig.setGlassOutlineStyle(GlassOutlineStyle.values()[i]);
                rebuild();
            });
            return;
        } else if (id == ID_GLASS_MENU) {
            ExteraConfig.setGlassMessageMenu(!ExteraConfig.getGlassMessageMenu());
        } else if (id == ID_HEADER_BLUR) {
            pick(getString(R.string.HeaderBlurStyle), headerBlurNames(), ExteraConfig.getHeaderBlurStyle().ordinal(), i -> {
                ExteraConfig.setHeaderBlurStyle(HeaderBlurStyle.values()[i]);
                rebuild();
            });
            return;
        } else if (id == ID_ICON) {
            presentFragment(new AppIconsActivity());
            return;
        } else if (id == ID_MORE_TELEGRAM) {
            presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
            return;
        } else if (id == ID_MORE_CLIENT) {
            presentFragment(new AppearancePreferencesActivity());
            return;
        } else if (id == ID_SAVE_DELETED) {
            AvraConfig.saveDeleted = !AvraConfig.saveDeleted;
            AvraConfig.save();
        } else if (id == ID_SAVE_VIEW_ONCE) {
            AvraConfig.saveViewOnce = !AvraConfig.saveViewOnce;
            AvraConfig.save();
        } else {
            return;
        }
        listView.adapter.update(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null && listView.adapter != null) listView.adapter.update(false);
    }

    private interface IntCallback { void run(int i); }

    private void pick(String title, String[] options, int selected, IntCallback cb) {
        String[] items = new String[options.length];
        for (int i = 0; i < options.length; i++) items[i] = (i == selected ? "● " : "○ ") + options[i];
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
        b.setTitle(title);
        b.setItems(items, (d, w) -> {
            cb.run(w);
            listView.adapter.update(true);
        });
        b.setNegativeButton(getString(R.string.AvraCancel), null);
        showDialog(b.create());
    }

    private void showFontPicker() {
        String[] names = new String[AvraFonts.FONTS.length];
        for (int i = 0; i < names.length; i++) names[i] = fontName(AvraFonts.FONTS[i][0]);
        pick(getString(R.string.AvraFont), names, AvraFonts.indexOf(AvraFonts.current()), i -> {
            if (AvraFonts.set(AvraFonts.FONTS[i][0])) offerRestart();
        });
    }

    private void refreshMessagesPreview() {
        if (messagesPreview == null) return;
        for (ChatMessageCell c : messagesPreview.getCells()) {
            if (c.getMessageObject() != null) c.getMessageObject().resetLayout();
            c.requestLayout();
            c.invalidate();
        }
        messagesPreview.invalidate();
    }

    private void rebuild() {
        if (avatarPreview != null) avatarPreview.invalidate();
        refreshMessagesPreview();
        if (listView != null) {
            listView.invalidate();
            listView.invalidateItemDecorations();
        }
        if (getParentLayout() != null) getParentLayout().rebuildFragments(0);
    }

    private void offerRestart() {
        AndroidUtilities.clearTypefaceCache();
        rebuild();
        BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.AvraRestartNeeded), getString(R.string.AvraRestart), AvraSettingsActivity::restartApp).show();
    }

    public static void restartApp() {
        Context ctx = ApplicationLoader.applicationContext;
        Intent i = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
        if (i == null) return;
        // Let pending writes (language pack, settings) finish before the process goes away.
        org.telegram.messenger.SharedConfig.saveConfig();
        AndroidUtilities.runOnUIThread(() -> {
            Intent restart = Intent.makeRestartActivityTask(i.getComponent());
            ctx.startActivity(restart);
            AndroidUtilities.runOnUIThread(() -> android.os.Process.killProcess(android.os.Process.myPid()), 400);
        }, 600);
    }

    private void applyTelegram() {
        Theme.ThemeInfo day = Theme.getTheme("Blue");
        Theme.ThemeInfo night = Theme.getTheme("Night");
        if (night == null) night = Theme.getTheme("Dark Blue");
        if (night != null) {
            Theme.setCurrentNightTheme(night);
            Theme.saveAutoNightThemeConfig();
        }
        Theme.ThemeInfo target = Theme.isCurrentThemeDark() && Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SYSTEM ? night : day;
        if (target != null) {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, target, target == night, null, -1);
        }
        afterDesign(AvraDesign.apply(null));
    }

    private void afterDesign(boolean fontChanged) {
        AndroidUtilities.runOnUIThread(() -> {
            rebuild();
            if (getParentLayout() != null) getParentLayout().rebuildAllFragmentViews(false, false);
            if (fontChanged) offerRestart();
        }, 300);
    }

    private void applyStyle(int index) {
        Theme.ThemeInfo theme = Theme.getTheme(AvraStyles.NAMES[index]);
        if (theme == null) return;
        boolean fontChanged = AvraDesign.apply(AvraStyles.NAMES[index]);
        boolean followSystem = Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SYSTEM;
        if (followSystem && AvraStyles.DARK[index]) {
            Theme.setCurrentNightTheme(theme);
            Theme.saveAutoNightThemeConfig();
            if (Theme.isCurrentThemeNight()) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, theme, true, null, -1);
            }
        } else {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, theme, false, null, -1);
        }
        afterDesign(fontChanged);
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
