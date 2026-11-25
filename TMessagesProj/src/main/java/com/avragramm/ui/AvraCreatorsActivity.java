package com.avragramm.ui;

import android.view.View;

import com.avragramm.AvraBrand;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

/** Creators: the Avragram team and a privacy-safe bug report. */
public class AvraCreatorsActivity extends UniversalFragment {

    private static final int ID_AUTHOR = 100;
    private static final int ID_REPORT = 200;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.AvraCreators);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.AvraTeam)));
        for (int i = 0; i < AvraBrand.AUTHORS.length; i++) {
            items.add(UItem.asButton(ID_AUTHOR + i, R.drawable.msg_contacts, "@" + AvraBrand.AUTHORS[i], getString(R.string.AvraOpen)));
        }
        items.add(UItem.asShadow(getString(R.string.AvraCreatorsInfo)));

        items.add(UItem.asHeader(getString(R.string.AvraSupport)));
        items.add(UItem.asButton(ID_REPORT, R.drawable.msg_report, getString(R.string.AvraReport)));
        items.add(UItem.asShadow(getString(R.string.AvraReportInfo) + "\n\n" + AvraBrand.version()));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;
        if (id >= ID_AUTHOR && id < ID_AUTHOR + AvraBrand.AUTHORS.length) {
            getMessagesController().openByUserName(AvraBrand.AUTHORS[id - ID_AUTHOR], this, 1);
        } else if (id == ID_REPORT) {
            showReport(this);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;
        if (id >= ID_AUTHOR && id < ID_AUTHOR + AvraBrand.AUTHORS.length) {
            copy(this, "@" + AvraBrand.AUTHORS[id - ID_AUTHOR]);
            return true;
        }
        return false;
    }

    static void copy(BaseFragment f, String text) {
        AndroidUtilities.addToClipboard(text);
        BulletinFactory.of(f).createCopyBulletin(formatString(R.string.AvraCopied, text)).show();
    }

    public static void showReport(BaseFragment f) {
        String template = AvraBrand.reportTemplate();
        AlertDialog.Builder b = new AlertDialog.Builder(f.getParentActivity());
        b.setTitle(getString(R.string.AvraReport));
        b.setMessage(formatString(R.string.AvraReportMessage, AvraBrand.AUTHORS[0], template));
        b.setPositiveButton(getString(R.string.AvraReportCopyWrite), (d, w) -> {
            AndroidUtilities.addToClipboard(template);
            f.getMessagesController().openByUserName(AvraBrand.AUTHORS[0], f, 0);
        });
        b.setNeutralButton(getString(R.string.AvraCopy), (d, w) -> copy(f, template));
        b.setNegativeButton(getString(R.string.AvraCancel), null);
        f.showDialog(b.create());
    }
}
