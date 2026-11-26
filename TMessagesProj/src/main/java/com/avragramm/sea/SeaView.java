package com.avragramm.sea;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;

/**
 * One AvraSea board: animated water, grid with coordinates, steel ships, splashes for misses,
 * explosions and flickering fire for hits, wrecks for sunk ships. Three modes: your fleet (the
 * opponent's shots on it), the enemy waters (tap to fire) and placement (drag ships, tap to turn).
 */
public class SeaView extends View {

    public static final int MODE_OWN = 0, MODE_ENEMY = 1, MODE_PLACE = 2;

    public interface Listener {
        void onCell(int cell);

        void onFleetChanged();
    }

    private int mode;
    private Listener listener;
    private SeaBoard fleet;
    private boolean showFleet = true;
    private int[] marks = new int[SeaBoard.CELLS];
    private final long[] markStart = new long[SeaBoard.CELLS];
    private int pending = -1;
    private boolean active = true;
    private String letters = "ABCDEFGHIJ";

    // placement drag
    private SeaBoard.Ship drag, ghost;
    private int dragDX, dragDY;
    private float downX, downY;
    private boolean moved;
    private SeaBoard.Ship shake;
    private long shakeStart;

    private int aim = -1;
    private final long born = SystemClock.uptimeMillis();

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private LinearGradient water;
    private int waterH;

