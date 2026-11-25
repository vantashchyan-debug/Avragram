package com.avragramm.games;

import android.content.Context;
import android.graphics.Canvas;

import androidx.core.graphics.ColorUtils;

import com.avragramm.AvraGames;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Random;

/**
 * AvraSnake: swipe to turn, eat to grow. Easy: slow, walls wrap around. Normal: walls kill.
 * Hard: fast and speeds up with every meal. Up to two turns are buffered so quick swipes are not lost.
 */
public class AvraSnakeView extends AvraGameView {

    private static final int COLS = 17;

    private final Random random = new Random();
    private int rows = 24;
    /** Body cells as r * COLS + c, head first. */
    private final ArrayList<Integer> body = new ArrayList<>();
    private final ArrayDeque<Integer> turns = new ArrayDeque<>();
    private int dir = RIGHT, prevDir = RIGHT;
    private int food = -1;
    private float acc, stepTime, foodPulse;
    private int grow;

    public AvraSnakeView(Context context) {
        super(context);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (!running) computeRows(w, h);
    }

    private void computeRows(int w, int h) {
        if (w <= 0 || h <= 0) return;
        float cell = (w - dp(24)) / COLS;
        rows = Math.max(12, Math.min(40, (int) ((h - dp(24)) / cell)));
    }

    @Override
    protected void reset() {
        computeRows(getWidth(), getHeight());
        body.clear();
        turns.clear();
        int r = rows / 2;
        for (int i = 0; i < 4; i++) body.add(r * COLS + (COLS / 2 - i));
        dir = prevDir = RIGHT;
        grow = 0;
        acc = 0;
        stepTime = difficulty == AvraGames.EASY ? 1f / 6.5f : difficulty == AvraGames.NORMAL ? 1f / 9f : 1f / 12f;
        placeFood();
    }

    private void placeFood() {
        int free = rows * COLS - body.size();
        if (free <= 0) {
            food = -1;
            return;
        }
        int k = random.nextInt(free);
        for (int i = 0; i < rows * COLS; i++) {
            if (body.contains(i)) continue;
            if (k-- == 0) {
                food = i;
                return;
            }
        }
    }

    @Override
    protected void onSwipe(int direction) {
        int last = turns.isEmpty() ? dir : turns.peekLast();
        if (direction == last || (direction + 2) % 4 == last) return;
        if (turns.size() < 2) turns.add(direction);
    }

    @Override
    protected void step(float dt) {
        foodPulse = (foodPulse + dt * 3f) % 1f;
        acc += dt;
        while (acc >= stepTime && running && !over) {
            acc -= stepTime;
            advance();
        }
    }

    private void advance() {
        prevDir = dir;
        if (!turns.isEmpty()) dir = turns.poll();
        int head = body.get(0);
        int r = head / COLS, c = head % COLS;
        switch (dir) {
            case UP: r--; break;
            case DOWN: r++; break;
            case LEFT: c--; break;
            default: c++; break;
        }
        boolean wrap = difficulty == AvraGames.EASY;
        if (r < 0 || r >= rows || c < 0 || c >= COLS) {
            if (!wrap) {
                gameOver();
                return;
            }
            r = (r + rows) % rows;
            c = (c + COLS) % COLS;
        }
        int next = r * COLS + c;
        // moving into the tail cell is fine unless the snake is growing this step
        int tailIndex = body.size() - 1;
        int hit = body.indexOf(next);
        if (hit >= 0 && !(hit == tailIndex && grow == 0)) {
            gameOver();
            return;
        }
        body.add(0, next);
        if (next == food) {
            grow += 2;
            addScore(difficulty == AvraGames.HARD ? 15 : difficulty == AvraGames.NORMAL ? 10 : 5);
            if (difficulty == AvraGames.HARD) stepTime = Math.max(1f / 22f, stepTime * 0.97f);
            else if (difficulty == AvraGames.NORMAL) stepTime = Math.max(1f / 16f, stepTime * 0.985f);
            placeFood();
        }
        if (grow > 0) grow--;
        else body.remove(body.size() - 1);
    }

