package com.vn.jet.mosco.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

/**
 * LanguageHelper — Tiện ích quản lý và cập nhật Locale ngôn ngữ của ứng dụng.
 * TẠI SAO: Hỗ trợ chuyển đổi ngôn ngữ động ở mức context trên cả Android cũ và mới (Android 9+).
 */
public final class LanguageHelper {

    private LanguageHelper() {
        // Utility class — không cho phép khởi tạo
    }

    /**
     * Cập nhật ngôn ngữ và bọc Context mới.
     * TẠI SAO: Từ Android N trở lên, API Configuration.locale bị deprecated, 
     * cần sử dụng createConfigurationContext để tránh lỗi không đồng bộ ngôn ngữ trên các thành phần UI.
     */
    public static Context updateLocale(Context context, String languageCode) {
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);

        Resources resources = context.getResources();
        Configuration configuration = resources.getConfiguration();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocale(locale);
            LocaleList localeList = new LocaleList(locale);
            LocaleList.setDefault(localeList);
            configuration.setLocales(localeList);
            return context.createConfigurationContext(configuration);
        } else {
            configuration.locale = locale;
            resources.updateConfiguration(configuration, resources.getDisplayMetrics());
            return context;
        }
    }
}
