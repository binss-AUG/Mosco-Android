package com.vn.jet.mosco.utils;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import com.airbnb.lottie.LottieAnimationView;
import com.vn.jet.mosco.R;

/**
 * CollectionRewardBinder — Xử lý hiệu ứng nhận thưởng mốc cực kỳ đẹp mắt.
 * Tận dụng Lottie và hiệu ứng Scale để tăng tính trải nghiệm.
 */
public class CollectionRewardBinder {

    public interface RewardClaimListener {
        void onClaimed();
    }

    /**
     * Hiển thị hộp thoại nhận quà mốc.
     */
    public static void showReward(Context context, String rewardText, RewardClaimListener listener) {
        if (context == null) return;

        Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.dialog_collection_reward);
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        // 1. Ánh xạ View
        LottieAnimationView lottie = dialog.findViewById(R.id.lottie_reward_explosion);
        View rewardContainer = dialog.findViewById(R.id.cv_reward_container);
        TextView tvDesc = dialog.findViewById(R.id.tv_reward_desc);
        View btnClaim = dialog.findViewById(R.id.btn_claim_reward);

        // 2. Thiết lập nội dung
        if (tvDesc != null) tvDesc.setText(rewardText);

        // 3. Hiệu ứng xuất hiện
        rewardContainer.setVisibility(View.INVISIBLE);
        btnClaim.setVisibility(View.INVISIBLE);

        // Chạy Lottie nổ trước
        if (lottie != null) {
            lottie.setAnimation("customUpgrade.json");
            lottie.playAnimation();
        }

        // Sau 400ms mới hiện phần thưởng với hiệu ứng "Bật lên" (Overshoot)
        rewardContainer.postDelayed(() -> {
            rewardContainer.setVisibility(View.VISIBLE);
            
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(rewardContainer, "scaleX", 0f, 1f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(rewardContainer, "scaleY", 0f, 1f);
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(scaleX, scaleY);
            animatorSet.setDuration(600);
            animatorSet.setInterpolator(new OvershootInterpolator());
            animatorSet.start();

            // Hiện nút nhận quà sau cùng
            btnClaim.postDelayed(() -> {
                btnClaim.setVisibility(View.VISIBLE);
                ObjectAnimator.ofFloat(btnClaim, "alpha", 0f, 1f).setDuration(400).start();
            }, 300);

        }, 400);

        // 4. Xử lý sự kiện nhận quà
        btnClaim.setOnClickListener(v -> {
            if (listener != null) listener.onClaimed();
            dialog.dismiss();
        });

        dialog.setCancelable(false);
        dialog.show();
    }
}
