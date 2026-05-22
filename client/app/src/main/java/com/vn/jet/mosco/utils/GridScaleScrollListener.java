package com.vn.jet.mosco.utils;

import android.view.View;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Hiệu ứng Quiet Luxury cho Grid.
 * Các item ở tâm màn hình có kích thước 100%.
 * Các item trượt sát lên viền trên hoặc viền dưới sẽ từ từ thu nhỏ và mờ đi.
 */
public class GridScaleScrollListener extends RecyclerView.OnScrollListener implements View.OnLayoutChangeListener {

    private final float minScale;

    public GridScaleScrollListener() {
        this(0.85f); // Thẻ sẽ bị thu nhỏ còn 85% khi ra rìa
    }
    public GridScaleScrollListener(float minScale) {
        this.minScale = minScale;
    }

    @Override
    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
        super.onScrolled(recyclerView, dx, dy);
        applyScaleEffect(recyclerView);
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom,
                               int oldLeft, int oldTop, int oldRight, int oldBottom) {
        if (v instanceof RecyclerView) {
            final RecyclerView recyclerView = (RecyclerView) v;
            // Sử dụng post để trì hoãn việc tính toán cho đến khi toàn bộ lượt layout của RecyclerView và các item con hoàn tất
            recyclerView.post(() -> {
                if (recyclerView.isAttachedToWindow()) {
                    applyScaleEffect(recyclerView);
                }
            });
        }
    }

    public void applyScaleEffect(RecyclerView recyclerView) {
        int height = recyclerView.getHeight();
        if (height == 0) return;

        int centerY = height / 2;
        float maxDistance = height / 2f;

        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            
            // Tránh tính toán sai lệch khi view chưa được đo đạc và định vị (layout) hoàn chỉnh
            if (child.getHeight() == 0) {
                child.setScaleX(1f);
                child.setScaleY(1f);
                child.setAlpha(1f);
                continue;
            }

            int childCenterY = child.getTop() + child.getHeight() / 2;

            // Tính khoảng cách từ tâm thẻ tới tâm danh sách
            float distance = Math.abs(centerY - childCenterY);
            float ratio = Math.min(1f, distance / maxDistance);

            // [QUIET LUXURY] Áp dụng Threshold (Ngưỡng 70%)
            // Chỉ bắt đầu thu nhỏ/mờ đi khi thẻ nằm ở 30% rìa màn hình (trôi xuống dưới menu)
            float threshold = 0.7f;
            float normalizedRatio = 0f;
            if (ratio > threshold) {
                normalizedRatio = (ratio - threshold) / (1f - threshold);
            }

            // Tỉ lệ thuận: Ở vùng trung tâm (normalizedRatio=0) -> scale = 1. Ra rìa -> scale = minScale
            float scale = 1f - (1f - minScale) * normalizedRatio;
            
            // Fading alpha logic
            float alpha = 1f - (0.6f * normalizedRatio); // Xuống 0.4 khi chạm đáy

            child.setScaleX(scale);
            child.setScaleY(scale);
            child.setAlpha(alpha);
        }
    }
}
