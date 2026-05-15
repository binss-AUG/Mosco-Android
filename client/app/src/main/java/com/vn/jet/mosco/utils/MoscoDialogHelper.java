package com.vn.jet.mosco.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.vn.jet.mosco.R;
import com.vn.jet.mosco.widget.MoscoButton;

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
        public String partnerName;
        public String cardAUrl;
        public String cardBUrl;
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

        View dialogView = LayoutInflater.from(activity).inflate(R.layout.layout_dialog_couple_streak, null);
        
        android.widget.ImageView btnClose = dialogView.findViewById(R.id.btn_close);
        android.widget.ImageView ivIllustration = dialogView.findViewById(R.id.iv_illustration);
        View layoutActive = dialogView.findViewById(R.id.layout_active_streak);
        TextView tvDescription = dialogView.findViewById(R.id.tv_description);
        MoscoButton btnLeft = dialogView.findViewById(R.id.btn_action_left);
        MoscoButton btnRight = dialogView.findViewById(R.id.btn_action_right);
        
        // Active status views
        TextView tvNameA = dialogView.findViewById(R.id.tv_name_a);
        TextView tvNameB = dialogView.findViewById(R.id.tv_name_b);
        android.widget.ImageView ivCardA = dialogView.findViewById(R.id.iv_card_a_img);
        android.widget.ImageView ivCardB = dialogView.findViewById(R.id.iv_card_b_img);
        TextView tvStreak = dialogView.findViewById(R.id.tv_streak_count);
        MoscoButton btnChange = dialogView.findViewById(R.id.btn_change_card);

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
                tvDescription.setText(activity.getString(R.string.couple_streak_invite_msg, data.partnerName));
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
                tvNameB.setText(data.partnerName);
                tvStreak.setText(String.valueOf(data.streakCount));
                
                // Load images (Sử dụng Glide hoặc ImageLoader nếu có, ở đây minh họa set null/default)
                // TODO: com.bumptech.glide.Glide.with(activity).load(data.cardAUrl).into(ivCardA);
                
                btnDescriptionShow(tvDescription, status, activity);
                
                btnLeft.setVisibility(View.VISIBLE);
                btnLeft.setText(activity.getString(R.string.couple_streak_action_cancel));
                btnRight.setText(activity.getString(R.string.couple_streak_action_share));
                
                btnChange.setOnClickListener(v -> {
                    // Xử lý đổi card (mở Inventory)
                    if (callback != null) callback.onNegative(); // Tận dụng callback để notify click
                });
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
            tv.setVisibility(View.GONE); // Trong bản vẽ màn Streak 30 không thấy description bên dưới
        } else {
            tv.setVisibility(View.VISIBLE);
        }
    }
}
