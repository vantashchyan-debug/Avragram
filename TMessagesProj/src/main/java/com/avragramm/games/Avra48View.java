package com.avragramm.games;

import android.content.Context;
import android.graphics.Canvas;

import androidx.core.graphics.ColorUtils;

import com.avragramm.AvraGames;

import java.util.ArrayList;
import java.util.Random;

/**
 * Avra48: slide and merge number tiles. Easy 5×5, normal 4×4, hard 4×4 with more 4s.
 * Reaching 2048 reports a win once (the player may keep going); no possible move ends the round.
 * Tile colours are shades between the theme's card colour and its accent.
 */
public class Avra48View extends AvraGameView {

    private static final float SLIDE_S = 0.11f, POP_S = 0.13f;

    private final Random random = new Random();
    private int n = 4;
    private int[][] grid = new int[4][4];
    private boolean[][] popped = new boolean[4][4];
    private boolean[][] spawned = new boolean[4][4];
    private boolean won;

    private static final class Move {
        int value, fr, fc, tr, tc;
    }

    private final ArrayList<Move> moves = new ArrayList<>();
    private float slideT = 1f, popT = 1f;

    public Avra48View(Context context) {
        super(context);
    }

    @Override
    protected void reset() {
        n = difficulty == AvraGames.EASY ? 5 : 4;
        grid = new int[n][n];
        popped = new boolean[n][n];
        spawned = new boolean[n][n];
        moves.clear();
        won = false;
        slideT = 1f;
        spawn();
        spawn();
        popT = 0f;
    }

