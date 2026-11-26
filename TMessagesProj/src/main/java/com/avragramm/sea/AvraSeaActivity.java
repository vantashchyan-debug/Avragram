package com.avragramm.sea;

import android.os.Bundle;
import android.view.View;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

/** AvraSea lobby: the bot, a challenge to a friend, your games, stats and sound. */
public class AvraSeaActivity extends UniversalFragment implements SeaNet.Listener {

    private static final int ID_BOT = 100, ID_FRIEND = 200, ID_CHAT = 201, ID_GAME = 1000, ID_SOUND = 300;
    private final long dialogId;
    private final ArrayList<SeaNet.Game> shown = new ArrayList<>();

    public AvraSeaActivity(long dialogId) {
        this.dialogId = dialogId;
    }

    @Override
    public boolean onFragmentCreate() {
        SeaNet.addListener(this);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        SeaNet.removeListener(this);
        super.onFragmentDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null) listView.adapter.update(false);
    }

    @Override
    public void onSeaUpdate(SeaNet.Game g, boolean incoming) {
        if (listView != null) listView.adapter.update(true);
    }

    @Override
    protected CharSequence getTitle() {
        return "🚢 " + getString(R.string.AvraSeaTitle);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.AvraSeaPlay)));
        TLRPC.User chatUser = dialogId > 0 ? MessagesController.getInstance(currentAccount).getUser(dialogId) : null;
        if (chatUser != null && !chatUser.bot && !UserObject.isUserSelf(chatUser)) {
            items.add(UItem.asButtonWithSubtext(ID_CHAT, R.drawable.msg_openprofile,
                    formatString(R.string.AvraSeaChallengeUser, UserObject.getFirstName(chatUser)), getString(R.string.AvraSeaChallengeInfo), 64, 60));
        }
        items.add(UItem.asButtonWithSubtext(ID_FRIEND, R.drawable.msg_contacts, getString(R.string.AvraSeaChallengeFriend), getString(R.string.AvraSeaChallengeInfo), 64, 60));
        for (int i = 0; i < SeaAi.LEVELS; i++) {
            int[] st = SeaNet.stats("bot" + i);
            items.add(UItem.asButtonWithSubtext(ID_BOT + i, R.drawable.msg_bot, "🤖 " + getString(AvraSeaGameActivity.LEVEL_NAMES[i]),
                    getString(i == 0 ? R.string.AvraSeaLevel0Info : i == 1 ? R.string.AvraSeaLevel1Info : R.string.AvraSeaLevel2Info)
                            + (st[0] + st[1] > 0 ? " · " + formatString(R.string.AvraSeaScore, st[0], st[1]) : ""), 64, 60));
        }
        int[] on = SeaNet.stats("online");
        items.add(UItem.asShadow(formatString(R.string.AvraSeaOnlineScore, on[0], on[1])));

        shown.clear();
        shown.addAll(SeaNet.list(currentAccount));
        if (!shown.isEmpty()) {
            items.add(UItem.asHeader(getString(R.string.AvraSeaGames)));
            for (int i = 0; i < shown.size() && i < 30; i++) {
                SeaNet.Game g = shown.get(i);
                TLRPC.User u = MessagesController.getInstance(currentAccount).getUser(g.peer);
                String name = u != null ? UserObject.getUserName(u) : g.oppName;
                String st;
                switch (g.state) {
                    case SeaNet.INVITE_IN: st = "📨 " + getString(R.string.AvraSeaStateInvite); break;
                    case SeaNet.INVITE_OUT: st = "⏳ " + getString(R.string.AvraSeaWaitAccept); break;
                    case SeaNet.ACTIVE: st = g.myTurn() ? "🎯 " + getString(R.string.AvraSeaYourTurn) : "🌊 " + getString(R.string.AvraSeaTheirTurn); break;
                    default: st = g.winner > 0 ? "🏆 " + getString(R.string.AvraSeaVictory) : "💀 " + getString(R.string.AvraSeaDefeat);
                }
                items.add(UItem.asButtonWithSubtext(ID_GAME + i, R.drawable.dialog_media_game_20, name, st, 64, 60));
            }
            items.add(UItem.asShadow(null));
        }

        items.add(UItem.asHeader(getString(R.string.AvraFunSettings)));
        items.add(UItem.asSwitchNoIcon(ID_SOUND, getString(R.string.AvraSeaSound)).setChecked(SeaSound.enabled()));
        items.add(UItem.asShadow(getString(R.string.AvraSeaFairInfo)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;
        if (id >= ID_BOT && id < ID_BOT + SeaAi.LEVELS) {
            presentFragment(AvraSeaGameActivity.bot(id - ID_BOT));
        } else if (id == ID_CHAT) {
            presentFragment(AvraSeaGameActivity.challenge(dialogId));
        } else if (id == ID_FRIEND) {
            Bundle args = new Bundle();
            args.putBoolean("onlySelect", true);
            args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_USERS_ONLY);
            args.putBoolean("allowGlobalSearch", false);
            DialogsActivity picker = new DialogsActivity(args);
            picker.setDelegate((fragment, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
                if (dids.isEmpty()) return false;
                long did = dids.get(0).dialogId;
                if (!DialogObject.isUserDialog(did)) return true;
                TLRPC.User u = MessagesController.getInstance(currentAccount).getUser(did);
                if (u == null || u.bot || UserObject.isUserSelf(u)) {
                    fragment.finishFragment();
                    BulletinFactory.of(this).createErrorBulletin(getString(R.string.AvraChessPickFriendError)).show();
                    return true;
                }
                fragment.presentFragment(AvraSeaGameActivity.challenge(did), true);
                return true;
            });
            presentFragment(picker);
        } else if (id >= ID_GAME && id < ID_GAME + shown.size()) {
            presentFragment(AvraSeaGameActivity.online(shown.get(id - ID_GAME).id));
        } else if (id == ID_SOUND) {
            SeaSound.setEnabled(!SeaSound.enabled());
            listView.adapter.update(true);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;
        if (id >= ID_GAME && id < ID_GAME + shown.size()) {
            SeaNet.Game g = shown.get(id - ID_GAME);
            if (g.state == SeaNet.FINISHED || g.state == SeaNet.DECLINED) {
                SeaNet.delete(g.id);
                listView.adapter.update(true);
                return true;
            }
        }
        return false;
    }
}
