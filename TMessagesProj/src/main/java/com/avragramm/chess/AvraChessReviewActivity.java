package com.avragramm.chess;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.Locale;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

/**
 * "Game review": step through every move of a finished game, see the engine's verdict for each one
 * (brilliant … blunder), the better move as an arrow, the evaluation bar and both players' accuracy.
 */
public class AvraChessReviewActivity extends BaseFragment {

    private static final int MENU_FLIP = 1;
    private static DispatchQueue queue;

    private final ArrayList<String> moves;
    private final String whiteName, blackName, resultLine;
    private boolean flipped;

    private ChessReview.Result result;
    private int ply; // number of moves shown, 0..moves.size()
    private final ArrayList<ChessBoard> positions = new ArrayList<>();
    private final ArrayList<String> sans = new ArrayList<>();
    private boolean autoplay;
    private volatile boolean destroyed;

    private ChessBoardView boardView;
    private EvalBar evalBar;
    private TextView progressText, accW, accB, moveInfo, autoButton;
    private LinearLayout summary, countsTable, movesTable;
    private final ArrayList<TextView> moveCells = new ArrayList<>();

    private final Runnable autoStep = new Runnable() {
        @Override
        public void run() {
            if (!autoplay || destroyed) return;
            if (ply >= moves.size()) {
                setAuto(false);
                return;
            }
            go(ply + 1, true);
            AndroidUtilities.runOnUIThread(this, 1100);
        }
    };

    /** bottomColor: whose side is at the bottom (1 white, -1 black). */
    public AvraChessReviewActivity(ArrayList<String> moves, String whiteName, String blackName, String resultLine, int bottomColor) {
        this.moves = new ArrayList<>(moves);
        this.whiteName = whiteName;
        this.blackName = blackName;
        this.resultLine = resultLine;
        this.flipped = bottomColor == ChessBoard.BLACK;
        ChessBoard b = new ChessBoard();
        positions.add(b.copy());
        for (int i = 0; i < this.moves.size(); i++) {
            int m = b.parseUci(this.moves.get(i));
            if (m == 0) {
                while (this.moves.size() > i) this.moves.remove(this.moves.size() - 1);
                break;
            }
            sans.add(b.san(m));
            b.make(m);
            positions.add(b.copy());
        }
        ply = this.moves.size();
    }

    @Override
    public boolean onFragmentCreate() {
        result = ChessReview.cached(moves);
        if (result == null) startAnalysis();
        ChessSound.preload();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        destroyed = true;
        AndroidUtilities.cancelRunOnUIThread(autoStep);
        super.onFragmentDestroy();
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return false;
    }

    @Override
    public boolean isLightStatusBar() {
        return AndroidUtilities.computePerceivedBrightness(ChessStyle.surface()) > 0.7f;
    }

    private void startAnalysis() {
        if (queue == null) queue = new DispatchQueue("avraChessReview");
        final ArrayList<String> list = new ArrayList<>(moves);
        queue.postRunnable(() -> {
            ChessReview.Result r = ChessReview.analyse(list, (done, total) -> {
                if (destroyed) return;
                AndroidUtilities.runOnUIThread(() -> {
                    if (progressText != null && result == null) {
                        progressText.setText(formatString(R.string.AvraChessReviewProgress, total == 0 ? 100 : done * 100 / total));
                    }
                });
            });
            ChessReview.store(list, r);
            AndroidUtilities.runOnUIThread(() -> {
                result = r;
                if (fragmentView != null) {
                    fillSummary();
                    fillMoves();
                    go(ply, false);
                }
            });
        });
    }

