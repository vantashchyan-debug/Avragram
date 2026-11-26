package com.avragramm.chess;

import android.os.Bundle;
import android.view.View;

import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

/**
 * Shared AvraChess leaderboard: you, everyone you played and the best players from their tables
 * (spread with every game message). Tabs: overall best / bullet / blitz / rapid / 3 days.
 */
public class AvraChessLeaderboardActivity extends UniversalFragment {

    private static final int ID_ROW = 1000;
    private int category = -1;
    private final ArrayList<ChessNet.Player> shown = new ArrayList<>();

    public AvraChessLeaderboardActivity() {
    }

    public AvraChessLeaderboardActivity(int category) {
        this.category = category;
    }

    @Override
    protected CharSequence getTitle() {
        return "🏆 " + getString(R.string.AvraChessLeaderboard);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        String[] tabs = new String[5];
        tabs[0] = getString(R.string.AvraChessOverall);
        for (int c = 0; c < 4; c++) tabs[c + 1] = ChessNet.CAT_EMOJI[c] + " " + getString(ChessNet.CAT_NAMES[c]);
        items.add(UItem.asSlideView(tabs, category + 1, (org.telegram.messenger.Utilities.Callback<Integer>) idx -> {
            category = idx - 1;
            listView.adapter.update(true);
        }));
        items.add(UItem.asShadow(null));

        shown.clear();
        shown.addAll(ChessNet.leaderboard(currentAccount, category));
        long self = UserConfig.getInstance(currentAccount).getClientUserId();
        items.add(UItem.asHeader(getString(R.string.AvraChessTop)));
        String[] medals = {"🥇", "🥈", "🥉"};
        for (int i = 0; i < shown.size() && i < 100; i++) {
            ChessNet.Player p = shown.get(i);
            int r = category < 0 ? p.best() : p.ratings[category];
            String place = i < 3 ? medals[i] : "#" + (i + 1);
            boolean me = p.id == self;
            String name = ChessNet.displayName(currentAccount, p) + (me ? " · " + getString(R.string.AvraChessYou) : "");
            String sub = me ? getString(R.string.AvraChessThatsYou)
                    : p.direct ? formatString(R.string.AvraChessPlayedWithYou, Math.max(1, p.games))
                    : getString(R.string.AvraChessFromFriends);
            items.add(UItem.asButtonWithSubtext(ID_ROW + i, me ? R.drawable.msg_openprofile : R.drawable.msg_contacts,
                    place + "  " + name + " — " + r, sub, 64, 60));
        }
        items.add(UItem.asShadow(getString(shown.size() <= 1 ? R.string.AvraChessLeaderboardEmpty : R.string.AvraChessLeaderboardInfo)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        int i = item.id - ID_ROW;
        if (i < 0 || i >= shown.size()) return;
        ChessNet.Player p = shown.get(i);
        if (getMessagesController().getUser(p.id) == null) return;
        Bundle args = new Bundle();
        args.putLong("user_id", p.id);
        presentFragment(new ProfileActivity(args));
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
