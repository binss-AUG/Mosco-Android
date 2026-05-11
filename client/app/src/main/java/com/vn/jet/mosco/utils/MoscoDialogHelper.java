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
}
