package com.avragramm.ui;

import android.view.View;

import com.avragramm.AvraConfig;

import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

import static org.telegram.messenger.LocaleController.getString;

/** Avragram privacy features: keep deleted messages, keep view-once media and allow screenshots. */
public class AvraPrivacyActivity extends UniversalFragment {

    private static final int ID_SAVE_DELETED = 10, ID_SAVE_VIEW_ONCE = 11;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.AvraPrivacy);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        AvraConfig.ensureLoaded();
        items.add(UItem.asHeader(getString(R.string.AvraPrivacy)));
        items.add(UItem.asSwitchNoIcon(ID_SAVE_DELETED, getString(R.string.AvraShowDeleted)).setChecked(AvraConfig.saveDeleted));
        items.add(UItem.asSwitchNoIcon(ID_SAVE_VIEW_ONCE, getString(R.string.AvraSaveViewOnce)).setChecked(AvraConfig.saveViewOnce));
        items.add(UItem.asShadow(getString(R.string.AvraPrivacyInfo)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_SAVE_DELETED) {
            AvraConfig.saveDeleted = !AvraConfig.saveDeleted;
        } else if (item.id == ID_SAVE_VIEW_ONCE) {
            AvraConfig.saveViewOnce = !AvraConfig.saveViewOnce;
        } else {
            return;
        }
        AvraConfig.save();
        listView.adapter.update(true);
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