    // ---- UI ----------------------------------------------------------------------------------------

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.AvraChessReview));
        actionBar.setSubtitle(whiteName + " — " + blackName);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
                else if (id == MENU_FLIP) {
                    flipped = !flipped;
                    boardView.setFlipped(flipped, true);
                }
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(MENU_FLIP, R.drawable.msg_switch);
        actionBar.setBackgroundColor(ChessStyle.surface());
        actionBar.setTitleColor(ChessStyle.text());
        actionBar.setSubtitleColor(ChessStyle.muted());
        actionBar.setItemsColor(ChessStyle.text(), false);
        actionBar.setItemsBackgroundColor(0x22000000 | (ChessStyle.accent() & 0xFFFFFF), false);

        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundColor(ChessStyle.bg());
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // accuracy summary
        summary = new LinearLayout(context);
        summary.setOrientation(LinearLayout.VERTICAL);
        summary.setBackground(card());
        summary.setPadding(dp(14), dp(10), dp(14), dp(10));
        root.addView(summary, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 10, 8, 10, 6));
        TextView res = text(context, 14, true, ChessStyle.text());
        res.setText(resultLine);
        res.setGravity(Gravity.CENTER);
        summary.addView(res, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        LinearLayout accRow = new LinearLayout(context);
        accRow.setGravity(Gravity.CENTER);
        accW = pill(context, 0xFFF5F5F5, 0xFF111111);
        accB = pill(context, 0xFF2A2A2A, 0xFFFFFFFF);
        TextView accLabel = text(context, 13, false, ChessStyle.muted());
        accLabel.setText(getString(R.string.AvraChessAccuracy));
        accLabel.setGravity(Gravity.CENTER);
        accRow.addView(accW, LayoutHelper.createLinear(0, 44, 1f));
        accRow.addView(accLabel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 10, 0, 10, 0));
        accRow.addView(accB, LayoutHelper.createLinear(0, 44, 1f));
        summary.addView(accRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));
        progressText = text(context, 13, false, ChessStyle.muted());
        progressText.setGravity(Gravity.CENTER);
        summary.addView(progressText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));

        evalBar = new EvalBar(context);
        root.addView(evalBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 22, 10, 4, 10, 6));

        boardView = new ChessBoardView(context);
        boardView.setControls(2);
        boardView.setFlipped(flipped);
        root.addView(boardView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 4, 0, 4, 0));

        moveInfo = text(context, 15, true, ChessStyle.text());
        moveInfo.setGravity(Gravity.CENTER);
        moveInfo.setPadding(dp(12), dp(10), dp(12), dp(4));
        root.addView(moveInfo, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout nav = new LinearLayout(context);
        nav.setGravity(Gravity.CENTER);
        nav.addView(navButton(context, "⏮", v -> go(0, false)), LayoutHelper.createLinear(0, 46, 1f, 4, 0, 4, 0));
        nav.addView(navButton(context, "◀", v -> go(ply - 1, false)), LayoutHelper.createLinear(0, 46, 1f, 4, 0, 4, 0));
        autoButton = navButton(context, "▶▶", v -> setAuto(!autoplay));
        nav.addView(autoButton, LayoutHelper.createLinear(0, 46, 1f, 4, 0, 4, 0));
        nav.addView(navButton(context, "▶", v -> go(ply + 1, true)), LayoutHelper.createLinear(0, 46, 1f, 4, 0, 4, 0));
        nav.addView(navButton(context, "⏭", v -> go(moves.size(), false)), LayoutHelper.createLinear(0, 46, 1f, 4, 0, 4, 0));
        root.addView(nav, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 8, 6, 8, 8));

        countsTable = new LinearLayout(context);
        countsTable.setOrientation(LinearLayout.VERTICAL);
        countsTable.setBackground(card());
        countsTable.setPadding(dp(14), dp(8), dp(14), dp(8));
        root.addView(countsTable, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 10, 4, 10, 6));

        movesTable = new LinearLayout(context);
        movesTable.setOrientation(LinearLayout.VERTICAL);
        movesTable.setBackground(card());
        movesTable.setPadding(dp(8), dp(6), dp(8), dp(6));
        root.addView(movesTable, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 10, 4, 10, 16));

        fragmentView = scroll;
        fillSummary();
        fillMoves();
        go(ply, false);
        return fragmentView;
    }

    private GradientDrawable card() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(ChessStyle.surface());
        d.setCornerRadius(dp(14));
        return d;
    }

    private static TextView text(Context c, int sp, boolean bold, int color) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sp);
        if (bold) t.setTypeface(AndroidUtilities.bold());
        t.setTextColor(color);
        return t;
    }

    private TextView pill(Context c, int bg, int fg) {
        TextView t = text(c, 20, true, fg);
        t.setGravity(Gravity.CENTER);
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setCornerRadius(dp(12));
        t.setBackground(d);
        return t;
    }

    private TextView navButton(Context c, String label, View.OnClickListener l) {
        TextView b = text(c, 18, true, ChessStyle.text());
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setBackground(card());
        b.setOnClickListener(l);
        return b;
    }

    private void setAuto(boolean on) {
        autoplay = on;
        autoButton.setText(on ? "⏸" : "▶▶");
        AndroidUtilities.cancelRunOnUIThread(autoStep);
        if (on) {
            if (ply >= moves.size()) go(0, false);
            AndroidUtilities.runOnUIThread(autoStep, 600);
        }
    }

    private void fillSummary() {
        if (result == null) {
            accW.setText("…");
            accB.setText("…");
            progressText.setVisibility(View.VISIBLE);
            if (TextUtils.isEmpty(progressText.getText())) progressText.setText(formatString(R.string.AvraChessReviewProgress, 0));
            countsTable.setVisibility(View.GONE);
            return;
        }
        progressText.setVisibility(View.GONE);
        accW.setText(String.format(Locale.US, "%.1f", result.accWhite));
        accB.setText(String.format(Locale.US, "%.1f", result.accBlack));
        countsTable.setVisibility(View.VISIBLE);
        countsTable.removeAllViews();
        Context c = getContext();
        LinearLayout head = new LinearLayout(c);
        head.addView(new View(c), LayoutHelper.createLinear(0, 1, 1f));
        TextView hw = text(c, 13, true, ChessStyle.muted());
        hw.setText(shortName(whiteName));
        hw.setGravity(Gravity.CENTER);
        TextView hb = text(c, 13, true, ChessStyle.muted());
        hb.setText(shortName(blackName));
        hb.setGravity(Gravity.CENTER);
        head.addView(hw, LayoutHelper.createLinear(84, LayoutHelper.WRAP_CONTENT));
        head.addView(hb, LayoutHelper.createLinear(84, LayoutHelper.WRAP_CONTENT));
        countsTable.addView(head, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));
        for (int k = 0; k < ChessReview.KINDS; k++) {
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(badge(c, k, 22), LayoutHelper.createLinear(22, 22));
            TextView name = text(c, 14, false, ChessStyle.text());
            name.setText(getString(ChessReview.NAMES[k]));
            row.addView(name, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 10, 0, 0, 0));
            for (int side = 0; side < 2; side++) {
                TextView n = text(c, 15, true, ChessReview.COLORS[k]);
                n.setText(String.valueOf(result.counts[side][k]));
                n.setGravity(Gravity.CENTER);
                if (result.counts[side][k] == 0) n.setTextColor(ChessStyle.muted());
                row.addView(n, LayoutHelper.createLinear(84, LayoutHelper.WRAP_CONTENT));
            }
            countsTable.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 32));
        }
    }

    private static String shortName(String s) {
        return s == null ? "" : s.length() > 10 ? s.substring(0, 9) + "…" : s;
    }

    private View badge(Context c, int kind, int sizeDp) {
        TextView t = text(c, kind == ChessReview.BOOK ? 13 : 11, true, 0xFFFFFFFF);
        t.setText(ChessReview.SYMBOLS[kind]);
        t.setGravity(Gravity.CENTER);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(ChessReview.COLORS[kind]);
        t.setBackground(d);
        t.setIncludeFontPadding(false);
        return t;
    }

    private void fillMoves() {
        movesTable.removeAllViews();
        moveCells.clear();
        Context c = getContext();
        for (int i = 0; i < moves.size(); i += 2) {
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView num = text(c, 13, false, ChessStyle.muted());
            num.setText((i / 2 + 1) + ".");
            row.addView(num, LayoutHelper.createLinear(40, LayoutHelper.WRAP_CONTENT, 6, 0, 0, 0));
            for (int j = i; j < i + 2; j++) {
                TextView cell = text(c, 15, false, ChessStyle.text());
                cell.setPadding(dp(8), dp(5), dp(8), dp(5));
                if (j < moves.size()) {
                    SpannableStringBuilder s = new SpannableStringBuilder(sans.get(j));
                    if (result != null && j < result.kind.length) {
                        int k = result.kind[j];
                        s.append("  ");
                        int st = s.length();
                        s.append(ChessReview.SYMBOLS[k]);
                        s.setSpan(new ForegroundColorSpan(ChessReview.COLORS[k]), st, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    cell.setText(s);
                    final int target = j + 1;
                    cell.setOnClickListener(v -> go(target, false));
                }
                moveCells.add(cell);
                row.addView(cell, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            }
            movesTable.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        if (moves.isEmpty()) {
            TextView t = text(c, 14, false, ChessStyle.muted());
            t.setText(getString(R.string.AvraChessNoMoves));
            t.setPadding(dp(8), dp(8), dp(8), dp(8));
            movesTable.addView(t);
        }
    }

    /** Shows the position after {@code target} moves. */
    private void go(int target, boolean sound) {
        target = Math.max(0, Math.min(moves.size(), target));
        boolean forwardOne = target == ply + 1;
        ply = target;
        ChessBoard b = positions.get(ply);
        boardView.setBoard(b, true);
        if (sound && forwardOne && ply > 0) {
            ChessBoard before = positions.get(ply - 1);
            int m = b.lastMove();
            int fl = ChessBoard.flags(m);
            if (b.inCheck()) ChessSound.play(ChessSound.CHECK);
            else if ((fl & ChessBoard.F_CASTLE) != 0) ChessSound.play(ChessSound.CASTLE);
            else if ((fl & ChessBoard.F_CAPTURE) != 0 || before.sq[ChessBoard.to(m)] != 0) ChessSound.play(ChessSound.CAPTURE);
            else ChessSound.play(ChessSound.MOVE);
        }
        for (int i = 0; i < moveCells.size(); i++) {
            TextView cell = moveCells.get(i);
            if (i == ply - 1) {
                GradientDrawable d = new GradientDrawable();
                d.setColor(0x33000000 | (ChessStyle.accent() & 0xFFFFFF));
                d.setCornerRadius(dp(8));
                cell.setBackground(d);
            } else {
                cell.setBackground(null);
            }
        }
        ArrayList<int[]> arrows = new ArrayList<>();
        SpannableStringBuilder info = new SpannableStringBuilder();
        if (ply == 0) {
            boardView.setBadge(-1, 0, null);
            info.append(getString(R.string.AvraChessReviewStart));
        } else {
            int i = ply - 1;
            int m = b.lastMove();
            info.append(i / 2 + 1 + (i % 2 == 0 ? ". " : "… ")).append(sans.get(i));
            if (result != null && i < result.kind.length) {
                int k = result.kind[i];
                boardView.setBadge(ChessBoard.to(m), ChessReview.COLORS[k], ChessReview.SYMBOLS[k]);
                info.append(" — ");
                int st = info.length();
                info.append(getString(ChessReview.NAMES[k]));
                info.setSpan(new ForegroundColorSpan(ChessReview.COLORS[k]), st, info.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                int best = result.best[i];
                if (best != 0 && best != m && k >= ChessReview.INACCURACY) {
                    arrows.add(new int[]{ChessBoard.from(best), ChessBoard.to(best), 0xCC81B64C});
                    info.append('\n').append(formatString(R.string.AvraChessReviewBetter, positions.get(i).san(best)));
                }
            } else {
                boardView.setBadge(-1, 0, null);
            }
        }
        boardView.setArrows(arrows);
        moveInfo.setText(info);
        if (result != null) {
            int ev = ply == 0 ? result.evalStart : ply - 1 < result.eval.length ? result.eval[ply - 1] : 0;
            ChessBoard pos = positions.get(ply);
            if (pos.status() == ChessBoard.MATE) ev = pos.side == 1 ? -100_000 + 1 : 100_000 - 1;
            evalBar.set(ev);
        }
    }

    /** Horizontal evaluation bar: white share = White's chances. */
    private static class EvalBar extends View {
        private float from = 0.5f, to = 0.5f;
        private long start;
        private String label = "0.0";
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        EvalBar(Context c) {
            super(c);
            p.setTypeface(AndroidUtilities.bold());
        }

        void set(int cp) {
            float now = current();
            from = now;
            to = ChessReview.winChance(cp) / 100f;
            start = SystemClock.uptimeMillis();
            if (ChessAi.isMate(cp)) label = "M" + Math.abs(ChessAi.mateIn(cp));
            else label = String.format(Locale.US, "%+.1f", cp / 100f);
            invalidate();
        }

        private float current() {
            float t = Math.min(1f, (SystemClock.uptimeMillis() - start) / 350f);
            t = 1 - (1 - t) * (1 - t) * (1 - t);
            return from + (to - from) * t;
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), rad = h / 2;
            float v = current();
            r.set(0, 0, w, h);
            p.setColor(0xFF2A2A2A);
            c.drawRoundRect(r, rad, rad, p);
            c.save();
            c.clipRect(0, 0, w * v, h);
            p.setColor(0xFFF2F2F2);
            c.drawRoundRect(r, rad, rad, p);
            c.restore();
            p.setTextSize(h * 0.6f);
            boolean whiteBetter = v >= 0.5f;
            p.setColor(whiteBetter ? 0xFF111111 : 0xFFF2F2F2);
            p.setTextAlign(whiteBetter ? Paint.Align.LEFT : Paint.Align.RIGHT);
            Paint.FontMetrics fm = p.getFontMetrics();
            c.drawText(label, whiteBetter ? h * 0.5f : w - h * 0.5f, h / 2 - (fm.ascent + fm.descent) / 2, p);
            if (SystemClock.uptimeMillis() - start < 360) postInvalidateOnAnimation();
        }
    }
}
