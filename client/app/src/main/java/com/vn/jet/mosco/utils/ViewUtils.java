package com.vn.jet.mosco.utils;

import android.view.ViewConfiguration;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Utility class for UI/View related optimizations.
 */
public class ViewUtils {

    /**
     * Giới hạn tốc độ lướt (Fling Velocity) của RecyclerView để đảm bảo mượt mà
     * và tránh overload khi load hàng ngàn hình ảnh.
     * [QUIET LUXURY] Tạo cảm giác lướt đằm và cao cấp.
     */
    public static void limitFlingVelocity(@NonNull RecyclerView recyclerView) {
        recyclerView.setOnFlingListener(new RecyclerView.OnFlingListener() {
            @Override
            public boolean onFling(int velocityX, int velocityY) {
                // Lấy tốc độ tối đa mong muốn (Tối ưu cho cảm giác lướt nhanh nhưng không loạn)
                int maxVelocity = recyclerView.getContext().getResources().getInteger(com.vn.jet.mosco.R.integer.max_fling_velocity);

                int newVelocityX = velocityX;
                int newVelocityY = velocityY;

                if (Math.abs(velocityX) > maxVelocity) {
                    newVelocityX = velocityX > 0 ? maxVelocity : -maxVelocity;
                }
                if (Math.abs(velocityY) > maxVelocity) {
                    newVelocityY = velocityY > 0 ? maxVelocity : -maxVelocity;
                }

                // Nếu có sự thay đổi tốc độ, ta thực hiện fling thủ công
                if (newVelocityX != velocityX || newVelocityY != velocityY) {
                    recyclerView.fling(newVelocityX, newVelocityY);
                    return true; // Đã xử lý xong
                }

                return false; // Để hệ thống xử lý mặc định nếu chưa vượt ngưỡng
            }
        });
    }
}
