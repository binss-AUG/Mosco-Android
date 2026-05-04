package com.vn.jet.mosco.utils;

import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieProperty;
import com.airbnb.lottie.model.KeyPath;
import com.airbnb.lottie.value.LottieValueCallback;

public class StreakColorHelper {

    /**
     * Áp dụng hiệu ứng Bóng tối (Shadow) cho Lottie Streak.
     * Biến Lottie thành màu xám đen, scale to và làm mờ.
     */
    public static void applyShadowEffect(LottieAnimationView ivIcon) {
        if (ivIcon == null) return;
        
        // Ma trận biến mọi màu thành màu xám đen (Dark Shadow)
        // R' = 0.2R, G' = 0.2G, B' = 0.2B (Giảm độ sáng xuống mức tối)
        ColorMatrix cm = new ColorMatrix(new float[] {
            0.1f, 0, 0, 0, 0,
            0, 0.1f, 0, 0, 0,
            0, 0, 0.1f, 0, 0,
            0, 0, 0, 1, 0
        });
        
        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(cm);
        ivIcon.addValueCallback(new KeyPath("**"), LottieProperty.COLOR_FILTER, new LottieValueCallback<>(filter));
    }

    /**
     * Thay đổi màu sắc của Lottie Streak dựa trên cấp độ (Streak Level).
     * Sử dụng ColorMatrix để Hue Shift nhằm giữ lại toàn bộ độ chuyển màu (gradient)
     * gốc của ngọn lửa mà vẫn thay đổi được màu nền cơ bản.
     */
    public static void applyStreakColor(LottieAnimationView ivIcon, int streakValue) {
        if (ivIcon == null) return;
        ColorMatrixColorFilter filter = getStreakColorFilter(streakValue);
        if (filter == null) {
            ivIcon.addValueCallback(new KeyPath("**"), LottieProperty.COLOR_FILTER, new LottieValueCallback<>(null));
        } else {
            ivIcon.addValueCallback(new KeyPath("**"), LottieProperty.COLOR_FILTER, new LottieValueCallback<>(filter));
        }
    }

    /**
     * Áp dụng màu sắc streak cho một View thông thường (ví dụ: View hào quang).
     */
    public static void applyStreakColor(android.view.View view, int streakValue) {
        if (view == null || view.getBackground() == null) return;
        ColorMatrixColorFilter filter = getStreakColorFilter(streakValue);
        if (filter == null) {
            view.getBackground().clearColorFilter();
        } else {
            view.getBackground().setColorFilter(filter);
        }
    }

    private static ColorMatrixColorFilter getStreakColorFilter(int streakValue) {
        float hueShift = 0f;
        boolean useFilter = true;
        float saturation = 1.0f;
        float brightness = 1.0f;
        float[] customMatrix = null;

        if (streakValue >= 1000) {
            // God Mode Base
            useFilter = true;
            brightness = 1.2f;
            saturation = 1.5f;
        } else if (streakValue >= 365) {
            // ASH & BLOOD: Black/White mix with dark red accents
            customMatrix = new float[] {
                0.3f, 0.3f, 0.3f, 0, 40,  // Red channel gets a boost of grey + red offset
                0.2f, 0.2f, 0.2f, 0, 0,   // Green channel is muted grey
                0.2f, 0.2f, 0.2f, 0, 0,   // Blue channel is muted grey
                0,    0,    0,    1, 0
            };
        } else if (streakValue >= 200) {
            hueShift = 260f; // Nebula Purple
        } else if (streakValue >= 100) {
            hueShift = 320f; // Nebula Pink
        } else if (streakValue >= 30) {
            hueShift = 200f; // Blue
        } else if (streakValue >= 10) {
            saturation = 2.0f; // Intense Orange-Red
            brightness = 0.9f;
        } else {
            useFilter = false;
        }

        if (!useFilter) return null;

        ColorMatrix cm = new ColorMatrix();
        if (customMatrix != null) {
            cm.set(customMatrix);
        } else {
            setHueRotation(cm, hueShift);
            if (saturation != 1.0f) {
                ColorMatrix satMatrix = new ColorMatrix();
                satMatrix.setSaturation(saturation);
                cm.postConcat(satMatrix);
            }
            if (brightness != 1.0f) {
                float[] bMat = {
                    brightness, 0, 0, 0, 0,
                    0, brightness, 0, 0, 0,
                    0, 0, brightness, 0, 0,
                    0, 0, 0, 1, 0
                };
                cm.postConcat(new ColorMatrix(bMat));
            }
        }
        return new ColorMatrixColorFilter(cm);
    }

    /**
     * Tạo hiệu ứng RGB 7 sắc cho mốc 1000+ ngày.
     */
    public static void applyRGBEffect(LottieAnimationView ivIcon, float hue) {
        if (ivIcon == null) return;
        ColorMatrix cm = new ColorMatrix();
        setHueRotation(cm, hue);
        
        // Boost brightness for metallic look
        float b = 1.3f;
        float[] bMat = {
            b, 0, 0, 0, 30,
            0, b, 0, 0, 30,
            0, 0, b, 0, 30,
            0, 0, 0, 1, 0
        };
        cm.postConcat(new ColorMatrix(bMat));
        
        ivIcon.addValueCallback(new KeyPath("**"), LottieProperty.COLOR_FILTER, new LottieValueCallback<>(new ColorMatrixColorFilter(cm)));
    }

    /**
     * Tính toán ma trận xoay màu (Hue Rotation)
     */
    private static void setHueRotation(ColorMatrix cm, float degree) {
        float angle = degree * (float) Math.PI / 180f;
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        
        // Hệ số độ chói tiêu chuẩn
        float lumR = 0.213f;
        float lumG = 0.715f;
        float lumB = 0.072f;
        
        float[] mat = new float[] {
            lumR + cos * (1 - lumR) + sin * (-lumR), lumG + cos * (-lumG) + sin * (-lumG), lumB + cos * (-lumB) + sin * (1 - lumB), 0, 0,
            lumR + cos * (-lumR) + sin * (0.143f), lumG + cos * (1 - lumG) + sin * (0.140f), lumB + cos * (-lumB) + sin * (-0.283f), 0, 0,
            lumR + cos * (-lumR) + sin * (-(1 - lumR)), lumG + cos * (-lumG) + sin * (lumG), lumB + cos * (1 - lumB) + sin * (lumB), 0, 0,
            0f, 0f, 0f, 1f, 0f
        };
        cm.set(mat);
    }
}
