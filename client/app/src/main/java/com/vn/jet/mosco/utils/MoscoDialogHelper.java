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
import com.vn.jet.mosco.widget.MoscoDialogManager;
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
        new MoscoDialogManager.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveText(positiveText)
                .setNegativeText(negativeText)
                .setCallback(callback)
                .show();
    }

    /**
     * Dialog Logout chuyên dụng
     */
    public static void showLogoutDialog(Activity activity, DialogCallback callback) {
        showConfirmDialog(activity,
                activity.getString(R.string.dialog_logout_title),
                activity.getString(R.string.dialog_logout_msg),
                activity.getString(R.string.action_confirm),
                activity.getString(R.string.action_cancel),
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
        new MoscoDialogManager.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveText(positiveText)
                .setCallback(callback)
                .setCancelable(false)
                .show();
    }

    public static void showSingleChoiceDialog(Activity activity, String title, String[] items, DialogChoiceCallback callback) {
        showSingleChoiceDialog(activity, title, items, -1, callback);
    }

    public static void showSingleChoiceDialog(Activity activity, String title, String[] items, int checkedItem, DialogChoiceCallback callback) {
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
            if (i == checkedItem) {
                rb.setChecked(true);
            }
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
    public static AlertDialog showCoupleStreakDialog(Activity activity, 
                                            CoupleStatus status, 
                                            CoupleData data, 
                                            DialogCallback callback) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return null;
        if (data == null) return null;

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
                btnRight.setText(activity.getString(R.string.couple_streak_action_cancel)); // "Hủy yêu cầu"
                btnRight.setEnabled(true);
                btnRight.setMoscoStyle("secondary");
                break;

            case RECEIVED_REQUEST:
                ivIllustration.setVisibility(View.VISIBLE);
                layoutActive.setVisibility(View.GONE);
                tvDescription.setText(activity.getString(R.string.couple_streak_received_msg, data.partnerName));
                btnLeft.setVisibility(View.VISIBLE);
                btnLeft.setText(activity.getString(R.string.couple_streak_action_decline));
                btnLeft.setMoscoStyle("secondary");
                btnRight.setText(activity.getString(R.string.couple_streak_action_accept));
                btnRight.setMoscoStyle("primary");
                break;

            case SETUP:
            case ACTIVE:
                ivIllustration.setVisibility(View.GONE);
                layoutActive.setVisibility(View.VISIBLE);
                tvNameA.setText(activity.getString(R.string.couple_streak_you)); 
                tvNameA.setSelected(true); // Kích hoạt Marquee (lineshow)
                tvNameB.setText(data.partnerName);
                tvNameB.setSelected(true); // Kích hoạt Marquee
                
                if (data.streakCount == 0) {
                    tvStreak.setVisibility(View.GONE);
                } else {
                    tvStreak.setVisibility(View.VISIBLE);
                    tvStreak.setText(String.valueOf(data.streakCount));
                }

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
                ImageView ivBackB = dialogView.findViewById(R.id.iv_card_b_back);
                com.google.android.material.card.MaterialCardView cardB = dialogView.findViewById(R.id.card_b);
                
                if (data.cardBUrl != null && !data.cardBUrl.isEmpty()) {
                    if (ivPlaceholderB != null) ivPlaceholderB.setVisibility(View.GONE);
                    View layoutFrontB = dialogView.findViewById(R.id.layout_selected_front_b);
                    if (layoutFrontB != null) layoutFrontB.setVisibility(View.VISIBLE);
                    
                    if (ivCardBImg != null) {
                        GlideBindingAdapter.loadImage(ivCardBImg, ensureHighQualityUrl(data.cardBUrl), false);
                    }
                    if (ivBackB != null && data.cardBBackUrl != null) {
                        GlideBindingAdapter.loadImage(ivBackB, ensureHighQualityUrl(data.cardBBackUrl), false);
                    }
                    if (tvCardNameB != null) tvCardNameB.setText(data.cardBName != null ? data.cardBName : "");
                    
                    ImageView ivBadgeB = dialogView.findViewById(R.id.card_iv_badge_b);
                    // TODO: Hiển thị badge sau này
                    // if (ivBadgeB != null && data.cardBGrade > 0) {
                    //     ivBadgeB.setVisibility(View.VISIBLE);
                    //     String badgePath = activity.getString(R.string.asset_grade_path) + data.cardBGrade + ".png";
                    //     com.bumptech.glide.Glide.with(activity).load(badgePath).into(ivBadgeB);
                    // }

                    if (cardB != null) {
                        com.vn.jet.mosco.model.CardDisplayItem mockItem = new com.vn.jet.mosco.model.CardDisplayItem();
                        mockItem.setFrontImage(data.cardBUrl);
                        // Sử dụng hash của URL làm ID để CardEffectHelper nhận biết thay đổi
                        mockItem.setId(data.cardBUrl.hashCode()); 
                        CardEffectHelper.apply(cardB, null, mockItem, true);
                    }
                } else {
                    if (ivPlaceholderB != null) ivPlaceholderB.setVisibility(View.VISIBLE);
                    View layoutFrontB = dialogView.findViewById(R.id.layout_selected_front_b);
                    if (layoutFrontB != null) layoutFrontB.setVisibility(View.GONE);
                    if (tvCardNameB != null) tvCardNameB.setText("");
                    if (cardB != null) CardEffectHelper.applyEmptyStateGlow(cardB, true);
                }
                
                // Cho phép lật thẻ B
                if (cardB != null && data.cardBUrl != null && !data.cardBUrl.isEmpty()) {
                    View layoutFrontB = dialogView.findViewById(R.id.layout_selected_front_b);
                    attach3DFlip(activity, cardB, layoutFrontB, ivBackB, data.cardBUrl, null);
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
        return dialog;
    }

    public static void updateCoupleStreakDialog(android.app.AlertDialog dialog, CoupleData data, Activity activity) {
        if (dialog == null || !dialog.isShowing() || data == null) return;
        
        // Cập nhật card A
        ImageView ivFrontA = dialog.findViewById(R.id.card_iv_image);
        ImageView ivBackA = dialog.findViewById(R.id.iv_card_a_back);
        TextView tvCardNameA = dialog.findViewById(R.id.tv_card_name_a);
        ImageView ivBadgeA = dialog.findViewById(R.id.card_iv_badge_a);
        
        if (ivFrontA != null && data.cardAUrl != null) {
            GlideBindingAdapter.loadImage(ivFrontA, ensureHighQualityUrl(data.cardAUrl), false);
            if (ivBackA != null && data.cardABackUrl != null) GlideBindingAdapter.loadImage(ivBackA, ensureHighQualityUrl(data.cardABackUrl), false);
            if (tvCardNameA != null) tvCardNameA.setText(data.cardAName != null ? data.cardAName : "");
            
            // TODO: Hiển thị badge sau này
            // if (ivBadgeA != null && data.cardAGrade > 0) {
            //     ivBadgeA.setVisibility(View.VISIBLE);
            //     String badgePath = activity.getString(R.string.asset_grade_path) + data.cardAGrade + ".png";
            //     com.bumptech.glide.Glide.with(activity).load(badgePath).into(ivBadgeA);
            // }
            // Khởi tạo lại toàn bộ Interaction (Tap đổi thẻ & Lật 3D)
            setupCardInteraction(activity, dialog.getWindow().getDecorView(), data);
        }

        // Cập nhật card B
        ImageView ivFrontB = dialog.findViewById(R.id.card_iv_image_b);
        ImageView ivBackB = dialog.findViewById(R.id.iv_card_b_back);
        TextView tvCardNameB = dialog.findViewById(R.id.tv_card_name_b);
        ImageView ivBadgeB = dialog.findViewById(R.id.card_iv_badge_b);
        
        if (ivFrontB != null && data.cardBUrl != null) {
            dialog.findViewById(R.id.iv_card_b_placeholder).setVisibility(View.GONE);
            dialog.findViewById(R.id.layout_selected_front_b).setVisibility(View.VISIBLE);
            GlideBindingAdapter.loadImage(ivFrontB, ensureHighQualityUrl(data.cardBUrl), false);
            if (ivBackB != null && data.cardBBackUrl != null) GlideBindingAdapter.loadImage(ivBackB, ensureHighQualityUrl(data.cardBBackUrl), false);
            if (tvCardNameB != null) tvCardNameB.setText(data.cardBName != null ? data.cardBName : "");
            
            // TODO: Hiển thị badge sau này
            // if (ivBadgeB != null && data.cardBGrade > 0) {
            //     ivBadgeB.setVisibility(View.VISIBLE);
            //     String badgePath = activity.getString(R.string.asset_grade_path) + data.cardBGrade + ".png";
            //     com.bumptech.glide.Glide.with(activity).load(badgePath).into(ivBadgeB);
            // }

            com.google.android.material.card.MaterialCardView cardB = dialog.findViewById(R.id.card_b);
            if (cardB != null) {
                // Áp dụng Glow màu thực từ ảnh (shimmer=null vì Card B không có shimmer view riêng)
                com.vn.jet.mosco.model.CardDisplayItem mockItemB = new com.vn.jet.mosco.model.CardDisplayItem();
                mockItemB.setFrontImage(data.cardBUrl);
                mockItemB.setId(data.cardBUrl.hashCode()); // Đồng bộ theo URL
                CardEffectHelper.apply(cardB, null, mockItemB, true);
                
                // Tái gắn Touch Listener lật 3D
                View layoutFrontB = dialog.findViewById(R.id.layout_selected_front_b);
                attach3DFlip(activity, cardB, layoutFrontB, ivBackB, data.cardBUrl, null);
            }
        } else {
            // Nếu mất thẻ B (bị gỡ)
            com.google.android.material.card.MaterialCardView cardB = dialog.findViewById(R.id.card_b);
            if (cardB != null) {
                CardEffectHelper.applyEmptyStateGlow(cardB, true);
                cardB.setOnTouchListener(null); // Gỡ bỏ lật
            }
        }
        
        // Cập nhật số streak
        TextView tvStreak = dialog.findViewById(R.id.tv_streak_count);
        if (tvStreak != null) {
            if (data.streakCount == 0) {
                tvStreak.setVisibility(View.GONE);
            } else {
                tvStreak.setVisibility(View.VISIBLE);
                tvStreak.setText(String.valueOf(data.streakCount));
            }
        }
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
                mockItem.setId(data.cardAUrl.hashCode()); // Dùng hash URL thay vì -1 để update glow chính xác
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
                        
                        // TODO: Hiển thị badge sau này
                        // if (ivBadgeA != null && data.cardAGrade > 0) {
                        //     ivBadgeA.setVisibility(View.VISIBLE);
                        //     String badgePath = activity.getString(R.string.asset_grade_path) + data.cardAGrade + ".png";
                        //     com.bumptech.glide.Glide.with(activity).load(badgePath).into(ivBadgeA);
                        // }

                        View shimmer = cardA.findViewById(R.id.view_card_shimmer);
                        if (shimmer != null) CardEffectHelper.apply(cardA, shimmer, selectedItem, true);

                        // Thay vì đợi callback, ta cập nhật trực tiếp tại chỗ
                        ImageView ivFrontA_local = cardA.findViewById(R.id.card_iv_image);
                        ImageView ivBackA_local = cardA.findViewById(R.id.iv_card_a_back);
                        if (ivFrontA_local != null) GlideBindingAdapter.loadImage(ivFrontA_local, ensureHighQualityUrl(data.cardAUrl), false);
                        if (ivBackA_local != null) GlideBindingAdapter.loadImage(ivBackA_local, ensureHighQualityUrl(data.cardABackUrl), false);

                        // 2. Sync to Backend
                        if (data.streakId != null) {
                            Long myId = new com.vn.jet.mosco.utils.SessionManager(activity).getUserId();
                            com.vn.jet.mosco.network.ApiClient.getClient(activity)
                                .create(com.vn.jet.mosco.network.GameApiService.class)
                                .updateCoupleStreakObjet(data.streakId, myId, selectedItem.getCollectionId(), selectedItem.getUpgradeLevel())
                                .enqueue(new retrofit2.Callback<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.CoupleStreakDto>>() {
                                    @Override
                                    public void onResponse(retrofit2.Call<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.CoupleStreakDto>> call, retrofit2.Response<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.CoupleStreakDto>> response) {
                                        if (response.isSuccessful()) {
                                            android.widget.Toast.makeText(activity, "Objet updated successfully!", android.widget.Toast.LENGTH_SHORT).show();
                                        } else {
                                            android.widget.Toast.makeText(activity, "Failed to update Objet", android.widget.Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                    @Override
                                    public void onFailure(retrofit2.Call<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.CoupleStreakDto>> call, Throwable t) {
                                        android.widget.Toast.makeText(activity, "Network error. Please try again.", android.widget.Toast.LENGTH_SHORT).show();
                                    }
                                });
                        }
                    });
                    bottomSheet.show(((AppCompatActivity) activity).getSupportFragmentManager(), "SelectCardStreak");
                }
                return true;
            }
        });

        attach3DFlip(activity, cardA, layoutFrontA, ivBackA, data.cardAUrl, detector);
    }

    private static void attach3DFlip(Activity activity, com.google.android.material.card.MaterialCardView card, View layoutFront, ImageView ivBack, String cardUrl, GestureDetectorCompat detector) {
        float scale = activity.getResources().getDisplayMetrics().density;
        card.setCameraDistance(12000 * scale);
        
        final float[] initialTouchX = {0f};
        final float[] startRotation = {0f};
        final boolean[] isFlipped = {false};
        final boolean[] isAnimating = {false};

        card.setOnTouchListener((v, event) -> {
            if (isAnimating[0]) return true;
            if (detector != null) detector.onTouchEvent(event);
            
            if (cardUrl == null || cardUrl.isEmpty()) {
                return true; 
            }

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    initialTouchX[0] = event.getRawX();
                    startRotation[0] = card.getRotationY();
                    v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float diffX = event.getRawX() - initialTouchX[0];
                    float targetRotation = startRotation[0] + (diffX / 4.5f);
                    card.setRotationY(targetRotation);
                    syncGlowToCard(card);

                    float normalized = Math.abs(targetRotation % 360);
                    boolean shouldShowBack = (normalized > 90 && normalized < 270);
                    
                    if (shouldShowBack != isFlipped[0]) {
                        isFlipped[0] = shouldShowBack;
                        if (layoutFront != null) layoutFront.setVisibility(shouldShowBack ? View.GONE : View.VISIBLE);
                        if (ivBack != null) ivBack.setVisibility(shouldShowBack ? View.VISIBLE : View.GONE);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    float finalRot = card.getRotationY();
                    float snapTo = Math.round(finalRot / 180f) * 180f;
                    
                    isAnimating[0] = true;
                    ObjectAnimator snapAnim = ObjectAnimator.ofFloat(card, "rotationY", finalRot, snapTo);
                    snapAnim.setDuration(450);
                    snapAnim.setInterpolator(new AccelerateDecelerateInterpolator());
                    snapAnim.addUpdateListener(anim -> syncGlowToCard(card));
                    snapAnim.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            isAnimating[0] = false;
                            v.setLayerType(View.LAYER_TYPE_NONE, null);
                            
                            float norm = Math.abs(card.getRotationY() % 360);
                            boolean isBack = (norm > 90 && norm < 270);
                            isFlipped[0] = isBack;
                            if (layoutFront != null) layoutFront.setVisibility(isBack ? View.GONE : View.VISIBLE);
                            if (ivBack != null) ivBack.setVisibility(isBack ? View.VISIBLE : View.GONE);
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

    /**
     * Hiển thị BottomSheet chi tiết Streak (Daily Check-in Streak) chuẩn Holographic Liquid Glass
     */
    public static void showStreakDetailBottomSheet(
            android.content.Context context,
            int currentStreak,
            int bestStreak,
            int restores,
            com.vn.jet.mosco.network.GameApiService gameApiService,
            Runnable onUpdated) {
        if (context == null) return;
        
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(context, R.style.LiquidGlass_BottomSheetTheme);
        View view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_streak_detail, null);
        
        TextView tvCurrent = view.findViewById(R.id.tv_current_streak);
        TextView tvBest = view.findViewById(R.id.tv_best_streak);
        android.widget.Button btnRestore = view.findViewById(R.id.btn_restore_streak);
        com.airbnb.lottie.LottieAnimationView ivIcon = view.findViewById(R.id.iv_streak_icon);
        
        if (ivIcon != null) {
            com.vn.jet.mosco.utils.StreakColorHelper.setupStreakLottie(ivIcon, currentStreak, currentStreak > 0);
            
            // Tạm dừng hoạt họa ngọn lửa ban đầu để chuẩn bị hiệu ứng "bùng cháy" sau khi mở hẳn BottomSheet
            if (ivIcon.isAnimating()) ivIcon.cancelAnimation();
            ivIcon.setFrame(0);
            ivIcon.setAlpha(0f);
            
            if (currentStreak >= 1000) {
                // Hiệu ứng RGB cầu vồng cho streak khủng
                android.animation.ValueAnimator dialogRgbAnimator = android.animation.ValueAnimator.ofFloat(0f, 360f);
                dialogRgbAnimator.setDuration(3000);
                dialogRgbAnimator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                dialogRgbAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
                dialogRgbAnimator.addUpdateListener(animation -> {
                    float hue = (float) animation.getAnimatedValue();
                    com.vn.jet.mosco.utils.StreakColorHelper.applyRGBEffect(ivIcon, hue);
                });
                dialogRgbAnimator.start();
                dialog.setOnDismissListener(d -> dialogRgbAnimator.cancel());
            }
        }

        // Hiệu ứng trượt so le (Staggered Animation) từ dưới lên của các phần tử stats và shield
        View statsLayout = view.findViewById(R.id.layout_streak_stats);
        if (statsLayout != null) {
            statsLayout.setAlpha(0f);
            statsLayout.setTranslationY(80f);
            statsLayout.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(500)
                .setStartDelay(120)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
        }

        View shieldCard = view.findViewById(R.id.card_streak_shield);
        if (shieldCard != null) {
            shieldCard.setAlpha(0f);
            shieldCard.setTranslationY(100f);
            shieldCard.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .setStartDelay(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
        }
        
        tvCurrent.setText(context.getString(R.string.rank_format_streak, currentStreak));
        tvBest.setText(context.getString(R.string.rank_format_streak, bestStreak));
        btnRestore.setText(restores < 3 ? "RESTORE (FREE " + (3 - restores) + "/3)" : "RESTORE (500 DIAMONDS)");

        btnRestore.setOnClickListener(v -> {
            if (currentStreak >= bestStreak && currentStreak > 0) return;
            btnRestore.setEnabled(false);
            gameApiService.restoreStreak().enqueue(new retrofit2.Callback<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.UserStats>>() {
                @Override
                public void onResponse(retrofit2.Call<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.UserStats>> call, retrofit2.Response<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.UserStats>> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        if (onUpdated != null) onUpdated.run();
                        dialog.dismiss();
                    } else { btnRestore.setEnabled(true); }
                }
                @Override
                public void onFailure(retrofit2.Call<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.UserStats>> call, Throwable t) { btnRestore.setEnabled(true); }
            });
        });
        
        final boolean[] hasBurst = {false};
        final Runnable burstAction = () -> {
            if (hasBurst[0]) return;
            hasBurst[0] = true;
            triggerStreakBurstAnimation(ivIcon);
        };

        dialog.setOnShowListener(d -> {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                com.google.android.material.bottomsheet.BottomSheetBehavior<View> behavior = 
                    com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);
                behavior.addBottomSheetCallback(new com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback() {
                    @Override
                    public void onStateChanged(@androidx.annotation.NonNull View bottomSheetView, int newState) {
                        if (newState == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED) {
                            burstAction.run();
                        }
                    }
                    @Override
                    public void onSlide(@androidx.annotation.NonNull View bottomSheetView, float slideOffset) {}
                });
            }
            
            // Backup Trigger: Đảm bảo bùng cháy chuẩn xác sau 350ms (thời gian trượt của window kết thúc)
            view.postDelayed(burstAction, 350);
        });

        dialog.setContentView(view);
        dialog.show();
    }

    private static void triggerStreakBurstAnimation(com.airbnb.lottie.LottieAnimationView ivIcon) {
        if (ivIcon == null) return;
        
        // Cấu hình điểm pivot ở đáy trung tâm để ngọn lửa bùng lên TỪ DƯỚI LÊN
        float width = ivIcon.getWidth() > 0 ? ivIcon.getWidth() / 2f : (130f * ivIcon.getResources().getDisplayMetrics().density) / 2f;
        float height = ivIcon.getHeight() > 0 ? ivIcon.getHeight() : (130f * ivIcon.getResources().getDisplayMetrics().density);
        ivIcon.setPivotX(width);
        ivIcon.setPivotY(height);
        
        // Kích hoạt ngọn lửa Lottie chạy hoạt họa
        ivIcon.playAnimation();
        
        // Hoạt họa bùng lên từ đáy (Scale Y mạnh hơn Scale X, kết hợp trượt nhẹ từ dưới lên)
        ivIcon.setScaleX(0.1f);
        ivIcon.setScaleY(0.1f);
        ivIcon.setTranslationY(60f); // hơi lùi xuống dưới
        ivIcon.setAlpha(0f);
        
        ivIcon.animate()
            .alpha(1f)
            .scaleX(1.1f)
            .scaleY(1.25f) // scale Y cao hơn để tạo cảm giác ngọn lửa vươn cao bùng cháy!
            .translationY(-15f) // hơi vọt lên trên đỉnh một chút
            .setDuration(450)
            .setInterpolator(new android.view.animation.AccelerateInterpolator())
            .withEndAction(() -> {
                // Đàn hồi nhẹ nhàng về kích thước và vị trí chuẩn ổn định (1.0f, translationY=0)
                ivIcon.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .translationY(0f)
                    .setDuration(250)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.4f))
                    .start();
            })
            .start();
    }
}
