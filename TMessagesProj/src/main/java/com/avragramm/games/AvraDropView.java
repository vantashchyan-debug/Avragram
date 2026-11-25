package com.avragramm.games;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;

import androidx.core.graphics.ColorUtils;

import com.avragramm.AvraGames;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

/**
 * AvraDrop: falling four-cell pieces on a 10×20 well; full lines clear, every 10 lines is a new level
 * and a faster fall. Controls: buttons ◀ ⟳ ▶ ▼ ⤓ under the well (move/soft drop repeat while held),
 * or gestures on the well: tap — rotate, drag sideways — move, quick swipe down — hard drop.
 * Pieces come from a shuffled bag of seven, with a ghost of the landing place and a next-piece preview.
 */
public class AvraDropView extends AvraGameView {

    private static final int W = 10, H = 20;
    private static final float LOCK_S = 0.45f, CLEAR_S = 0.25f;
    private static final float REPEAT_DELAY = 0.17f, REPEAT_EVERY = 0.05f;

    /** Seven pieces: cells in a size×size box (spawn orientation). */
    private static final int[][][] PIECES = {
            {{0, 1}, {1, 1}, {2, 1}, {3, 1}},  // I (box 4)
            {{0, 0}, {0, 1}, {1, 1}, {2, 1}},  // J
            {{2, 0}, {0, 1}, {1, 1}, {2, 1}},  // L
            {{0, 0}, {1, 0}, {0, 1}, {1, 1}},  // O (box 2)
            {{1, 0}, {2, 0}, {0, 1}, {1, 1}},  // S
            {{1, 0}, {0, 1}, {1, 1}, {2, 1}},  // T
            {{0, 0}, {1, 0}, {1, 1}, {2, 1}},  // Z
    };
    private static final int[] BOX = {4, 3, 3, 2, 3, 3, 3};

    private final Random random = new Random();
    /** 0 = empty, otherwise piece type + 1. */
    private final int[][] well = new int[H][W];
    private final ArrayList<Integer> bag = new ArrayList<>();
    private int type, next, rot, px, py;
    private int[][] cells = new int[4][2];
    private float fallAcc, lockAcc, clearT = 1f;
    private final boolean[] clearingRows = new boolean[H];
    private int lines, level;

    // buttons
    private static final int B_LEFT = 0, B_ROTATE = 1, B_RIGHT = 2, B_SOFT = 3, B_HARD = 4;
    private static final String[] B_LABELS = {"◀", "⟳", "▶", "▼", "⤓"};
    private int held = -1;
    private float heldTime, repeatAcc;

    // gesture
    private float gDownX, gDownY, gLastX;
    private long gDownTime;
    private boolean gMoved, gOnWell;

    // layout
    private float cell, wellLeft, wellTop, sideLeft, buttonsTop, buttonW, buttonH;

    public AvraDropView(Context context) {
        super(context);
    }

