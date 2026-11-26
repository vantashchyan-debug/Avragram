package com.avragramm.ui;

import android.view.View;

import com.avragramm.AvraBrand;
import com.avragramm.AvraUpdater;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

/** Settings → Avragram → Updates: version, processor, daily auto check / download and a manual check. */
public class AvraUpdateActivity extends UniversalFragment implements AvraUpdater.Listener {

    private static final int ID_ACTION = 1, ID_AUTO = 2, ID_AUTO_DOWNLOAD = 3, ID_RELEASES = 4;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.AvraUpdates);
    }

    @Override
    public boolean onFragmentCreate() {
        AvraUpdater.addListener(this);
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

    private CharSequence actionTitle() {
        AvraUpdater.Release r = AvraUpdater.release;
        switch (AvraUpdater.state) {
            case AvraUpdater.CHECKING: return getString(R.string.AvraUpdateChecking);
            case AvraUpdater.AVAILABLE: return formatString(R.string.AvraUpdateDownloadVersion, r.version);
            case AvraUpdater.DOWNLOADING: return formatString(R.string.AvraUpdateDownloading, AvraUpdater.progress);
            case AvraUpdater.READY: return formatString(R.string.AvraUpdateInstallVersion, r.version);
            default: return getString(R.string.AvraUpdateCheck);
        }
    }

    private CharSequence statusText() {
        switch (AvraUpdater.state) {
            case AvraUpdater.UP_TO_DATE: return getString(R.string.AvraUpdateUpToDate);
            case AvraUpdater.ERROR: return formatString(R.string.AvraUpdateError, AvraUpdater.error == null ? "" : AvraUpdater.error);
            case AvraUpdater.DOWNLOADING: return getString(R.string.AvraUpdateDownloadingInfo);
            default: {
                long last = AvraUpdater.lastCheck();
                return last == 0 ? getString(R.string.AvraUpdateNever)
                        : formatString(R.string.AvraUpdateLast, LocaleController.formatDateAudio(last / 1000, true));
            }
        }
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.AvraUpdateVersionHeader)));
        items.add(UItem.asButton(-1, R.drawable.msg_info, getString(R.string.AvraUpdateCurrent), AvraBrand.VERSION));
        items.add(UItem.asButton(-2, R.drawable.msg_customize, getString(R.string.AvraUpdateCpu), AvraUpdater.abiTitle()));
        items.add(UItem.asButton(ID_ACTION, R.drawable.msg_download, actionTitle()));
        items.add(UItem.asShadow(statusText()));

        items.add(UItem.asHeader(getString(R.string.AvraUpdateAutoHeader)));
        items.add(UItem.asCheck(ID_AUTO, getString(R.string.AvraUpdateAuto), getString(R.string.AvraUpdateAutoInfo), true).setChecked(AvraUpdater.autoCheck()));
        if (AvraUpdater.autoCheck()) {
            items.add(UItem.asCheck(ID_AUTO_DOWNLOAD, getString(R.string.AvraUpdateAutoDownload), formatString(R.string.AvraUpdateAutoDownloadInfo, AvraUpdater.assetName() == null ? "?" : AvraUpdater.assetName()), true).setChecked(AvraUpdater.autoDownload()));
        }
        items.add(UItem.asButton(ID_RELEASES, R.drawable.msg_openin, getString(R.string.AvraUpdateReleases)));
        items.add(UItem.asShadow(getString(R.string.AvraUpdateInfo)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_ACTION:
                switch (AvraUpdater.state) {
                    case AvraUpdater.CHECKING:
                        break;
                    case AvraUpdater.AVAILABLE:
                        AvraUpdater.download(() -> {
                            if (AvraUpdater.state == AvraUpdater.READY && getParentActivity() != null) AvraUpdater.install(getParentActivity());
                        });
                        break;
                    case AvraUpdater.DOWNLOADING:
                        AvraUpdater.cancelDownload();
                        break;
                    case AvraUpdater.READY:
                        AvraUpdater.install(getParentActivity());
                        break;
                    default:
                        AvraUpdater.check(true, null);
                }
                break;
            case ID_AUTO:
                AvraUpdater.setAutoCheck(!AvraUpdater.autoCheck());
                listView.adapter.update(true);
                break;
            case ID_AUTO_DOWNLOAD:
                AvraUpdater.setAutoDownload(!AvraUpdater.autoDownload());
                listView.adapter.update(true);
                break;
            case ID_RELEASES:
                Browser.openUrl(getParentActivity(), AvraUpdater.release != null && AvraUpdater.release.pageUrl != null ? AvraUpdater.release.pageUrl : AvraUpdater.RELEASES_URL);
                break;
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
