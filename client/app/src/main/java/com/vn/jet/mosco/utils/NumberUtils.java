package com.vn.jet.mosco.utils;

import android.content.Context;
import com.vn.jet.mosco.R;

/**
 *  NumberUtils — Tiện ích định dạng con số lớn (K, M, B, T).
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

        java.util.Locale locale = java.util.Locale.US;

        if (value >= 999_950_000_000L) {
            return String.format(locale, context.getString(R.string.format_currency_trillions), value / 1_000_000_000_000.0);
        } else if (value >= 999_950_000L) {
            return String.format(locale, context.getString(R.string.format_currency_billions), value / 1_000_000_000.0);
        } else if (value >= 999_950L) {
            return String.format(locale, context.getString(R.string.format_currency_millions), value / 1_000_000.0);
        } else if (value >= 999L) {
            return String.format(locale, context.getString(R.string.format_currency_thousands), value / 1_000.0);
        } else {
            return String.valueOf(value);
        }
    }

    /**
     * Tạo tiền tố mùa rút gọn từ tên mùa đầy đủ.
     * Quy tắc: Lấy ký tự đầu (Hoa) + số thứ tự trong tên mùa (bỏ số 0 đầu).
     * Ví dụ: "Binary02" → "B2", "Apollo01" → "A1", "First" → "F"
     *
     * @param season Tên mùa đầy đủ từ server (Ví dụ: "Binary02")
     * @return Tiền tố rút gọn (Ví dụ: "B2")
     */
    public static String formatSeasonPrefix(String season) {
        if (season == null || season.isEmpty()) return "";

        // Lấy ký tự đầu tiên (in hoa)
        String firstChar = season.substring(0, 1).toUpperCase();

        // Trích xuất số trong tên mùa (ví dụ: "Binary02" → "02" → "2")
        String digits = season.replaceAll("[^0-9]", "");
        if (!digits.isEmpty()) {
            try {
                // Chuyển "02" → 2 → "2" để bỏ số 0 đầu
                String number = String.valueOf(Integer.parseInt(digits));
                return firstChar + number;
            } catch (NumberFormatException e) {
                // Fallback: dùng digits thô
                return firstChar + digits;
            }
        }

        return firstChar;
    }

    /**
     * Định dạng số có dấu phân cách nghìn (Ví dụ: 1.000.000).
     * Cưỡng bức dùng dấu chấm (.) để thống nhất phong cách Luxury.
     */
    public static String formatFull(long value) {
        java.text.DecimalFormatSymbols symbols = new java.text.DecimalFormatSymbols(java.util.Locale.US);
        symbols.setGroupingSeparator('.');
        java.text.DecimalFormat df = new java.text.DecimalFormat("#,###", symbols);
        return df.format(value);
    }
}