    @Override
    protected void reset() {
        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) well[r][c] = 0;
            clearingRows[r] = false;
        }
        bag.clear();
        lines = 0;
        level = 1;
        fallAcc = lockAcc = 0;
        clearT = 1f;
        held = -1;
        next = fromBag();
        spawn();
    }

    private int fromBag() {
        if (bag.isEmpty()) {
            for (int i = 0; i < 7; i++) bag.add(i);
            Collections.shuffle(bag, random);
        }
        return bag.remove(0);
    }

    private void spawn() {
        type = next;
        next = fromBag();
        rot = 0;
        px = (W - BOX[type]) / 2;
        py = type == 0 ? -1 : 0;
        cells = rotated(type, 0);
        fallAcc = lockAcc = 0;
        if (!fits(cells, px, py)) gameOver();
    }

    private static int[][] rotated(int t, int r) {
        int size = BOX[t];
        int[][] out = new int[4][2];
        for (int i = 0; i < 4; i++) {
            int x = PIECES[t][i][0], y = PIECES[t][i][1];
            for (int k = 0; k < r; k++) {
                int nx = size - 1 - y, ny = x; // clockwise
                x = nx;
                y = ny;
            }
            out[i][0] = x;
            out[i][1] = y;
        }
        return out;
    }

    private boolean fits(int[][] cs, int ox, int oy) {
        for (int[] c : cs) {
            int x = ox + c[0], y = oy + c[1];
            if (x < 0 || x >= W || y >= H) return false;
            if (y >= 0 && well[y][x] != 0) return false;
        }
        return true;
    }

    private float fallTime() {
        float base = difficulty == AvraGames.EASY ? 0.85f : difficulty == AvraGames.NORMAL ? 0.65f : 0.42f;
        return Math.max(0.05f, base * (float) Math.pow(0.85, level - 1));
    }

    private boolean move(int dx) {
        if (clearT < 1f) return false;
        if (fits(cells, px + dx, py)) {
            px += dx;
            if (!fits(cells, px, py + 1)) lockAcc = 0; // moving on the floor resets the lock timer
            return true;
        }
        return false;
    }

    private void rotate() {
        if (clearT < 1f || type == 3) return;
        int nr = (rot + 1) % 4;
        int[][] nc = rotated(type, nr);
        int[] kicks = {0, -1, 1, -2, 2};
        for (int up = 0; up <= 1; up++) {
            for (int k : kicks) {
                if (fits(nc, px + k, py - up)) {
                    px += k;
                    py -= up;
                    rot = nr;
                    cells = nc;
                    lockAcc = 0;
                    return;
                }
            }
        }
    }

    private boolean softDrop() {
        if (clearT < 1f) return false;
        if (fits(cells, px, py + 1)) {
            py++;
            addScore(1);
            fallAcc = 0;
            return true;
        }
        return false;
    }

    private void hardDrop() {
        if (clearT < 1f) return;
        int d = 0;
        while (fits(cells, px, py + 1)) {
            py++;
            d++;
        }
        addScore(d * 2);
        lock();
    }

    private void lock() {
        for (int[] c : cells) {
            int x = px + c[0], y = py + c[1];
            if (y < 0) {
                gameOver();
                return;
            }
            well[y][x] = type + 1;
        }
        int cleared = 0;
        for (int r = 0; r < H; r++) {
            boolean full = true;
            for (int c = 0; c < W; c++) if (well[r][c] == 0) full = false;
            clearingRows[r] = full;
            if (full) cleared++;
        }
        if (cleared > 0) {
            int[] table = {0, 100, 300, 500, 800};
            addScore(table[cleared] * level);
            lines += cleared;
            level = 1 + lines / 10;
            clearT = 0f;
        } else {
            spawn();
        }
    }

    private void collapse() {
        int dst = H - 1;
        for (int r = H - 1; r >= 0; r--) {
            if (clearingRows[r]) continue;
            if (dst != r) System.arraycopy(well[r], 0, well[dst], 0, W);
            dst--;
        }
        for (int r = dst; r >= 0; r--) for (int c = 0; c < W; c++) well[r][c] = 0;
        for (int r = 0; r < H; r++) clearingRows[r] = false;
        spawn();
    }

    @Override
    protected void step(float dt) {
        if (clearT < 1f) {
            clearT = Math.min(1f, clearT + dt / CLEAR_S);
            if (clearT >= 1f) collapse();
            return;
        }
        if (held >= 0) {
            heldTime += dt;
            if (heldTime > REPEAT_DELAY && (held == B_LEFT || held == B_RIGHT || held == B_SOFT)) {
                repeatAcc += dt;
                while (repeatAcc >= REPEAT_EVERY) {
                    repeatAcc -= REPEAT_EVERY;
                    if (held == B_LEFT) move(-1);
                    else if (held == B_RIGHT) move(1);
                    else softDrop();
                }
            }
        }
        if (fits(cells, px, py + 1)) {
            lockAcc = 0;
            fallAcc += dt;
            float ft = fallTime();
            while (fallAcc >= ft) {
                fallAcc -= ft;
                if (fits(cells, px, py + 1)) py++;
                else break;
            }
        } else {
            lockAcc += dt;
            if (lockAcc >= LOCK_S) lock();
        }
    }

    private void layout() {
        float w = getWidth(), h = getHeight();
        float pad = dp(12);
        buttonH = Math.max(dp(52), Math.min(dp(72), h * 0.09f));
        buttonsTop = h - buttonH - pad;
        float side = Math.max(dp(84), w * 0.22f);
        cell = Math.min((w - pad * 3 - side) / W, (buttonsTop - pad * 2) / H);
        float total = cell * W + pad + side;
        wellLeft = (w - total) / 2f;
        wellTop = Math.max(pad, (buttonsTop - pad - cell * H) / 2f);
        sideLeft = wellLeft + cell * W + pad;
        buttonW = (w - pad * 2 - dp(8) * 4) / 5f;
    }

    private int pieceColor(int t) {
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(cAccent, hsl);
        hsl[0] = (hsl[0] + t * (360f / 7f)) % 360f;
        hsl[1] = Math.max(0.45f, hsl[1]);
        hsl[2] = Math.max(0.4f, Math.min(0.6f, hsl[2]));
        return ColorUtils.HSLToColor(hsl);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isRunning() || paused) return true;
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                if (y >= buttonsTop) {
                    int b = (int) ((x - dp(12)) / (buttonW + dp(8)));
                    if (b >= 0 && b <= 4) {
                        held = b;
                        heldTime = 0;
                        repeatAcc = 0;
                        press(b);
                    }
                    gOnWell = false;
                } else {
                    gOnWell = true;
                    gDownX = gLastX = x;
                    gDownY = y;
                    gDownTime = e.getEventTime();
                    gMoved = false;
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!gOnWell) return true;
                float step = Math.max(cell, dp(18));
                while (x - gLastX >= step) {
                    move(1);
                    gLastX += step;
                    gMoved = true;
                }
                while (gLastX - x >= step) {
                    move(-1);
                    gLastX -= step;
                    gMoved = true;
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (gOnWell && e.getActionMasked() == MotionEvent.ACTION_UP) {
                    float dy = y - gDownY, dx = x - gDownX;
                    long dtMs = e.getEventTime() - gDownTime;
                    if (dy > cell * 3 && Math.abs(dy) > Math.abs(dx) * 1.5f && dtMs < 400) {
                        hardDrop();
                    } else if (!gMoved && Math.abs(dx) < dp(12) && Math.abs(dy) < dp(12)) {
                        rotate();
                    }
                }
                held = -1;
                gOnWell = false;
                invalidate();
                return true;
            }
        }
        return true;
    }

    private void press(int b) {
        switch (b) {
            case B_LEFT: move(-1); break;
            case B_RIGHT: move(1); break;
            case B_ROTATE: rotate(); break;
            case B_SOFT: softDrop(); break;
            case B_HARD: hardDrop(); break;
        }
    }

    @Override
    protected void render(Canvas canvas) {
        layout();
        float radius = cellRadius(cell) * 0.6f;
        float g = Math.max(1f, cell * 0.06f);
        roundRect(canvas, wellLeft - dp(4), wellTop - dp(4), wellLeft + cell * W + dp(4), wellTop + cell * H + dp(4), radius + dp(4), cCard);
        int empty = ColorUtils.blendARGB(cCard, cText, 0.04f);
        for (int r = 0; r < H; r++) for (int c = 0; c < W; c++) {
            float x = wellLeft + c * cell, y = wellTop + r * cell;
            int v = well[r][c];
            int color = v == 0 ? empty : pieceColor(v - 1);
            if (clearingRows[r] && clearT < 1f) color = ColorUtils.blendARGB(color, 0xFFFFFFFF, (float) Math.sin(Math.PI * clearT) * 0.8f);
            roundRect(canvas, x + g, y + g, x + cell - g, y + cell - g, radius, color);
        }
        if (clearT >= 1f && (running || over)) {
            // ghost
            int gy = py;
            while (fits(cells, px, gy + 1)) gy++;
            stroke.setColor(ColorUtils.setAlphaComponent(pieceColor(type), 170));
            stroke.setStrokeWidth(Math.max(1.5f, g));
            for (int[] c : cells) {
                int yy = gy + c[1];
                if (yy < 0) continue;
                float x = wellLeft + (px + c[0]) * cell, y = wellTop + yy * cell;
                rect.set(x + g * 2, y + g * 2, x + cell - g * 2, y + cell - g * 2);
                canvas.drawRoundRect(rect, radius, radius, stroke);
            }
            for (int[] c : cells) {
                int yy = py + c[1];
                if (yy < 0) continue;
                float x = wellLeft + (px + c[0]) * cell, y = wellTop + yy * cell;
                roundRect(canvas, x + g, y + g, x + cell - g, y + cell - g, radius, pieceColor(type));
            }
        }
        // side panel: next, level, lines
        float sw = getWidth() - sideLeft - dp(12);
        float boxTop = wellTop;
        roundRect(canvas, sideLeft, boxTop, sideLeft + sw, boxTop + sw, radius + dp(4), cCard);
        text.setTextAlign(Paint.Align.CENTER);
        centerText(canvas, LocaleController.getString(R.string.AvraGameNext), sideLeft + sw / 2f, boxTop + dp(14), dp(12), cGray);
        float pc = Math.min(sw / 5f, cell);
        int[][] nc = rotated(next, 0);
        int box = BOX[next];
        float nx = sideLeft + (sw - box * pc) / 2f, ny = boxTop + dp(24) + (sw - dp(24) - box * pc) / 2f;
        for (int[] c : nc) {
            float x = nx + c[0] * pc, y = ny + c[1] * pc;
            roundRect(canvas, x + g, y + g, x + pc - g, y + pc - g, radius, pieceColor(next));
        }
        float infoTop = boxTop + sw + dp(16);
        centerText(canvas, LocaleController.getString(R.string.AvraGameLevel), sideLeft + sw / 2f, infoTop, dp(12), cGray);
        centerText(canvas, String.valueOf(level), sideLeft + sw / 2f, infoTop + dp(22), dp(22), cText);
        centerText(canvas, LocaleController.getString(R.string.AvraGameLines), sideLeft + sw / 2f, infoTop + dp(54), dp(12), cGray);
        centerText(canvas, String.valueOf(lines), sideLeft + sw / 2f, infoTop + dp(76), dp(22), cText);
        // buttons
        float br = cellRadius(buttonH) * 1.4f;
        for (int b = 0; b < 5; b++) {
            float x = dp(12) + b * (buttonW + dp(8));
            boolean pressed = held == b;
            int bg = b == B_HARD ? cAccent : pressed ? ColorUtils.blendARGB(cCard, cAccent, 0.35f) : cCard;
            roundRect(canvas, x, buttonsTop, x + buttonW, buttonsTop + buttonH, Math.min(br, buttonH / 2f), bg);
            centerText(canvas, B_LABELS[b], x + buttonW / 2f, buttonsTop + buttonH / 2f, buttonH * 0.4f, b == B_HARD ? cAccentText : cText);
        }
    }
}
