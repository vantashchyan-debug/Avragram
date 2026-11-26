package com.avragramm.chess;

import android.os.Bundle;
import android.view.View;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

/** AvraChess lobby: play the bot, two on one phone or a friend online; your games, ratings, leaderboard. */
public class AvraChessActivity extends UniversalFragment implements ChessNet.Listener {

    private static final int ID_BOT = 1, ID_LOCAL = 2, ID_ONLINE = 3, ID_CHALLENGE = 4, ID_RESUME_BOT = 5, ID_RESUME_LOCAL = 6,
            ID_LEADERBOARD = 7, ID_SETTINGS = 8, ID_GAME = 1000, ID_RATING = 900;

    /** Private chat the lobby was opened from (0 = none): offers "Challenge X". */
    private final long chatUserId;
    private final ArrayList<ChessNet.Game> shown = new ArrayList<>();

    public AvraChessActivity() {
        this(0);
    }

    public AvraChessActivity(long dialogId) {
        chatUserId = DialogObject.isUserDialog(dialogId) ? dialogId : 0;
    }

    @Override
    protected CharSequence getTitle() {
        return "♟ AvraChess";
    }

    @Override
    public boolean onFragmentCreate() {
        ChessNet.addListener(this);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        ChessNet.removeListener(this);
        super.onFragmentDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null) listView.adapter.update(false);
    }

    @Override
    public void onChessUpdate(ChessNet.Game game, boolean incoming) {
        if (listView != null) listView.adapter.update(true);
    }

    private TLRPC.User chatUser() {
        if (chatUserId == 0) return null;
        TLRPC.User u = getMessagesController().getUser(chatUserId);
        if (u == null || u.bot || UserObject.isUserSelf(u) || u.deleted) return null;
        return u;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.AvraChessPlay)));
        TLRPC.User cu = chatUser();
        if (cu != null) {
            items.add(UItem.asButtonWithSubtext(ID_CHALLENGE, R.drawable.msg_openprofile,
                    formatString(R.string.AvraChessChallenge, UserObject.getFirstName(cu)), getString(R.string.AvraChessChallengeInfo), 64, 60));
        }
        if (AvraChessGameActivity.hasSaved(AvraChessGameActivity.MODE_BOT)) {
            items.add(UItem.asButtonWithSubtext(ID_RESUME_BOT, R.drawable.msg_media, getString(R.string.AvraChessResumeBot), getString(R.string.AvraChessResumeInfo), 64, 60));
        }
        if (AvraChessGameActivity.hasSaved(AvraChessGameActivity.MODE_LOCAL)) {
            items.add(UItem.asButtonWithSubtext(ID_RESUME_LOCAL, R.drawable.msg_media, getString(R.string.AvraChessResumeLocal), getString(R.string.AvraChessResumeInfo), 64, 60));
        }
        items.add(UItem.asButtonWithSubtext(ID_BOT, R.drawable.msg_bot, getString(R.string.AvraChessVsBot), getString(R.string.AvraChessVsBotInfo), 64, 60));
        items.add(UItem.asButtonWithSubtext(ID_LOCAL, R.drawable.msg_contacts, getString(R.string.AvraChessVsLocal), getString(R.string.AvraChessVsLocalInfo), 64, 60));
        items.add(UItem.asButtonWithSubtext(ID_ONLINE, R.drawable.msg_language, getString(R.string.AvraChessVsOnline), getString(R.string.AvraChessVsOnlineInfo), 64, 60));
        items.add(UItem.asShadow(null));

        shown.clear();
        shown.addAll(ChessNet.list(currentAccount));
        if (!shown.isEmpty()) {
            items.add(UItem.asHeader(getString(R.string.AvraChessGames)));
            for (int i = 0; i < shown.size() && i < 40; i++) {
                ChessNet.Game g = shown.get(i);
                TLRPC.User u = getMessagesController().getUser(g.peer);
                String name = u != null ? UserObject.getUserName(u) : g.oppName;
                items.add(UItem.asButtonWithSubtext(ID_GAME + i, iconOf(g), "⚔ " + name + " · " + ChessNet.tcTitle(g.tc), gameStatus(g), 64, 60));
            }
            items.add(UItem.asShadow(getString(R.string.AvraChessGamesInfo)));
        }

        items.add(UItem.asHeader(getString(R.string.AvraChessMyRating)));
        for (int c = 0; c < 4; c++) {
            int n = ChessNet.games(c);
            int[] wdl = ChessNet.wdl(c);
            String sub = n == 0 ? getString(R.string.AvraChessNoRatedGames) : formatString(R.string.AvraChessWDL, n, wdl[0], wdl[1], wdl[2]);
            items.add(UItem.asButtonWithSubtext(ID_RATING + c, R.drawable.msg_stats,
                    ChessNet.CAT_EMOJI[c] + " " + getString(ChessNet.CAT_NAMES[c]) + " — " + ChessNet.rating(c) + (n < 5 ? "?" : ""), sub, 64, 60));
        }
        items.add(UItem.asButton(ID_LEADERBOARD, R.drawable.msg_premium_liststar, getString(R.string.AvraChessLeaderboard)));
        items.add(UItem.asButton(ID_SETTINGS, R.drawable.msg_customize, getString(R.string.AvraChessSettings)));
        items.add(UItem.asShadow(getString(R.string.AvraChessLobbyInfo)));
    }

    private static int iconOf(ChessNet.Game g) {
        switch (g.state) {
            case ChessNet.INVITE_IN: return R.drawable.msg_mention;
            case ChessNet.FINISHED:
            case ChessNet.DECLINED: return R.drawable.msg_archive;
            default: return g.myTurn() ? R.drawable.msg_arrow_forward : R.drawable.msg_recent;
        }
    }

    private String gameStatus(ChessNet.Game g) {
        switch (g.state) {
            case ChessNet.INVITE_IN: return getString(R.string.AvraChessInviteIn);
            case ChessNet.INVITE_OUT: return getString(R.string.AvraChessWaitAccept);
            case ChessNet.DECLINED: return getString(R.string.AvraChessDeclined);
            case ChessNet.FINISHED: {
                String head = g.result == 0 ? getString(R.string.AvraChessDraw) : g.result == g.myColor ? getString(R.string.AvraChessYouWon) : getString(R.string.AvraChessYouLost);
                String delta = g.ratingDelta == 0 ? "" : (g.ratingDelta > 0 ? " · +" : " · ") + g.ratingDelta;
                return head + " · " + ChessNet.reasonText(g.reason) + delta;
            }
            default:
                return (g.myTurn() ? getString(R.string.AvraChessYourMove) : getString(R.string.AvraChessTheirMove))
                        + " · " + formatString(R.string.AvraChessMoveN, g.moves.size() / 2 + 1);
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;
        if (id == ID_BOT) {
            pickBot();
        } else if (id == ID_LOCAL) {
            pickClock(tc -> presentFragment(new AvraChessGameActivity(AvraChessGameActivity.MODE_LOCAL, 0, 1, tc, false)), true);
        } else if (id == ID_ONLINE) {
            pickOnline(0);
        } else if (id == ID_CHALLENGE) {
            pickOnline(chatUserId);
        } else if (id == ID_RESUME_BOT) {
            presentFragment(new AvraChessGameActivity(AvraChessGameActivity.MODE_BOT, 1, 1, -1, true));
        } else if (id == ID_RESUME_LOCAL) {
            presentFragment(new AvraChessGameActivity(AvraChessGameActivity.MODE_LOCAL, 0, 1, -1, true));
        } else if (id == ID_LEADERBOARD) {
            presentFragment(new AvraChessLeaderboardActivity());
        } else if (id == ID_SETTINGS) {
            presentFragment(new AvraChessSettingsActivity());
        } else if (id >= ID_RATING && id < ID_RATING + 4) {
            presentFragment(new AvraChessLeaderboardActivity(id - ID_RATING));
        } else if (id >= ID_GAME && id - ID_GAME < shown.size()) {
            presentFragment(new AvraChessGameActivity(shown.get(id - ID_GAME).id));
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= ID_GAME && item.id - ID_GAME < shown.size()) {
            ChessNet.Game g = shown.get(item.id - ID_GAME);
            if (g.state == ChessNet.ACTIVE) return false;
            new AlertDialog.Builder(getContext())
                    .setTitle(getString(R.string.AvraChessDeleteGame))
                    .setPositiveButton(getString(R.string.Delete), (d, w) -> {
                        ChessNet.delete(g.id);
                        listView.adapter.update(true);
                    })
                    .setNegativeButton(getString(R.string.Cancel), null).show();
            return true;
        }
        return false;
    }

    // ---- choosers --------------------------------------------------------------------------------

    private interface IntCallback {
        void run(int value);
    }

    private void pickBot() {
        CharSequence[] levels = new CharSequence[ChessAi.LEVELS];
        for (int i = 0; i < levels.length; i++) {
            levels[i] = "🤖 " + getString(AvraChessGameActivity.LEVEL_NAMES[i]) + " · ~" + ChessAi.LEVEL_RATING[i];
        }
        new AlertDialog.Builder(getContext()).setTitle(getString(R.string.AvraChessPickLevel)).setItems(levels, (d, lvl) ->
                pickColor(color -> pickClock(tc -> {
                    ChessSound.play(ChessSound.START);
                    presentFragment(new AvraChessGameActivity(AvraChessGameActivity.MODE_BOT, lvl, color == 0 ? (Math.random() < 0.5 ? 1 : -1) : color, tc, false));
                }, true), true)).show();
    }

    private void pickColor(IntCallback cb, boolean allowRandom) {
        CharSequence[] items = {"♔ " + getString(R.string.AvraChessWhite), "♚ " + getString(R.string.AvraChessBlack), "🎲 " + getString(R.string.AvraChessRandom)};
        new AlertDialog.Builder(getContext()).setTitle(getString(R.string.AvraChessPickColor))
                .setItems(allowRandom ? items : new CharSequence[]{items[0], items[1]}, (d, w) -> cb.run(w == 0 ? 1 : w == 1 ? -1 : 0)).show();
    }

    /** offline = true adds "no clock" and hides the 3-day control. */
    private void pickClock(IntCallback cb, boolean offline) {
        ArrayList<CharSequence> names = new ArrayList<>();
        ArrayList<Integer> values = new ArrayList<>();
        if (offline) {
            names.add("♾ " + getString(R.string.AvraChessNoClock));
            values.add(-1);
        }
        for (int i = 0; i < ChessNet.TC.length; i++) {
            if (offline && ChessNet.isDaily(i)) continue;
            names.add(ChessNet.tcTitle(i));
            values.add(i);
        }
        new AlertDialog.Builder(getContext()).setTitle(getString(R.string.AvraChessPickClock))
                .setItems(names.toArray(new CharSequence[0]), (d, w) -> cb.run(values.get(w))).show();
    }

    private void pickOnline(long peer) {
        pickClock(tc -> pickColor(color -> {
            if (peer != 0) {
                sendInvite(peer, tc, color);
                return;
            }
            Bundle args = new Bundle();
            args.putBoolean("onlySelect", true);
            args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_USERS_ONLY);
            args.putBoolean("allowGlobalSearch", false);
            DialogsActivity picker = new DialogsActivity(args);
            picker.setDelegate((fragment, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
                if (dids.isEmpty()) return false;
                long did = dids.get(0).dialogId;
                fragment.finishFragment();
                if (!DialogObject.isUserDialog(did)) return true;
                TLRPC.User u = MessagesController.getInstance(currentAccount).getUser(did);
                if (u == null || u.bot || UserObject.isUserSelf(u)) {
                    BulletinFactory.of(this).createErrorBulletin(getString(R.string.AvraChessPickFriendError)).show();
                    return true;
                }
                sendInvite(did, tc, color);
                return true;
            });
            presentFragment(picker);
        }, true), false);
    }

    private void sendInvite(long peer, int tc, int color) {
        ChessNet.Game g = ChessNet.invite(currentAccount, peer, tc, color);
        presentFragment(new AvraChessGameActivity(g.id));
    }
}
