package com.avragramm.games;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;

import androidx.core.graphics.ColorUtils;

import com.avragramm.AvraGames;

import java.util.ArrayList;
import java.util.Random;

/**
 * AvraBlast — an original block puzzle: drag pieces from the tray onto an 8×8 board; full rows and
 * columns vanish. Three pieces per tray, a new tray when all are placed; the round ends when no
 * piece left in the tray fits anywhere. Clearing on consecutive moves builds a combo.
 * Difficulty changes the mix of small and large pieces.
 * Piece colours are hue steps of the theme accent, so they follow every palette.
 */
public class AvraBlastView extends AvraGameView {

    private static final int N = 8;
    private static final float CLEAR_S = 0.28f, RETURN_S = 0.16f;

    /** Shapes as {width, height, cells as row-major bitmask string}. */
    private static final String[] SHAPES = {
            "1x1:1",
            "2x1:11", "1x2:11",
            "3x1:111", "1x3:111",
            "2x2:1101", "2x2:1110", "2x2:0111", "2x2:1011",
            "2x2:1111",
            "4x1:1111", "1x4:1111",
            "5x1:11111", "1x5:11111",
            "3x2:111111", "2x3:111111",
            "3x3:111111111",
            "3x3:100100111", "3x3:001001111", "3x3:111100100", "3x3:111001001",
            "3x2:111010", "3x2:010111", "2x3:101110", "2x3:011101",
            "3x2:110011", "3x2:011110", "2x3:101101", "2x3:011110",
            "2x2:1001", "2x2:0110",
    };

    private static final class Shape {
        int w, h;
        boolean[][] cells;
        int size;
    }

    private static final Shape[] SHAPE_LIST = new Shape[SHAPES.length];

    static {
        for (int i = 0; i < SHAPES.length; i++) {
            String s = SHAPES[i];
            int x = s.indexOf('x'), colon = s.indexOf(':');
            Shape sh = new Shape();
            sh.w = Integer.parseInt(s.substring(0, x));
            sh.h = Integer.parseInt(s.substring(x + 1, colon));
            String bits = s.substring(colon + 1);
            sh.cells = new boolean[sh.h][sh.w];
            for (int r = 0; r < sh.h; r++) for (int c = 0; c < sh.w; c++) {
                int k = r * sh.w + c;
                sh.cells[r][c] = k < bits.length() && bits.charAt(k) == '1';
                if (sh.cells[r][c]) sh.size++;
            }
            SHAPE_LIST[i] = sh;
        }
    }

    private static final class Piece {
        Shape shape;
        int colorIndex;
        boolean used;
    }

    private final Random random = new Random();
    /** 0 = empty, otherwise colorIndex + 1. */
    private final int[][] board = new int[N][N];
    private final boolean[][] clearing = new boolean[N][N];
    private final int[][] clearingColor = new int[N][N];
    private final Piece[] tray = new Piece[3];
    private float clearT = 1f;
    private int combo;
    private int roundToken;

    // drag state
    private int dragging = -1;
    private float dragX, dragY;
    private int previewR = -1, previewC = -1;
    private boolean previewFits;
    private int returning = -1;
    private float returnT = 1f, returnFromX, returnFromY;

    // layout
    private float boardLeft, boardTop, cell, traySlotW, trayTop, trayCell;

    public AvraBlastView(Context context) {
        super(context);
    }

