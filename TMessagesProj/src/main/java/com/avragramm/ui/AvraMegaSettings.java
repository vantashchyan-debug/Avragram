package com.avragramm.ui;

import android.content.Context;
import android.widget.ImageView;

import com.avragramm.AvraBrand;
import com.exteragram.messenger.appicons.AppIconController;
import com.exteragram.messenger.appicons.AppIconPreviewDrawable;
import com.exteragram.messenger.appicons.ui.AppIconsActivity;
import com.exteragram.messenger.plugins.PluginsController;
import com.exteragram.messenger.plugins.ui.PluginsActivity;
import com.exteragram.messenger.preferences.GeneralPreferencesActivity;
import com.exteragram.messenger.preferences.OtherPreferencesActivity;
import com.exteragram.messenger.preferences.appearance.AppearancePreferencesActivity;
import com.exteragram.messenger.preferences.chats.ChatsPreferencesActivity;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.SettingsActivity;

import java.util.ArrayList;

/**
 * The single "mega" settings tab: Avragram + client (ex-exteraGram) categories + plugins are listed
 * right inside Telegram's Settings tab, together with Telegram's own sections.
 */
public final class AvraMegaSettings {

    public static final int ID_AVRA = -101, ID_ICON = -102, ID_GENERAL = -103, ID_APPEARANCE = -104,
            ID_CHATS = -105, ID_PLUGINS = -106, ID_OTHER = -107, ID_CREATORS = -110, ID_REPORT = -111;

    private AvraMegaSettings() {}

    public static void fillTop(ArrayList<UItem> items) {
        items.add(UItem.asHeader(AvraBrand.NAME));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_AVRA, 0xFF6C6FF5, 0xFF4F52E8, R.drawable.msg_theme, "Стили и оформление", "Темы, B Glass, стекло, приватность"));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_ICON, 0xFF3FB8F0, 0xFF1C8ADB, R.drawable.msg_photos, "Иконка приложения", AppIconController.getSelectedIcon().getTitle()));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_GENERAL, 0xFF8E8E93, 0xFF6D6D72, R.drawable.msg_media, "Основные", "Клиент: поведение и функции"));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_APPEARANCE, 0xFFF5A623, 0xFFE07B12, R.drawable.msg_palette, "Внешний вид", "Аватары, шрифты, иконки, анимации"));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_CHATS, 0xFF34C759, 0xFF1FA544, R.drawable.msg_discussion, "Чаты клиента", "Сообщения, медиа, ввод"));
        if (PluginsController.isPluginEngineSupported()) {
            items.add(SettingsActivity.SettingCell.Factory.of(ID_PLUGINS, 0xFFAF52DE, 0xFF8A35C0, R.drawable.msg_plugins, "Поддержка плагинов из exteraGram", "Плагины .plugin"));
        }
        items.add(SettingsActivity.SettingCell.Factory.of(ID_OTHER, 0xFFFF6482, 0xFFE5405E, R.drawable.msg_fave, "Другое", "Резервные копии, сброс, отладка"));
        items.add(UItem.asShadow(null));
    }

    public static void fillBottom(ArrayList<UItem> items) {
        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader(AvraBrand.NAME));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_CREATORS, 0xFF6C6FF5, 0xFF4F52E8, R.drawable.msg_contacts, "Создатели", "@" + AvraBrand.AUTHORS[0] + " · @" + AvraBrand.AUTHORS[1]));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_REPORT, 0xFFFF9500, 0xFFE07B12, R.drawable.msg_report, "Сообщить о проблеме"));
    }

    /** Selected launcher icon shown in the "Иконка приложения" row, kept in sync with the icon screen. */
    public static boolean bindIcon(UItem item, ImageView iconView) {
        if (item.id != ID_ICON) {
            iconView.setScaleX(1f);
            iconView.setScaleY(1f);
            return false;
        }
        AppIconPreviewDrawable d = new AppIconPreviewDrawable(AppIconController.getSelectedIcon(), AndroidUtilities.dp(40));
        d.setOnPreviewReady(iconView::invalidate);
        iconView.clearColorFilter();
        iconView.setImageDrawable(d);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iconView.setScaleX(1.34f);
        iconView.setScaleY(1.34f);
        return true;
    }

    public static boolean onClick(BaseFragment f, int id) {
        BaseFragment next;
        switch (id) {
            case ID_AVRA: next = new AvraSettingsActivity(); break;
            case ID_ICON: next = new AppIconsActivity(); break;
            case ID_GENERAL: next = new GeneralPreferencesActivity(); break;
            case ID_APPEARANCE: next = new AppearancePreferencesActivity(); break;
            case ID_CHATS: next = new ChatsPreferencesActivity(); break;
            case ID_PLUGINS: next = new PluginsActivity(); break;
            case ID_OTHER: next = new OtherPreferencesActivity(); break;
            case ID_CREATORS: next = new AvraCreatorsActivity(); break;
            case ID_REPORT:
                AvraCreatorsActivity.showReport(f);
                return true;
            default: return false;
        }
        f.presentFragment(next);
        return true;
    }
}