    public SeaView(Context c, int mode) {
        super(c);
        this.mode = mode;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        text.setTextAlign(Paint.Align.CENTER);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setMode(int m) {
        mode = m;
        drag = ghost = null;
        invalidate();
    }

    public void setLetters(String l) {
        if (l != null && l.length() >= 10) letters = l;
    }

    /** The fleet drawn on the board (yours, or the opponent's once revealed); null for none. */
    public void setFleet(SeaBoard f, boolean show) {
        fleet = f;
        showFleet = show;
        invalidate();
    }

    public SeaBoard getFleet() {
        return fleet;
    }

    /** Marks per cell ({@link SeaBoard#knowledge}); changed cells animate. */
    public void setMarks(int[] m, boolean animate) {
        long now = SystemClock.uptimeMillis();
        for (int i = 0; i < SeaBoard.CELLS; i++) {
            if (m[i] != marks[i]) markStart[i] = animate ? now : 0;
        }
        marks = m.clone();
        invalidate();
    }

    public void setPending(int cell) {
        pending = cell;
        invalidate();
    }

    /** Enemy board: is it your turn (otherwise the water is dimmed and taps are ignored). */
    public void setActive(boolean a) {
        active = a;
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        int s = MeasureSpec.getSize(w);
        setMeasuredDimension(s, s);
    }

    private float band() {
        return getWidth() / (SeaBoard.N + 0.6f) * 0.6f;
    }

    private float cell() {
        return (getWidth() - band()) / SeaBoard.N;
    }

    private float cx(int c) {
        return band() + (c % SeaBoard.N + 0.5f) * cell();
    }

    private float cy(int c) {
        return band() + (c / SeaBoard.N + 0.5f) * cell();
    }

    private int cellAt(float x, float y) {
        float b = band(), s = cell();
        int gx = (int) Math.floor((x - b) / s), gy = (int) Math.floor((y - b) / s);
        if (gx < 0 || gy < 0 || gx >= SeaBoard.N || gy >= SeaBoard.N) return -1;
        return gy * SeaBoard.N + gx;
    }

    // ---- drawing ------------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float t = (now - born) / 1000f;
        float b = band(), s = cell(), size = s * SeaBoard.N;
        float left = b, top = b, right = b + size, bottom = b + size;

        // water
        if (water == null || waterH != getHeight()) {
            water = new LinearGradient(0, top, 0, bottom, new int[]{0xFF1283B5, 0xFF0B5C86, 0xFF07405F}, null, Shader.TileMode.CLAMP);
            waterH = getHeight();
        }
        float r = s * 0.35f;
        rect.set(left, top, right, bottom);
        paint.setShader(water);
        c.drawRoundRect(rect, r, r, paint);
        paint.setShader(null);

        c.save();
        path.reset();
        path.addRoundRect(rect, r, r, Path.Direction.CW);
        c.clipPath(path);
        // moving waves
        stroke.setStrokeWidth(Math.max(1, s * 0.05f));
        for (int i = 0; i < 9; i++) {
            float y0 = top + (i + 0.5f) * size / 9f;
            stroke.setColor(0x16FFFFFF);
            path.reset();
            for (int k = 0; k <= 24; k++) {
                float x = left + size * k / 24f;
                float y = y0 + (float) Math.sin(k * 0.9f + t * 1.6f + i * 1.3f) * s * 0.12f;
                if (k == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            c.drawPath(path, stroke);
        }
        // grid
        stroke.setStrokeWidth(Math.max(1, s * 0.025f));
        stroke.setColor(0x38FFFFFF);
        for (int i = 1; i < SeaBoard.N; i++) {
            c.drawLine(left + i * s, top, left + i * s, bottom, stroke);
            c.drawLine(left, top + i * s, right, top + i * s, stroke);
        }
        // water around sunk ships
        paint.setColor(0x40FFFFFF);
        for (int i = 0; i < SeaBoard.CELLS; i++) {
            if (marks[i] == SeaBoard.NEAR) c.drawCircle(cx(i), cy(i), s * 0.07f, paint);
        }
        // ships
        ArrayList<ArrayList<Integer>> wrecks = SeaBoard.sunkShips(marks);
        if (fleet != null && (showFleet || mode != MODE_ENEMY)) {
            for (SeaBoard.Ship sh : fleet.ships) {
                if (sh == drag) continue;
                boolean sunk = true;
                for (int i = 0; i < sh.len; i++) if (marks[sh.cell(i)] != SeaBoard.SUNK) sunk = false;
                float dx = 0;
                if (sh == shake) {
                    float st = (now - shakeStart) / 300f;
                    if (st < 1) dx = (float) Math.sin(st * Math.PI * 6) * s * 0.15f * (1 - st);
                    else shake = null;
                }
                drawShip(c, sh.x, sh.y, sh.len, sh.vertical, sunk ? 2 : mode == MODE_ENEMY ? 1 : 0, dx, 1f);
            }
        }
        if (mode == MODE_ENEMY) {
            for (ArrayList<Integer> w : wrecks) {
                int first = w.get(0), last = w.get(w.size() - 1);
                boolean vert = w.size() > 1 && last - first >= SeaBoard.N;
                if (fleet != null && showFleet) continue; // drawn above from the revealed fleet
                long st = markStart[first];
                float a = st == 0 ? 1 : Math.min(1, (now - st) / 500f);
                drawShip(c, first % SeaBoard.N, first / SeaBoard.N, w.size(), vert, 2, 0, a);
            }
        }
        // placement ghost
        if (mode == MODE_PLACE && drag != null && ghost != null) {
            boolean ok = fleet.fits(ghost, drag);
            drawShip(c, ghost.x, ghost.y, ghost.len, ghost.vertical, ok ? 3 : 4, 0, 0.9f);
        }

        // shots
        for (int i = 0; i < SeaBoard.CELLS; i++) {
            int m = marks[i];
            long st = markStart[i];
            float age = st == 0 ? 99 : (now - st) / 1000f;
            float x = cx(i), y = cy(i);
            if (m == SeaBoard.MISS) {
                paint.setColor(0xCCE3F2FD);
                c.drawCircle(x, y, s * 0.11f, paint);
                if (age < 0.9f) {
                    for (int k = 0; k < 2; k++) {
                        float p = Math.min(1, Math.max(0, (age - k * 0.15f) / 0.7f));
                        if (p <= 0) continue;
                        stroke.setStrokeWidth(s * 0.05f);
                        stroke.setColor(((int) (0xCC * (1 - p)) << 24) | 0xFFFFFF);
                        c.drawCircle(x, y, s * (0.12f + 0.4f * p), stroke);
                    }
                }
            } else if (m == SeaBoard.HIT || m == SeaBoard.SUNK) {
                if (m == SeaBoard.HIT) {
                    float fl = 0.85f + 0.15f * (float) Math.sin(t * 13 + i * 1.7f) + 0.08f * (float) Math.sin(t * 29 + i);
                    paint.setShader(new RadialGradient(x, y + s * 0.05f, s * 0.48f * fl, new int[]{0xFFFFF59D, 0xFFFF9800, 0x88FF3D00, 0x00FF3D00},
                            new float[]{0, 0.3f, 0.65f, 1}, Shader.TileMode.CLAMP));
                    c.drawCircle(x, y, s * 0.48f * fl, paint);
                    paint.setShader(null);
                } else {
                    paint.setColor(0x66263238);
                    c.drawCircle(x - s * 0.08f, y - s * 0.1f, s * 0.22f, paint);
                }
                stroke.setStrokeWidth(s * 0.09f);
                stroke.setColor(m == SeaBoard.HIT ? 0xFFB71C1C : 0xFFFF5252);
                float d = s * 0.22f;
                c.drawLine(x - d, y - d, x + d, y + d, stroke);
                c.drawLine(x - d, y + d, x + d, y - d, stroke);
                if (age < 0.6f) { // explosion burst
                    float p = age / 0.6f;
                    paint.setColor(((int) (0xEE * (1 - p)) << 24) | 0xFFC107);
                    c.drawCircle(x, y, s * (0.2f + 0.6f * p), paint);
                    stroke.setStrokeWidth(s * 0.06f);
                    stroke.setColor(((int) (0xFF * (1 - p)) << 24) | 0xFFEB3B);
                    for (int k = 0; k < 8; k++) {
                        double a = k * Math.PI / 4 + i;
                        float r1 = s * (0.25f + 0.5f * p), r2 = s * (0.4f + 0.7f * p);
                        c.drawLine(x + (float) Math.cos(a) * r1, y + (float) Math.sin(a) * r1, x + (float) Math.cos(a) * r2, y + (float) Math.sin(a) * r2, stroke);
                    }
                }
            }
        }
        // the shot waiting for an answer
        if (pending >= 0) {
            float p = (float) (0.5 + 0.5 * Math.sin(t * 8));
            stroke.setStrokeWidth(s * 0.07f);
            stroke.setColor(0xFFFF5252);
            float x = cx(pending), y = cy(pending);
            c.drawCircle(x, y, s * (0.28f + 0.08f * p), stroke);
            c.drawLine(x - s * 0.45f, y, x - s * 0.15f, y, stroke);
            c.drawLine(x + s * 0.15f, y, x + s * 0.45f, y, stroke);
            c.drawLine(x, y - s * 0.45f, x, y - s * 0.15f, stroke);
            c.drawLine(x, y + s * 0.15f, x, y + s * 0.45f, stroke);
        }
        // aim under the finger
        if (aim >= 0) {
            paint.setColor(0x44FFFFFF);
            float x = cx(aim) - s / 2, y = cy(aim) - s / 2;
            c.drawRect(x, y, x + s, y + s, paint);
            stroke.setStrokeWidth(s * 0.06f);
            stroke.setColor(0xFFFFFFFF);
            c.drawCircle(cx(aim), cy(aim), s * 0.3f, stroke);
        }
        if (mode == MODE_ENEMY && !active) {
            paint.setColor(0x47000814);
            c.drawRect(rect, paint);
        }
        c.restore();

        // frame and coordinates
        stroke.setStrokeWidth(Math.max(2, s * 0.06f));
        stroke.setColor(mode == MODE_ENEMY && active ? 0xFF4FC3F7 : 0x66B3E5FC);
        c.drawRoundRect(rect, r, r, stroke);
        text.setTextSize(s * 0.42f);
        text.setColor(0xCCE1F5FE);
        Paint.FontMetrics fm = text.getFontMetrics();
        for (int i = 0; i < SeaBoard.N; i++) {
            c.drawText(String.valueOf(letters.charAt(i)), left + (i + 0.5f) * s, b * 0.5f - (fm.ascent + fm.descent) / 2, text);
            c.drawText(String.valueOf(i + 1), b * 0.45f, top + (i + 0.5f) * s - (fm.ascent + fm.descent) / 2, text);
        }
        if (isShown()) postInvalidateDelayed(33);
    }

    /** style: 0 yours, 1 revealed enemy, 2 wreck, 3 ghost ok, 4 ghost blocked. */
    private void drawShip(Canvas c, int x, int y, int len, boolean vertical, int style, float dx, float alpha) {
        float s = cell(), b = band();
        float in = s * 0.13f;
        float l = b + x * s + in + dx, t = b + y * s + in;
        float w = (vertical ? 1 : len) * s - 2 * in, h = (vertical ? len : 1) * s - 2 * in;
        rect.set(l, t, l + w, t + h);
        float rad = Math.min(w, h) / 2;
        int a = (int) (255 * alpha);
        // shadow
        if (style < 3) {
            paint.setColor(0x55000000);
            paint.setAlpha((int) (0x55 * alpha));
            rect.offset(s * 0.05f, s * 0.07f);
            c.drawRoundRect(rect, rad, rad, paint);
            rect.offset(-s * 0.05f, -s * 0.07f);
        }
        int c1, c2, edge;
        switch (style) {
            case 1: c1 = 0xFFFFCC80; c2 = 0xFFE65100; edge = 0xFF4E2600; break;
            case 2: c1 = 0xFF5D4037; c2 = 0xFF212121; edge = 0xFFFF5252; break;
            case 3: c1 = 0xAA69F0AE; c2 = 0xAA00C853; edge = 0xFFB9F6CA; break;
            case 4: c1 = 0xAAFF8A80; c2 = 0xAAD50000; edge = 0xFFFFCDD2; break;
            default: c1 = 0xFFE3ECF1; c2 = 0xFF78909C; edge = 0xFF263238; break;
        }
        paint.setShader(vertical
                ? new LinearGradient(l, 0, l + w, 0, c1, c2, Shader.TileMode.CLAMP)
                : new LinearGradient(0, t, 0, t + h, c1, c2, Shader.TileMode.CLAMP));
        paint.setAlpha(a);
        c.drawRoundRect(rect, rad, rad, paint);
        paint.setShader(null);
        stroke.setStrokeWidth(s * 0.05f);
        stroke.setColor(edge);
        stroke.setAlpha(a);
        c.drawRoundRect(rect, rad, rad, stroke);
        // deck line and turrets
        if (style <= 2) {
            stroke.setStrokeWidth(s * 0.035f);
            stroke.setColor(style == 2 ? 0x66FF8A65 : 0x88263238);
            stroke.setAlpha((int) (stroke.getAlpha() * alpha));
            if (len > 1) {
                if (vertical) c.drawLine(l + w / 2, t + h * 0.18f, l + w / 2, t + h * 0.82f, stroke);
                else c.drawLine(l + w * 0.18f, t + h / 2, l + w * 0.82f, t + h / 2, stroke);
            }
            paint.setColor(style == 2 ? 0xFF3E2723 : 0xFF455A64);
            paint.setAlpha(a);
            for (int i = 0; i < len; i++) {
                float px = vertical ? l + w / 2 : b + (x + i + 0.5f) * s + dx;
                float py = vertical ? b + (y + i + 0.5f) * s : t + h / 2;
                c.drawCircle(px, py, s * 0.12f, paint);
            }
        }
        paint.setAlpha(255);
        stroke.setAlpha(255);
    }

    // ---- touch --------------------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int cell = cellAt(e.getX(), e.getY());
        if (mode == MODE_ENEMY) {
            if (!active) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    aim = cell >= 0 && marks[cell] == SeaBoard.UNKNOWN && pending < 0 ? cell : -1;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                    int target = aim;
                    aim = -1;
                    invalidate();
                    if (target >= 0 && target == cell && listener != null) {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        listener.onCell(target);
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    aim = -1;
                    invalidate();
                    return true;
            }
            return false;
        }
        if (mode != MODE_PLACE || fleet == null) return false;
        float s = cell(), b = band();
        int gx = (int) Math.floor((e.getX() - b) / s), gy = (int) Math.floor((e.getY() - b) / s);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                if (cell < 0) return false;
                int i = fleet.shipAt(cell);
                if (i < 0) return false;
                drag = fleet.ships.get(i);
                ghost = drag.copy();
                dragDX = gx - drag.x;
                dragDY = gy - drag.y;
                downX = e.getX();
                downY = e.getY();
                moved = false;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                if (drag == null) return false;
                if (Math.hypot(e.getX() - downX, e.getY() - downY) > s * 0.3f) moved = true;
                if (moved) {
                    ghost.x = Math.max(0, Math.min(SeaBoard.N - (ghost.vertical ? 1 : ghost.len), gx - dragDX));
                    ghost.y = Math.max(0, Math.min(SeaBoard.N - (ghost.vertical ? ghost.len : 1), gy - dragDY));
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_UP: {
                if (drag == null) return false;
                SeaBoard.Ship d = drag;
                if (!moved) {
                    SeaBoard.Ship turned = new SeaBoard.Ship(d.x, d.y, d.len, !d.vertical);
                    if (d.len > 1 && fleet.fits(turned, d)) {
                        d.vertical = !d.vertical;
                        changed();
                    } else if (d.len > 1) {
                        shake = d;
                        shakeStart = SystemClock.uptimeMillis();
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    }
                } else if (fleet.fits(ghost, d)) {
                    d.x = ghost.x;
                    d.y = ghost.y;
                    changed();
                } else {
                    shake = d;
                    shakeStart = SystemClock.uptimeMillis();
                }
                drag = ghost = null;
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                drag = ghost = null;
                invalidate();
                return true;
        }
        return false;
    }

    private void changed() {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        SeaSound.play(SeaSound.PLACE);
        if (listener != null) listener.onFleetChanged();
    }
}
