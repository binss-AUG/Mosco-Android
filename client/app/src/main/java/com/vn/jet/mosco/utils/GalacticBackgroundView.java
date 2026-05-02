package com.vn.jet.mosco.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.vn.jet.mosco.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Custom View for Galactic Background with Planet Arcs and Twinkling Particles.
 * Implements "Quiet Luxury" aesthetic with interactive, slow-moving elements.
 */
public class GalacticBackgroundView extends View {

    private Paint planetPaint;
    private Paint particlePaint;
    private List<Particle> particles;
    private Random random;
    private int primaryColor;
    private int primaryDimColor;

    private float touchX = -1;
    private float touchY = -1;

    public GalacticBackgroundView(Context context) {
        super(context);
        init();
    }

    public GalacticBackgroundView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public enum Mode {
        SPLASH, ONBOARDING, SIGN_IN, SIGN_UP, RECOVERY, RANDOM
    }

    // --- COSMIC CONTINUITY: Static positions to persist across Activities ---
    private static float staticP1X = -1, staticP1Y = -1;
    private static float staticP2X = -1, staticP2Y = -1;

    private Mode currentMode = Mode.RANDOM;
    private float p1X, p1Y, p2X, p2Y;
    private float targetP1X, targetP1Y, targetP2X, targetP2Y;
    private long startTime;
    private boolean initialized = false;
    private boolean showPlanets = true;

    private void init() {
        random = new Random();
        particles = new ArrayList<>();
        startTime = System.currentTimeMillis();
        
        primaryColor = ContextCompat.getColor(getContext(), R.color.mosco_primary);
        primaryDimColor = ContextCompat.getColor(getContext(), R.color.mosco_primary_dim);

        planetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Initialize particles
        for (int i = 0; i < 80; i++) {
            particles.add(new Particle());
        }
    }

    public void setMode(Mode mode) {
        // Save current positions to static before switching mode if already initialized
        if (initialized) {
            staticP1X = p1X; staticP1Y = p1Y;
            staticP2X = p2X; staticP2Y = p2Y;
        }
        
        this.currentMode = mode;
        this.initialized = false; // Trigger re-target
        invalidate();
    }

    private void randomizePositions(int w, int h) {
        if (initialized) return;
        
        showPlanets = true;
        
        switch (currentMode) {
            case SPLASH:
                showPlanets = false;
                break;
            case ONBOARDING:
                targetP1X = w / 2f; targetP1Y = h * 1.05f; // Planet center at bottom edge
                targetP2X = w / 2f; targetP2Y = h * 2.0f; 
                break;
            case SIGN_IN:
                targetP1X = w * 1.1f; targetP1Y = -h * 0.1f; // Further top-right
                targetP2X = -w * 0.1f; targetP2Y = h * 1.1f; // Further bottom-left
                break;
            case SIGN_UP:
                targetP1X = -w * 0.1f; targetP1Y = -h * 0.1f; // Further top-left
                targetP2X = w * 1.1f; targetP2Y = h * 1.1f;  // Further bottom-right
                break;
            case RECOVERY:
                targetP1X = -w * 0.6f; targetP1Y = h / 2f;    // Extremely far left
                targetP2X = w * 1.6f; targetP2Y = h / 2f;     // Extremely far right
                break;
            default: // RANDOM
                int config = random.nextInt(4);
                if (config == 0) { targetP1X = w; targetP1Y = 0; targetP2X = 0; targetP2Y = h; }
                else if (config == 1) { targetP1X = 0; targetP1Y = 0; targetP2X = w; targetP2Y = h; }
                else if (config == 2) { targetP1X = w/2f; targetP1Y = -h*0.1f; targetP2X = w/2f; targetP2Y = h*1.1f; }
                else { targetP1X = -w*0.1f; targetP1Y = h/2f; targetP2X = w*1.1f; targetP2Y = h/2f; }
                break;
        }

        // Use static positions if available for continuity, otherwise start from random offset
        if (staticP1X != -1) {
            p1X = staticP1X; p1Y = staticP1Y;
            p2X = staticP2X; p2Y = staticP2Y;
        } else {
            // Initial load - start from a drastic offset for transition effect
            p1X = targetP1X + (random.nextFloat() - 0.5f) * w * 1.5f;
            p1Y = targetP1Y + (random.nextFloat() - 0.5f) * h * 1.5f;
            p2X = targetP2X + (random.nextFloat() - 0.5f) * w * 1.5f;
            p2Y = targetP2Y + (random.nextFloat() - 0.5f) * h * 1.5f;
        }

        initialized = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;
        
        randomizePositions(w, h);
        canvas.drawColor(Color.BLACK);

        float elapsed = (System.currentTimeMillis() - startTime) / 1000f;

        // Smoothly interpolate towards target positions
        // Faster initial move, then slow down
        p1X += (targetP1X - p1X) * 0.035f;
        p1Y += (targetP1Y - p1Y) * 0.035f;
        p2X += (targetP2X - p2X) * 0.035f;
        p2Y += (targetP2Y - p2Y) * 0.035f;

        // Update static positions for the next activity
        staticP1X = p1X; staticP1Y = p1Y;
        staticP2X = p2X; staticP2Y = p2Y;

        // 2. Draw Planet Arcs
        if (showPlanets) {
            drawPlanets(canvas, w, h, elapsed);
        }

        // 3. Draw Particles
        drawParticles(canvas);

        invalidate();
    }