    @Override
    protected void reset() {
        for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) {
            board[r][c] = 0;
            clearing[r][c] = false;
        }
        combo = 0;
        clearT = 1f;
        roundToken++;
        dragging = -1;
        returning = -1;
        fillTray();
    }

    private void fillTray() {
        for (int i = 0; i < 3; i++) {
            Piece p = new Piece();
            p.shape = randomShape();
            p.colorIndex = random.nextInt(6);
            tray[i] = p;
        }
        // never hand out a tray where nothing fits if a single cell would: keep the game fair on easy
        if (difficulty == AvraGames.EASY && !anyFits()) tray[0].shape = SHAPE_LIST[0];
    }

    private Shape randomShape() {
        // weight by size: easy prefers small, hard prefers big
        float total = 0;
        float[] wgt = new float[SHAPE_LIST.length];
        for (int i = 0; i < SHAPE_LIST.length; i++) {
            int s = SHAPE_LIST[i].size;
            float w;
            if (difficulty == AvraGames.EASY) w = s <= 3 ? 3f : s <= 4 ? 2f : s <= 6 ? 0.8f : 0.3f;
            else if (difficulty == AvraGames.NORMAL) w = s <= 2 ? 1.2f : s <= 4 ? 2f : s <= 6 ? 1.4f : 0.8f;
            else w = s <= 2 ? 0.5f : s <= 4 ? 1.5f : s <= 6 ? 2f : 1.6f;
            wgt[i] = w;
            total += w;
        }
        float x = random.nextFloat() * total;
        for (int i = 0; i < SHAPE_LIST.length; i++) {
            x -= wgt[i];
            if (x <= 0) return SHAPE_LIST[i];
        }
        return SHAPE_LIST[0];
    }

    private boolean fits(Shape s, int r0, int c0) {
        if (r0 < 0 || c0 < 0 || r0 + s.h > N || c0 + s.w > N) return false;
        for (int r = 0; r < s.h; r++) for (int c = 0; c < s.w; c++) {
            if (s.cells[r][c] && board[r0 + r][c0 + c] != 0) return false;
        }
        return true;
    }

    private boolean fitsAnywhere(Shape s) {
        for (int r = 0; r + s.h <= N; r++) for (int c = 0; c + s.w <= N; c++) if (fits(s, r, c)) return true;
        return false;
    }

    private boolean anyFits() {
        for (Piece p : tray) if (p != null && !p.used && fitsAnywhere(p.shape)) return true;
        return false;
    }

    private void place(Piece p, int r0, int c0) {
        Shape s = p.shape;
        for (int r = 0; r < s.h; r++) for (int c = 0; c < s.w; c++) {
            if (s.cells[r][c]) board[r0 + r][c0 + c] = p.colorIndex + 1;
        }
        p.used = true;
        int gained = s.size;
        boolean[] fullRow = new boolean[N], fullCol = new boolean[N];
        int lines = 0;
        for (int i = 0; i < N; i++) {
            boolean row = true, col = true;
            for (int j = 0; j < N; j++) {
                if (board[i][j] == 0) row = false;
                if (board[j][i] == 0) col = false;
            }
            fullRow[i] = row;
            fullCol[i] = col;
            if (row) lines++;
            if (col) lines++;
        }
        if (lines > 0) {
            combo++;
            for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) {
                if (fullRow[r] || fullCol[c]) {
                    clearing[r][c] = true;
                    clearingColor[r][c] = board[r][c];
                    board[r][c] = 0;
                }
            }
            clearT = 0f;
            int mult = difficulty == AvraGames.HARD ? 15 : 10;
            gained += mult * lines * lines + 5 * (combo - 1) * lines;
        } else {
            combo = 0;
        }
        addScore(gained);
        boolean allUsed = true;
        for (Piece t : tray) if (!t.used) allUsed = false;
        if (allUsed) fillTray();
        if (!anyFits()) {
            // let the clear animation finish before the result screen
            final int token = ++roundToken;
            postDelayed(() -> {
                if (token == roundToken && running && !over) gameOver();
            }, lines > 0 ? 380 : 150);
        }
        kick();
    }

    @Override
    protected boolean needsFrames() {
        return clearT < 1f || returnT < 1f;
    }

    @Override
    protected void step(float dt) {
        if (clearT < 1f) {
            clearT = Math.min(1f, clearT + dt / CLEAR_S);
            if (clearT >= 1f) {
                for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) clearing[r][c] = false;
            }
        }
        if (returnT < 1f) {
            returnT = Math.min(1f, returnT + dt / RETURN_S);
            if (returnT >= 1f) returning = -1;
        }
    }

    private void layout() {
        float w = getWidth(), h = getHeight();
        float pad = dp(16);
        float boardSize = Math.min(w - pad * 2, h * 0.64f);
        cell = boardSize / N;
        boardLeft = (w - boardSize) / 2f;
        boardTop = Math.max(pad, (h - boardSize - dp(150)) / 2f);
        trayTop = boardTop + boardSize + dp(24);
        traySlotW = (w - pad * 2) / 3f;
        trayCell = Math.min(cell * 0.55f, Math.min(traySlotW / 5.5f, (h - trayTop - pad) / 5.5f));
    }

    private float slotCenterX(int i) {
        return dp(16) + traySlotW * (i + 0.5f);
    }

    private float slotCenterY() {
        return trayTop + trayCell * 2.75f;
    }

    private int pieceColor(int colorIndex) {
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(cAccent, hsl);
        hsl[0] = (hsl[0] + colorIndex * 52f) % 360f;
        hsl[1] = Math.max(0.45f, hsl[1]);
        hsl[2] = Math.max(0.38f, Math.min(0.62f, hsl[2]));
        return ColorUtils.HSLToColor(hsl);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isRunning() || paused) return true;
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                if (y < trayTop - dp(8)) return true;
                int slot = (int) ((x - dp(16)) / traySlotW);
                if (slot < 0 || slot > 2 || tray[slot] == null || tray[slot].used) return true;
                dragging = slot;
                returning = -1;
                returnT = 1f;
                dragX = x;
                dragY = y;
                updatePreview();
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                if (dragging < 0) return true;
                dragX = x;
                dragY = y;
                updatePreview();
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (dragging < 0) return true;
                int d = dragging;
                dragging = -1;
                if (e.getActionMasked() == MotionEvent.ACTION_UP && previewFits) {
                    place(tray[d], previewR, previewC);
                } else {
                    returning = d;
                    returnT = 0f;
                    Shape s = tray[d].shape;
                    returnFromX = dragX - s.w * cell / 2f;
                    returnFromY = dragY - dp(72) - s.h * cell;
                    kick();
                }
                previewR = previewC = -1;
                previewFits = false;
                invalidate();
                return true;
            }
        }
        return true;
    }

    /** The piece floats above the finger (so it is visible); its top-left snaps to the nearest cell. */
    private void updatePreview() {
        Shape s = tray[dragging].shape;
        float left = dragX - s.w * cell / 2f, top = dragY - dp(72) - s.h * cell;
        int c = Math.round((left - boardLeft) / cell), r = Math.round((top - boardTop) / cell);
        previewR = r;
        previewC = c;
        previewFits = fits(s, r, c);
    }

    @Override
    protected void render(Canvas canvas) {
        layout();
        float radius = cellRadius(cell) * 0.7f;
        float boardSize = cell * N;
        roundRect(canvas, boardLeft - dp(6), boardTop - dp(6), boardLeft + boardSize + dp(6), boardTop + boardSize + dp(6), radius + dp(6), cCard);
        int empty = ColorUtils.blendARGB(cCard, cText, 0.07f);
        float g = Math.max(1f, cell * 0.06f);
        // highlight rows/columns that the preview would complete
        boolean[] hlRow = new boolean[N], hlCol = new boolean[N];
        if (dragging >= 0 && previewFits) computeHighlights(tray[dragging].shape, hlRow, hlCol);
        for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) {
            float x = boardLeft + c * cell, y = boardTop + r * cell;
            int v = board[r][c];
            int color = v != 0 ? pieceColor(v - 1) : empty;
            if (v == 0 && (hlRow[r] || hlCol[c])) color = ColorUtils.blendARGB(empty, cAccent, 0.25f);
            if (v != 0 && (hlRow[r] || hlCol[c])) color = ColorUtils.blendARGB(color, 0xFFFFFFFF, 0.25f);
            roundRect(canvas, x + g, y + g, x + cell - g, y + cell - g, radius, color);
            if (clearing[r][c] && clearT < 1f) {
                float k = 1f - clearT;
                float s = (cell - g * 2) * (0.4f + 0.6f * k);
                float cx = x + cell / 2f, cy = y + cell / 2f;
                int cc = ColorUtils.blendARGB(pieceColor(clearingColor[r][c] - 1), 0xFFFFFFFF, clearT * 0.6f);
                roundRect(canvas, cx - s / 2f, cy - s / 2f, cx + s / 2f, cy + s / 2f, radius, ColorUtils.setAlphaComponent(cc, (int) (255 * k)));
            }
        }
        // preview ghost
        if (dragging >= 0 && previewFits) {
            Shape s = tray[dragging].shape;
            int ghost = ColorUtils.setAlphaComponent(pieceColor(tray[dragging].colorIndex), 110);
            for (int r = 0; r < s.h; r++) for (int c = 0; c < s.w; c++) {
                if (!s.cells[r][c]) continue;
                float x = boardLeft + (previewC + c) * cell, y = boardTop + (previewR + r) * cell;
                roundRect(canvas, x + g, y + g, x + cell - g, y + cell - g, radius, ghost);
            }
        }
        // tray
        for (int i = 0; i < 3; i++) {
            Piece p = tray[i];
            if (p == null || p.used || i == dragging) continue;
            boolean placeable = fitsAnywhere(p.shape);
            float sc = trayCell;
            float ox = slotCenterX(i) - p.shape.w * sc / 2f, oy = slotCenterY() - p.shape.h * sc / 2f;
            if (i == returning && returnT < 1f) {
                float t = 1f - (1f - returnT) * (1f - returnT);
                float fromCell = cell;
                sc = fromCell + (trayCell - fromCell) * t;
                ox = returnFromX + (slotCenterX(i) - p.shape.w * trayCell / 2f - returnFromX) * t;
                oy = returnFromY + (slotCenterY() - p.shape.h * trayCell / 2f - returnFromY) * t;
            }
            int color = pieceColor(p.colorIndex);
            if (!placeable) color = ColorUtils.blendARGB(color, cCard, 0.65f);
            drawShape(canvas, p.shape, ox, oy, sc, color, radius * sc / cell);
        }
        // dragged piece on top, at board scale
        if (dragging >= 0) {
            Piece p = tray[dragging];
            float left = dragX - p.shape.w * cell / 2f, top = dragY - dp(72) - p.shape.h * cell;
            drawShape(canvas, p.shape, left, top, cell, pieceColor(p.colorIndex), radius);
        }
        if (combo > 1 && clearT < 1f) {
            centerText(canvas, "×" + combo, boardLeft + boardSize / 2f, boardTop + boardSize / 2f,
                    cell * (1.1f + 0.4f * (1f - clearT)), ColorUtils.setAlphaComponent(cAccent, (int) (255 * (1f - clearT))));
        }
    }

    private void computeHighlights(Shape s, boolean[] hlRow, boolean[] hlCol) {
        for (int i = 0; i < N; i++) {
            boolean row = true, col = true;
            for (int j = 0; j < N; j++) {
                if (!filledWithPreview(s, i, j)) row = false;
                if (!filledWithPreview(s, j, i)) col = false;
            }
            hlRow[i] = row;
            hlCol[i] = col;
        }
    }

    private boolean filledWithPreview(Shape s, int r, int c) {
        if (board[r][c] != 0) return true;
        int pr = r - previewR, pc = c - previewC;
        return pr >= 0 && pc >= 0 && pr < s.h && pc < s.w && s.cells[pr][pc];
    }

    private void drawShape(Canvas canvas, Shape s, float left, float top, float sc, int color, float radius) {
        float g = Math.max(1f, sc * 0.06f);
        for (int r = 0; r < s.h; r++) for (int c = 0; c < s.w; c++) {
            if (!s.cells[r][c]) continue;
            float x = left + c * sc, y = top + r * sc;
            roundRect(canvas, x + g, y + g, x + sc - g, y + sc - g, radius, color);
        }
    }
}
