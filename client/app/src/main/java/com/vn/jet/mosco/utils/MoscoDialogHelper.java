package com.vn.jet.mosco.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.vn.jet.mosco.R;
import com.vn.jet.mosco.fragment.InventoryBottomSheet;
import com.vn.jet.mosco.widget.MoscoButton;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GestureDetectorCompat;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.AnimatorListenerAdapter;
import android.animation.Animator;
import android.view.animation.AccelerateDecelerateInterpolator;
import com.vn.jet.mosco.utils.GlideBindingAdapter;
import com.vn.jet.mosco.utils.CardEffectHelper;
import android.widget.ImageView;
import android.widget.FrameLayout;

/**
 * MoscoDialogHelper - Chuẩn hóa toàn bộ Dialog trong hệ thống theo phong cách "Quiet Luxury".
 * Sử dụng layout_mosco_dialog_base.xml làm template gốc.
 */
public class MoscoDialogHelper {

    public interface DialogCallback {
        void onPositive();
        default void onNegative() {}
    }

    public interface DialogChoiceCallback {
        void onChoice(int index);
    }

    public enum CoupleStatus {
        INVITE,
        WAITING,
        RECEIVED_REQUEST,
        SETUP,
        ACTIVE
    }

    public static class CoupleData {
        public Long streakId;
        public String partnerName;
        public String cardAUrl;
        public String cardBUrl;
        public String cardABackUrl;
        public String cardBBackUrl;
        public String cardAName;
        public String cardBName;
        public int cardAGrade;
        public int cardBGrade;
        public int streakCount;
        
        public CoupleData(String partnerName, String cardAUrl, String cardBUrl, int streakCount) {
            this.partnerName = partnerName;
            this.cardAUrl = cardAUrl;
            this.cardBUrl = cardBUrl;
            this.streakCount = streakCount;
        }
    }

    /**
     * Hiển thị Dialog xác nhận cơ bản (Xác nhận/Hủy)
     */
    public static void showConfirmDialog(Activity activity, 
                                       String title, 
                                       String message, 
                                       String positiveText, 
                                       String negativeText, 
                                       DialogCallback callback) {
        
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        View dialogView = LayoutInflater.from(activity).inflate(R.layout.layout_mosco_dialog_base, null);
        
        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        TextView tvMessage = dialogView.findViewById(R.id.tv_dialog_message);
        MoscoButton btnPositive = dialogView.findViewById(R.id.btn_positive);
        MoscoButton btnNegative = dialogView.findViewById(R.id.btn_negative);

        tvTitle.setText(title);
        tvMessage.setText(message);
        btnPositive.setText(positiveText);
        btnNegative.setText(negativeText);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            // Thêm hiệu ứng fade/scale nếu cần (Galactic style)
            dialog.getWindow().setWindowAnimations(android.R.style.Animation_Dialog);
        }

        btnPositive.setOnClickListener(v -> {
            dialog.dismiss();
            if (callback != null) callback.onPositive();
        });

        btnNegative.setOnClickListener(v -> {
            dialog.dismiss();
            if (callback != null) callback.onNegative();
        });

