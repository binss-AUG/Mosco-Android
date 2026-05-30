package com.vn.jet.mosco.utils;

import android.app.Activity;
import android.content.Context;

import com.vn.jet.mosco.R;
import com.vn.jet.mosco.database.AppDatabase;
import com.vn.jet.mosco.model.UserStats;
import com.vn.jet.mosco.network.ApiClient;
import com.vn.jet.mosco.network.GameApiService;
import com.vn.jet.mosco.widget.MoscoNotification;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * BadgeSyncHelper — Lớp tiện ích đồng bộ thông số và kiểm tra huy hiệu mới.
 * TẠI SAO: Tự động chạy ngầm sau khi người dùng thực hiện các thao tác làm thay đổi chỉ số 
 * (như mở Pack, Spin, nâng cấp thẻ) để phát hiện và hiển thị banner chúc mừng ngay lập tức
 * mà không cần phải đợi người dùng truy cập vào màn hình Profile.
 */
public class BadgeSyncHelper {

    public static void syncAndCheckBadges(final Activity activity, final Long userId) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || userId == null) {
            return;
        }

        final Context appContext = activity.getApplicationContext();
        
        // TẠI SAO: Chạy tác vụ đọc SQLite trong luồng diskIO để tránh block luồng chính (Main Thread)
        AppExecutors.getInstance().diskIO().execute(() -> {
            final UserStats oldStats = AppDatabase.getInstance(appContext).userStatsDao().getUserStatsSync(userId);

            GameApiService apiService = ApiClient.getClient(appContext).create(GameApiService.class);
            apiService.getUserStats(userId).enqueue(new Callback<UserStats>() {
                @Override
                public void onResponse(Call<UserStats> call, Response<UserStats> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        final UserStats newStats = response.body();

                        // TẠI SAO: So sánh và cập nhật dữ liệu SQLite trong luồng diskIO để tránh xung đột
                        AppExecutors.getInstance().diskIO().execute(() -> {
                            if (oldStats != null && newStats != null) {
                                List<String> oldBadges = oldStats.getBadges();
                                List<String> newBadges = newStats.getBadges();
                                if (oldBadges != null && newBadges != null) {
                                    for (final String badge : newBadges) {
                                        if (!oldBadges.contains(badge)) {
                                            // TẠI SAO: Đẩy thông báo lên Main Thread thông qua Executor chính để tương tác với UI an toàn
                                            AppExecutors.getInstance().mainThread().execute(() -> {
                                                if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                                                    MoscoNotification.showSuccess(
                                                            activity,
                                                            activity.getString(R.string.badge_unlocked_congrats_format, badge)
                                                    );
                                                }
                                            });
                                        }
                                    }
                                }
                            }
                            // Cập nhật dữ liệu cache local mới nhất từ server
                            AppDatabase.getInstance(appContext).userStatsDao().insertUserStats(newStats);
                        });
                    }
                }

                @Override
                public void onFailure(Call<UserStats> call, Throwable t) {
                    // TẠI SAO: Bỏ qua lỗi kết nối mạng lặng lẽ vì đây là tiến trình đồng bộ ngầm tự động
                }
            });
        });
    }
}
