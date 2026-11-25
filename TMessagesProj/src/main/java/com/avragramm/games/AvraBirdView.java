package com.avragramm.games;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.view.MotionEvent;

import androidx.core.graphics.ColorUtils;

import com.avragramm.AvraGames;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

import java.util.ArrayList;
import java.util.Random;

/**
 * AvraBird: tap to flap through gaps between columns. The round waits for the first tap.
 * Difficulty sets the gap size, speed and spacing; the speed also grows slowly with the score.
 * Physics is in fractions of the view height, so phones and tablets play the same.
 */
public class AvraBirdView extends AvraGameView {

    private static final class Pipe {
        float x, gapY;
        boolean passed;
    }

    private final Random random = new Random();
    private final ArrayList<Pipe> pipes = new ArrayList<>();
    private final Path path = new Path();
    private float birdY, vy, wing, groundShift, cloudShift;
    private boolean waiting;

    public AvraBirdView(Context context) {
        super(context);
    }

    private float groundH() {
        return getHeight() * 0.1f;
    }

    private float playH() {
        return getHeight() - groundH();
    }

    private float birdX() {
        return getWidth() * 0.3f;
    }

    private float birdR() {
        return Math.min(getWidth(), getHeight()) * 0.042f;
    }

    private float gap() {
        float k = difficulty == AvraGames.EASY ? 0.31f : difficulty == AvraGames.NORMAL ? 0.26f : 0.22f;
        return playH() * k;
    }

    private float speed() {
        float base = difficulty == AvraGames.EASY ? 0.34f : difficulty == AvraGames.NORMAL ? 0.42f : 0.5f;
        return getWidth() * base * (1f + Math.min(0.35f, score * 0.012f));
    }

    private float pipeW() {
        return Math.max(dp(44), getWidth() * 0.15f);
    }

    private float spacing() {
        return getWidth() * (difficulty == AvraGames.HARD ? 0.56f : 0.62f) + pipeW();
    }

    @Override
    protected void reset() {
        pipes.clear();
        birdY = playH() * 0.45f;
        vy = 0;
        waiting = true;
    }

