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
 * AvraRun: an endless runner. Touch — jump (hold for a higher jump), swipe down — duck on the
 * ground or dive in the air. Obstacles: columns on the ground and, later, flying bars to duck
 * under. The speed keeps growing; difficulty sets the starting speed, growth and gaps.
 * Score = distance. Sizes are fractions of the view, so phones and tablets play the same.
 */
public class AvraRunView extends AvraGameView {

    private static final class Obstacle {
        float x, w, h;
        boolean flying;
    }

    private final Random random = new Random();
    private final ArrayList<Obstacle> obstacles = new ArrayList<>();
    private float y, vy, distance, speed, nextGap, legT, groundShift, duckLeft;
    private boolean holding, onGround = true;
    private float downY;

    public AvraRunView(Context context) {
        super(context);
    }

    private float unit() {
        return Math.min(getWidth() * 0.9f, getHeight()) / 10f;
    }

    private float groundY() {
        return getHeight() * 0.72f;
    }

    private float runnerX() {
        return getWidth() * 0.16f;
    }

    private float startSpeed() {
        return unit() * (difficulty == AvraGames.EASY ? 6.5f : difficulty == AvraGames.NORMAL ? 8f : 10f);
    }

    private float maxSpeed() {
        return unit() * (difficulty == AvraGames.HARD ? 26f : 22f);
    }

    @Override
    protected void reset() {
        obstacles.clear();
        y = 0;
        vy = 0;
        onGround = true;
        distance = 0;
        speed = startSpeed();
        nextGap = unit() * 8f;
        duckLeft = 0;
        holding = false;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isRunning() || paused) return true;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = e.getY();
                holding = true;
                if (onGround && duckLeft <= 0) {
                    vy = unit() * 17f;
                    onGround = false;
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (e.getY() - downY > dp(32)) {
                    holding = false;
                    if (onGround) duckLeft = 0.7f;
                    else vy = Math.min(vy, -unit() * 22f);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                holding = false;
                return true;
        }
        return true;
    }

    @Override
    protected void step(float dt) {
        float u = unit();
        float accel = u * (difficulty == AvraGames.EASY ? 0.12f : difficulty == AvraGames.NORMAL ? 0.18f : 0.26f);
        speed = Math.min(maxSpeed(), speed + accel * dt);
        distance += speed * dt;
        int newScore = (int) (distance / u);
        if (newScore > score) addScore(newScore - score);
        legT += dt * speed / u * 1.3f;
        groundShift += speed * dt;
        if (duckLeft > 0) duckLeft -= dt;
        // jump physics: holding the finger keeps a lower gravity for a higher jump
        if (!onGround) {
            float g = u * (holding && vy > 0 ? 38f : 70f);
            vy -= g * dt;
            y += vy * dt;
            if (y <= 0) {
                y = 0;
                vy = 0;
                onGround = true;
            }
        }
        for (Obstacle o : obstacles) o.x -= speed * dt;
        while (!obstacles.isEmpty() && obstacles.get(0).x + obstacles.get(0).w < 0) obstacles.remove(0);
        nextGap -= speed * dt;
        if (nextGap <= 0) spawnObstacle();
        if (hit()) gameOver();
    }

    private void spawnObstacle() {
        float u = unit();
        Obstacle o = new Obstacle();
        o.x = getWidth() + u;
        boolean canFly = score > (difficulty == AvraGames.HARD ? 150 : 300) && difficulty != AvraGames.EASY;
        if (canFly && random.nextFloat() < 0.3f) {
            o.flying = true;
            o.w = u * 1.6f;
            o.h = u * 0.55f;
        } else {
            int kind = random.nextInt(3);
            o.w = u * (kind == 0 ? 0.6f : kind == 1 ? 1.0f : 1.6f);
            o.h = u * (kind == 0 ? 1.3f : kind == 1 ? 1.7f : 1.15f);
        }
        obstacles.add(o);
        float speedK = speed / startSpeed();
        float minGap = u * (difficulty == AvraGames.HARD ? 6.5f : difficulty == AvraGames.NORMAL ? 7.5f : 9f) * Math.max(1f, speedK * 0.75f);
        nextGap = minGap + random.nextFloat() * u * 7f;
    }

    private boolean ducking() {
        return onGround && duckLeft > 0;
    }

    private float runnerW() {
        return unit() * (ducking() ? 1.35f : 0.95f);
    }

    private float runnerH() {
        return unit() * (ducking() ? 0.7f : 1.35f);
    }

