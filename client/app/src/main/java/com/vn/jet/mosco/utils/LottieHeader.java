package com.vn.jet.mosco.utils;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import com.airbnb.lottie.LottieAnimationView;
import com.scwang.smart.refresh.layout.api.RefreshHeader;
import com.scwang.smart.refresh.layout.api.RefreshKernel;
import com.scwang.smart.refresh.layout.api.RefreshLayout;
import com.scwang.smart.refresh.layout.constant.RefreshState;
import com.scwang.smart.refresh.layout.constant.SpinnerStyle;
import com.vn.jet.mosco.R;

public class LottieHeader extends LinearLayout implements RefreshHeader {

    private LottieAnimationView lottieView;

    public LottieHeader(Context context) {
        this(context, null);
    }

    public LottieHeader(Context context, AttributeSet attrs) {
        super(context, attrs, 0);
        setGravity(Gravity.CENTER);
        setMinimumHeight((int) (120 * context.getResources().getDisplayMetrics().density));
        
        lottieView = new LottieAnimationView(context);
        lottieView.setAnimation(R.raw.loading);
        lottieView.setRepeatCount(com.airbnb.lottie.LottieDrawable.INFINITE);
        
        LayoutParams params = new LayoutParams(
            (int) (80 * context.getResources().getDisplayMetrics().density),
            (int) (80 * context.getResources().getDisplayMetrics().density)
        );
        addView(lottieView, params);
    }

    @NonNull
    @Override
    public View getView() {
        return this;
    }

    @NonNull
    @Override
    public SpinnerStyle getSpinnerStyle() {
        // Translate giúp Lottie bị kéo trượt từ trên xuống như các app xịn
        return SpinnerStyle.Translate;
    }

    @Override
    public void setPrimaryColors(int... colors) {}

    @Override
    public void onInitialized(@NonNull RefreshKernel kernel, int height, int maxDragHeight) {}

    @Override
    public void onMoving(boolean isDragging, float percent, int offset, int height, int maxDragHeight) {
        // Tùy chọn: Có thể set frame lottie theo percent để khi kéo nó múa chậm chậm theo tay, 
        // nhưng để đơn giản ta cứ để nó autoplay cho mượt.
    }

    @Override
    public void onReleased(@NonNull RefreshLayout refreshLayout, int height, int maxDragHeight) {}

    @Override
    public void onStartAnimator(@NonNull RefreshLayout refreshLayout, int height, int maxDragHeight) {
        if (lottieView != null && !lottieView.isAnimating()) {
            lottieView.playAnimation();
        }
    }

    @Override
    public int onFinish(@NonNull RefreshLayout refreshLayout, boolean success) {
        if (lottieView != null) {
            lottieView.cancelAnimation();
        }
        return 0; // return delay in ms
    }

    @Override
    public void onHorizontalDrag(float percentX, int offsetX, int offsetMax) {}

    @Override
    public boolean isSupportHorizontalDrag() {
        return false;
    }

    @Override
    public void onStateChanged(@NonNull RefreshLayout refreshLayout, @NonNull RefreshState oldState, @NonNull RefreshState newState) {
        if (newState == RefreshState.PullDownToRefresh) {
            if (lottieView != null && !lottieView.isAnimating()) {
                lottieView.playAnimation(); // Bắt đầu múa nhẹ khi vừa kéo
            }
        } else if (newState == RefreshState.None) {
            if (lottieView != null) {
                lottieView.cancelAnimation();
            }
        }
    }

    @Override
    public boolean autoOpen(int duration, float dragRate, boolean animationOnly) {
        return false;
    }
}
