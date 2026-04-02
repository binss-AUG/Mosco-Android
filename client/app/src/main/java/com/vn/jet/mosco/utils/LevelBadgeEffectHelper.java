package com.vn.jet.mosco.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.vn.jet.mosco.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class LevelBadgeEffectHelper implements SensorEventListener {

    private static LevelBadgeEffectHelper instance;
    private SensorManager sensorManager;
    private Sensor gravitySensor;

    private final List<BadgeData> activeBadges = new ArrayList<>();

    private static class BadgeData {
        WeakReference<ImageView> badgeRef;
        WeakReference<ShimmerOverlayView> shimmerRef;

        BadgeData(ImageView badge, ShimmerOverlayView shimmer) {
            this.badgeRef = new WeakReference<>(badge);
            this.shimmerRef = new WeakReference<>(shimmer);
        }
    }

    private static class ShimmerOverlayView extends View {
        private Paint paint;
        private Matrix matrix;
        private LinearGradient gradient;
        private float fractionX = 0.5f;

        private int[] colors;
        private float[] positions;
        private float cornerRadius = 0f;

        public ShimmerOverlayView(Context context) {
            super(context);
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            matrix = new Matrix();
        }

        public void setCornerRadius(float radius) {
            this.cornerRadius = radius;
        }

        public void setColors(int[] colors, float[] positions) {
            this.colors = colors;
            this.positions = positions;
            post(() -> {
                int w = getWidth();
                int h = getHeight();
                if (w > 0 && h > 0) {
                    // Cố định vĩnh viễn hệ số X và Y theo Width để khóa chết góc nghiêng (Ngăn lỗi dốc đứng cục bộ)
                    float spanX = w * 2.0f;
                    float spanY = w * 1.2f; 
                    gradient = new LinearGradient(-spanX, 0, spanX, spanY, colors, positions, Shader.TileMode.CLAMP);
                    paint.setShader(gradient);
                    invalidate();
                }
            });
        }

        public void updateShimmerOffset(float fractionX) {
            this.fractionX = fractionX;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (colors != null && positions != null && w > 0 && h > 0) {
                float spanX = w * 2.0f;
                float spanY = w * 1.2f; 
                gradient = new LinearGradient(-spanX, 0, spanX, spanY, colors, positions, Shader.TileMode.CLAMP);
                paint.setShader(gradient);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (gradient != null) {
                matrix.reset();
                float displacement = (fractionX - 0.5f) * getWidth() * 2f;
                matrix.setTranslate(displacement, 0);
                gradient.setLocalMatrix(matrix);

                RectF rect = new RectF(0, 0, getWidth(), getHeight());
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);
            }
        }
    }

    private LevelBadgeEffectHelper(Context context) {
        sensorManager = (SensorManager) context.getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
            sensorManager.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    public static LevelBadgeEffectHelper getInstance(Context context) {
        if (instance == null) {
            instance = new LevelBadgeEffectHelper(context);
        }
        return instance;
    }

    public static void apply(ImageView badgeView, int level) {
        if (badgeView == null || level <= 0) return;
        Context context = badgeView.getContext();

        remove(badgeView);

        ViewGroup parent = (ViewGroup) badgeView.getParent();
        if (parent != null) {
            // cWhite: Điểm chói sáng tuyệt đối tại chính giữa (Tâm)
            int cWhite = Color.argb(245, 255, 255, 255); 
            int cCore, cEdge;
            float[] positions;

            // QUY TẮC ĐỐI XỨNG: {0f, p1, p2, 0.5f, p3, p4, 1f}
            // Khoảng cách từ 0.5 tới p2 phải bằng từ 0.5 tới p3 (Lõi)
            // Khoảng cách từ 0.5 tới p1 phải bằng từ 0.5 tới p4 (Viền)

            if (level <= 1) return;

            else if (level <= 4) {
                // +3: Đồng
                cCore = Color.argb(220, 255, 180, 130);
                cEdge = Color.argb(30, 139, 69, 19);
                positions = new float[]{0f, 0.35f, 0.45f, 0.5f, 0.55f, 0.65f, 1f};
            } 
            else if (level <= 7) {
                // +5: Bạc (Dải hẹp và gắt)
                cCore = Color.argb(100, 230, 240, 255);
                cEdge = Color.argb(20, 100, 110, 130);
                positions = new float[]{0f, 0.35f, 0.45f, 0.5f, 0.55f, 0.65f, 1f};
            } 
            else {
                // +9: Vàng (Dải rộng và lộng lẫy)
                cCore = Color.argb(220, 255, 215, 0);
                cEdge = Color.argb(40, 184, 134, 11);
                positions = new float[]{0f, 0.35f, 0.45f, 0.5f, 0.55f, 0.65f, 1f};
            }

            // Đồng bộ màu trong suốt với màu viền để fade out mượt mà, không bị sọc đen
            int t = Color.argb(0, Color.red(cEdge), Color.green(cEdge), Color.blue(cEdge));

            // Cấu trúc mảng màu ĐỐI XỨNG hoàn hảo:
            // [Trong suốt] -> [Viền tối] -> [Lõi màu] -> [Tâm trắng lóa] -> [Lõi màu] -> [Viền tối] -> [Trong suốt]
            int[] colors = new int[]{t, cEdge, cCore, cWhite, cCore, cEdge, t};

            // CONTAINER MASKING: Tạo môi trường đục lỗ bằng Hardware Layer
            android.widget.FrameLayout maskContainer = new android.widget.FrameLayout(context);
            maskContainer.setId(View.generateViewId());
            maskContainer.setElevation(badgeView.getElevation() + 1f);
            maskContainer.setLayerType(View.LAYER_TYPE_HARDWARE, null); // Kích hoạt bộ đệm chém mask

            // 1. LỚP SHIMMER (Nằm dưới trong Mask)
            ShimmerOverlayView shimmerOverlay = new ShimmerOverlayView(context);
            
            // BẮT BO GÓC (Corner Radius)
            shimmerOverlay.setClipToOutline(true);
            shimmerOverlay.setOutlineProvider(badgeView.getOutlineProvider());
            
            float fallbackRadius = context.getResources().getDisplayMetrics().density * 6f; // Mặc định 6dp
            shimmerOverlay.setCornerRadius(fallbackRadius);
            
            shimmerOverlay.setColors(colors, positions);
            maskContainer.addView(shimmerOverlay, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            // 2. LỚP ĐỤC LỖ (Nằm trên, dùng DST_OUT để chọc lỗ Shimmer đúng chỗ số)
            ImageView gradeBorderView = new ImageView(context);
            gradeBorderView.setScaleType(badgeView.getScaleType()); // Đồng bộ khung hình scale
            
            Paint dstOutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            dstOutPaint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT));
            gradeBorderView.setLayerType(View.LAYER_TYPE_HARDWARE, dstOutPaint);
            
            // Load file ảnh mặt nạ gradeborder từ assets
            String borderPath = "file:///android_asset/gradeborder/" + Math.min(level, 10) + ".png";
            com.bumptech.glide.Glide.with(context).load(borderPath).into(gradeBorderView);
            
            maskContainer.addView(gradeBorderView, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            parent.addView(maskContainer, parent.indexOfChild(badgeView) + 1);
            badgeView.setTag(R.id.view_progress_fill, maskContainer);

            ViewGroup.LayoutParams rawParams = badgeView.getLayoutParams();
            if (rawParams instanceof ConstraintLayout.LayoutParams) {
                ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(0, 0);
                params.topToTop = badgeView.getId();
                params.bottomToBottom = badgeView.getId();
                params.startToStart = badgeView.getId();
                params.endToEnd = badgeView.getId();
                maskContainer.setLayoutParams(params);
            }

            getInstance(context).registerBadge(badgeView, shimmerOverlay);
        }
    }

    public static void remove(ImageView badgeView) {
        if (badgeView == null) return;
        badgeView.setRotationY(0f);
        
        View shimmerOverlay = (View) badgeView.getTag(R.id.view_progress_fill);
        if (shimmerOverlay != null && shimmerOverlay.getParent() != null) {
            ((ViewGroup) shimmerOverlay.getParent()).removeView(shimmerOverlay);
            badgeView.setTag(R.id.view_progress_fill, null);
        }
        
        if (instance != null) {
            instance.unregisterBadge(badgeView);
        }
    }

    private void registerBadge(ImageView badge, ShimmerOverlayView shimmer) {
        cleanDeadReferences();
        activeBadges.add(new BadgeData(badge, shimmer));
    }

    private void unregisterBadge(ImageView badge) {
        Iterator<BadgeData> iterator = activeBadges.iterator();
        while (iterator.hasNext()) {
            BadgeData data = iterator.next();
            if (data.badgeRef.get() == null || data.badgeRef.get() == badge) {
                iterator.remove();
            }
        }
    }

    private void cleanDeadReferences() {
        Iterator<BadgeData> iterator = activeBadges.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().badgeRef.get() == null) iterator.remove();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {
            float gX = event.values[0]; 
            float maxTilt = 4.0f;
            float normalized = Math.max(-maxTilt, Math.min(maxTilt, gX));
            float percentX = (normalized + maxTilt) / (maxTilt * 2);
            float shimmerFraction = 1.0f - percentX;

            for (int i = 0; i < activeBadges.size(); i++) {
                BadgeData data = activeBadges.get(i);
                ImageView badge = data.badgeRef.get();
                ShimmerOverlayView shimmer = data.shimmerRef.get();
                
                if (badge != null && shimmer != null) {
                    shimmer.updateShimmerOffset(shimmerFraction);
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}