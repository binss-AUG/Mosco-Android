package com.vn.jet.mosco.utils;

import android.content.Context;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatTextView;

/**
 * AlwaysMarqueeTextView - Một TextView tùy chỉnh luôn trả về true cho isFocused().
 * Tại sao (WHY): Android mặc định chỉ chạy hiệu ứng chạy chữ (marquee/lineshow) khi View đang có focus.
 * Khi một PopupWindow (như menu overflow) mở ra, nó sẽ chiếm focus của cửa sổ chính, làm cho marquee
 * thông báo ở Home bị dừng lại. Bằng cách luôn trả về true cho isFocused, TextView này sẽ đánh lừa hệ thống
 * để duy trì hoạt ảnh chạy chữ liên tục ngay cả khi mất focus của cửa sổ.
 */
public class AlwaysMarqueeTextView extends AppCompatTextView {

    public AlwaysMarqueeTextView(Context context) {
        super(context);
    }

    public AlwaysMarqueeTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AlwaysMarqueeTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean isFocused() {
        return true;
    }
}
