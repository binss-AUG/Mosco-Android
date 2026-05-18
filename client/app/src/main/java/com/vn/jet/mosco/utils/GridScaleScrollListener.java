package com.vn.jet.mosco.utils;

import android.view.View;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * GridScaleScrollListener - Luxury grid scroll listener that scales items based on vertical proximity to center.
 */
public class GridScaleScrollListener extends RecyclerView.OnScrollListener {
    private final float minScale;

    public GridScaleScrollListener(float minScale) {
        this.minScale = minScale;
    }

    @Override
    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
        super.onScrolled(recyclerView, dx, dy);
        
        int childCount = recyclerView.getChildCount();
        int height = recyclerView.getHeight();
        if (height <= 0) return;
        
        int midY = height / 2;

        for (int i = 0; i < childCount; i++) {
            View child = recyclerView.getChildAt(i);
            if (child == null) continue;
            
            int childMidY = (child.getTop() + child.getBottom()) / 2;
            float distance = Math.abs(midY - childMidY);
            
            // Tỷ lệ khoảng cách so với nửa chiều cao RecyclerView
            float ratio = distance / (height / 2f);
            if (ratio > 1f) ratio = 1f;
            
            // Tính toán tỷ lệ scale mượt mà
            float scale = 1f - ratio * (1f - minScale);
            scale = Math.max(minScale, Math.min(1f, scale));
            
            child.setScaleX(scale);
            child.setScaleY(scale);
        }
    }
}
