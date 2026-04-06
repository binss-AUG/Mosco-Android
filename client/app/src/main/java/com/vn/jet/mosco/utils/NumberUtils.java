package com.vn.jet.mosco.utils;

import android.content.Context;
import com.vn.jet.mosco.R;

/**
 * 📊 NumberUtils — Tiện ích định dạng con số lớn (K, M, B, T).
 * Giúp hiển thị tài nguyên/giá cả gọn gàng, tránh vỡ layout khi số quá dài.
 */
public class NumberUtils {

    /**
     * Định dạng số theo chuẩn rút gọn (Ví dụ: 1,500 -> 1.5K, 5,000,000,000 -> 5.0B).
     * @param context Context để truy cập Resource strings.
     * @param value Giá trị cần định dạng.
     * @return Chuỗi đã định dạng.
     */
    public static String format(Context context, long value) {
        if (context == null) return String.valueOf(value);

        if (value >= 999_950_000_000L) {
            return String.format(context.getString(R.string.home_format_currency_trillions), value / 1_000_000_000_000.0);
        } else if (value >= 999_950_000L) {
            return String.format(context.getString(R.string.home_format_currency_billions), value / 1_000_000_000.0);
        } else if (value >= 999_950L) {
            return String.format(context.getString(R.string.home_format_currency_millions), value / 1_000_000.0);
        } else if (value >= 999L) {
            return String.format(context.getString(R.string.home_format_currency_thousands), value / 1_000.0);
        } else {
            return String.valueOf(value);
        }
    }

    /**
     * Định dạng số có dấu phân cách nghìn (Ví dụ: 1,000,000).
     * Dùng cho những chỗ cần hiển thị chính xác con số.
     */
    public static String formatFull(long value) {
        return String.format("%,d", value);
    }
}
