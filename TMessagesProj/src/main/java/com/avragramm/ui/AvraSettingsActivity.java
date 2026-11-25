package com.avragramm.ui;

import android.view.View;

import com.avragramm.AvraConfig;
import com.avragramm.AvraStyles;

import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/** AvraGramm settings: styles, glass, privacy features. Voice changer and spam live in the chat ⋮ menu. */
public class AvraSettingsActivity extends UniversalFragment {

    private static final int ID_STYLE_BASE = 1000;
    private static final int ID_FOLLOW_SYSTEM = 10, ID_TELEGRAM = 11;
    private static final int ID_GLASS_OFF = 20, ID_GLASS_FROSTED = 21, ID_GLASS_LIQUID = 22;
    private static final int ID_SAVE_DELETED = 30, ID_SAVE_VIEW_ONCE = 32;

    @Override
    protected CharSequence getTitle() {
        return "Стили Avragram";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        AvraConfig.ensureLoaded();
        final boolean followSystem = Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SYSTEM;
        final Theme.ThemeInfo active = Theme.getActiveTheme();
        final Theme.ThemeInfo day = Theme.getCurrentTheme();
        final Theme.ThemeInfo night = Theme.getCurrentNightTheme();

        items.add(UItem.asHeader("Оформление"));
        items.add(UItem.asSwitchNoIcon(ID_FOLLOW_SYSTEM, "Светлый / тёмный как в системе").setChecked(followSystem));
        items.add(UItem.asShadow(followSystem
                ? "Днём — выбранный светлый стиль, ночью — выбранный тёмный."
                : "Выбранный стиль применяется всегда."));

        items.add(UItem.asHeader(followSystem ? "Светлые стили (днём)" : "Стили"));
        boolean telegramActive = active == null || !AvraStyles.isAvra(active.assetName);
        items.add(UItem.asRadio(ID_TELEGRAM, "Обычный Telegram", "по умолчанию").setChecked(telegramActive));
        for (int i = 0; i < AvraStyles.NAMES.length; i++) {
            if (followSystem && AvraStyles.DARK[i]) continue;
            Theme.ThemeInfo t = Theme.getTheme(AvraStyles.NAMES[i]);
            boolean checked = followSystem ? t == day : t == active;
            items.add(UItem.asRadio(ID_STYLE_BASE + i, AvraStyles.NAMES[i], AvraStyles.DARK[i] ? "тёмный" : "светлый").setChecked(checked));
        }
        if (followSystem) {
            items.add(UItem.asShadow(null));
            items.add(UItem.asHeader("Тёмные стили (ночью)"));
            for (int i = 0; i < AvraStyles.NAMES.length; i++) {
                if (!AvraStyles.DARK[i]) continue;
                Theme.ThemeInfo t = Theme.getTheme(AvraStyles.NAMES[i]);
                items.add(UItem.asRadio(ID_STYLE_BASE + i, AvraStyles.NAMES[i]).setChecked(t == night));
            }
        }
        items.add(UItem.asShadow("Стиль меняет не только цвета, но и форму: скругление пузырей, форму аватаров, карточки или плоский список, заголовок и шрифт."));

        items.add(UItem.asHeader("Стекло"));
        items.add(UItem.asRadio(ID_GLASS_LIQUID, "Жидкое стекло", "Android 13+").setChecked(AvraConfig.glassMode == AvraConfig.GLASS_LIQUID));
        items.add(UItem.asRadio(ID_GLASS_FROSTED, "Матовое размытие").setChecked(AvraConfig.glassMode == AvraConfig.GLASS_FROSTED));
        items.add(UItem.asRadio(ID_GLASS_OFF, "Выключено").setChecked(AvraConfig.glassMode == AvraConfig.GLASS_OFF));
        items.add(UItem.asShadow("На Android 12 и ниже жидкое стекло работает как матовое размытие. Если интерфейс тормозит — выключите."));

        items.add(UItem.asHeader("Приватность"));
        items.add(UItem.asSwitchNoIcon(ID_SAVE_DELETED, "Показывать удалённые сообщения").setChecked(AvraConfig.saveDeleted));
        items.add(UItem.asSwitchNoIcon(ID_SAVE_VIEW_ONCE, "Сохранять «один просмотр», разрешить скриншоты").setChecked(AvraConfig.saveViewOnce));
        items.add(UItem.asShadow("Войсченджер и спам — в меню ⋮ внутри любого чата."));

        items.add(UItem.asShadow(com.avragramm.AvraBrand.BASED_ON + " — github.com/exteraSquad/exteraGram (GPL-3.0)."));
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
        } else if (id == ID_GLASS_OFF || id == ID_GLASS_FROSTED || id == ID_GLASS_LIQUID) {
            AvraConfig.glassMode = id == ID_GLASS_OFF ? AvraConfig.GLASS_OFF : id == ID_GLASS_FROSTED ? AvraConfig.GLASS_FROSTED : AvraConfig.GLASS_LIQUID;
            AvraConfig.save();
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewTheme, false, false);
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
        afterDesign(com.avragramm.AvraDesign.apply(null));
    }

    private void afterDesign(boolean fontChanged) {
        org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
            if (getParentLayout() != null) getParentLayout().rebuildAllFragmentViews(false, false);
        }, fontChanged ? 350 : 250);
    }

    private void applyStyle(int index) {
        Theme.ThemeInfo theme = Theme.getTheme(AvraStyles.NAMES[index]);
        if (theme == null) return;
        afterDesign(com.avragramm.AvraDesign.apply(AvraStyles.NAMES[index]));
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
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
