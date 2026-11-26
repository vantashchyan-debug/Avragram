package com.avragramm.ui;

import android.content.Intent;
import android.net.Uri;
import android.view.View;

import com.avragramm.AvraBrand;
import com.avragramm.AvraUpdater;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

/**
 * Settings → Updates → Choose version: every Avragram release for this phone. A newer one installs
 * over the current app (also one step at a time), the same one reinstalls; an older one needs the app
 * removed first — Android never installs an older version over a newer one.
 */
public class AvraVersionsActivity extends UniversalFragment implements AvraUpdater.Listener {

    private static final int ID_VERSION = 1000;
    private ArrayList<AvraUpdater.Release> list;
    private String error;
    private boolean loading;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.AvraUpdateVersions);
    }

    @Override
    public boolean onFragmentCreate() {
        AvraUpdater.addListener(this);
        load();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        AvraUpdater.removeListener(this);
        super.onFragmentDestroy();
    }

    @Override
    public void onState() {
        if (listView != null) listView.adapter.update(true);
    }

    private void load() {
        loading = true;
        AvraUpdater.queue.postRunnable(() -> {
            ArrayList<AvraUpdater.Release> l = null;
            String err = null;
            try {
                l = AvraUpdater.fetchAll();
            } catch (Exception e) {
                err = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            final ArrayList<AvraUpdater.Release> fl = l;
            final String fe = err;
            AndroidUtilities.runOnUIThread(() -> {
                loading = false;
                list = fl;
                error = fe;
                if (listView != null) listView.adapter.update(true);
            });
        });
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(formatString(R.string.AvraUpdateVersionsHeader, AvraBrand.VERSION)));
        if (loading) {
            items.add(UItem.asShadow(getString(R.string.AvraUpdateChecking)));
            return;
        }
        if (list == null) {
            items.add(UItem.asShadow(formatString(R.string.AvraUpdateError, error == null ? "" : error)));
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            AvraUpdater.Release r = list.get(i);
            int cmp = AvraUpdater.compare(r.version, AvraBrand.VERSION);
            String tag = cmp == 0 ? getString(R.string.AvraUpdateTagCurrent) : cmp > 0 ? getString(R.string.AvraUpdateTagNewer) : getString(R.string.AvraUpdateTagOlder);
            if (i == 0) tag = getString(R.string.AvraUpdateTagLatest) + " · " + tag;
            if (r.url == null) tag += " · " + getString(R.string.AvraUpdateTagNoFile);
            String date = r.date != null && r.date.length() >= 10 ? r.date.substring(0, 10) : "";
            boolean busy = AvraUpdater.state == AvraUpdater.DOWNLOADING && AvraUpdater.release == r;
            items.add(UItem.asButtonWithSubtext(ID_VERSION + i, cmp == 0 ? R.drawable.msg_check_s : cmp > 0 ? R.drawable.msg_download : R.drawable.msg_reset,
                    "Avragram " + r.version + (busy ? "  · " + AvraUpdater.progress + "%" : ""), tag + (date.isEmpty() ? "" : " · " + date), 64, 60));
        }
        items.add(UItem.asShadow(getString(R.string.AvraUpdateVersionsInfo)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        int i = item.id - ID_VERSION;
        if (list == null || i < 0 || i >= list.size()) return;
        AvraUpdater.Release r = list.get(i);
        if (r.url == null) {
            Browser.openUrl(getParentActivity(), r.pageUrl);
            return;
        }
        int cmp = AvraUpdater.compare(r.version, AvraBrand.VERSION);
        AlertDialog.Builder b = new AlertDialog.Builder(getContext());
        b.setTitle("Avragram " + r.version);
        if (cmp >= 0) {
            String notes = r.notes == null ? "" : r.notes;
            int files = notes.indexOf("### Файлы");
            if (files > 0) notes = notes.substring(0, files);
            notes = notes.replace("**", "").trim();
            if (notes.length() > 1200) notes = notes.substring(0, 1200) + "…";
            b.setMessage((cmp == 0 ? getString(R.string.AvraUpdateReinstallInfo) + "\n\n" : "") + notes);
            b.setPositiveButton(getString(cmp == 0 ? R.string.AvraUpdateReinstall : R.string.AvraUpdateInstall), (d, w) -> {
                AvraUpdater.select(r);
                if (AvraUpdater.state == AvraUpdater.READY) {
                    AvraUpdater.install(getParentActivity());
                } else {
                    AvraUpdater.download(() -> {
                        if (AvraUpdater.state == AvraUpdater.READY && getParentActivity() != null) AvraUpdater.install(getParentActivity());
                    });
                }
            });
        } else {
            b.setMessage(formatString(R.string.AvraUpdateRollbackInfo, r.version));
            b.setPositiveButton(getString(R.string.AvraUpdateRollbackDownload), (d, w) -> Browser.openUrl(getParentActivity(), r.url));
            b.setNeutralButton(getString(R.string.AvraUpdateRollbackRemove), (d, w) -> {
                try {
                    Intent del = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + getParentActivity().getPackageName()));
                    getParentActivity().startActivity(del);
                } catch (Exception ignore) {
                }
            });
        }
        b.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