    private void addPipe(float x) {
        Pipe p = new Pipe();
        p.x = x;
        float margin = playH() * 0.08f + gap() / 2f;
        p.gapY = margin + random.nextFloat() * Math.max(1f, playH() - margin * 2);
        pipes.add(p);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN && isRunning() && !paused) {
            flap();
        }
        return true;
    }

    private void flap() {
        if (waiting) {
            waiting = false;
            pipes.clear();
            addPipe(getWidth() + pipeW());
        }
        vy = -playH() * 0.78f;
    }

    @Override
    protected void step(float dt) {
        wing += dt * (waiting ? 4f : 10f);
        cloudShift += dt * getWidth() * 0.04f;
        if (waiting) {
            birdY = playH() * 0.45f + (float) Math.sin(wing) * playH() * 0.015f;
            groundShift += dt * speed();
            return;
        }
        float g = playH() * 2.7f;
        vy = Math.min(vy + g * dt, playH() * 1.2f);
        birdY += vy * dt;
        float sp = speed();
        groundShift += dt * sp;
        for (Pipe p : pipes) p.x -= sp * dt;
        if (!pipes.isEmpty() && pipes.get(0).x + pipeW() < -dp(4)) pipes.remove(0);
        Pipe last = pipes.isEmpty() ? null : pipes.get(pipes.size() - 1);
        if (last == null || last.x < getWidth() + pipeW() - spacing()) addPipe(last == null ? getWidth() + pipeW() : last.x + spacing());
        float bx = birdX(), r = birdR() * 0.86f;
        for (Pipe p : pipes) {
            if (!p.passed && p.x + pipeW() < bx - r) {
                p.passed = true;
                addScore(1);
            }
            if (bx + r > p.x && bx - r < p.x + pipeW()) {
                float top = p.gapY - gap() / 2f, bottom = p.gapY + gap() / 2f;
                if (circleHitsRect(bx, birdY, r, p.x, -getHeight(), p.x + pipeW(), top)
                        || circleHitsRect(bx, birdY, r, p.x, bottom, p.x + pipeW(), playH())) {
                    gameOver();
                    return;
                }
            }
        }
        if (birdY + r >= playH() || birdY - r <= -birdR() * 2) gameOver();
    }

    private static boolean circleHitsRect(float cx, float cy, float r, float l, float t, float rr, float b) {
        float nx = Math.max(l, Math.min(cx, rr)), ny = Math.max(t, Math.min(cy, b));
        float dx = cx - nx, dy = cy - ny;
        return dx * dx + dy * dy < r * r;
    }

    @Override
    protected void render(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        if (pipes.isEmpty() && !running && !over) {
            birdY = playH() * 0.45f;
        }
        // soft background clouds in the card colour
        int cloud = ColorUtils.setAlphaComponent(cCard, 200);
        fill.setColor(cloud);
        float cw = w * 0.5f;
        for (int i = 0; i < 4; i++) {
            float x = ((i * cw * 0.7f - cloudShift) % (w + cw) + (w + cw)) % (w + cw) - cw * 0.4f;
            float y = playH() * (0.12f + 0.17f * (i % 3));
            canvas.drawCircle(x, y, cw * 0.16f, fill);
            canvas.drawCircle(x + cw * 0.16f, y + cw * 0.03f, cw * 0.12f, fill);
            canvas.drawCircle(x - cw * 0.15f, y + cw * 0.04f, cw * 0.1f, fill);
        }
        // columns
        float pw = pipeW();
        float radius = cellRadius(pw);
        int pipeColor = ColorUtils.blendARGB(cAccent, cCard, 0.15f);
        int capColor = ColorUtils.blendARGB(cAccent, cText, 0.2f);
        for (Pipe p : pipes) {
            float top = p.gapY - gap() / 2f, bottom = p.gapY + gap() / 2f;
            roundRect(canvas, p.x, -radius, p.x + pw, top, radius, pipeColor);
            roundRect(canvas, p.x - dp(4), top - dp(18), p.x + pw + dp(4), top, radius, capColor);
            roundRect(canvas, p.x, bottom, p.x + pw, playH() + radius, radius, pipeColor);
            roundRect(canvas, p.x - dp(4), bottom, p.x + pw + dp(4), bottom + dp(18), radius, capColor);
        }
        // ground
        fill.setColor(cCard);
        canvas.drawRect(0, playH(), w, h, fill);
        fill.setColor(cDivider);
        canvas.drawRect(0, playH(), w, playH() + dp(3), fill);
        float stripe = dp(28);
        float off = groundShift % (stripe * 2);
        fill.setColor(ColorUtils.blendARGB(cCard, cText, 0.06f));
        for (float x = -off; x < w; x += stripe * 2) {
            canvas.drawRect(x, playH() + dp(3), x + stripe, playH() + dp(12), fill);
        }
        drawBird(canvas);
        if (waiting && running) {
            centerText(canvas, LocaleController.getString(R.string.AvraGameTapToFly), w / 2f, playH() * 0.7f, dp(18), cGray);
        }
    }

    private void drawBird(Canvas canvas) {
        float x = birdX(), y = birdY, r = birdR();
        float angle = waiting ? 0f : Math.max(-25f, Math.min(70f, vy / playH() * 70f));
        canvas.save();
        canvas.rotate(angle, x, y);
        fill.setColor(cAccent);
        canvas.drawCircle(x, y, r, fill);
        // wing
        float flapK = (float) Math.sin(wing);
        fill.setColor(ColorUtils.blendARGB(cAccent, cAccentText, 0.35f));
        rect.set(x - r * 0.8f, y - r * 0.1f + flapK * r * 0.25f, x + r * 0.05f, y + r * 0.45f + flapK * r * 0.25f);
        canvas.drawOval(rect, fill);
        // eye
        fill.setColor(0xFFFFFFFF);
        canvas.drawCircle(x + r * 0.38f, y - r * 0.3f, r * 0.3f, fill);
        fill.setColor(0xFF1A1A1A);
        canvas.drawCircle(x + r * 0.46f, y - r * 0.3f, r * 0.13f, fill);
        // beak
        fill.setColor(cDanger);
        path.reset();
        path.moveTo(x + r * 0.75f, y - r * 0.05f);
        path.lineTo(x + r * 1.35f, y + r * 0.12f);
        path.lineTo(x + r * 0.75f, y + r * 0.32f);
        path.close();
        canvas.drawPath(path, fill);
        canvas.restore();
    }
}
