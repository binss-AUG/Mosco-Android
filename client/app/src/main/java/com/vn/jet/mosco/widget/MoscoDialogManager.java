package com.vn.jet.mosco.widget;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.vn.jet.mosco.R;

/**
 * Quản lý hệ thống Dialog toàn dự án.
 * Đảm bảo mọi Dialog đều tuân thủ phong cách 'Liquid Glass' và sử dụng MoscoButton.
 */
public class MoscoDialogManager {

    /**
     * Tạo một Dialog cơ bản với nền Liquid Glass.
     */
    @NonNull
    public static Dialog createLiquidDialog(@NonNull Context context, @NonNull View contentView) {
        Dialog dialog = new Dialog(context);
        dialog.setContentView(contentView);
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            // Hiệu ứng làm mờ nền
            dialog.getWindow().setDimAmount(0.7f);
        }
        
        return dialog;
    }

    /**
     * Hiển thị Dialog xác nhận nhanh (Confirm Dialog).
     */
    public static void showConfirm(@NonNull Context context, 
                                   @NonNull String title, 
                                   @NonNull String message, 
                                   @NonNull String posText,
                                   @Nullable Runnable onPositive) {
        
        View view = LayoutInflater.from(context).inflate(R.layout.layout_mosco_dialog_base, null);
        Dialog dialog = createLiquidDialog(context, view);

        TextView tvTitle = view.findViewById(R.id.tv_dialog_title);
        TextView tvMessage = view.findViewById(R.id.tv_dialog_message);
        MoscoButton btnPos = view.findViewById(R.id.btn_positive);
        MoscoButton btnNeg = view.findViewById(R.id.btn_negative);

        tvTitle.setText(title);
        tvMessage.setText(message);
        btnPos.setText(posText);

        btnPos.setOnClickListener(v -> {
            if (onPositive != null) onPositive.run();
            dialog.dismiss();
        });

        btnNeg.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    /**
     * Hiển thị Dialog mua hàng trong Shop (Liquid Version).
     * @param customContentView View được inflate từ dialog_shop_buy.xml hoặc tương đương.
     */
    public static void showShopBuy(@NonNull Context context, @NonNull View customContentView) {
        // Gắn nền Liquid Glass cho View tùy chỉnh
        customContentView.setBackgroundResource(R.drawable.bg_surface_solid);
        Dialog dialog = createLiquidDialog(context, customContentView);
        dialog.show();
    }
}
