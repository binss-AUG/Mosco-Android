package com.vn.jet.mosco.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.Random;

public class BadgeAuraView extends View {

    private static final int PARTICLE_COUNT = 25;
    private Paint paint;
    private Particle[] particles;
    private ValueAnimator animator;
    private Random random;
    private float density;

    public BadgeAuraView(Context context) {
        super(context);
        init();
    }

    public BadgeAuraView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        random = new Random();
        density = getResources().getDisplayMetrics().density;
        particles = new Particle[PARTICLE_COUNT];
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particles[i] = new Particle();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        for (Particle p : particles) {
            p.reset(w, h, random, density);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (particles == null || getWidth() == 0) return;

        for (Particle p : particles) {
            paint.setAlpha((int) (p.alpha * 255));
            canvas.drawCircle(p.x, p.y, p.radius, paint);
        }
    }

    public void startAnimation() {
        if (animator != null && animator.isRunning()) return;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1000); 
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            int w = getWidth();
            int h = getHeight();
            if (w > 0 && h > 0) {
                for (Particle p : particles) {
                    p.update(w, h, density);
                }
                invalidate();
            }
        });
        animator.start();
    }

    public void stopAnimation() {
        if (animator != null) {
            animator.cancel();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
    }

    private static class Particle {
        float x, y;
        float vx, vy;
        float radius;
        float alpha;
        float alphaSpeed;
        boolean fadingIn;

        void reset(int w, int h, Random random, float density) {
            x = random.nextFloat() * w;
            y = random.nextFloat() * h;
            // Very slow, drift-like movement
            vx = (random.nextFloat() - 0.5f) * 0.8f; 
            vy = (random.nextFloat() - 0.5f) * 0.8f;
            radius = (1.5f + random.nextFloat() * 3f) * density; 
            alpha = random.nextFloat();
            alphaSpeed = 0.005f + random.nextFloat() * 0.02f;
            fadingIn = random.nextBoolean();
        }

        void update(int w, int h, float density) {
            x += vx * density;
            y += vy * density;

            if (x < -20) { x = w + 20; }
            if (x > w + 20) { x = -20; }
            if (y < -20) { y = h + 20; }
            if (y > h + 20) { y = -20; }

            if (fadingIn) {
                alpha += alphaSpeed;
                if (alpha >= 1f) {
                    alpha = 1f;
                    fadingIn = false;
                }
            } else {
                alpha -= alphaSpeed;
                if (alpha <= 0.05f) {
                    alpha = 0.05f;
                    fadingIn = true;
                }
            }
        }
    }
}
