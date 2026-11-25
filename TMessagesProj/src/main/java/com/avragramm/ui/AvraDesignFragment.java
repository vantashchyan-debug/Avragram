package com.avragramm.ui;

import android.content.Context;
import android.content.Intent;
import android.view.View;

import com.avragramm.AvraDesign;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalFragment;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

/**
 * Common base of the Avragram → Appearance tabs.
 *
 * <ul>
 *   <li>marks values that come from the user's override layer ("custom"),</li>
 *   <li>long-press on such an item returns the theme's value ({@link #fieldFor}),</li>
 *   <li>a per-tab "Reset to theme (n)" button,</li>
 *   <li>other screens are rebuilt once, debounced, instead of on every slider tick.</li>
 * </ul>
 */
public abstract class AvraDesignFragment extends UniversalFragment {

    protected static final int ID_RESET_SECTION = 9000;

    private final Runnable rebuildOthers = () -> {
        if (getParentLayout() != null) {
            Theme.applyCommonTheme();
            getParentLayout().rebuildFragments(0);
        }
    };

    /** The design fields this tab owns (for the reset button). Empty if the tab has none. */
    protected abstract AvraDesign.Field[] ownFields();

    /** Field edited by the item with this id, or null. */
    protected AvraDesign.Field fieldFor(int itemId) {
        return null;
    }

    /** " · custom" when the value is an override, "" otherwise. */
    protected static String mark(AvraDesign.Field f) {
        return AvraDesign.isOverridden(f) ? " · " + getString(R.string.AvraCustomValue) : "";
    }

    protected void addResetSection(java.util.ArrayList<UItem> items) {
        AvraDesign.Field[] fields = ownFields();
        if (fields.length == 0) return;
        int n = AvraDesign.overriddenCount(fields);
        items.add(UItem.asButton(ID_RESET_SECTION, R.drawable.msg_reset, formatString(R.string.AvraResetSection, n)).red());
        items.add(UItem.asShadow(getString(R.string.AvraOverridesInfo)));
    }

    /** Applies the result of a design change: font restart prompt, list refresh, debounced rebuild. */
    protected void changed(boolean fontChanged) {
        if (listView != null && listView.adapter != null) listView.adapter.update(true);
        scheduleRebuild();
        if (fontChanged) offerRestart();
    }

    protected void scheduleRebuild() {
        AndroidUtilities.cancelRunOnUIThread(rebuildOthers);
        AndroidUtilities.runOnUIThread(rebuildOthers, 350);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (AvraDesign.fontChangedByTheme) {
            AvraDesign.fontChangedByTheme = false;
            offerRestart();
        }
        if (listView != null && listView.adapter != null) listView.adapter.update(false);
    }

    @Override
    public void onFragmentDestroy() {
        AndroidUtilities.cancelRunOnUIThread(rebuildOthers);
        super.onFragmentDestroy();
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_RESET_SECTION) {
            if (AvraDesign.overriddenCount(ownFields()) == 0) {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.AvraNothingToReset)).show();
                return;
            }
            boolean font = AvraDesign.reset(ownFields());
            onDesignReset();
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.AvraResetDone)).show();
            changed(font);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        AvraDesign.Field f = fieldFor(item.id);
        if (f == null || !AvraDesign.isOverridden(f)) return false;
        boolean font = AvraDesign.reset(f);
        onDesignReset();
        BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.AvraResetDone)).show();
        changed(font);
        return true;
    }

    /** Hook for tabs with live previews to refresh them after a reset. */
    protected void onDesignReset() {
    }

    protected void offerRestart() {
        BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.AvraRestartNeeded), getString(R.string.AvraRestart), AvraDesignFragment::restartApp).show();
    }

    /** Restarts the app after pending writes (settings, language pack) had time to land. */
    public static void restartApp() {
        Context ctx = ApplicationLoader.applicationContext;
        Intent i = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
        if (i == null || i.getComponent() == null) return;
        SharedConfig.saveConfig();
        AndroidUtilities.runOnUIThread(() -> {
            ctx.startActivity(Intent.makeRestartActivityTask(i.getComponent()));
            AndroidUtilities.runOnUIThread(() -> android.os.Process.killProcess(android.os.Process.myPid()), 400);
        }, 600);
    }
}