    private void spawn() {
        ArrayList<int[]> empty = new ArrayList<>();
        for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) if (grid[r][c] == 0) empty.add(new int[]{r, c});
        if (empty.isEmpty()) return;
        int[] p = empty.get(random.nextInt(empty.size()));
        float p4 = difficulty == AvraGames.HARD ? 0.4f : 0.1f;
        grid[p[0]][p[1]] = random.nextFloat() < p4 ? 4 : 2;
        spawned[p[0]][p[1]] = true;
    }

    @Override
    protected void onSwipe(int direction) {
        if (slideT < 1f) finishSlide();
        moves.clear();
        for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) {
            popped[r][c] = false;
            spawned[r][c] = false;
        }
        int[][] next = new int[n][n];
        boolean changed = false;
        int gained = 0;
        for (int line = 0; line < n; line++) {
            // walk the line from the edge the tiles move towards
            int target = 0;
            int lastValue = 0, lastTarget = -1;
            boolean lastMerged = false;
            for (int k = 0; k < n; k++) {
                int r = rowOf(direction, line, k), c = colOf(direction, line, k);
                int v = grid[r][c];
                if (v == 0) continue;
                Move m = new Move();
                m.value = v;
                m.fr = r;
                m.fc = c;
                if (lastValue == v && !lastMerged) {
                    int tr = rowOf(direction, line, lastTarget), tc = colOf(direction, line, lastTarget);
                    next[tr][tc] = v * 2;
                    popped[tr][tc] = true;
                    gained += v * 2;
                    lastMerged = true;
                    m.tr = tr;
                    m.tc = tc;
                } else {
                    int tr = rowOf(direction, line, target), tc = colOf(direction, line, target);
                    next[tr][tc] = v;
                    lastValue = v;
                    lastTarget = target;
                    lastMerged = false;
                    target++;
                    m.tr = tr;
                    m.tc = tc;
                }
                if (m.tr != m.fr || m.tc != m.fc) changed = true;
                moves.add(m);
            }
        }
        if (!changed) {
            moves.clear();
            return;
        }
        grid = next;
        addScore(gained);
        spawn();
        slideT = 0f;
        popT = 0f;
        if (!won) {
            for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) {
                if (grid[r][c] >= 2048) {
                    won = true;
                    final int s = score;
                    post(() -> {
                        if (listener != null) listener.onWin(s);
                    });
                }
            }
        }
        if (!canMove()) gameOver();
        kick();
    }

    /** Cell k (0 = the edge tiles move to) of line {@code line} for a direction. */
    private int rowOf(int dir, int line, int k) {
        switch (dir) {
            case UP: return k;
            case DOWN: return n - 1 - k;
            default: return line;
        }
    }

    private int colOf(int dir, int line, int k) {
        switch (dir) {
            case LEFT: return k;
            case RIGHT: return n - 1 - k;
            default: return line;
        }
    }

    private boolean canMove() {
        for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) {
            int v = grid[r][c];
            if (v == 0) return true;
            if (r + 1 < n && grid[r + 1][c] == v) return true;
            if (c + 1 < n && grid[r][c + 1] == v) return true;
        }
        return false;
    }

    private void finishSlide() {
        slideT = 1f;
        moves.clear();
    }

    @Override
    protected boolean needsFrames() {
        return slideT < 1f || popT < 1f;
    }

    @Override
    protected void step(float dt) {
        if (slideT < 1f) {
            slideT = Math.min(1f, slideT + dt / SLIDE_S);
            if (slideT >= 1f) moves.clear();
        } else if (popT < 1f) {
            popT = Math.min(1f, popT + dt / POP_S);
        }
    }

    @Override
    protected void render(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float pad = dp(16);
        float board = Math.min(w, h) - pad * 2;
        float left = (w - board) / 2f, top = (h - board) / 2f;
        float gap = board * 0.025f;
        float cell = (board - gap * (n + 1)) / n;
        float radius = cellRadius(cell);
        roundRect(canvas, left, top, left + board, top + board, radius + gap, cCard);
        int emptyColor = ColorUtils.blendARGB(cCard, cText, 0.06f);
        for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) {
            float x = left + gap + c * (cell + gap), y = top + gap + r * (cell + gap);
            roundRect(canvas, x, y, x + cell, y + cell, radius, emptyColor);
        }
        if (slideT < 1f && !moves.isEmpty()) {
            float t = ease(slideT);
            for (Move m : moves) {
                float fx = left + gap + m.fc * (cell + gap), fy = top + gap + m.fr * (cell + gap);
                float tx = left + gap + m.tc * (cell + gap), ty = top + gap + m.tr * (cell + gap);
                drawTile(canvas, fx + (tx - fx) * t, fy + (ty - fy) * t, cell, radius, m.value, 1f);
            }
            return;
        }
        for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) {
            int v = grid[r][c];
            if (v == 0) continue;
            float scale = 1f;
            if (popT < 1f) {
                if (popped[r][c]) scale = 1f + 0.14f * (float) Math.sin(Math.PI * popT);
                else if (spawned[r][c]) scale = 0.3f + 0.7f * ease(popT);
            }
            float x = left + gap + c * (cell + gap), y = top + gap + r * (cell + gap);
            drawTile(canvas, x, y, cell, radius, v, scale);
        }
    }

    private void drawTile(Canvas canvas, float x, float y, float cell, float radius, int value, float scale) {
        int log = 31 - Integer.numberOfLeadingZeros(value);
        int color = value > 2048 ? ColorUtils.blendARGB(cAccent, cText, 0.3f) : shade(0.12f + 0.88f * Math.min(1f, (log - 1) / 10f));
        float s = cell * scale, off = (cell - s) / 2f;
        roundRect(canvas, x + off, y + off, x + off + s, y + off + s, radius * scale, color);
        String label = String.valueOf(value);
        float size = cell * (label.length() <= 2 ? 0.42f : label.length() == 3 ? 0.34f : label.length() == 4 ? 0.27f : 0.22f) * scale;
        centerText(canvas, label, x + cell / 2f, y + cell / 2f, size, onColor(color));
    }

    private static float ease(float t) {
        return 1f - (1f - t) * (1f - t);
    }
}