    private boolean hit() {
        float u = unit();
        float l = runnerX() + u * 0.1f, r = runnerX() + runnerW() - u * 0.1f;
        float bottom = groundY() - y, top = bottom - runnerH() + u * 0.08f;
        for (Obstacle o : obstacles) {
            float ol = o.x, or = o.x + o.w;
            float ob, ot;
            if (o.flying) {
                ob = groundY() - u * 0.95f;
                ot = ob - o.h;
            } else {
                ob = groundY();
                ot = ob - o.h;
            }
            if (r > ol && l < or && bottom > ot && top < ob) return true;
        }
        return false;
    }

    @Override
    protected void render(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float u = unit();
        float gy = groundY();
        // horizon hills
        fill.setColor(ColorUtils.setAlphaComponent(cCard, 220));
        float hillShift = (groundShift * 0.2f) % (w * 0.8f);
        for (int i = -1; i < 3; i++) {
            float cx = i * w * 0.8f - hillShift + w * 0.4f;
            canvas.drawCircle(cx, gy + u * 1.5f, u * 3.2f, fill);
        }
        // ground
        fill.setColor(cCard);
        canvas.drawRect(0, gy, w, h, fill);
        fill.setColor(cDivider);
        canvas.drawRect(0, gy, w, gy + dp(2), fill);
        fill.setColor(ColorUtils.blendARGB(cCard, cText, 0.1f));
        float dash = u * 0.5f, period = u * 1.7f;
        float off = groundShift % period;
        for (float x = -off; x < w; x += period) {
            canvas.drawRect(x, gy + u * 0.35f, x + dash, gy + u * 0.35f + dp(2), fill);
            canvas.drawRect(x + period * 0.45f, gy + u * 0.8f, x + period * 0.45f + dash * 0.6f, gy + u * 0.8f + dp(2), fill);
        }
        // obstacles
        float radius = cellRadius(u);
        int col = ColorUtils.blendARGB(cText, cCard, 0.35f);
        for (Obstacle o : obstacles) {
            if (o.flying) {
                float ob = gy - u * 0.95f;
                roundRect(canvas, o.x, ob - o.h, o.x + o.w, ob, radius, cDanger);
                fill.setColor(ColorUtils.setAlphaComponent(cDanger, 110));
                float flap = (float) Math.sin(legT * 2f) * o.h * 0.5f;
                canvas.drawRect(o.x + o.w * 0.3f, ob - o.h - flap * 0.5f - dp(3), o.x + o.w * 0.7f, ob - o.h, fill);
            } else {
                roundRect(canvas, o.x, gy - o.h, o.x + o.w, gy + radius, radius, col);
                if (o.w > u * 0.8f) {
                    roundRect(canvas, o.x - u * 0.18f, gy - o.h * 0.65f, o.x + u * 0.12f, gy - o.h * 0.4f, radius * 0.5f, col);
                }
            }
        }
        // runner
        float rw = runnerW(), rh = runnerH();
        float left = runnerX(), bottom = gy - y;
        float legH = u * 0.28f;
        boolean anim = onGround && !over;
        float l1 = anim ? (float) Math.sin(legT * 6f) * legH * 0.5f : 0f;
        fill.setColor(ColorUtils.blendARGB(cAccent, cText, 0.25f));
        canvas.drawRect(left + rw * 0.22f, bottom - legH, left + rw * 0.22f + u * 0.16f, bottom - Math.max(0, l1), fill);
        canvas.drawRect(left + rw * 0.6f, bottom - legH, left + rw * 0.6f + u * 0.16f, bottom - Math.max(0, -l1), fill);
        roundRect(canvas, left, bottom - rh, left + rw, bottom - legH * 0.6f, cellRadius(rw), over ? cDanger : cAccent);
        // eye and visor
        fill.setColor(0xFFFFFFFF);
        float ex = left + rw * 0.72f, ey = bottom - rh + u * 0.32f;
        canvas.drawCircle(ex, ey, u * 0.13f, fill);
        fill.setColor(0xFF1A1A1A);
        if (over) {
            stroke.setColor(0xFF1A1A1A);
            stroke.setStrokeWidth(dp(2));
            float s = u * 0.08f;
            canvas.drawLine(ex - s, ey - s, ex + s, ey + s, stroke);
            canvas.drawLine(ex - s, ey + s, ex + s, ey - s, stroke);
        } else {
            canvas.drawCircle(ex + u * 0.04f, ey, u * 0.06f, fill);
        }
    }
}
