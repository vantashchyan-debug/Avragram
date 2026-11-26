package com.avragramm.chess;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.Locale;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

/**
 * One AvraChess game: against the bot, two players on one phone, or online with a friend.
 * Colors come only from ChessStyle, never from the Telegram theme.
 */
public class AvraChessGameActivity extends BaseFragment implements ChessNet.Listener {

    public static final int MODE_BOT = 0, MODE_LOCAL = 1, MODE_ONLINE = 2;
    private static final int MENU_FLIP = 1, MENU_SETTINGS = 2;

    private final int mode;
    private int level = 1;
    /** Bot game: your color. */
    private int humanColor = ChessBoard.WHITE;
    /** Local clocks for bot / two-player games: -1 = no clock. */
    private int localTc = -1;
    private long localW, localB, localTick;
    private boolean localOver;
    private String localResult;

    private String gameId;
    private ChessBoard board = new ChessBoard();

    private LinearLayout root;
    private ChessBoardView boardView;
    private TextView topName, topInfo, topClock, bottomName, bottomInfo, bottomClock, status, movesView;
    private LinearLayout buttons, offerRow;
    private HorizontalScrollView movesScroll;
    private boolean thinking;
    private static DispatchQueue aiQueue;
    private final ChessAi ai = new ChessAi();
    private int lowTimeBeeped;
    private boolean userFlip, flipAnimated;
    private TextView[] barViews;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateClocks();
            AndroidUtilities.runOnUIThread(this, 200);
        }
    };

    /** Online game. */
    public AvraChessGameActivity(String gameId) {
        this.mode = MODE_ONLINE;
        this.gameId = gameId;
    }

    /** Bot (color 1 / -1) or local game; tc = -1 for no clock; resume = continue the saved game. */
    public AvraChessGameActivity(int mode, int level, int color, int tc, boolean resume) {
        this.mode = mode;
        this.level = level;
        this.humanColor = color;
        this.localTc = tc;
        if (resume) restoreLocal();
        else resetLocalClocks();
    }

    // ---- local save ------------------------------------------------------------------------------

    private static String saveKey(int mode) {
        return mode == MODE_BOT ? "saved_bot" : "saved_local";
    }

    public static boolean hasSaved(int mode) {
        return !TextUtils.isEmpty(ChessStyle.prefs().getString(saveKey(mode), null));
    }

    private void saveLocal() {
        if (mode == MODE_ONLINE) return;
        try {
            if (localOver) {
                ChessStyle.prefs().edit().remove(saveKey(mode)).apply();
                return;
            }
            StringBuilder m = new StringBuilder();
            for (int i = 0; i < board.moveCount(); i++) {
                if (i > 0) m.append('.');
                m.append(ChessBoard.uci(board.moveAt(i)));
            }
            JSONObject o = new JSONObject().put("lv", level).put("c", humanColor).put("tc", localTc)
                    .put("w", localW).put("b", localB).put("m", m.toString());
            ChessStyle.prefs().edit().putString(saveKey(mode), o.toString()).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void restoreLocal() {
        try {
            JSONObject o = new JSONObject(ChessStyle.prefs().getString(saveKey(mode), "{}"));
            level = o.optInt("lv", level);
            humanColor = o.optInt("c", humanColor);
            localTc = o.optInt("tc", -1);
            localW = o.optLong("w");
            localB = o.optLong("b");
            board = new ChessBoard();
            String m = o.optString("m");
            if (!m.isEmpty()) for (String u : m.split("\\.")) {
                int mv = board.parseUci(u);
                if (mv == 0) break;
                board.make(mv);
            }
        } catch (Exception e) {
            FileLog.e(e);
            resetLocalClocks();
        }
    }

    private void resetLocalClocks() {
        if (localTc >= 0 && localTc < ChessNet.TC.length && !ChessNet.isDaily(localTc)) {
            localW = localB = ChessNet.TC[localTc][0] * 1000L;
        } else {
            localTc = -1;
        }
    }

    /** The review screen for a finished online game. */
    public static BaseFragment review(ChessNet.Game g) {
        String me = getString(R.string.AvraChessYou);
        TLRPC.User u = MessagesController.getInstance(g.account).getUser(g.peer);
        String opp = u != null ? UserObject.getUserName(u) : g.oppName;
        if (TextUtils.isEmpty(opp)) opp = getString(g.myColor == 1 ? R.string.AvraChessBlack : R.string.AvraChessWhite);
        String res = g.state == ChessNet.FINISHED ? ChessNet.resultText(g) : getString(R.string.AvraChessReview);
        return new AvraChessReviewActivity(g.moves, g.myColor == 1 ? me : opp, g.myColor == 1 ? opp : me, res, g.myColor);
    }

    private void openReview() {
        ChessNet.Game g = game();
        if (g != null) {
            presentFragment(review(g));
            return;
        }
        ArrayList<String> list = new ArrayList<>();
        for (int i = 0; i < board.moveCount(); i++) list.add(ChessBoard.uci(board.moveAt(i)));
        presentFragment(new AvraChessReviewActivity(list, nameOf(ChessBoard.WHITE), nameOf(ChessBoard.BLACK), statusText(),
                mode == MODE_BOT ? humanColor : ChessBoard.WHITE));
    }

    @Override
    public boolean isSwipeBackEnabled(android.view.MotionEvent event) {
        return false; // the board takes every drag
    }

    // ---- lifecycle -------------------------------------------------------------------------------

    @Override
    public boolean onFragmentCreate() {
        ChessNet.addListener(this);
        ChessSound.preload();
        if (mode == MODE_ONLINE) {
            ChessNet.Game g = ChessNet.get(gameId);
            if (g == null) return false;
            board = g.board();
            ChessNet.sync(currentAccount, g.peer, null);
        }
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        ChessNet.removeListener(this);
        AndroidUtilities.cancelRunOnUIThread(ticker);
        saveLocal();
        super.onFragmentDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        localTick = System.currentTimeMillis();
        if (mode == MODE_ONLINE) ChessNet.openGameId = gameId;
        AndroidUtilities.cancelRunOnUIThread(ticker);
        AndroidUtilities.runOnUIThread(ticker);
        applyColors();
        if (boardView != null) boardView.invalidate();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (gameId != null && gameId.equals(ChessNet.openGameId)) ChessNet.openGameId = null;
        AndroidUtilities.cancelRunOnUIThread(ticker);
        saveLocal();
    }

    @Override
    public boolean isLightStatusBar() {
        return AndroidUtilities.computePerceivedBrightness(ChessStyle.surface()) > 0.7f;
    }

    // ---- UI --------------------------------------------------------------------------------------

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("♟ AvraChess");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_FLIP) {
                    userFlip = !userFlip;
                    flipAnimated = true;
                    refresh(false);
                    flipAnimated = false;
                } else if (id == MENU_SETTINGS) {
                    presentFragment(new AvraChessSettingsActivity());
                }
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(MENU_FLIP, R.drawable.msg_switch);
        menu.addItem(MENU_SETTINGS, R.drawable.msg_settings);

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout top = playerBar(context);
        topName = barViews[0];
        topInfo = barViews[1];
        topClock = barViews[2];
        root.addView(top, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56, 8, 6, 8, 6));

        boardView = new ChessBoardView(context);
        boardView.setDelegate(new ChessBoardView.Delegate() {
            @Override
            public void onMove(int move) {
                playerMove(move);
            }

            @Override
            public void onPromotion(ArrayList<Integer> options) {
                askPromotion(options);
            }
        });
        root.addView(boardView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 4, 0, 4, 0));

        LinearLayout bottom = playerBar(context);
        bottomName = barViews[0];
        bottomInfo = barViews[1];
        bottomClock = barViews[2];
        root.addView(bottom, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56, 8, 6, 8, 6));

        status = new TextView(context);
        status.setGravity(Gravity.CENTER);
        status.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        status.setTypeface(AndroidUtilities.bold());
        root.addView(status, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 12, 2, 12, 4));

        offerRow = new LinearLayout(context);
        offerRow.setOrientation(LinearLayout.HORIZONTAL);
        offerRow.setGravity(Gravity.CENTER);
        root.addView(offerRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 12, 0, 12, 4));

        movesScroll = new HorizontalScrollView(context);
        movesScroll.setHorizontalScrollBarEnabled(false);
        movesView = new TextView(context);
        movesView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        movesView.setPadding(dp(12), dp(8), dp(12), dp(8));
        movesView.setSingleLine(true);
        movesScroll.addView(movesView);
        root.addView(movesScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 8, 0, 8, 4));

        buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        root.addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52, 8, 4, 8, 12));

        fragmentView = scroll;
        applyColors();
        refresh(false);
        if (mode == MODE_BOT && board.side != humanColor) AndroidUtilities.runOnUIThread(this::botMove, 400);
        return fragmentView;
    }

    private LinearLayout playerBar(Context context) {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(context);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        name.setTypeface(AndroidUtilities.bold());
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        TextView info = new TextView(context);
        info.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        info.setSingleLine(true);
        info.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(name);
        texts.addView(info);
        bar.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        TextView clock = new TextView(context);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        clock.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        clock.setGravity(Gravity.CENTER);
        clock.setPadding(dp(12), dp(4), dp(12), dp(4));
        bar.addView(clock, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        barViews = new TextView[]{name, info, clock};
        return bar;
    }

    private void applyColors() {
        if (fragmentView == null) return;
        fragmentView.setBackgroundColor(ChessStyle.bg());
        actionBar.setBackgroundColor(ChessStyle.surface());
        actionBar.setTitleColor(ChessStyle.text());
        actionBar.setSubtitleColor(ChessStyle.muted());
        actionBar.setItemsColor(ChessStyle.text(), false);
        actionBar.setItemsBackgroundColor(0x22000000 | (ChessStyle.accent() & 0xFFFFFF), false);
        for (TextView t : new TextView[]{topName, bottomName, status}) t.setTextColor(ChessStyle.text());
        for (TextView t : new TextView[]{topInfo, bottomInfo, movesView}) t.setTextColor(ChessStyle.muted());
        GradientDrawable mv = new GradientDrawable();
        mv.setColor(ChessStyle.surface());
        mv.setCornerRadius(dp(10));
        movesScroll.setBackground(mv);
    }

    private TextView button(String text, boolean accent, View.OnClickListener click) {
        TextView b = new TextView(getContext());
        b.setText(text);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        b.setTypeface(AndroidUtilities.bold());
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setPadding(dp(8), 0, dp(8), 0);
        int bg = accent ? ChessStyle.accent() : ChessStyle.surface();
        b.setTextColor(accent ? (AndroidUtilities.computePerceivedBrightness(bg) > 0.6f ? 0xFF111111 : 0xFFFFFFFF) : ChessStyle.text());
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setCornerRadius(dp(12));
        b.setBackground(d);
        b.setOnClickListener(click);
        return b;
    }

    private void addButton(LinearLayout row, TextView b) {
        row.addView(b, LayoutHelper.createLinear(0, 44, 1f, 4, 0, 4, 0));
    }

    private ChessNet.Game game() {
        return mode == MODE_ONLINE ? ChessNet.get(gameId) : null;
    }

    private int myColor() {
        if (mode == MODE_ONLINE) {
            ChessNet.Game g = game();
            return g == null ? 1 : g.myColor;
        }
        return mode == MODE_BOT ? humanColor : board.side;
    }

    /** Re-reads the state and redraws everything. */
    private void refresh(boolean animate) {
        if (boardView == null) return;
        ChessNet.Game g = game();
        if (g != null) board = g.board();
        int bottomColor;
        if (mode == MODE_LOCAL) {
            bottomColor = ChessStyle.flipLocal() ? board.side : ChessBoard.WHITE;
        } else {
            bottomColor = myColor();
        }
        boardView.setFlipped((bottomColor == ChessBoard.BLACK) != userFlip, flipAnimated);
        boardView.setBoard(board, animate);
        boolean over = isOver();
        if (over) boardView.setControls(2);
        else if (mode == MODE_LOCAL) boardView.setControls(0);
        else if (mode == MODE_BOT) boardView.setControls(thinking ? 2 : humanColor);
        else boardView.setControls(g != null && g.myTurn() ? g.myColor : 2);

        int topColor = boardView.isFlipped() ? ChessBoard.WHITE : ChessBoard.BLACK;
        int botColor = -topColor;
        topName.setText(nameOf(topColor));
        bottomName.setText(nameOf(botColor));
        topInfo.setText(infoOf(topColor));
        bottomInfo.setText(infoOf(botColor));
        actionBar.setSubtitle(subtitle());
        movesView.setText(moveList());
        movesScroll.post(() -> movesScroll.fullScroll(View.FOCUS_RIGHT));
        status.setText(statusText());
        fillButtons();
        updateClocks();
    }

    private String subtitle() {
        if (mode == MODE_ONLINE) {
            ChessNet.Game g = game();
            return g == null ? "" : ChessNet.tcTitle(g.tc) + " · " + getString(R.string.AvraChessRated);
        }
        String clock = localTc >= 0 ? " · " + ChessNet.tcTitle(localTc) : "";
        if (mode == MODE_BOT) return formatString(R.string.AvraChessBotLevel, getString(LEVEL_NAMES[level]), ChessAi.LEVEL_RATING[level]) + clock;
        return getString(R.string.AvraChessLocal) + clock;
    }

    public static final int[] LEVEL_NAMES = {R.string.AvraChessLevel0, R.string.AvraChessLevel1, R.string.AvraChessLevel2, R.string.AvraChessLevel3};

    private String nameOf(int color) {
        String side = getString(color == 1 ? R.string.AvraChessWhite : R.string.AvraChessBlack);
        if (mode == MODE_LOCAL) return side;
        if (mode == MODE_BOT) {
            if (color == humanColor) return getString(R.string.AvraChessYou);
            return "🤖 AvraBot · " + getString(LEVEL_NAMES[level]);
        }
        ChessNet.Game g = game();
        if (g == null) return side;
        if (color == g.myColor) return getString(R.string.AvraChessYou);
        TLRPC.User u = MessagesController.getInstance(currentAccount).getUser(g.peer);
        String n = u != null ? UserObject.getUserName(u) : g.oppName;
        return TextUtils.isEmpty(n) ? side : n;
    }

    private CharSequence infoOf(int color) {
        StringBuilder s = new StringBuilder();
        ChessNet.Game g = game();
        if (g != null) {
            int cat = ChessNet.TC[g.tc][2];
            int r = color == g.myColor ? ChessNet.rating(cat) : g.oppRating;
            s.append(r);
            if (g.state == ChessNet.FINISHED && color == g.myColor && g.ratingDelta != 0) {
                s.append(g.ratingDelta > 0 ? " (+" : " (").append(g.ratingDelta).append(")");
            }
            s.append("  ");
        } else if (mode == MODE_BOT && color != humanColor) {
            s.append(ChessAi.LEVEL_RATING[level]).append("  ");
        }
        // pieces this color has taken
        int[] start = {0, 8, 2, 2, 2, 1, 1};
        int[] have = new int[7];
        int material = 0;
        for (int sq = 0; sq < 64; sq++) {
            int p = board.sq[sq];
            if (p == 0) continue;
            if ((p > 0 ? 1 : -1) == -color) have[Math.abs(p)]++;
            material += (p > 0 ? 1 : -1) * ChessBoard.value(Math.abs(p));
        }
        String glyphs = color == 1 ? " ♟♞♝♜♛" : " ♙♘♗♖♕";
        for (int t = 5; t >= 1; t--) for (int i = have[t]; i < start[t]; i++) s.append(glyphs.charAt(t));
        int adv = material * color;
        if (adv > 0) s.append(" +").append(adv);
        return s;
    }

    private String moveList() {
        ChessBoard b = new ChessBoard();
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < board.moveCount(); i++) {
            int m = board.moveAt(i);
            if (i % 2 == 0) s.append(i / 2 + 1).append(". ");
            s.append(b.san(m)).append("  ");
            b.make(m);
        }
        return s.length() == 0 ? getString(R.string.AvraChessNoMoves) : s.toString().trim();
    }

    private boolean isOver() {
        if (mode == MODE_ONLINE) {
            ChessNet.Game g = game();
            return g == null || g.state == ChessNet.FINISHED || g.state == ChessNet.DECLINED;
        }
        return localOver || board.status() != ChessBoard.ONGOING;
    }

    private String statusText() {
        ChessNet.Game g = game();
        if (g != null) {
            switch (g.state) {
                case ChessNet.INVITE_OUT: return getString(R.string.AvraChessWaitAccept);
                case ChessNet.INVITE_IN: return getString(R.string.AvraChessInviteIn);
                case ChessNet.DECLINED: return getString(R.string.AvraChessDeclined);
                case ChessNet.FINISHED: return resultLine(g.result, ChessNet.reasonText(g.reason), g.myColor);
            }
            if (g.drawOfferIn) return getString(R.string.AvraChessDrawOfferIn);
            String check = board.inCheck() ? " · " + getString(R.string.AvraChessCheck) : "";
            return (g.myTurn() ? getString(R.string.AvraChessYourMove) : getString(R.string.AvraChessTheirMove)) + check;
        }
        if (localOver && localResult != null) return localResult;
        int st = board.status();
        if (st != ChessBoard.ONGOING) {
            int result = st == ChessBoard.MATE ? -board.side : 0;
            String reason = ChessNet.reasonText(st == ChessBoard.MATE ? "mate" : st == ChessBoard.STALEMATE ? "stalemate"
                    : st == ChessBoard.REPETITION ? "repetition" : st == ChessBoard.FIFTY ? "fifty" : "material");
            return resultLine(result, reason, mode == MODE_BOT ? humanColor : 0);
        }
        String side = getString(board.side == 1 ? R.string.AvraChessWhiteToMove : R.string.AvraChessBlackToMove);
        if (mode == MODE_BOT) side = thinking ? getString(R.string.AvraChessBotThinking) : board.side == humanColor ? getString(R.string.AvraChessYourMove) : side;
        return side + (board.inCheck() ? " · " + getString(R.string.AvraChessCheck) : "");
    }

    private String resultLine(int result, String reason, int me) {
        String head;
        if (result == 0) head = "🤝 " + getString(R.string.AvraChessDraw);
        else if (me == 0) head = "🏁 " + getString(result == 1 ? R.string.AvraChessWhiteWins : R.string.AvraChessBlackWins);
        else head = result == me ? "🏆 " + getString(R.string.AvraChessYouWon) : "💀 " + getString(R.string.AvraChessYouLost);
        return head + " · " + reason;
    }

    private void fillButtons() {
        buttons.removeAllViews();
        offerRow.removeAllViews();
        ChessNet.Game g = game();
        if (g != null) {
            if (g.state == ChessNet.INVITE_IN) {
                addButton(buttons, button(getString(R.string.AvraChessAccept), true, v -> ChessNet.accept(g)));
                addButton(buttons, button(getString(R.string.AvraChessDecline), false, v -> ChessNet.decline(g)));
            } else if (g.state == ChessNet.INVITE_OUT) {
                addButton(buttons, button(getString(R.string.AvraChessCancelInvite), false, v -> ChessNet.decline(g)));
            } else if (g.state == ChessNet.ACTIVE) {
                if (g.drawOfferIn) {
                    addButton(offerRow, button(getString(R.string.AvraChessAcceptDraw), true, v -> ChessNet.offerDraw(g)));
                    addButton(offerRow, button(getString(R.string.AvraChessDeclineDraw), false, v -> ChessNet.declineDraw(g)));
                }
                if (g.remaining(g.sideToMove()) <= 0 && g.sideToMove() != g.myColor) {
                    addButton(offerRow, button(getString(R.string.AvraChessClaimTime), true, v -> ChessNet.claimTimeout(g)));
                }
                addButton(buttons, button(getString(g.drawOfferOut ? R.string.AvraChessDrawOffered : R.string.AvraChessOfferDraw), false, v -> {
                    if (!g.drawOfferOut) ChessNet.offerDraw(g);
                }));
                addButton(buttons, button(getString(R.string.AvraChessResign), false, v -> confirmResign(() -> ChessNet.resign(g))));
            } else {
                addButton(buttons, button(getString(R.string.AvraChessRematch), true, v -> {
                    ChessNet.Game n = ChessNet.invite(currentAccount, g.peer, g.tc, -g.myColor);
                    presentFragment(new AvraChessGameActivity(n.id), true);
                }));
                if (g.state == ChessNet.FINISHED && !g.moves.isEmpty()) {
                    addButton(buttons, button(getString(R.string.AvraChessBtnReview), false, v -> openReview()));
                }
            }
            return;
        }
        boolean over = isOver();
        if (!over) {
            addButton(buttons, button(getString(R.string.AvraChessUndo), false, v -> undo()));
            addButton(buttons, button(getString(R.string.AvraChessResign), false, v -> confirmResign(() -> {
                localOver = true;
                int loser = mode == MODE_BOT ? humanColor : board.side;
                localResult = resultLine(-loser, ChessNet.reasonText("resign"), mode == MODE_BOT ? humanColor : 0);
                ChessSound.play(ChessSound.END);
                saveLocal();
                refresh(false);
            })));
        } else {
            addButton(buttons, button(getString(R.string.AvraChessNewGame), true, v -> {
                localOver = false;
                localResult = null;
                board = new ChessBoard();
                if (mode == MODE_BOT) humanColor = -humanColor;
                resetLocalClocks();
                ChessSound.play(ChessSound.START);
                refresh(false);
                if (mode == MODE_BOT && board.side != humanColor) AndroidUtilities.runOnUIThread(this::botMove, 400);
            }));
            if (board.moveCount() > 0) addButton(buttons, button(getString(R.string.AvraChessBtnReview), false, v -> openReview()));
            else addButton(buttons, button(getString(R.string.AvraChessUndo), false, v -> undo()));
        }
    }

    private void confirmResign(Runnable r) {
        if (!ChessStyle.confirmResign()) {
            r.run();
            return;
        }
        new AlertDialog.Builder(getContext())
                .setTitle(getString(R.string.AvraChessResignTitle))
                .setMessage(getString(R.string.AvraChessResignInfo))
                .setPositiveButton(getString(R.string.AvraChessResign), (d, w) -> r.run())
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private void undo() {
        if (thinking || mode == MODE_ONLINE) return;
        localOver = false;
        localResult = null;
        int n = mode == MODE_BOT ? (board.side == humanColor ? 2 : 1) : 1;
        for (int i = 0; i < n && board.moveCount() > 0; i++) board.unmake();
        refresh(false);
        saveLocal();
    }

    private void askPromotion(ArrayList<Integer> options) {
        Activity a = getParentActivity();
        if (a == null) return;
        String[] names = {getString(R.string.AvraChessQueen), getString(R.string.AvraChessKnight), getString(R.string.AvraChessRook), getString(R.string.AvraChessBishop)};
        int[] types = {ChessBoard.QUEEN, ChessBoard.KNIGHT, ChessBoard.ROOK, ChessBoard.BISHOP};
        String glyph = board.side == 1 ? "♕♘♖♗" : "♛♞♜♝";
        CharSequence[] items = new CharSequence[4];
        for (int i = 0; i < 4; i++) items[i] = glyph.charAt(i) + "  " + names[i];
        new AlertDialog.Builder(a).setTitle(getString(R.string.AvraChessPromote)).setItems(items, (d, which) -> {
            for (int m : options) if (ChessBoard.promo(m) == types[which]) {
                boardView.commit(m);
                return;
            }
        }).show();
    }

    // ---- moves -----------------------------------------------------------------------------------

    private void playerMove(int move) {
        if (isOver()) return;
        playSound(board, move);
        if (mode == MODE_ONLINE) {
            ChessNet.Game g = game();
            if (g == null || !g.myTurn()) return;
            ChessNet.move(g, g.board(), move);
            return; // the listener refreshes
        }
        tickLocal();
        if (localTc >= 0) {
            long inc = ChessNet.TC[localTc][1] * 1000L;
            if (board.side == 1) localW += inc;
            else localB += inc;
        }
        board.make(move);
        refresh(true);
        saveLocal();
        if (mode == MODE_BOT && !isOver()) AndroidUtilities.runOnUIThread(this::botMove, 250);
    }

    private void playSound(ChessBoard before, int move) {
        int fl = ChessBoard.flags(move);
        before.make(move);
        boolean check = before.inCheck();
        before.unmake();
        if (check) ChessSound.play(ChessSound.CHECK);
        else if ((fl & ChessBoard.F_CASTLE) != 0) ChessSound.play(ChessSound.CASTLE);
        else if ((fl & ChessBoard.F_CAPTURE) != 0) ChessSound.play(ChessSound.CAPTURE);
        else ChessSound.play(ChessSound.MOVE);
    }

    private void botMove() {
        if (mode != MODE_BOT || isOver() || board.side == humanColor || thinking) return;
        thinking = true;
        refresh(false);
        if (aiQueue == null) aiQueue = new DispatchQueue("avraChessAi");
        final ChessBoard snapshot = board.copy();
        final int lvl = level;
        aiQueue.postRunnable(() -> {
            int m = ai.think(snapshot, lvl);
            AndroidUtilities.runOnUIThread(() -> {
                thinking = false;
                if (fragmentView == null || isOver() || board.side == humanColor || board.key != snapshot.key) {
                    refresh(false);
                    return;
                }
                if (m != 0) {
                    tickLocal();
                    if (localTc >= 0) {
                        long inc = ChessNet.TC[localTc][1] * 1000L;
                        if (board.side == 1) localW += inc;
                        else localB += inc;
                    }
                    playSound(board, m);
                    board.make(m);
                }
                refresh(true);
                saveLocal();
            });
        });
    }

    // ---- clocks ----------------------------------------------------------------------------------

    private void tickLocal() {
        long now = System.currentTimeMillis();
        if (localTc >= 0 && !isOver() && board.moveCount() > 0) {
            long d = now - localTick;
            if (board.side == 1) localW -= d;
            else localB -= d;
        }
        localTick = now;
    }

    private void updateClocks() {
        if (topClock == null) return;
        int topColor = boardView.isFlipped() ? ChessBoard.WHITE : ChessBoard.BLACK;
        ChessNet.Game g = game();
        long wLeft, bLeft;
        boolean daily = false, running = !isOver();
        if (g != null) {
            daily = ChessNet.isDaily(g.tc);
            wLeft = g.remaining(1);
            bLeft = g.remaining(-1);
            running = g.state == ChessNet.ACTIVE;
            if (running && g.remaining(g.myColor) <= 0 && g.myTurn()) {
                ChessNet.claimTimeout(g); // you ran out of time
                return;
            }
            if (running && !g.myTurn() && g.remaining(g.sideToMove()) <= 0 && offerRow.getChildCount() == 0) fillButtons();
        } else if (localTc >= 0) {
            tickLocal();
            wLeft = localW;
            bLeft = localB;
            if (!localOver && (localW <= 0 || localB <= 0)) {
                localOver = true;
                int loser = localW <= 0 ? 1 : -1;
                localResult = resultLine(-loser, ChessNet.reasonText("time"), mode == MODE_BOT ? humanColor : 0);
                ChessSound.play(ChessSound.END);
                saveLocal();
                refresh(false);
                return;
            }
        } else {
            topClock.setVisibility(View.GONE);
            bottomClock.setVisibility(View.GONE);
            return;
        }
        topClock.setVisibility(View.VISIBLE);
        bottomClock.setVisibility(View.VISIBLE);
        int side = board.side;
        styleClock(topClock, topColor == 1 ? wLeft : bLeft, daily, running && side == topColor);
        styleClock(bottomClock, topColor == 1 ? bLeft : wLeft, daily, running && side == -topColor);
        long mine = myColor() == 1 ? wLeft : bLeft;
        if (running && !daily && side == myColor() && mine < 10_000 && mine > 0) {
            int sec = (int) (mine / 1000);
            if (sec != lowTimeBeeped) {
                lowTimeBeeped = sec;
                ChessSound.play(ChessSound.LOW_TIME);
            }
        }
    }

    private void styleClock(TextView v, long ms, boolean daily, boolean active) {
        ms = Math.max(0, ms);
        String t;
        if (daily) {
            long h = ms / 3_600_000;
            t = h >= 24 ? formatString(R.string.AvraChessDaysHours, h / 24, h % 24) : formatString(R.string.AvraChessHoursMinutes, h, ms / 60_000 % 60);
        } else if (ms < 10_000) {
            t = String.format(Locale.US, "%d.%d", ms / 1000, ms / 100 % 10);
        } else {
            long s = ms / 1000;
            t = s >= 3600 ? String.format(Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format(Locale.US, "%d:%02d", s / 60, s % 60);
        }
        v.setText(t);
        int bg = active ? (ms < 10_000 && !daily ? 0xFFE53935 : ChessStyle.accent()) : ChessStyle.surface();
        v.setTextColor(active ? (AndroidUtilities.computePerceivedBrightness(bg) > 0.6f ? 0xFF111111 : Color.WHITE) : ChessStyle.muted());
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setCornerRadius(dp(8));
        v.setBackground(d);
    }

    // ---- online updates --------------------------------------------------------------------------

    @Override
    public void onChessUpdate(ChessNet.Game g, boolean incoming) {
        if (mode != MODE_ONLINE || g == null || !g.id.equals(gameId)) return;
        if (incoming && g.moves.size() > board.moveCount()) {
            // the opponent's move: the same sounds as your own
            ChessBoard before = g.board();
            before.unmake();
            playSound(before, before.parseUci(g.moves.get(g.moves.size() - 1)));
        }
        refresh(true);
    }
}
