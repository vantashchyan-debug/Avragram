package com.avragramm.ui;

import android.view.View;

import com.avragramm.AvraConfig;
import com.avragramm.AvraGames;

import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

/**
 * "Fun" — opened from the ⋮ menu of a chat (instead of the old "Spam" item) or from Settings.
 * Games, tools (soundpad, spam for the current chat), settings and built-in plugins
 * (send game results, /calc"…" in messages, "Said behind your back").
 */
public class AvraFunActivity extends UniversalFragment {

    private static final int ID_GAME = 100, ID_SOUNDPAD = 200, ID_SPAM = 201,
            ID_SEND_RESULTS = 300, ID_CALC_COMMAND = 301, ID_BEHIND = 302,
            ID_DOWNLOAD_SPEED = 400, ID_PHONE_REGION = 401;

    /** Chat the section was opened from (0 = from Settings: no spam, results are not sent). */
    private final long dialogId;

    public AvraFunActivity(long dialogId) {
        this.dialogId = dialogId;
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.AvraFun);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null) listView.adapter.update(false); // fresh records after a game
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        AvraConfig.ensureLoaded();
        items.add(UItem.asHeader(getString(R.string.AvraFunGames)));
        for (int i = 0; i < AvraGames.IDS.length; i++) {
            int best = AvraGames.bestAny(i);
            String sub = getString(AvraGames.DESCRIPTIONS[i]);
            if (best > 0) sub = formatString(R.string.AvraGameBestShort, best) + " · " + sub;
            items.add(UItem.asButtonWithSubtext(ID_GAME + i, R.drawable.dialog_media_game_20,
                    AvraGames.EMOJI[i] + "  " + AvraGames.TITLES[i], sub, 64, 60));
        }
        items.add(UItem.asShadow(getString(dialogId != 0 ? R.string.AvraFunGamesInfo : R.string.AvraFunGamesInfoNoChat)));

        items.add(UItem.asHeader(getString(R.string.AvraFunTools)));
        items.add(UItem.asButtonWithSubtext(ID_SOUNDPAD, R.drawable.bot_music, getString(R.string.AvraSoundpad), getString(R.string.AvraSoundpadInfoShort), 64, 60));
        if (dialogId != 0) {
            items.add(UItem.asButtonWithSubtext(ID_SPAM, R.drawable.msg_retry, getString(R.string.AvraSpam), getString(R.string.AvraFunSpamInfo), 64, 60));
        }
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.AvraFunSettings)));
        items.add(UItem.asSwitchNoIcon(ID_SEND_RESULTS, getString(R.string.AvraGameSendSwitch)).setChecked(AvraGames.sendResults()));
        items.add(UItem.asSwitchNoIcon(ID_CALC_COMMAND, getString(R.string.AvraCalcCommandSwitch)).setChecked(AvraConfig.calcCommand));
        items.add(UItem.asButton(ID_BEHIND, R.drawable.msg_block2, getString(R.string.AvraBehind)));
        items.add(UItem.asShadow(getString(R.string.AvraFunSettingsInfo)));

        items.add(UItem.asHeader(getString(R.string.AvraFunPlugins)));
        items.add(UItem.asCheck(ID_DOWNLOAD_SPEED, getString(R.string.AvraDownloadSpeed), getString(R.string.AvraDownloadSpeedInfo), true).setChecked(AvraConfig.downloadSpeed));
        items.add(UItem.asCheck(ID_PHONE_REGION, getString(R.string.AvraPhoneRegion), getString(R.string.AvraPhoneRegionInfo), true).setChecked(AvraConfig.phoneRegion));
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;
        if (id >= ID_GAME && id < ID_GAME + AvraGames.IDS.length) {
            presentFragment(new AvraGameActivity(id - ID_GAME, dialogId));
        } else if (id == ID_SOUNDPAD) {
            if (getParentActivity() != null) {
                new AvraSoundpadSheet(getParentActivity(), org.telegram.ui.ActionBar.Theme.isCurrentThemeDark()).show();
            }
        } else if (id == ID_DOWNLOAD_SPEED) {
            AvraConfig.downloadSpeed = !AvraConfig.downloadSpeed;
            AvraConfig.save();
            listView.adapter.update(true);
        } else if (id == ID_PHONE_REGION) {
            AvraConfig.phoneRegion = !AvraConfig.phoneRegion;
            AvraConfig.save();
            listView.adapter.update(true);
        } else if (id == ID_SPAM) {
            AvraChatTools.showSpam(this, currentAccount, dialogId, null);
        } else if (id == ID_SEND_RESULTS) {
            AvraGames.setSendResults(!AvraGames.sendResults());
            listView.adapter.update(true);
        } else if (id == ID_CALC_COMMAND) {
            AvraConfig.calcCommand = !AvraConfig.calcCommand;
            AvraConfig.save();
            listView.adapter.update(true);
        } else if (id == ID_BEHIND) {
            presentFragment(new AvraBehindActivity());
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
