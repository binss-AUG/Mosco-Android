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
 * MoscoDialogManager - Quản lý Dialog theo Builder Pattern.
 * Thay thế cho MoscoDialogHelper cũ (đang bị phình to).
 */
public class MoscoDialogManager {

    public static class Builder {
        private final Activity activity;
        private String title;
        private String message;
        private String positiveText;
        private String negativeText;
        private MoscoDialogHelper.DialogCallback callback;
        private boolean cancelable = true;

        public Builder(Activity activity) {
            this.activity = activity;
        }

        public Builder setTitle(String title) {
            this.title = title;
            return this;
        }

        public Builder setMessage(String message) {
            this.message = message;
            return this;
        }

        public Builder setPositiveText(String positiveText) {
            this.positiveText = positiveText;
            return this;
        }

        public Builder setNegativeText(String negativeText) {
            this.negativeText = negativeText;
            return this;
        }

        public Builder setCallback(MoscoDialogHelper.DialogCallback callback) {
            this.callback = callback;
            return this;
        }

        public Builder setCancelable(boolean cancelable) {
            this.cancelable = cancelable;
            return this;
        }

        public AlertDialog show() {
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) return null;

            View dialogView = LayoutInflater.from(activity).inflate(R.layout.layout_mosco_dialog_base, null);
            
            TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
            TextView tvMessage = dialogView.findViewById(R.id.tv_dialog_message);
            MoscoButton btnPositive = dialogView.findViewById(R.id.btn_positive);
            MoscoButton btnNegative = dialogView.findViewById(R.id.btn_negative);

            if (title != null) tvTitle.setText(title);
            if (message != null) tvMessage.setText(message);
            
            if (positiveText != null) {
                btnPositive.setText(positiveText);
                btnPositive.setVisibility(View.VISIBLE);
            } else {
                btnPositive.setVisibility(View.GONE);
            }

            if (negativeText != null) {
                btnNegative.setText(negativeText);
                btnNegative.setVisibility(View.VISIBLE);
            } else {
                btnNegative.setVisibility(View.GONE);
            }

            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setView(dialogView)
                    .setCancelable(cancelable)
                    .create();

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
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
            return dialog;
        }
    }
}
