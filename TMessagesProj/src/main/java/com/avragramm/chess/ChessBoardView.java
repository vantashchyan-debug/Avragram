package com.avragramm.chess;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;

/**
 * The board: squares, highlights, legal move dots, tap or drag & drop to move, and smooth animations —
 * every position change slides the pieces (captures fade out, promotions pop in), a dragged piece
 * lifts with a shadow and snaps into place, illegal drops fly back, the board turns when flipped,
 * check pulses. Review mode adds arrows and move-quality badges.
 */
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

    /** The position on screen once the running animations end. */
    private final int[] shown = new int[64];
    private boolean hasShown;

    private int selected = -1;
    private long selectStart;
    private final ArrayList<Integer> targets = new ArrayList<>();
    private long targetsStart;
    private boolean wasSelected;

    private int dragFrom = -1;
    private float downX, downY, dragX, dragY;
    private boolean dragging;
    private long liftStart;
    private int hoverSq = -1;
    /** Where a dropped piece was released (square units), so it snaps from there instead of sliding. */
    private float dropF = -1, dropR;
    private int dropFrom = -1;

    private long lastMoveStart;
    private long checkStart;
    private int checkKing = -1;

    private long flipStart;
    private static final long FLIP_MS = 420;

    // ---- running animations ----
    private static final class Fly {
        int piece, toSq;
        float f0, r0, f1, r1;
        long start, dur, delay;
    }

    private static final class Fade {
        int piece, sq;
        long start, dur, delay;
        boolean in;
    }

    private final ArrayList<Fly> flies = new ArrayList<>();
    private final ArrayList<Fade> fades = new ArrayList<>();

    // ---- review decorations ----
    private final ArrayList<int[]> arrows = new ArrayList<>();
    private int badgeSq = -1, badgeColor;
    private String badgeText;
    private long badgeStart;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public ChessBoardView(Context context) {
        super(context);
        text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    }

    public void setDelegate(Delegate d) {
        delegate = d;
    }

    public void setFlipped(boolean f) {
        setFlipped(f, false);
    }

    /** Turns the board; with {@code animate} it rotates half a turn while the pieces stay upright. */
    public void setFlipped(boolean f, boolean animate) {
        if (f == flipped) return;
        flipped = f;
        flipStart = animate && ChessStyle.animate() && isLaidOut() ? SystemClock.uptimeMillis() : 0;
        invalidate();
    }

    public boolean isFlipped() {
        return flipped;
    }

    public void setControls(int c) {
        if (controls == c) return;
        controls = c;
        clearSelection();
    }

    /** Shows a position; with {@code animate} the change from the current picture is animated. */
    public void setBoard(ChessBoard b, boolean animate) {
        board = b;
        long now = SystemClock.uptimeMillis();
        boolean changed = false;
        for (int s = 0; s < 64; s++) if (shown[s] != b.sq[s]) changed = true;
        if (!hasShown || !changed) {
            if (!hasShown) System.arraycopy(b.sq, 0, shown, 0, 64);
            hasShown = true;
            if (!changed) {
                invalidate();
                return;
            }
        }
        clearSelection();
        flies.clear();
        fades.clear();
        if (animate && ChessStyle.animate()) diff(shown, b.sq, now);
        System.arraycopy(b.sq, 0, shown, 0, 64);
        dropFrom = -1;
        lastMoveStart = now;
        int k = b.inCheck() ? b.kingSquare(b.side) : -1;
        if (k >= 0 && k != checkKing) checkStart = now;
        checkKing = k;
        invalidate();
    }

    /** Pairs the pieces that left a square with the ones that arrived, and animates each pair. */
    private void diff(int[] from, int[] to, long now) {
        ArrayList<Integer> gone = new ArrayList<>(), came = new ArrayList<>();
        for (int s = 0; s < 64; s++) {
            if (from[s] != 0 && from[s] != to[s]) gone.add(s);
            if (to[s] != 0 && to[s] != from[s]) came.add(s);
        }
        boolean[] usedGone = new boolean[gone.size()];
        ArrayList<Integer> unmatched = new ArrayList<>();
        // same piece first, then a pawn of the same color (promotion)
        for (int pass = 0; pass < 2; pass++) {
            ArrayList<Integer> rest = new ArrayList<>();
            for (int a : (pass == 0 ? came : unmatched)) {
                int p = to[a], best = -1;
                float bestD = 99;
                for (int i = 0; i < gone.size(); i++) {
                    if (usedGone[i]) continue;
                    int g = gone.get(i), q = from[g];
                    boolean ok = pass == 0 ? q == p : (q == (p > 0 ? ChessBoard.PAWN : -ChessBoard.PAWN));
                    if (!ok) continue;
                    if (g == dropFrom) {
                        best = i;
                        break;
                    }
                    float d = (float) Math.hypot(ChessBoard.file(g) - ChessBoard.file(a), ChessBoard.rank(g) - ChessBoard.rank(a));
                    if (d < bestD) {
                        bestD = d;
                        best = i;
                    }
                }
                if (best < 0) {
                    rest.add(a);
                    continue;
                }
                usedGone[best] = true;
                int g = gone.get(best);
                Fly f = new Fly();
                f.piece = from[g];
                f.toSq = a;
                f.f1 = ChessBoard.file(a);
                f.r1 = ChessBoard.rank(a);
                if (g == dropFrom && dropF >= 0) {
                    f.f0 = dropF;
                    f.r0 = dropR;
                    f.dur = 110;
                } else {
                    f.f0 = ChessBoard.file(g);
                    f.r0 = ChessBoard.rank(g);
                    float d = (float) Math.hypot(f.f1 - f.f0, f.r1 - f.r0);
                    f.dur = (long) Math.min(320, 170 + 22 * d);
                }
                f.start = now;
                flies.add(f);
                if (q(from[g]) != q(to[a])) { // promotion: the new piece pops in at the end
                    Fade in = new Fade();
                    in.in = true;
                    in.piece = to[a];
                    in.sq = a;
                    in.start = now;
                    in.delay = f.dur;
                    in.dur = 160;
                    fades.add(in);
                    f.piece = from[g];
                }
            }
            unmatched = rest;
        }
        for (int i = 0; i < gone.size(); i++) {
            if (usedGone[i]) continue;
            Fade out = new Fade();
            out.piece = from[gone.get(i)];
            out.sq = gone.get(i);
            out.start = now;
            out.delay = 60;
            out.dur = 200;
            fades.add(out);
        }
        for (int a : unmatched) {
            Fade in = new Fade();
            in.in = true;
            in.piece = to[a];
            in.sq = a;
            in.start = now;
            in.dur = 200;
            fades.add(in);
        }
    }

    private static int q(int p) {
        return Math.abs(p);
    }

    public ChessBoard getBoard() {
        return board;
    }

    private void clearSelection() {
        selected = -1;
        targets.clear();
        dragging = false;
        dragFrom = -1;
        hoverSq = -1;
        invalidate();
    }

    // ---- review decorations -----------------------------------------------------------------------

    /** Arrows {from, to, color}; null or empty clears. */
    public void setArrows(ArrayList<int[]> list) {
        arrows.clear();
        if (list != null) arrows.addAll(list);
        invalidate();
    }

    /** A round move-quality badge on the corner of a square; sq -1 hides it. */
    public void setBadge(int sq, int color, String symbol) {
        if (sq != badgeSq || !android.text.TextUtils.equals(symbol, badgeText)) badgeStart = SystemClock.uptimeMillis();
        badgeSq = sq;
        badgeColor = color;
        badgeText = symbol;
        invalidate();
    }

    // ---- geometry ---------------------------------------------------------------------------------

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec), h = MeasureSpec.getSize(heightMeasureSpec);
        int s = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED ? w : Math.min(w, h);
        setMeasuredDimension(s, s);
    }

    private float cell() {
        return getWidth() / 8f;
    }

    /** Left edge of a (fractional) file, in pixels, before the flip rotation. */
    private float fx(float file) {
        return (flipped ? 7 - file : file) * cell();
    }

    private float ry(float rank) {
        return (flipped ? rank : 7 - rank) * cell();
    }

    private float sx(int sq) {
        return fx(ChessBoard.file(sq));
    }

    private float sy(int sq) {
        return ry(ChessBoard.rank(sq));
    }

    private int squareAt(float x, float y) {
        int c = (int) Math.floor(x / cell()), r = (int) Math.floor(y / cell());
        if (c < 0 || c > 7 || r < 0 || r > 7) return -1;
        int file = flipped ? 7 - c : c, rank = flipped ? r : 7 - r;
        return rank * 8 + file;
    }

    private static float ease(float t) {
        t = Math.max(0, Math.min(1, t));
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    private static float easeInOut(float t) {
        t = Math.max(0, Math.min(1, t));
        return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
    }

    private static float overshoot(float t) {
        t = Math.max(0, Math.min(1, t));
        float s = 1.9f;
        t -= 1;
        return t * t * ((s + 1) * t + s) + 1;
    }

    // ---- drawing ----------------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        boolean again = false;
        float c = cell();
        float half = getWidth() / 2f;
        int light = ChessStyle.light(), dark = ChessStyle.dark(), hl = ChessStyle.highlight();
        int set = ChessStyle.pieceSet();

        // flip: the board layer turns, pieces ride along but stay upright
        float flipT = flipStart == 0 ? 1 : easeInOut((now - flipStart) / (float) FLIP_MS);
        if (flipT >= 1) flipStart = 0;
        else again = true;
        float angle = (1 - flipT) * 180f;

        canvas.save();
        if (angle != 0) canvas.rotate(angle, half, half);
        paint.setStyle(Paint.Style.FILL);
        for (int s = 0; s < 64; s++) {
            boolean isLight = (ChessBoard.file(s) + ChessBoard.rank(s)) % 2 == 1;
            paint.setShader(null);
            paint.setColor(isLight ? light : dark);
            float x = sx(s), y = sy(s);
            canvas.drawRect(x, y, x + c, y + c, paint);
        }
        // last move (fades in)
        int last = board.lastMove();
        if (last != 0 && ChessStyle.lastMove()) {
            float t = ease((now - lastMoveStart) / 220f);
            if (t < 1) again = true;
            paint.setColor(hl);
            paint.setAlpha((int) (0x70 * t));
            for (int s : new int[]{ChessBoard.from(last), ChessBoard.to(last)}) canvas.drawRect(sx(s), sy(s), sx(s) + c, sy(s) + c, paint);
        }
        if (selected >= 0) {
            float t = ease((now - selectStart) / 140f);
            if (t < 1) again = true;
            paint.setColor(hl);
            paint.setAlpha((int) (0xA8 * t));
            canvas.drawRect(sx(selected), sy(selected), sx(selected) + c, sy(selected) + c, paint);
        }
        // check: a red glow that pulses three times
        if (checkKing >= 0 && board.inCheck()) {
            float pt = (now - checkStart) / 1000f;
            float pulse = pt < 1.8f ? (float) (0.5 + 0.5 * Math.cos(pt * Math.PI * 2 / 0.6)) : 1f;
            if (pt < 1.8f) again = true;
            float cx = sx(checkKing) + c / 2, cy = sy(checkKing) + c / 2;
            int a1 = (int) (0x90 + 0x6F * pulse), a2 = (int) (0x30 + 0x40 * pulse);
            paint.setShader(new RadialGradient(cx, cy, c * (0.62f + 0.12f * pulse), new int[]{(a1 << 24) | 0xFF1744, (a2 << 24) | 0xFF1744, 0x00FF1744},
                    new float[]{0, 0.55f, 1}, Shader.TileMode.CLAMP));
            canvas.drawRect(sx(checkKing), sy(checkKing), sx(checkKing) + c, sy(checkKing) + c, paint);
            paint.setShader(null);
        }
        // drag hover: a ring on the square under the piece
        if (dragging && hoverSq >= 0 && hoverSq != dragFrom) {
            boolean legal = false;
            for (int m : targets) if (ChessBoard.to(m) == hoverSq) legal = true;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(c * 0.07f);
            paint.setColor(legal ? 0xCCFFFFFF : 0x55FFFFFF);
            canvas.drawRect(sx(hoverSq) + c * 0.035f, sy(hoverSq) + c * 0.035f, sx(hoverSq) + c * 0.965f, sy(hoverSq) + c * 0.965f, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        canvas.restore();

        // coordinates (hidden while turning)
        if (ChessStyle.coords() && angle == 0) {
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

        // pieces at rest
        boolean[] busy = new boolean[64];
        for (int i = flies.size() - 1; i >= 0; i--) {
            Fly f = flies.get(i);
            if (now - f.start - f.delay >= f.dur) flies.remove(i);
            else busy[f.toSq] = true;
        }
        for (int i = fades.size() - 1; i >= 0; i--) {
            Fade f = fades.get(i);
            if (now - f.start - f.delay >= f.dur) fades.remove(i);
            else if (f.in) busy[f.sq] = true;
        }
        for (int s = 0; s < 64; s++) {
            int p = shown[s];
            if (p == 0 || busy[s] || (dragging && s == dragFrom)) continue;
            drawAt(canvas, p, s, c, set, 1f, 1f, angle, half);
        }
        // captured pieces shrink away, promoted ones pop in
        for (Fade f : fades) {
            float t = (now - f.start - f.delay) / (float) f.dur;
            if (t < 0) {
                if (!f.in) drawAt(canvas, f.piece, f.sq, c, set, 1f, 1f, angle, half);
                continue;
            }
            float e = f.in ? overshoot(t) : ease(t);
            float scale = f.in ? 0.4f + 0.6f * e : 1f - 0.35f * e;
            float alpha = f.in ? Math.min(1f, t * 2) : 1f - e;
            drawAt(canvas, f.piece, f.sq, c, set, alpha, scale, angle, half);
        }
        if (!fades.isEmpty()) again = true;

        // legal targets pop in
        if (ChessStyle.hints() && !targets.isEmpty()) {
            float t = (now - targetsStart) / 180f;
            if (t < 1) again = true;
            float k = overshoot(t);
            canvas.save();
            if (angle != 0) canvas.rotate(angle, half, half);
            for (int m : targets) {
                int to = ChessBoard.to(m);
                float cx = sx(to) + c / 2, cy = sy(to) + c / 2;
                paint.setColor(0x4A000000);
                if (board.sq[to] != 0 || (ChessBoard.flags(m) & ChessBoard.F_EP) != 0) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(c * 0.08f);
                    canvas.drawCircle(cx, cy, c * 0.44f * Math.max(0.5f, k), paint);
                    paint.setStyle(Paint.Style.FILL);
                } else {
                    canvas.drawCircle(cx, cy, c * 0.16f * k, paint);
                }
            }
            canvas.restore();
        }

        // moving pieces on top
        for (Fly f : flies) {
            float t = ease((now - f.start - f.delay) / (float) f.dur);
            float ff = f.f0 + (f.f1 - f.f0) * t, rr = f.r0 + (f.r1 - f.r0) * t;
            float x = fx(ff) + c / 2, y = ry(rr) + c / 2;
            float lift = (float) Math.sin(Math.PI * t) * 0.08f;
            drawPx(canvas, f.piece, x, y, c * (1 + lift), set, 1f, angle, half);
        }
        if (!flies.isEmpty()) again = true;

        // review: arrows and the quality badge
        if (!arrows.isEmpty()) {
            canvas.save();
            if (angle != 0) canvas.rotate(angle, half, half);
            for (int[] a : arrows) drawArrow(canvas, a[0], a[1], a[2], c);
            canvas.restore();
        }
        if (badgeSq >= 0 && badgeText != null) {
            float t = (now - badgeStart) / 260f;
            if (t < 1) again = true;
            float k = overshoot(t);
            float r = c * 0.2f * k;
            float[] p = rotate(sx(badgeSq) + c * 0.86f, sy(badgeSq) + c * 0.14f, angle, half);
            float bx = Math.min(getWidth() - r, Math.max(r, p[0])), by = Math.min(getHeight() - r, Math.max(r, p[1]));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x55000000);
            canvas.drawCircle(bx, by + c * 0.02f, r * 1.08f, paint);
            paint.setColor(badgeColor);
            canvas.drawCircle(bx, by, r, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(c * 0.025f);
            paint.setColor(0xFFFFFFFF);
            canvas.drawCircle(bx, by, r, paint);
            paint.setStyle(Paint.Style.FILL);
            text.setColor(0xFFFFFFFF);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(r * (badgeText.length() > 1 ? 0.95f : 1.25f));
            Paint.FontMetrics fm = text.getFontMetrics();
            canvas.drawText(badgeText, bx, by - (fm.ascent + fm.descent) / 2, text);
        }

        // the lifted piece follows the finger
        if (dragging && dragFrom >= 0) {
            float t = ease((now - liftStart) / 130f);
            if (t < 1) again = true;
            float size = c * (1 + 0.28f * t);
            float cx = dragX, cy = dragY - c * 0.35f * t;
            paint.setShader(new RadialGradient(dragX, dragY + c * 0.05f, c * 0.45f, 0x55000000, 0x00000000, Shader.TileMode.CLAMP));
            canvas.drawCircle(dragX, dragY + c * 0.05f, c * 0.45f, paint);
            paint.setShader(null);
            ChessStyle.drawPiece(canvas, shown[dragFrom], cx, cy, size, set, 1f);
        }
        if (again) postInvalidateOnAnimation();
    }

    private float[] rotate(float x, float y, float angle, float half) {
        if (angle == 0) return new float[]{x, y};
        double a = Math.toRadians(angle);
        float dx = x - half, dy = y - half;
        return new float[]{(float) (half + dx * Math.cos(a) - dy * Math.sin(a)), (float) (half + dx * Math.sin(a) + dy * Math.cos(a))};
    }

    private void drawAt(Canvas canvas, int piece, int sq, float c, int set, float alpha, float scale, float angle, float half) {
        drawPx(canvas, piece, sx(sq) + c / 2, sy(sq) + c / 2, c * scale, set, alpha, angle, half);
    }

    private void drawPx(Canvas canvas, int piece, float x, float y, float size, int set, float alpha, float angle, float half) {
        float[] p = rotate(x, y, angle, half);
        ChessStyle.drawPiece(canvas, piece, p[0], p[1], size, set, alpha);
    }

    private void drawArrow(Canvas canvas, int from, int to, int color, float c) {
        float x0 = sx(from) + c / 2, y0 = sy(from) + c / 2, x1 = sx(to) + c / 2, y1 = sy(to) + c / 2;
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.hypot(dx, dy);
        if (len < 1) return;
        float ux = dx / len, uy = dy / len;
        float head = c * 0.42f, w = c * 0.14f;
        float sx0 = x0 + ux * c * 0.25f, sy0 = y0 + uy * c * 0.25f;
        float bx = x1 - ux * head, by = y1 - uy * head;
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(w);
        paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(sx0, sy0, bx, by, paint);
        paint.setStyle(Paint.Style.FILL);
        path.reset();
        path.moveTo(x1 - ux * c * 0.08f, y1 - uy * c * 0.08f);
        path.lineTo(bx - uy * head * 0.62f, by + ux * head * 0.62f);
        path.lineTo(bx + uy * head * 0.62f, by - ux * head * 0.62f);
        path.close();
        canvas.drawPath(path, paint);
    }

    // ---- input ------------------------------------------------------------------------------------

    private boolean canMove(int sq) {
        if (sq < 0) return false;
        int p = board.sq[sq];
        if (p == 0 || controls == 2) return false;
        int color = p > 0 ? 1 : -1;
        if (color != board.side) return false;
        return controls == 0 || controls == color;
    }

    private void select(int sq) {
        if (selected != sq) selectStart = SystemClock.uptimeMillis();
        selected = sq;
        targets.clear();
        for (int m : board.legal()) if (ChessBoard.from(m) == sq) targets.add(m);
        targetsStart = SystemClock.uptimeMillis();
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
        int from = ChessBoard.from(m);
        if (dropFrom != from) dropFrom = -1;
        clearSelection();
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        if (delegate != null) delegate.onMove(m);
        dropFrom = -1;
    }

    /** Square units (file, rank) of a point on the screen. */
    private float[] toSquareUnits(float x, float y) {
        float c = cell();
        float col = x / c - 0.5f, row = y / c - 0.5f;
        return new float[]{flipped ? 7 - col : col, flipped ? row : 7 - row};
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                int sq = squareAt(x, y);
                if (sq < 0 || flipStart != 0) return false;
                if (selected >= 0 && sq != selected && !canMove(sq)) {
                    if (!tryMove(sq)) clearSelection();
                    return true;
                }
                if (canMove(sq)) {
                    wasSelected = selected == sq;
                    select(sq);
                    dragFrom = sq;
                    downX = dragX = x;
                    downY = dragY = y;
                    dragging = false;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                } else {
                    clearSelection();
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                if (dragFrom >= 0) {
                    if (!dragging && Math.hypot(x - downX, y - downY) > cell() * 0.12f) {
                        dragging = true;
                        liftStart = SystemClock.uptimeMillis();
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    }
                    dragX = x;
                    dragY = y;
                    if (dragging) {
                        int h = squareAt(x, y - cell() * 0.35f);
                        if (h != hoverSq) hoverSq = h;
                    }
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP: {
                if (dragging && dragFrom >= 0) {
                    int from = dragFrom;
                    float px = dragX, py = dragY - cell() * 0.35f;
                    int sq = squareAt(px, py);
                    dragging = false;
                    dragFrom = -1;
                    hoverSq = -1;
                    float[] u = toSquareUnits(px, py);
                    if (sq >= 0 && sq != from) {
                        dropFrom = from;
                        dropF = u[0];
                        dropR = u[1];
                        if (tryMove(sq)) return true;
                        dropFrom = -1;
                    }
                    // illegal drop: fly back home
                    if (ChessStyle.animate()) {
                        Fly f = new Fly();
                        f.piece = shown[from];
                        f.toSq = from;
                        f.f0 = u[0];
                        f.r0 = u[1];
                        f.f1 = ChessBoard.file(from);
                        f.r1 = ChessBoard.rank(from);
                        f.start = SystemClock.uptimeMillis();
                        f.dur = 180;
                        flies.add(f);
                    }
                    invalidate();
                    return true;
                }
                // a tap on the already selected piece unselects it
                if (dragFrom >= 0 && wasSelected) clearSelection();
                dragFrom = -1;
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                dragFrom = -1;
                hoverSq = -1;
                invalidate();
                return true;
        }
        return false;
    }
}
