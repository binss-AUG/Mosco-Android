package com.vn.jet.mosco.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
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
 * UpgradeSceneView for Mosco - Premium Cinematic Edition
 * Optimized for 60 FPS on Android 9 Emulators.
 */
public class UpgradeSceneView extends View {

    private Paint corePaint;
    private Paint glitch1Paint;
    private Paint glitch2Paint;
    private Paint streakPaint;
    private Paint glowPaint;
    private Paint floorGlowPaint;

    private RectF coreBounds = new RectF();
    private float currentOffsetY = 0f;
    private float shakeOffsetX = 0f;
    private float shakeOffsetY = 0f;
    private float glitch1OffsetX = 0f;
    private float glitch2OffsetX = 0f;
    private float glitchAlphaMult = 0f;

    private long startTime;
    private ValueAnimator animator;
    private List<Streak> streaks = new ArrayList<>();
    private Random random = new Random();

    private int coreGlowColor = 0;
    private LinearGradient streakGradientPrototype;
    private Matrix gradientMatrix = new Matrix();

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
        
        // Sử dụng SOFTWARE để hỗ trợ ShadowLayer và Gradient mượt mà nhất trên emulator
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        corePaint.setColor(Color.WHITE);
        corePaint.setShadowLayer(50f, 0, 0, coreGlowColor);

        glitch1Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glitch1Paint.setColor(Color.WHITE);

        glitch2Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glitch2Paint.setColor(Color.WHITE);

        streakPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        streakPaint.setStyle(Paint.Style.STROKE);
        streakPaint.setStrokeCap(Paint.Cap.ROUND);
        streakPaint.setColor(Color.WHITE);
        
        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setColor(coreGlowColor);

        floorGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        floorGlowPaint.setStyle(Paint.Style.FILL);

