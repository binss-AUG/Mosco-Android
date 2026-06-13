package com.vn.jet.mosco.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SnowParticleView extends View implements SensorEventListener {

    private Paint paint;
    private List<Particle> particles = new ArrayList<>();
    private Random random = new Random();
    private ValueAnimator animator;
    
    private SensorManager sensorManager;
    private Sensor gravitySensor;
    private float windX = 0f; // Derived from gravity sensor

    private class Particle {
        float x, y, speedY, size, offsetSpeed;
        Particle(int width, int height) {
            reset(width, height, true);
        }
        void reset(int width, int height, boolean startRandomY) {
            x = random.nextFloat() * width;
            y = startRandomY ? random.nextFloat() * height : -20f;
            speedY = height * 0.005f + random.nextFloat() * height * 0.005f;
            size = width * 0.02f + random.nextFloat() * width * 0.03f;
            offsetSpeed = (random.nextFloat() - 0.5f) * 2f;
        }
        void update(int width, int height) {
            y += speedY;
            x += windX * 3f + offsetSpeed;
            if (y > height || x < -20 || x > width + 20) {
                reset(width, height, false);
            }
        }
    }

    public SnowParticleView(Context context) {
        super(context);
        init();
    }

    public SnowParticleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        
        sensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        }

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            int w = getWidth();
            int h = getHeight();
            if (w > 0 && h > 0) {
                for (Particle p : particles) {
                    p.update(w, h);
                }
                invalidate();
            }
        });
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (particles.isEmpty() && w > 0 && h > 0) {
            int numParticles = 30;
            for (int i = 0; i < numParticles; i++) {
                particles.add(new Particle(w, h));
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (Particle p : particles) {
            paint.setAlpha((int) (255 * (1f - p.y / getHeight())));
            canvas.drawCircle(p.x, p.y, p.size, paint);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animator.start();
        if (sensorManager != null && gravitySensor != null) {
            sensorManager.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        animator.cancel();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {
            float x = event.values[0]; 
            // Smoothly update wind
            windX += (-x - windX) * 0.1f;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