    @Override
    protected void render(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float cell = Math.min((w - dp(24)) / COLS, (h - dp(24)) / rows);
        float bw = cell * COLS, bh = cell * rows;
        float left = (w - bw) / 2f, top = (h - bh) / 2f;
        float radius = cellRadius(cell);
        roundRect(canvas, left - dp(4), top - dp(4), left + bw + dp(4), top + bh + dp(4), radius + dp(4), cCard);
        // subtle checkerboard
        int alt = ColorUtils.blendARGB(cCard, cText, 0.035f);
        fill.setColor(alt);
        for (int r = 0; r < rows; r++) for (int c = (r & 1); c < COLS; c += 2) {
            canvas.drawRect(left + c * cell, top + r * cell, left + (c + 1) * cell, top + (r + 1) * cell, fill);
        }
        if (difficulty != AvraGames.EASY) {
            stroke.setColor(ColorUtils.setAlphaComponent(cDanger, 150));
            stroke.setStrokeWidth(dp(2));
            rect.set(left - dp(1), top - dp(1), left + bw + dp(1), top + bh + dp(1));
            canvas.drawRoundRect(rect, radius, radius, stroke);
        }
        if (food >= 0) {
            float fr = food / COLS, fc = food % COLS;
            float s = cell * (0.62f + 0.12f * (float) Math.sin(foodPulse * Math.PI * 2));
            float cx = left + fc * cell + cell / 2f, cy = top + fr * cell + cell / 2f;
            roundRect(canvas, cx - s / 2f, cy - s / 2f, cx + s / 2f, cy + s / 2f, Math.min(radius, s / 2f), cDanger);
        }
        int n = body.size();
        // smooth movement: the head and tail are drawn between cells according to the step progress
        float t = running && !paused && !over ? Math.min(1f, acc / stepTime) : 0f;
        for (int i = n - 1; i >= 0; i--) {
            int idx = body.get(i);
            float r = idx / COLS, c = idx % COLS;
            float k = n <= 1 ? 1f : 1f - (float) i / (n - 1);
            int color = ColorUtils.blendARGB(ColorUtils.blendARGB(cAccent, cCard, 0.45f), cAccent, k);
            float inset = cell * (i == 0 ? 0.04f : 0.1f);
            float x = left + c * cell, y = top + r * cell;
            if (i == 0 && t > 0 && !over) {
                float dx = dir == RIGHT ? 1 : dir == LEFT ? -1 : 0, dy = dir == DOWN ? 1 : dir == UP ? -1 : 0;
                // only nudge, never cross the wall visually
                float nudge = Math.min(t, 0.3f) * cell;
                x += dx * nudge;
                y += dy * nudge;
            }
            roundRect(canvas, x + inset, y + inset, x + cell - inset, y + cell - inset, radius, color);
            if (i == 0) drawEyes(canvas, x, y, cell);
        }
    }

    private void drawEyes(Canvas canvas, float x, float y, float cell) {
        int eye = onColor(cAccent);
        fill.setColor(eye);
        float e = cell * 0.11f, a = cell * 0.3f, b = cell * 0.68f, f = cell * 0.66f;
        float ax, ay, bx, by;
        switch (dir) {
            case UP: ax = x + a; ay = y + cell - f; bx = x + b; by = ay; break;
            case DOWN: ax = x + a; ay = y + f; bx = x + b; by = ay; break;
            case LEFT: ax = x + cell - f; ay = y + a; bx = ax; by = y + b; break;
            default: ax = x + f; ay = y + a; bx = ax; by = y + b; break;
        }
        canvas.drawCircle(ax, ay, e, fill);
        canvas.drawCircle(bx, by, e, fill);
    }
}