    private void drawPlanets(Canvas canvas, int w, int h, float elapsed) {
        float driftX = (float) Math.sin(elapsed * 0.12) * 40;
        float driftY = (float) Math.cos(elapsed * 0.12) * 40;

        // Dynamic radius based on mode
        float baseRadius = (currentMode == Mode.RECOVERY) ? h * 0.7f : h * 1.0f;
        float bodyRadius = (currentMode == Mode.RECOVERY) ? h * 0.4f : h * 0.58f;

        // Planet 1 - Increased Opacity for "Solid" feel
        planetPaint.setShader(new RadialGradient(p1X + driftX, p1Y + driftY, baseRadius * 1.1f,
                new int[]{
                    Color.argb(80, Color.red(primaryDimColor), Color.green(primaryDimColor), Color.blue(primaryDimColor)),
                    Color.argb(30, Color.red(primaryDimColor), Color.green(primaryDimColor), Color.blue(primaryDimColor)),
                    Color.TRANSPARENT
                },
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(p1X + driftX, p1Y + driftY, baseRadius, planetPaint);

        planetPaint.setShader(new LinearGradient(p1X - w*0.3f, p1Y, p1X + w*0.3f, p1Y + h*0.3f,
                new int[]{Color.argb(180, Color.red(primaryDimColor), Color.green(primaryDimColor), Color.blue(primaryDimColor)), Color.BLACK},
                null, Shader.TileMode.CLAMP));
        planetPaint.setAlpha(160);
        canvas.drawCircle(p1X + driftX, p1Y + driftY, bodyRadius, planetPaint);

        // Planet 2
        if (currentMode != Mode.ONBOARDING) {
            float baseRadius2 = (currentMode == Mode.RECOVERY) ? h * 0.8f : h * 1.1f;
            float bodyRadius2 = (currentMode == Mode.RECOVERY) ? h * 0.45f : h * 0.68f;

            planetPaint.setShader(new RadialGradient(p2X + driftX, p2Y + driftY, baseRadius2 * 1.1f,
                    new int[]{
                        Color.argb(100, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor)),
                        Color.argb(40, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor)),
                        Color.TRANSPARENT
                    },
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(p2X + driftX, p2Y + driftY, baseRadius2, planetPaint);

            planetPaint.setShader(new LinearGradient(p2X - w*0.3f, p2Y - h*0.3f, p2X + w*0.3f, p2Y,
                    new int[]{Color.argb(200, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor)), Color.BLACK},
                    null, Shader.TileMode.CLAMP));
            planetPaint.setAlpha(180);
            canvas.drawCircle(p2X + driftX, p2Y + driftY, bodyRadius2, planetPaint);
        }
    }

    private void drawParticles(Canvas canvas) {
        for (Particle p : particles) {
            p.update(getWidth(), getHeight(), touchX, touchY);
            particlePaint.setColor(Color.WHITE);
            particlePaint.setAlpha(p.alpha);
            
            if (p.alpha > 150) {
                Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                glowPaint.setShader(new RadialGradient(p.x, p.y, p.size * 3,
                        new int[]{Color.argb(p.alpha / 4, 255, 255, 255), Color.TRANSPARENT},
                        null, Shader.TileMode.CLAMP));
                canvas.drawCircle(p.x, p.y, p.size * 3, glowPaint);
            }
            
            canvas.drawCircle(p.x, p.y, p.size, particlePaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                touchX = event.getX();
                touchY = event.getY();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                touchX = -1;
                touchY = -1;
                break;
        }
        return true;
    }

    private class Particle {
        float x, y, size, speedX, speedY;
        int alpha;
        boolean fadingIn;

        Particle() {
            reset(true);
        }

        void reset(boolean fullRandom) {
            x = random.nextInt(getWidth() > 0 ? getWidth() : 1000);
            y = random.nextInt(getHeight() > 0 ? getHeight() : 2000);
            size = 0.5f + random.nextFloat() * 2.0f;
            speedX = (random.nextFloat() - 0.5f) * 0.12f;
            speedY = (random.nextFloat() - 0.5f) * 0.12f;
            alpha = random.nextInt(150) + 20;
            fadingIn = random.nextBoolean();
        }

        void update(int w, int h, float tx, float ty) {
            x += speedX;
            y += speedY;

            if (fadingIn) {
                alpha += 1;
                if (alpha >= 180) fadingIn = false;
            } else {
                alpha -= 1;
                if (alpha <= 20) fadingIn = true;
            }

            if (tx != -1 && ty != -1) {
                float dx = x - tx;
                float dy = y - ty;
                float distSq = dx * dx + dy * dy;
                if (distSq < 40000) {
                    float dist = (float) Math.sqrt(distSq);
                    x += (dx / dist) * 1.2f;
                    y += (dy / dist) * 1.2f;
                }
            }

            if (x < 0) x = w;
            if (x > w) x = 0;
            if (y < 0) y = h;
            if (y > h) y = 0;
        }
    }
}