        // Gradient cho tia sáng
        streakGradientPrototype = new LinearGradient(
                -250, 0, 0, 0,
                new int[]{Color.TRANSPARENT, Color.argb(220, 0, 200, 255), Color.WHITE},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
        );
    }

    public void setCoreBounds(float left, float top, float right, float bottom) {
        coreBounds.set(left, top, right, bottom);
        if (getWidth() > 0) generateStreaks();
        postInvalidate();
    }

    public void startAnimation() {
        startTime = System.currentTimeMillis();
        if (getWidth() > 0) generateStreaks();

        if (animator != null) animator.cancel();
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
        if (animator != null) animator.cancel();
    }

    private void updateValues() {
        long elapsed = System.currentTimeMillis() - startTime;
        float density = getResources().getDisplayMetrics().density;
        
        float moveProgress = Math.min(1f, elapsed / 2000f);
        float targetOffsetY = getHeight() * 0.15f - coreBounds.top;
        currentOffsetY = targetOffsetY * (moveProgress * moveProgress);

        float shakeIntensity = Math.min(1f, Math.max(0f, elapsed / 2880f));
        float currentMaxShake = density * (1f + 8f * shakeIntensity);
        shakeOffsetX = (random.nextFloat() * 2f - 1f) * currentMaxShake;
        shakeOffsetY = (random.nextFloat() * 2f - 1f) * currentMaxShake;

        glitchAlphaMult = elapsed >= 2000 ? Math.min(1f, (elapsed - 2000) / 880f) : 0f;
        if (glitchAlphaMult > 0) {
            glitch1OffsetX = getGlitchOffset(elapsed, 300, -10, 5) * density;
            glitch2OffsetX = getGlitchOffset(elapsed, 450, 12, -6) * density;
        }
    }

    private float getGlitchOffset(long elapsed, int period, float min, float max) {
        float t = (elapsed % period) / (float) period;
        return min + (max - min) * t;
    }

    private void generateStreaks() {
        if (getWidth() == 0 || coreBounds.isEmpty()) return;
        streaks.clear();
        int numStreaks = 120; 
        int w = getWidth();
        int h = getHeight();
        
        for (int i = 0; i < numStreaks; i++) {
            float t = (float) i / (numStreaks - 1);
            float startX = (w / 2f) - (w * 0.5f) + (t * w);
            float midX = coreBounds.centerX() - (coreBounds.width() / 1.8f) + (t * coreBounds.width() * 1.1f);
            float startY = h + 150;
            float midY = coreBounds.centerY() + 100;
            float endY = -200;
            
            Path path = new Path();
            path.moveTo(startX, startY);
            path.cubicTo(startX + (midX - startX) * 0.4f, startY - (startY - midY) * 0.2f, midX, midY + (startY - midY) * 0.5f, midX, midY);
            path.lineTo(midX, endY);
            
            streaks.add(new Streak(path, 
                    100f + random.nextFloat() * 100f, 
                    2.0f + random.nextFloat() * 2.5f, 
                    (0.35f + random.nextFloat() * 0.55f) * 1000f, 
                    random.nextFloat() * 2500f));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (coreBounds.isEmpty()) return;
        
        if (streaks.isEmpty() && getWidth() > 0) {
            generateStreaks();
        }
        if (streaks.isEmpty()) return;

        long elapsed = System.currentTimeMillis() - startTime;
        float density = getResources().getDisplayMetrics().density;

        // Draw Floor Glow
        drawFloorGlow(canvas, elapsed);

        // Draw Streaks
        canvas.save();
        canvas.translate(0, currentOffsetY);
        for (Streak s : streaks) {
            s.draw(canvas, streakPaint, glowPaint, elapsed);
        }
        canvas.restore();

        // Draw Card & Glitches
        canvas.save();
        canvas.translate(shakeOffsetX, currentOffsetY + shakeOffsetY);

        if (glitchAlphaMult > 0) {
            drawGlitch(canvas, glitch2OffsetX, Color.CYAN, glitch2Paint);
            drawGlitch(canvas, glitch1OffsetX, Color.MAGENTA, glitch1Paint);
        }

        // Core Card Glow
        canvas.drawRoundRect(coreBounds, 12f*density, 12f*density, corePaint);
        canvas.restore();
    }

    private void drawFloorGlow(Canvas canvas, long elapsed) {
        int w = getWidth();
        int h = getHeight();
        float intensity = 0.3f + 0.2f * (float) Math.sin(elapsed / 150f);
        intensity *= Math.min(1f, elapsed / 800f);
        
        LinearGradient floorGrad = new LinearGradient(0, h, 0, h * 0.7f, 
                new int[]{Color.argb((int)(255 * intensity), Color.red(coreGlowColor), Color.green(coreGlowColor), Color.blue(coreGlowColor)), Color.TRANSPARENT}, 
                null, Shader.TileMode.CLAMP);
        floorGlowPaint.setShader(floorGrad);
        canvas.drawRect(0, h * 0.7f, w, h, floorGlowPaint);
    }

    private void drawGlitch(Canvas canvas, float offset, int color, Paint paint) {
        canvas.save();
        canvas.translate(offset, 0);
        paint.setAlpha((int) (255 * 0.4f * glitchAlphaMult));
        canvas.drawRoundRect(coreBounds, 12f*getResources().getDisplayMetrics().density, 12f*getResources().getDisplayMetrics().density, paint);
        canvas.restore();
    }

    private class Streak {
        private final float[] pointsX = new float[60];
        private final float[] pointsY = new float[60];
        private final float[] angles = new float[60];
        private final float length;
        private final float thickness;
        private final float durationMs;
        private final float delayMs;
        private final float totalLength;

        Streak(Path path, float len, float thick, float dur, float del) {
            this.length = len;
            this.thickness = thick;
            this.durationMs = dur;
            this.delayMs = del;
            
            PathMeasure pm = new PathMeasure(path, false);
            this.totalLength = pm.getLength();
            
            float[] pos = new float[2];
            float[] tan = new float[2];
            for (int i = 0; i < 60; i++) {
                pm.getPosTan(totalLength * (i / 59f), pos, tan);
                pointsX[i] = pos[0];
                pointsY[i] = pos[1];
                angles[i] = (float) (Math.atan2(tan[1], tan[0]) * 180 / Math.PI);
            }
        }

        void draw(Canvas canvas, Paint basePaint, Paint glowPaint, long elapsedMs) {
            float rawT = elapsedMs - delayMs;
            if (rawT < 0) return;
            
            float progress = (rawT % durationMs) / durationMs;
            int idx = (int) (progress * 59);
            if (idx < 0 || idx >= 60) return;

            float opacity = 1.0f;
            if (progress < 0.15f) opacity = progress / 0.15f;
            else if (progress > 0.75f) opacity = 1f - ((progress - 0.75f) / 0.25f);
            
            float intensity = 0.5f + 0.5f * Math.min(1f, elapsedMs / 1000f);
            opacity *= intensity;

            if (opacity <= 0.02f) return;

            canvas.save();
            canvas.translate(pointsX[idx], pointsY[idx]);
            canvas.rotate(angles[idx]);

            // 1. Glow stroke
            glowPaint.setStrokeWidth(thickness * 3.5f);
            glowPaint.setAlpha((int) (255 * opacity * 0.45f));
            canvas.drawLine(-length, 0, 0, 0, glowPaint);

            // 2. Core white stroke
            basePaint.setStrokeWidth(thickness);
            basePaint.setAlpha((int) (255 * opacity));
            
            gradientMatrix.setTranslate(0, 0); 
            streakGradientPrototype.setLocalMatrix(gradientMatrix);
            basePaint.setShader(streakGradientPrototype);
            
            canvas.drawLine(-length, 0, 0, 0, basePaint);
            basePaint.setShader(null);
            
            canvas.restore();
        }
    }
}
