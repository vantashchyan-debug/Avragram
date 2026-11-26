package com.avragramm.chess;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;

/** The board: squares, highlights, legal move dots, drag or tap to move, move animation. */
public class ChessBoardView extends View {

    public interface Delegate {
        /** A legal move was made on the board (promotion already chosen). */
        void onMove(int move);

        /** Several promotion moves are possible: ask the user, then call {@link #commit(int)}. */
        void onPromotion(ArrayList<Integer> options);
    }

    private ChessBoard board = new ChessBoard();
    private Delegate delegate;
    private boolean flipped;
    /** Which color may move pieces: 1, -1, or 0 for both (two players on one phone); 2 = nobody. */
    private int controls = 0;

    private int selected = -1;
    private final ArrayList<Integer> targets = new ArrayList<>();
    private int dragFrom = -1;
    private float dragX, dragY;
    private boolean dragging;

    private int animFrom = -1, animTo = -1, animPiece;
    private long animStart;
    private static final long ANIM_MS = 170;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ChessBoardView(Context context) {
        super(context);
        text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    }

    public void setDelegate(Delegate d) {
        delegate = d;
    }

    public void setFlipped(boolean f) {
        flipped = f;
        invalidate();
    }

    public boolean isFlipped() {
        return flipped;
    }

    public void setControls(int c) {
        controls = c;
        clearSelection();
    }

    /** Shows a position; animates the last move when {@code animate}. */
    public void setBoard(ChessBoard b, boolean animate) {
        board = b;
        clearSelection();
        int last = b.lastMove();
        if (animate && last != 0 && ChessStyle.animate()) {
            animFrom = ChessBoard.from(last);
            animTo = ChessBoard.to(last);
            animPiece = b.sq[animTo];
            animStart = SystemClock.uptimeMillis();
        } else {
            animFrom = animTo = -1;
        }
        invalidate();
    }

    public ChessBoard getBoard() {
        return board;
    }

