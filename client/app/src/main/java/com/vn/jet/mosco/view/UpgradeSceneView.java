package com.vn.jet.mosco.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.vn.jet.mosco.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * UpgradeSceneView for Mosco
 * Handles the high-intensity core animation (glitches, shakes, streaks) during the upgrade process.
 */
public class UpgradeSceneView extends View {

    private Paint corePaint;
    private Paint glitch1Paint;
    private Paint glitch2Paint;
    private Paint floorGlowPaint;
    private Paint streakPaint;

    private RectF coreBounds = new RectF();

    private float shakeOffsetY = 0f;
    private float shakeOffsetX = 0f;
    private float glitch1OffsetX = 0f;
    private float glitch2OffsetX = 0f;

    private float currentOffsetY = 0f;
    private float glitchAlphaMult = 0f;

    private long startTime;
    private ValueAnimator animator;

    private List<Streak> streaks = new ArrayList<>();
    private Random random = new Random();

    private int coreColor = Color.WHITE;
    private int coreGlowColor = 0; // Will be initialized in init()
    private int glitch1Color = Color.argb((int)(0.4f * 255), 255, 0, 85);
    private int glitch2Color = Color.argb((int)(0.4f * 255), 0, 255, 255);

    public UpgradeSceneView(Context context) {
        super(context);
        init();
    }

    public UpgradeSceneView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        if (coreGlowColor == 0) {
            coreGlowColor = ContextCompat.getColor(getContext(), R.color.palette_cyan_accent);
        }
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        corePaint.setColor(coreColor);
        corePaint.setShadowLayer(50f, 0, 0, coreGlowColor);

        glitch1Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glitch1Paint.setColor(Color.WHITE);
        glitch1Paint.setAlpha((int) (255 * 0.4f));

        glitch2Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glitch2Paint.setColor(Color.WHITE);
        glitch2Paint.setAlpha((int) (255 * 0.4f));

        floorGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        floorGlowPaint.setColor(coreGlowColor);
        floorGlowPaint.setAlpha((int) (255 * 0.4f));
        floorGlowPaint.setShadowLayer(40f, 0, 0, coreGlowColor);

        streakPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        streakPaint.setStyle(Paint.Style.STROKE);
        streakPaint.setStrokeCap(Paint.Cap.ROUND);
        streakPaint.setShadowLayer(6f, 0, 0, Color.argb((int)(0.6f * 255), 0, 162, 255));
    }

    public void setCoreGlowColor(int color) {
        this.coreGlowColor = color;
        corePaint.setShadowLayer(50f, 0, 0, color);
        floorGlowPaint.setColor(color);
        floorGlowPaint.setShadowLayer(40f, 0, 0, color);
        postInvalidate();
    }

    public void setCoreBounds(float left, float top, float right, float bottom) {
        coreBounds.set(left, top, right, bottom);
        generateStreaks();
        postInvalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (!coreBounds.isEmpty()) {
            generateStreaks();
        }
    }

    public void startAnimation() {
        startTime = System.currentTimeMillis();
        generateStreaks();

        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(10000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            updateValues();
            invalidate();
        });
        animator.start();
    }

    public void stopAnimation() {
        if (animator != null) {
            animator.cancel();
        }
    }

    private void updateValues() {
        long elapsed = System.currentTimeMillis() - startTime;
        float density = getResources().getDisplayMetrics().density;
        
        // 1. Movement (0 -> 2000ms)
        float moveProgress = Math.min(1f, elapsed / 2000f);
        moveProgress = moveProgress * moveProgress; // ease in
        float targetOffsetY = getHeight() * 0.15f - coreBounds.top;
        currentOffsetY = targetOffsetY * moveProgress;

        // 2. Shake (0 -> 2880ms)
        float shakeIntensity = Math.min(1f, Math.max(0f, elapsed / 2880f));
        float currentMaxShake = density * (1f + 7f * shakeIntensity); // 1dp -> 8dp
        shakeOffsetX = (random.nextFloat() * 2f - 1f) * currentMaxShake;
        shakeOffsetY = (random.nextFloat() * 2f - 1f) * currentMaxShake;

        // 3. Glitch Alpha (2000 -> 2880ms)
        if (elapsed >= 2000) {
            glitchAlphaMult = Math.min(1f, (elapsed - 2000) / 880f);
        } else {
            glitchAlphaMult = 0f;
        }

        // 4. Glitch offsets
        if (glitchAlphaMult > 0) {
            float g1Cycle = (elapsed % 300) / 300f;
            glitch1OffsetX = getGlitch1Offset(g1Cycle) * density;
            float g2Cycle = (elapsed % 400) / 400f;
            glitch2OffsetX = getGlitch2Offset(g2Cycle) * density;
        }
    }

    private float getGlitch1Offset(float t) {
        if (t < 0.2f) return lerp(0, -8, t / 0.2f);
        if (t < 0.4f) return lerp(-8, 4, (t - 0.2f) / 0.2f);
        if (t < 0.6f) return lerp(4, -10, (t - 0.4f) / 0.2f);
        if (t < 0.8f) return lerp(-10, 3, (t - 0.6f) / 0.2f);
        return lerp(3, -5, (t - 0.8f) / 0.2f);
    }

    private float getGlitch2Offset(float t) {
        if (t < 0.25f) return lerp(0, 9, t / 0.25f);
        if (t < 0.5f) return lerp(9, -4, (t - 0.25f) / 0.25f);
        if (t < 0.75f) return lerp(-4, 6, (t - 0.5f) / 0.25f);
        return lerp(6, 10, (t - 0.75f) / 0.25f);
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private void generateStreaks() {
        if (getWidth() == 0 || coreBounds.isEmpty()) return;
        streaks.clear();
        int numStreaks = 120;
        int w = getWidth();
        int h = getHeight();
        
        float bottomWidth = w * 0.9f;
        float cardWidth = coreBounds.width();
        
        for (int i = 0; i < numStreaks; i++) {
            float t = (float) i / (numStreaks - 1);
            
            float startX = (w / 2f) - (bottomWidth / 2f) + (t * bottomWidth);
            float midX = coreBounds.centerX() - (cardWidth / 2f) + (t * cardWidth);
            
            float startY = h + 100;
            float midY = coreBounds.centerY() + 90;
            float endY = -100;
            
            float distY = startY - midY;
            
            float cp1X = startX + (midX - startX) * 0.3f;
            float cp1Y = startY - distY * 0.15f;
            
            float cp2X = midX;
            float cp2Y = midY + distY * 0.4f;
            
            Path path = new Path();
            path.moveTo(startX, startY);
            path.cubicTo(cp1X, cp1Y, cp2X, cp2Y, midX, midY);
            path.lineTo(midX, endY);
            
            float length = 60f + random.nextFloat() * 80f;
            float thickness = 1.5f + random.nextFloat() * 1.5f;
            float duration = 0.5f + random.nextFloat() * 0.7f;
            float delay = random.nextFloat() * 2f;
            
            streaks.add(new Streak(path, length, thickness, duration * 1000f, delay * 1000f));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        long elapsed = System.currentTimeMillis() - startTime;

        if (coreBounds.isEmpty()) return;

        canvas.save();
        canvas.translate(0, currentOffsetY);
        for (Streak s : streaks) {
            s.draw(canvas, streakPaint, elapsed);
        }
        canvas.restore();

        // Draw Card & Glitches
        canvas.save();
        canvas.translate(shakeOffsetX, currentOffsetY + shakeOffsetY);

        if (glitchAlphaMult > 0) {
            canvas.save();
            canvas.translate(glitch2OffsetX, 0);
            glitch2Paint.setAlpha((int) (255 * 0.4f * glitchAlphaMult));
            glitch2Paint.setShadowLayer(4f*density, -4f*density, 0, Color.argb((int)(0.8f * 255 * glitchAlphaMult), 0, 255, 255));
            canvas.drawRoundRect(coreBounds, 12f*density, 12f*density, glitch2Paint);
            canvas.restore();

            canvas.save();
            canvas.translate(glitch1OffsetX, 0);
            glitch1Paint.setAlpha((int) (255 * 0.4f * glitchAlphaMult));
            glitch1Paint.setShadowLayer(4f*density, 4f*density, 0, Color.argb((int)(0.8f * 255 * glitchAlphaMult), 255, 0, 85));
            canvas.drawRoundRect(coreBounds, 12f*density, 12f*density, glitch1Paint);
            canvas.restore();
        }

        // Draw Core Card
        float glowRadius = coreBounds.width() * 0.20f;
        Paint exGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
        exGlow.setColor(Color.TRANSPARENT);
        exGlow.setShadowLayer(glowRadius, 0, 0, coreGlowColor);
        canvas.drawRoundRect(coreBounds, 12f*density, 12f*density, exGlow);
        
        Paint exGlow2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        exGlow2.setColor(Color.TRANSPARENT);
        exGlow2.setShadowLayer(glowRadius * 0.45f, 0, 0, coreGlowColor);
        canvas.drawRoundRect(coreBounds, 12f*density, 12f*density, exGlow2);

        canvas.drawRoundRect(coreBounds, 12f*density, 12f*density, corePaint);
        canvas.restore();
    }

    private class Streak {
        Path path;
        PathMeasure pathMeasure;
        float pathLength;
        float length;
        float thickness;
        float durationMs;
        float delayMs;
        
        float[] pos = new float[2];
        float[] tan = new float[2];

        Streak(Path p, float len, float thick, float dur, float del) {
            path = p;
            length = len;
            thickness = thick;
            durationMs = dur;
            delayMs = del;
            pathMeasure = new PathMeasure(path, false);
            pathLength = pathMeasure.getLength();
        }

        void draw(Canvas canvas, Paint basePaint, long elapsedMs) {
            float rawT = elapsedMs - delayMs;
            if (rawT < 0) return;
            
            float tTime = rawT - (float)Math.floor(rawT / durationMs) * durationMs;
            float progress = tTime / durationMs; 
            if (progress <= 0 || progress >= 1) return;

            float distance = progress * pathLength;
            pathMeasure.getPosTan(distance, pos, tan);

            float opacity = 1f;
            if (progress < 0.1f) opacity = progress / 0.1f;
            else if (progress > 0.8f) opacity = 1f - ((progress - 0.8f) / 0.2f);
            
            float globalIntensity = Math.min(1f, Math.max(0.1f, elapsedMs / 2880f));
            opacity *= globalIntensity;

            if (opacity <= 0f) return;

            canvas.save();
            canvas.translate(pos[0], pos[1]);
            float angle = (float) (Math.atan2(tan[1], tan[0]) * 180 / Math.PI);
            canvas.rotate(angle);

            basePaint.setStrokeWidth(thickness);
            basePaint.setAlpha((int) (255 * opacity));
            
            LinearGradient grad = new LinearGradient(
                    -length, 0, 0, 0,
                    new int[]{Color.TRANSPARENT, ContextCompat.getColor(getContext(), R.color.mosco_primary_alpha_60), Color.WHITE},
                    new float[]{0f, 0.4f, 1f},
                    Shader.TileMode.CLAMP
            );
            basePaint.setShader(grad);

            canvas.drawLine(-length, 0, 0, 0, basePaint);
            
            basePaint.setShader(null);
            canvas.restore();
        }
    }
}
