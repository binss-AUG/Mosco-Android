package com.vn.jet.mosco.utils;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.vn.jet.mosco.R;

/**
 * ErrorTranslator — Bộ dịch lỗi phản hồi từ API Backend sang giao diện đa ngôn ngữ.
 * TẠI SAO: Tránh việc hiển thị chuỗi thông báo lỗi cứng Tiếng Việt từ server. 
 * Nếu server trả về một mã key (ví dụ: stage_err_team_size), class này sẽ tự động 
 * tra cứu strings.xml để hiển thị tiếng Anh/Việt tương ứng.
 */
public final class ErrorTranslator {

    private ErrorTranslator() {
        // Utility class — không cho phép khởi tạo
    }

    /**
     * Dịch mã lỗi từ server sang chuỗi hiển thị đa ngôn ngữ.
     * Nếu không tìm thấy mã lỗi tương ứng trong strings resource, trả về chuỗi gốc.
     */
    @NonNull
    public static String translate(@NonNull Context context, @Nullable String serverMessage) {
        if (serverMessage == null || serverMessage.trim().isEmpty()) {
            return context.getString(R.string.common_error_unknown);
        }

        // Chuẩn hóa key (loại bỏ ký tự lạ, giữ lại chữ, số và gạch dưới)
        String cleanKey = serverMessage.trim().replaceAll("[^a-zA-Z0-9_]", "");
        
        try {
            int resId = context.getResources().getIdentifier(cleanKey, "string", context.getPackageName());
            if (resId != 0) {
                return context.getString(resId);
            }
        } catch (Exception e) {
            // TẠI SAO: Tránh crash nếu quá trình tra cứu resource gặp lỗi ngoại lệ
        }

        // Tương thích ngược: Trả về thông điệp gốc nếu không có key dịch tương ứng
        return serverMessage;
    }
}
