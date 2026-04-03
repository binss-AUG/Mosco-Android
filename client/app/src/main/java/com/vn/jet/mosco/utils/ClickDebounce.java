package com.vn.jet.mosco.utils;

import android.os.SystemClock;
import android.view.View;

/**
 * ClickDebounce - Ngăn chặn click quá nhanh gây spam hoặc thực hiện thao tác nhiều lần.
 * Giải pháp "3 Nhất": Ngắn nhất, An toàn nhất và Dễ scale nhất cho dự án Mosco.
 */
public abstract class ClickDebounce implements View.OnClickListener {
    private static final long MIN_CLICK_INTERVAL = 1000; // 1 giây (có thể điều chỉnh tùy ý)
    private long lastClickTime = 0;

    @Override
    public final void onClick(View v) {
        long currentClickTime = SystemClock.uptimeMillis();
        long elapsedTime = currentClickTime - lastClickTime;
        
        // Chỉ thực hiện click nếu thời gian trôi qua lớn hơn mức tối thiểu cho phép
        if (elapsedTime <= MIN_CLICK_INTERVAL) {
            return;
        }
        
        lastClickTime = currentClickTime;
        onDebouncedClick(v);
    }

    /**
     * Phương thức này sẽ được gọi thay thế cho onClick thông thường.
     * @param v View được click.
     */
    public abstract void onDebouncedClick(View v);
}
