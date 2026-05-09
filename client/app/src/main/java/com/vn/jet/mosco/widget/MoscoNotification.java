package com.vn.jet.mosco.widget;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.vn.jet.mosco.R;

/**
 * Hệ thống thông báo tùy chỉnh (Custom Notification) thay thế cho Toast.
 * Áp dụng phong cách 'Liquid Glass' với hiệu ứng rơi tự do và đàn hồi.
 */
public class MoscoNotification {

    private static final long DISPLAY_DURATION = 3000L; // 3 giây
    private static final long ANIM_DURATION = 500L;

    /**
     * Hiển thị thông báo thành công (Màu xanh neon).
     */
    public static void showSuccess(@NonNull Activity activity, @NonNull String message) {
        show(activity, message, R.drawable.ic_check, R.color.mosco_success);
    }

    /**
     * Hiển thị thông báo lỗi (Màu đỏ).
     */
    public static void showError(@NonNull Activity activity, @NonNull String message) {
        show(activity, message, R.drawable.ic_sad, R.color.mosco_error);
    }

    /**
     * Hiển thị thông báo thông tin (Màu trắng/xanh dương).
     */
    public static void showInfo(@NonNull Activity activity, @NonNull String message) {
        show(activity, message, R.drawable.ic_info, R.color.white);
    }

    private static void show(@NonNull Activity activity, @NonNull String message, 
                             @DrawableRes int iconRes, int tintColorRes) {
        
        ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
        View notiView = LayoutInflater.from(activity).inflate(R.layout.layout_mosco_notification, decorView, false);
        
        TextView tvMessage = notiView.findViewById(R.id.tv_noti_message);
        ImageView ivIcon = notiView.findViewById(R.id.iv_noti_icon);
        View container = notiView.findViewById(R.id.container_liquid);

        tvMessage.setText(message);
        ivIcon.setImageResource(iconRes);
        ivIcon.setColorFilter(ContextCompat.getColor(activity, tintColorRes));

        // Thêm vào DecorView
        decorView.addView(notiView);

        // Hiệu ứng "Rơi tự do + Đàn hồi" (Liquid Drop)
        container.setTranslationY(-500f);
        container.setAlpha(0f);
        
        container.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(ANIM_DURATION)
                .setInterpolator(new OvershootInterpolator(1.5f))
                .start();

        // Tự động biến mất sau DISPLAY_DURATION
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (activity.isFinishing()) return;
            
            container.animate()
                    .translationY(-500f)
                    .alpha(0f)
                    .setDuration(ANIM_DURATION)
                    .withEndAction(() -> decorView.removeView(notiView))
                    .start();
        }, DISPLAY_DURATION);
    }
}