        dialog.show();
    }

    /**
     * Dialog Logout chuyên dụng
     */
    public static void showLogoutDialog(Activity activity, DialogCallback callback) {
        showConfirmDialog(activity,
                activity.getString(R.string.dialog_logout_title),
                activity.getString(R.string.dialog_logout_msg),
                activity.getString(R.string.dialog_action_confirm),
                activity.getString(R.string.dialog_action_cancel),
                callback);
    }

    /**
     * Dialog Exit App chuyên dụng
     */
    public static void showExitDialog(Activity activity, DialogCallback callback) {
        showConfirmDialog(activity,
                activity.getString(R.string.dialog_exit_title),
                activity.getString(R.string.dialog_exit_msg),
                activity.getString(R.string.dialog_btn_exit),
                activity.getString(R.string.dialog_btn_stay),
                callback);
    }

    public static void showInfoDialog(Activity activity, String title, String message, String positiveText, DialogCallback callback) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        View dialogView = LayoutInflater.from(activity).inflate(R.layout.layout_mosco_dialog_base, null);
        
        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        TextView tvMessage = dialogView.findViewById(R.id.tv_dialog_message);
        MoscoButton btnPositive = dialogView.findViewById(R.id.btn_positive);
        MoscoButton btnNegative = dialogView.findViewById(R.id.btn_negative);

        tvTitle.setText(title);
        tvMessage.setText(message);
        btnPositive.setText(positiveText);
        btnNegative.setVisibility(View.GONE);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setWindowAnimations(android.R.style.Animation_Dialog);
        }

        btnPositive.setOnClickListener(v -> {
            dialog.dismiss();
            if (callback != null) callback.onPositive();
        });

        dialog.show();
    }

    public static void showSingleChoiceDialog(Activity activity, String title, String[] items, DialogChoiceCallback callback) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        View dialogView = LayoutInflater.from(activity).inflate(R.layout.layout_mosco_dialog_base, null);
        
        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        android.widget.FrameLayout flContent = dialogView.findViewById(R.id.fl_dialog_content);
        MoscoButton btnPositive = dialogView.findViewById(R.id.btn_positive);
        MoscoButton btnNegative = dialogView.findViewById(R.id.btn_negative);

        tvTitle.setText(title);
        flContent.removeAllViews();

        android.widget.RadioGroup radioGroup = new android.widget.RadioGroup(activity);
        radioGroup.setOrientation(android.widget.RadioGroup.VERTICAL);

        int primaryColor = androidx.core.content.ContextCompat.getColor(activity, R.color.mosco_primary);
        android.content.res.ColorStateList colorStateList = new android.content.res.ColorStateList(
                new int[][]{
                        new int[]{-android.R.attr.state_checked},
                        new int[]{android.R.attr.state_checked}
                },
                new int[]{
                        androidx.core.content.ContextCompat.getColor(activity, R.color.mosco_white_40),
                        primaryColor
                }
        );

        for (int i = 0; i < items.length; i++) {
            androidx.appcompat.widget.AppCompatRadioButton rb = new androidx.appcompat.widget.AppCompatRadioButton(activity);
            rb.setText(items[i]);
            rb.setId(i);
            rb.setTextColor(androidx.core.content.ContextCompat.getColor(activity, R.color.white));
            rb.setPadding(32, 32, 32, 32);
            rb.setButtonTintList(colorStateList);
            rb.setTextSize(16);
            radioGroup.addView(rb);
        }
        flContent.addView(radioGroup);

        btnPositive.setText("Select");
        btnNegative.setText("Cancel");

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setWindowAnimations(android.R.style.Animation_Dialog);
        }

        btnPositive.setOnClickListener(v -> {
            int selectedId = radioGroup.getCheckedRadioButtonId();
            if (selectedId != -1) {
                dialog.dismiss();
                if (callback != null) callback.onChoice(selectedId);
            } else {
                android.widget.Toast.makeText(activity, "Please select an option", android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        btnNegative.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    /**
     * Hiển thị Dialog Couple's Streak đa trạng thái
     */
    public static void showCoupleStreakDialog(Activity activity, 
                                            CoupleStatus status, 
                                            CoupleData data, 
                                            DialogCallback callback) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (data == null) return;

        View dialogView = LayoutInflater.from(activity).inflate(R.layout.layout_streak_luxury_v3, null);
        
        android.widget.ImageView btnClose = dialogView.findViewById(R.id.btn_close);
        android.widget.ImageView ivIllustration = dialogView.findViewById(R.id.iv_illustration);
        View layoutActive = dialogView.findViewById(R.id.layout_active_streak);
        TextView tvDescription = dialogView.findViewById(R.id.tv_description);
        MoscoButton btnLeft = dialogView.findViewById(R.id.btn_action_left);
        MoscoButton btnRight = dialogView.findViewById(R.id.btn_action_right);
        
        // Active status views
        TextView tvNameA = dialogView.findViewById(R.id.tv_name_a);
        TextView tvNameB = dialogView.findViewById(R.id.tv_name_b);
        TextView tvStreak = dialogView.findViewById(R.id.tv_streak_count);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setWindowAnimations(android.R.style.Animation_Dialog);
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());

        // Logic hiển thị theo từng State
        switch (status) {
            case INVITE:
                ivIllustration.setVisibility(View.VISIBLE);
                layoutActive.setVisibility(View.GONE);
                if (tvDescription != null) {
                    String pName = (data.partnerName != null) ? data.partnerName : "Galactic Partner";
                    String stylizedText = activity.getString(R.string.couple_streak_invite_stylized, pName);
                    tvDescription.setText(android.text.Html.fromHtml(stylizedText));
                }
                btnLeft.setVisibility(View.GONE);
                btnRight.setText(activity.getString(R.string.couple_streak_action_request));
                break;

            case WAITING:
                ivIllustration.setVisibility(View.VISIBLE);
                layoutActive.setVisibility(View.GONE);
                tvDescription.setText(activity.getString(R.string.couple_streak_waiting_msg, data.partnerName));
                btnLeft.setVisibility(View.GONE);
                btnRight.setText(activity.getString(R.string.couple_streak_action_wait, data.partnerName));
                btnRight.setEnabled(false);
                break;

            case RECEIVED_REQUEST:
                ivIllustration.setVisibility(View.VISIBLE);
                layoutActive.setVisibility(View.GONE);
                tvDescription.setText(activity.getString(R.string.couple_streak_received_msg, data.partnerName));
                btnLeft.setVisibility(View.VISIBLE);
                btnLeft.setText(activity.getString(R.string.couple_streak_action_decline));
                btnRight.setText(activity.getString(R.string.couple_streak_action_accept));
                break;

            case SETUP:
            case ACTIVE:
                ivIllustration.setVisibility(View.GONE);
                layoutActive.setVisibility(View.VISIBLE);
                tvNameA.setText(activity.getString(R.string.couple_streak_you)); 
                tvNameA.setSelected(true); // Kích hoạt Marquee (lineshow)
                tvNameB.setText(data.partnerName);
                tvNameB.setSelected(true); // Kích hoạt Marquee
                tvStreak.setText(String.valueOf(data.streakCount));

                com.airbnb.lottie.LottieAnimationView ivFire = dialogView.findViewById(R.id.iv_fire_streak);
                if (ivFire != null) {
                    com.vn.jet.mosco.utils.StreakColorHelper.setupStreakLottie(ivFire, data.streakCount, status == CoupleStatus.ACTIVE);
                }
                
                // Khởi tạo tương tác cho thẻ A (Chính chủ)
                setupCardInteraction(activity, dialogView, data);
                
                // Load thẻ B (Đối phương) - Chỉ hiển thị, không tương tác đổi thẻ
                ImageView ivCardBImg = dialogView.findViewById(R.id.card_iv_image_b);
                ImageView ivPlaceholderB = dialogView.findViewById(R.id.iv_card_b_placeholder);
                TextView tvCardNameB = dialogView.findViewById(R.id.tv_card_name_b);
                com.google.android.material.card.MaterialCardView cardB = dialogView.findViewById(R.id.card_b);
                
                if (data.cardBUrl != null && !data.cardBUrl.isEmpty()) {
                    if (ivPlaceholderB != null) ivPlaceholderB.setVisibility(View.GONE);
                    if (ivCardBImg != null) {
                        ivCardBImg.setVisibility(View.VISIBLE);
                        GlideBindingAdapter.loadImage(ivCardBImg, data.cardBUrl, true);
                    }
                    if (tvCardNameB != null) tvCardNameB.setText(data.cardBName != null ? data.cardBName : "");
                    if (cardB != null) {
                        com.vn.jet.mosco.model.CardDisplayItem mockItem = new com.vn.jet.mosco.model.CardDisplayItem();
                        mockItem.setFrontImage(data.cardBUrl);
                        mockItem.setId(-1);
                        CardEffectHelper.apply(cardB, null, mockItem, true);
                    }
                } else {
                    if (ivPlaceholderB != null) ivPlaceholderB.setVisibility(View.VISIBLE);
                    if (ivCardBImg != null) ivCardBImg.setVisibility(View.GONE);
                    if (tvCardNameB != null) tvCardNameB.setText("");
                    if (cardB != null) CardEffectHelper.applyEmptyStateGlow(cardB, true);
                }
                
                btnDescriptionShow(tvDescription, status, activity);
                
                btnLeft.setVisibility(View.GONE); // Chỉ hiển thị 1 nút Share duy nhất như trong ảnh mẫu
                btnRight.setText(activity.getString(R.string.couple_streak_action_share));
                break;
        }

        btnRight.setOnClickListener(v -> {
            dialog.dismiss();
            if (callback != null) callback.onPositive();
        });

        btnLeft.setOnClickListener(v -> {
            dialog.dismiss();
            if (callback != null) callback.onNegative();
        });

        dialog.show();
    }

    private static void btnDescriptionShow(TextView tv, CoupleStatus status, Activity activity) {
        if (status == CoupleStatus.ACTIVE) {
            tv.setVisibility(View.GONE); 
        } else {
            tv.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Thiết lập logic tương tác cho thẻ bài (Spin style)
     */
    private static void setupCardInteraction(Activity activity, View root, CoupleData data) {
        com.google.android.material.card.MaterialCardView cardA = root.findViewById(R.id.card_a);
        if (cardA == null) return;
        
        FrameLayout btnAddA = cardA.findViewById(R.id.btn_add_card_a);
        View layoutFrontA = cardA.findViewById(R.id.layout_card_a_front);
        ImageView ivFrontA = cardA.findViewById(R.id.card_iv_image); 
        ImageView ivBackA = cardA.findViewById(R.id.iv_card_a_back);
        ImageView ivBadgeA = cardA.findViewById(R.id.card_iv_badge_a);
        TextView tvCardNameA = root.findViewById(R.id.tv_card_name_a);

        // Load initial state
        if (data.cardAUrl != null && !data.cardAUrl.isEmpty()) {
            btnAddA.setVisibility(View.GONE);
            layoutFrontA.setVisibility(View.VISIBLE);
            GlideBindingAdapter.loadImage(ivFrontA, ensureHighQualityUrl(data.cardAUrl), false);
            if (data.cardABackUrl != null) GlideBindingAdapter.loadImage(ivBackA, ensureHighQualityUrl(data.cardABackUrl), false);
            if (tvCardNameA != null) tvCardNameA.setText(data.cardAName != null ? data.cardAName : "");
            
            // Load Badge Grade A
            if (ivBadgeA != null && data.cardAGrade > 0) {
                ivBadgeA.setVisibility(View.VISIBLE);
                String badgePath = activity.getString(R.string.asset_grade_path) + data.cardAGrade + ".png";
                com.bumptech.glide.Glide.with(activity).load(badgePath).into(ivBadgeA);
            }

            // Apply shimmer effect
            View shimmer = cardA.findViewById(R.id.view_card_shimmer);
            if (shimmer != null) {
                com.vn.jet.mosco.model.CardDisplayItem mockItem = new com.vn.jet.mosco.model.CardDisplayItem();
                mockItem.setFrontImage(data.cardAUrl);
                mockItem.setId(-1);
                CardEffectHelper.apply(cardA, shimmer, mockItem, true);
            }
        } else {
            btnAddA.setVisibility(View.VISIBLE);
            layoutFrontA.setVisibility(View.GONE);
            if (tvCardNameA != null) tvCardNameA.setText("");
            // Áp dụng Glow mờ cho trạng thái trống (Spin aesthetic) - KHÔNG nhấp nhô (Phase 2)
            CardEffectHelper.applyEmptyStateGlow(cardA, false); 
        }

        // Gesture Detector cho Tap (Đổi thẻ)
        GestureDetectorCompat detector = new GestureDetectorCompat(activity, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (activity instanceof AppCompatActivity) {
                    InventoryBottomSheet bottomSheet = new InventoryBottomSheet();
                    bottomSheet.setOnCardSelectedListener(selectedItem -> {
                        // 1. Cập nhật Local UI
                        data.cardAUrl = selectedItem.getFrontImage();
                        data.cardABackUrl = selectedItem.getBackImage();
                        data.cardAName = selectedItem.getMember(); 
                        data.cardAGrade = selectedItem.getUpgradeLevel();

                        btnAddA.setVisibility(View.GONE);
                        layoutFrontA.setVisibility(View.VISIBLE);
                        GlideBindingAdapter.loadImage(ivFrontA, ensureHighQualityUrl(data.cardAUrl), false);
                        GlideBindingAdapter.loadImage(ivBackA, ensureHighQualityUrl(data.cardABackUrl), false);
                        if (tvCardNameA != null) tvCardNameA.setText(data.cardAName);
                        
                        if (ivBadgeA != null && data.cardAGrade > 0) {
                            ivBadgeA.setVisibility(View.VISIBLE);
                            String badgePath = activity.getString(R.string.asset_grade_path) + data.cardAGrade + ".png";
                            com.bumptech.glide.Glide.with(activity).load(badgePath).into(ivBadgeA);
                        }

                        View shimmer = cardA.findViewById(R.id.view_card_shimmer);
                        if (shimmer != null) CardEffectHelper.apply(cardA, shimmer, selectedItem, true);

                        // 2. Sync to Backend
                        if (data.streakId != null) {
                            Long myId = new com.vn.jet.mosco.utils.SessionManager(activity).getUserId();
                            com.vn.jet.mosco.network.ApiClient.getClient(activity)
                                .create(com.vn.jet.mosco.network.GameApiService.class)
                                .updateCoupleStreakObjet(data.streakId, myId, selectedItem.getCollectionId())
                                .enqueue(new retrofit2.Callback<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.CoupleStreakDto>>() {
                                    @Override
                                    public void onResponse(retrofit2.Call<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.CoupleStreakDto>> call, retrofit2.Response<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.CoupleStreakDto>> response) {
                                        if (response.isSuccessful()) {
                                            android.util.Log.d("STREAK", "Objet synced successfully");
                                        }
                                    }
                                    @Override
                                    public void onFailure(retrofit2.Call<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.CoupleStreakDto>> call, Throwable t) {
                                        android.util.Log.e("STREAK", "Failed to sync objet", t);
                                    }
                                });
                        }
                    });
                    bottomSheet.show(((AppCompatActivity) activity).getSupportFragmentManager(), "SelectCardStreak");
                }
                return true;
            }
        });

        // 3D Flip Logic (Cao cấp - Silent Luxury)
        float scale = activity.getResources().getDisplayMetrics().density;
        cardA.setCameraDistance(12000 * scale); // Tăng chiều sâu để xoay mượt hơn
        
        final float[] initialTouchX = {0f};
        final float[] startRotation = {0f};
        final boolean[] isFlipped = {false};
        final boolean[] isAnimating = {false};

        cardA.setOnTouchListener((v, event) -> {
            if (isAnimating[0]) return true;
            detector.onTouchEvent(event);
            
            if (data.cardAUrl == null || data.cardAUrl.isEmpty()) {
                return true; 
            }

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    initialTouchX[0] = event.getRawX();
                    startRotation[0] = cardA.getRotationY();
                    v.setLayerType(View.LAYER_TYPE_HARDWARE, null); // Kích hoạt phần cứng
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float diffX = event.getRawX() - initialTouchX[0];
                    float targetRotation = startRotation[0] + (diffX / 4.5f); // Độ nhạy vừa phải
                    cardA.setRotationY(targetRotation);
                    syncGlowToCard(cardA);

                    float normalized = Math.abs(targetRotation % 360);
                    boolean shouldShowBack = (normalized > 90 && normalized < 270);
                    
                    if (shouldShowBack != isFlipped[0]) {
                        isFlipped[0] = shouldShowBack;
                        layoutFrontA.setVisibility(shouldShowBack ? View.GONE : View.VISIBLE);
                        ivBackA.setVisibility(shouldShowBack ? View.VISIBLE : View.GONE);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    // Snap về 0 hoặc 180 mượt mà như SpinFragment
                    float finalRot = cardA.getRotationY();
                    float snapTo = Math.round(finalRot / 180f) * 180f;
                    
                    isAnimating[0] = true;
                    ObjectAnimator snapAnim = ObjectAnimator.ofFloat(cardA, "rotationY", finalRot, snapTo);
                    snapAnim.setDuration(450);
                    snapAnim.setInterpolator(new AccelerateDecelerateInterpolator());
                    snapAnim.addUpdateListener(anim -> syncGlowToCard(cardA));
                    snapAnim.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            isAnimating[0] = false;
                            v.setLayerType(View.LAYER_TYPE_NONE, null);
                            
                            float norm = Math.abs(cardA.getRotationY() % 360);
                            boolean isBack = (norm > 90 && norm < 270);
                            isFlipped[0] = isBack;
                            layoutFrontA.setVisibility(isBack ? View.GONE : View.VISIBLE);
                            ivBackA.setVisibility(isBack ? View.VISIBLE : View.GONE);
                        }
                    });
                    snapAnim.start();
                    return true;
            }
            return true;
        });
    }

    /**
     * Đồng bộ hóa Glow (aura) theo chuyển động của Card (Pattern từ ItemRevealFragment)
     */
    private static void syncGlowToCard(View card) {
        if (card == null) return;
        View glow = (View) card.getTag(R.id.view_progress_fill);
        if (glow != null) {
            glow.setRotationY(card.getRotationY());
            glow.setRotationX(card.getRotationX());
            glow.setTranslationX(card.getTranslationX());
            glow.setTranslationY(card.getTranslationY());
            glow.setScaleX(card.getScaleX());
            glow.setScaleY(card.getScaleY());
        }
    }

    /**
     * Ép URL sử dụng variant 4x (Xịn nhất) để hiển thị trong Dialog cao cấp
     */
    private static String ensureHighQualityUrl(String url) {
        if (url == null || url.isEmpty()) return url;
        if (url.endsWith("/thumbnail")) {
            return url.substring(0, url.length() - 10) + "/4x";
        } else if (url.endsWith("/original")) {
            return url.substring(0, url.length() - 9) + "/4x";
        } else if (!url.contains("/") && !url.startsWith("http")) {
            return url + "/4x";
        }
        return url;
    }
}
