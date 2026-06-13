package com.vn.jet.mosco.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class LavaFlowView extends View implements SensorEventListener {

    private Paint paint;
    private Path path;
    private float offset = 0f;
    private float tiltOffset = 0f; // Shift based on device tilt
    private float targetTiltOffset = 0f;
    private ValueAnimator animator;
    private SensorManager sensorManager;
    private Sensor gravitySensor;

    public LavaFlowView(Context context) {
        super(context);
        init();
    }

    public LavaFlowView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        path = new Path();
        
        sensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        }

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(2000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            offset = (float) animation.getAnimatedValue();
            // Smoothly approach target tilt
            tiltOffset += (targetTiltOffset - tiltOffset) * 0.1f;
            invalidate();
        });
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        LinearGradient gradient = new LinearGradient(0, 0, 0, h,
                new int[]{Color.parseColor("#FFCA28"), Color.parseColor("#FF5252"), Color.parseColor("#C62828")},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(gradient);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) return;

        path.reset();
        
        // Base lava level
        float baseHeight = height * 0.4f;
        float waveHeight = height * 0.15f;
        
        // Tilt effect makes one side higher
        float leftHeight = baseHeight - tiltOffset * 10f;
        float rightHeight = baseHeight + tiltOffset * 10f;

        path.moveTo(0, height);
        path.lineTo(0, leftHeight);

        // Draw flowing wave
        float waveLength = width;
        int segments = 20;
        for (int i = 0; i <= segments; i++) {
            float x = (width * i) / (float) segments;
            float y = baseHeight + (x / width) * (rightHeight - leftHeight) 
                      + (float) Math.sin((x / waveLength + offset) * Math.PI * 2) * waveHeight;
            path.lineTo(x, y);
        }

        path.lineTo(width, height);
        path.close();

        canvas.drawPath(path, paint);
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
            float x = event.values[0]; // -9.81 to 9.81
            targetTiltOffset = x; // positive x means device tilted left
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
