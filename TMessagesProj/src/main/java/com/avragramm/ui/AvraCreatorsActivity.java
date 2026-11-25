package com.avragramm.ui;

import android.view.View;

import com.avragramm.AvraBrand;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/** «Создатели»: Avragram team, bug report, exteraGram attribution. */
public class AvraCreatorsActivity extends UniversalFragment {

    private static final int ID_AUTHOR = 100;
    private static final int ID_REPORT = 200;
    private static final int ID_EXTERA_CHANNEL = 300, ID_EXTERA_SOURCE = 301;

    @Override
    protected CharSequence getTitle() {
        return "Создатели";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader("Команда Avragram"));
        for (int i = 0; i < AvraBrand.AUTHORS.length; i++) {
            items.add(UItem.asButton(ID_AUTHOR + i, R.drawable.msg_contacts, "@" + AvraBrand.AUTHORS[i], "открыть"));
        }
        items.add(UItem.asShadow("Нажмите, чтобы открыть профиль, удерживайте — чтобы скопировать юзернейм."));

        items.add(UItem.asHeader("Поддержка"));
        items.add(UItem.asButton(ID_REPORT, R.drawable.msg_report, "Сообщить о проблеме"));
        items.add(UItem.asShadow("В описании укажите версию, тему и шаги воспроизведения. Не отправляйте переписки, номера, коды входа и ключи."));

        items.add(UItem.asHeader("Основа"));
        items.add(UItem.asButton(ID_EXTERA_CHANNEL, R.drawable.msg_channel, "Создатель exteraGram", "@" + AvraBrand.EXTERA_CHANNEL));
        items.add(UItem.asButton(ID_EXTERA_SOURCE, R.drawable.msg_link2, "Исходный код exteraGram", "GitHub"));
        items.add(UItem.asShadow(AvraBrand.BASED_ON + " (GPL-3.0) и не является официальной версией exteraGram.\n\n" + AvraBrand.version()));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;
        if (id >= ID_AUTHOR && id < ID_AUTHOR + AvraBrand.AUTHORS.length) {
            getMessagesController().openByUserName(AvraBrand.AUTHORS[id - ID_AUTHOR], this, 1);
        } else if (id == ID_REPORT) {
            showReport(this);
        } else if (id == ID_EXTERA_CHANNEL) {
            getMessagesController().openByUserName(AvraBrand.EXTERA_CHANNEL, this, 1);
        } else if (id == ID_EXTERA_SOURCE) {
            Browser.openUrl(getParentActivity(), AvraBrand.EXTERA_SOURCE);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;
        String text = null;
        if (id >= ID_AUTHOR && id < ID_AUTHOR + AvraBrand.AUTHORS.length) text = "@" + AvraBrand.AUTHORS[id - ID_AUTHOR];
        else if (id == ID_EXTERA_CHANNEL) text = "@" + AvraBrand.EXTERA_CHANNEL;
        else if (id == ID_EXTERA_SOURCE) text = AvraBrand.EXTERA_SOURCE;
        if (text == null) return false;
        copy(text);
        return true;
    }

    private void copy(String text) {
        copy(this, text);
    }

    static void copy(org.telegram.ui.ActionBar.BaseFragment f, String text) {
        AndroidUtilities.addToClipboard(text);
        BulletinFactory.of(f).createCopyBulletin("Скопировано: " + text).show();
    }

    public static void showReport(org.telegram.ui.ActionBar.BaseFragment f) {
        String template = AvraBrand.reportTemplate();
        AlertDialog.Builder b = new AlertDialog.Builder(f.getParentActivity());
        b.setTitle("Сообщить о проблеме");
        b.setMessage("Опишите проблему и отправьте её @" + AvraBrand.AUTHORS[0] + ". Шаблон с версией, устройством и темой будет скопирован — вставьте его в сообщение.\n\nНе прикладывайте переписки, номера телефонов, коды и ключи.\n\n" + template);
        b.setPositiveButton("Скопировать и написать", (d, w) -> {
            AndroidUtilities.addToClipboard(template);
            f.getMessagesController().openByUserName(AvraBrand.AUTHORS[0], f, 0);
        });
        b.setNeutralButton("Скопировать", (d, w) -> copy(f, template));
        b.setNegativeButton("Отмена", null);
        f.showDialog(b.create());
    }
}