    private void clearSelection() {
        selected = -1;
        targets.clear();
        dragging = false;
        dragFrom = -1;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec), h = MeasureSpec.getSize(heightMeasureSpec);
        int s = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED ? w : Math.min(w, h);
        setMeasuredDimension(s, s);
    }

    private float cell() {
        return getWidth() / 8f;
    }

    private float sx(int sq) {
        int f = ChessBoard.file(sq);
        return (flipped ? 7 - f : f) * cell();
    }

    private float sy(int sq) {
        int r = ChessBoard.rank(sq);
        return (flipped ? r : 7 - r) * cell();
    }

    private int squareAt(float x, float y) {
        int c = (int) (x / cell()), r = (int) (y / cell());
        if (c < 0 || c > 7 || r < 0 || r > 7) return -1;
        int file = flipped ? 7 - c : c, rank = flipped ? r : 7 - r;
        return rank * 8 + file;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float c = cell();
        int light = ChessStyle.light(), dark = ChessStyle.dark(), hl = ChessStyle.highlight();
        int set = ChessStyle.pieceSet();
        for (int s = 0; s < 64; s++) {
            boolean isLight = (ChessBoard.file(s) + ChessBoard.rank(s)) % 2 == 1;
            paint.setShader(null);
            paint.setColor(isLight ? light : dark);
            float x = sx(s), y = sy(s);
            canvas.drawRect(x, y, x + c, y + c, paint);
        }
        // last move and selection
        int last = board.lastMove();
        if (last != 0 && ChessStyle.lastMove()) {
            paint.setColor(hl);
            paint.setAlpha(0x70);
            for (int s : new int[]{ChessBoard.from(last), ChessBoard.to(last)}) canvas.drawRect(sx(s), sy(s), sx(s) + c, sy(s) + c, paint);
        }
        if (selected >= 0) {
            paint.setColor(hl);
            paint.setAlpha(0xA0);
            canvas.drawRect(sx(selected), sy(selected), sx(selected) + c, sy(selected) + c, paint);
        }
        // check glow
        if (board.inCheck()) {
            int k = board.kingSquare(board.side);
            if (k >= 0) {
                float cx = sx(k) + c / 2, cy = sy(k) + c / 2;
                paint.setShader(new RadialGradient(cx, cy, c * 0.7f, new int[]{0xEEFF1744, 0x66FF1744, 0x00FF1744}, new float[]{0, 0.55f, 1}, Shader.TileMode.CLAMP));
                canvas.drawRect(sx(k), sy(k), sx(k) + c, sy(k) + c, paint);
                paint.setShader(null);
            }
        }
        // coordinates
        if (ChessStyle.coords()) {
            text.setTextSize(c * 0.2f);
            for (int i = 0; i < 8; i++) {
                int fileSq = flipped ? 7 - i : i;
                int bottom = flipped ? 56 + fileSq : fileSq;
                boolean bl = (ChessBoard.file(bottom) + ChessBoard.rank(bottom)) % 2 == 1;
                text.setColor(bl ? dark : light);
                text.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(String.valueOf((char) ('a' + fileSq)), (i + 1) * c - c * 0.06f, 8 * c - c * 0.07f, text);
                int rankIdx = flipped ? i : 7 - i;
                int left = rankIdx * 8 + (flipped ? 7 : 0);
                boolean ll = (ChessBoard.file(left) + ChessBoard.rank(left)) % 2 == 1;
                text.setColor(ll ? dark : light);
                text.setTextAlign(Paint.Align.LEFT);
                canvas.drawText(String.valueOf(rankIdx + 1), c * 0.06f, i * c + c * 0.22f, text);
            }
        }
        // pieces
        float animT = 1f;
        if (animFrom >= 0) {
            animT = Math.min(1f, (SystemClock.uptimeMillis() - animStart) / (float) ANIM_MS);
            if (animT >= 1f) animFrom = animTo = -1;
        }
        for (int s = 0; s < 64; s++) {
            int p = board.sq[s];
            if (p == 0 || (dragging && s == dragFrom) || s == animTo) continue;
            ChessStyle.drawPiece(canvas, p, sx(s) + c / 2, sy(s) + c / 2, c, set, 1f);
        }
        if (animTo >= 0) {
            float t = 1 - (1 - animT) * (1 - animT);
            float x = sx(animFrom) + (sx(animTo) - sx(animFrom)) * t, y = sy(animFrom) + (sy(animTo) - sy(animFrom)) * t;
            ChessStyle.drawPiece(canvas, animPiece, x + c / 2, y + c / 2, c, set, 1f);
            invalidate();
        }
        // legal targets
        if (ChessStyle.hints()) {
            for (int m : targets) {
                int t = ChessBoard.to(m);
                float cx = sx(t) + c / 2, cy = sy(t) + c / 2;
                paint.setColor(0x55000000);
                if (board.sq[t] != 0 || (ChessBoard.flags(m) & ChessBoard.F_EP) != 0) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(c * 0.08f);
                    canvas.drawCircle(cx, cy, c * 0.44f, paint);
                    paint.setStyle(Paint.Style.FILL);
                } else {
                    canvas.drawCircle(cx, cy, c * 0.16f, paint);
                }
            }
        }
        if (dragging && dragFrom >= 0) {
            ChessStyle.drawPiece(canvas, board.sq[dragFrom], dragX, dragY - c * 0.4f, c * 1.25f, set, 1f);
        }
    }

    private boolean canMove(int sq) {
        int p = board.sq[sq];
        if (p == 0 || controls == 2) return false;
        int color = p > 0 ? 1 : -1;
        if (color != board.side) return false;
        return controls == 0 || controls == color;
    }

    private void select(int sq) {
        selected = sq;
        targets.clear();
        for (int m : board.legal()) if (ChessBoard.from(m) == sq) targets.add(m);
        invalidate();
    }

    private boolean tryMove(int to) {
        ArrayList<Integer> options = new ArrayList<>();
        for (int m : targets) if (ChessBoard.to(m) == to) options.add(m);
        if (options.isEmpty()) return false;
        if (options.size() > 1) {
            if (ChessStyle.autoQueen()) {
                for (int m : options) if (ChessBoard.promo(m) == ChessBoard.QUEEN) {
                    commit(m);
                    return true;
                }
            }
            clearSelection();
            if (delegate != null) delegate.onPromotion(options);
            return true;
        }
        commit(options.get(0));
        return true;
    }

    /** Makes a move chosen on this board. */
    public void commit(int m) {
        clearSelection();
        animFrom = animTo = -1;
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        if (delegate != null) delegate.onMove(m);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int sq = squareAt(e.getX(), e.getY());
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (sq < 0) return false;
                if (selected >= 0 && sq != selected && !canMove(sq)) {
                    if (!tryMove(sq)) clearSelection();
                    return true;
                }
                if (canMove(sq)) {
                    select(sq);
                    dragFrom = sq;
                    dragX = e.getX();
                    dragY = e.getY();
                    dragging = false;
                    getParent().requestDisallowInterceptTouchEvent(true);
                } else {
                    clearSelection();
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragFrom >= 0) {
                    if (!dragging && Math.hypot(e.getX() - dragX, e.getY() - dragY) > cell() * 0.15f) dragging = true;
                    dragX = e.getX();
                    dragY = e.getY();
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging && dragFrom >= 0) {
                    dragging = false;
                    int from = dragFrom;
                    dragFrom = -1;
                    if (sq >= 0 && sq != from && tryMove(sq)) return true;
                    invalidate();
                }
                dragFrom = -1;
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                dragFrom = -1;
                invalidate();
                return true;
        }
        return false;
    }
}
