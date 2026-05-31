package com.vn.jet.mosco.utils;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.vn.jet.mosco.MoscoBaseActivity;
import com.vn.jet.mosco.R;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * NetworkErrorHandler — Tiện ích tập trung xử lý và phân loại lỗi mạng.
 *
 * TẠI SAO: Trước đây mỗi file xử lý onFailure khác nhau (có chỗ Toast,
 * có chỗ bỏ trống, có chỗ hiện t.getMessage() kỹ thuật). Class này
 * chuẩn hóa toàn bộ thành message thân thiện từ strings.xml.
 */
public final class NetworkErrorHandler {

    private NetworkErrorHandler() {
        // Utility class — không cho phép khởi tạo
    }

    /**
     * Phân loại Throwable và trả về chuỗi thông báo thân thiện cho người dùng.
     *
     * TẠI SAO: Phân biệt rõ 3 loại lỗi chính để user biết cần làm gì:
     * - Mất mạng → kiểm tra WiFi/4G
     * - Server offline → chờ và thử lại
     * - Timeout → thử lại
     */
    @NonNull
    public static String getUserFriendlyMessage(@NonNull Context context, @Nullable Throwable t) {
        if (t == null) {
            return context.getString(R.string.common_error_unknown);
        }

        // 1. Mất mạng hoàn toàn (DNS fail, không có kết nối)
        if (t instanceof UnknownHostException) {
            return context.getString(R.string.common_error_network);
        }

        // 2. Server offline hoặc bị từ chối kết nối
        if (t instanceof ConnectException) {
            return context.getString(R.string.error_server_offline);
        }

        // 3. Timeout — server quá tải hoặc mạng yếu
        if (t instanceof SocketTimeoutException) {
            return context.getString(R.string.error_timeout);
        }

        // 4. IOException khác (SSL error, connection reset, etc.)
        if (t instanceof java.io.IOException) {
            return context.getString(R.string.common_error_network);
        }

        // 5. Lỗi không xác định
        return context.getString(R.string.common_error_unknown);
    }

    /**
     * Hiển thị thông báo lỗi mạng cho người dùng.
     *
     * TẠI SAO: Ưu tiên dùng Snackbar qua MoscoBaseActivity (đẹp hơn, nhất quán),
     * fallback sang Toast nếu context không phải MoscoBaseActivity (ví dụ: Fragment)
     */
    public static void handleError(@NonNull Context context, @Nullable Throwable t) {
        String message = getUserFriendlyMessage(context, t);

        if (context instanceof MoscoBaseActivity) {
            ((MoscoBaseActivity) context).showMoscoMessage(message);
        } else {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Hiển thị lỗi mạng — phiên bản an toàn cho Fragment.
     *
     * TẠI SAO: Fragment có thể bị detach bất cứ lúc nào, getContext() có thể null.
     * Cần kiểm tra trước khi hiển thị Toast/Snackbar để tránh NPE.
     */
    public static void handleErrorSafe(@Nullable Context context, @Nullable Throwable t) {
        if (context == null) return;

        // Kiểm tra Activity đã bị destroy chưa (tránh WindowManager leak)
        if (context instanceof Activity && (((Activity) context).isFinishing() || ((Activity) context).isDestroyed())) {
            return;
        }

        handleError(context, t);
    }
}
