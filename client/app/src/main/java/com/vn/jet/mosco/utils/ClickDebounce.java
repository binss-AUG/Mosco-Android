package com.vn.jet.mosco.utils;

import android.os.SystemClock;
import android.view.View;

/**
 * ClickDebounce - Ngăn chặn click quá nhanh gây spam hoặc thực hiện thao tác nhiều lần.
 * Phiên bản nâng cấp: Hỗ trợ tùy chỉnh interval và tích hợp Listener lambda.
 */
public class ClickDebounce implements View.OnClickListener {
    private final long minClickInterval;
    private long lastClickTime = 0;
    private View.OnClickListener listener;

    public ClickDebounce(long minClickInterval, View.OnClickListener listener) {
        this.minClickInterval = minClickInterval;
        this.listener = listener;
    }

    public ClickDebounce(View.OnClickListener listener) {
        this(1000, listener);
    }

    public ClickDebounce(long minClickInterval) {
        this.minClickInterval = minClickInterval;
    }

    public ClickDebounce() {
        this(1000);
    }

    public void onDebouncedClick(View v) {
        // Có thể override trong anonymous class
    }

    @Override
    public final void onClick(View v) {
        long currentClickTime = SystemClock.uptimeMillis();
        long elapsedTime = currentClickTime - lastClickTime;
        
        if (elapsedTime <= minClickInterval) {
            return;
        }
        
        lastClickTime = currentClickTime;
        if (listener != null) {
            listener.onClick(v);
        } else {
            onDebouncedClick(v);
        }
    }
}
