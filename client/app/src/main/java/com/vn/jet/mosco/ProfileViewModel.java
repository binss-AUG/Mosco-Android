package com.vn.jet.mosco;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.vn.jet.mosco.database.AppDatabase;
import com.vn.jet.mosco.database.UserStatsDao;
import com.vn.jet.mosco.model.UserStats;
import com.vn.jet.mosco.network.ApiClient;
import com.vn.jet.mosco.network.GameApiService;
import com.vn.jet.mosco.utils.AppExecutors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileViewModel extends AndroidViewModel {
    private final UserStatsDao userStatsDao;
    private final GameApiService gameApiService;
    private final MutableLiveData<Long> userIdLiveData = new MutableLiveData<>();
    private final LiveData<UserStats> userStats;
    // TẠI SAO: Quản lý trạng thái shimmer tập trung để các sub-fragment (General, Trophy)
    // nhận biết được khi nào đang loading và tự động skeletonize đồng bộ.
    private final MutableLiveData<Boolean> isShimmering = new MutableLiveData<>(true);
    private final MutableLiveData<String> newBadgeUnlockedEvent = new MutableLiveData<>();

    public LiveData<String> getNewBadgeUnlockedEvent() {
        return newBadgeUnlockedEvent;
    }

    public LiveData<Boolean> getIsShimmering() {
        return isShimmering;
    }

    public void setShimmering(boolean shimmering) {
        isShimmering.setValue(shimmering);
    }

    public ProfileViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getInstance(application);
        userStatsDao = db.userStatsDao();
        gameApiService = ApiClient.getClient(application).create(GameApiService.class);

        // Sử dụng Transformations để chỉ quan sát dữ liệu khi userId thay đổi
        userStats = Transformations.switchMap(userIdLiveData, userStatsDao::getUserStats);
    }

    public void setUserId(Long userId) {
        if (userId == null) return;
        userIdLiveData.setValue(userId);
        refreshUserStats(userId);
    }

    public LiveData<UserStats> getUserStats() {
        return userStats;
    }

    /**
     * Đồng bộ dữ liệu từ Server về Local DB sử dụng AppExecutors
     */
    public void refreshUserStats(Long userId) {
        gameApiService.getUserStats(userId).enqueue(new Callback<UserStats>() {
            @Override
            public void onResponse(Call<UserStats> call, Response<UserStats> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // Chạy trong background thread của AppExecutors để đảm bảo an toàn và ổn định
                    AppExecutors.getInstance().diskIO().execute(() -> {
                        // TẠI SAO: Đọc stats cũ từ Room DB trước khi ghi đè để phát hiện xem có Badge nào mới mở khóa hay không
                        UserStats oldStats = userStatsDao.getUserStatsSync(userId);
                        UserStats newStats = response.body();
                        if (oldStats != null && newStats != null) {
                            java.util.List<String> oldBadges = oldStats.getBadges();
                            java.util.List<String> newBadges = newStats.getBadges();
                            if (oldBadges != null && newBadges != null) {
                                for (String badge : newBadges) {
                                    if (!oldBadges.contains(badge)) {
                                        // TẠI SAO: Post sự kiện mở khóa huy hiệu mới lên Main Thread thông qua LiveData
                                        newBadgeUnlockedEvent.postValue(badge);
                                    }
                                }
                            }
                        }
                        userStatsDao.insertUserStats(newStats);
                    });
                }
            }

            @Override
            public void onFailure(Call<UserStats> call, Throwable t) {
                // Xử lý lỗi nếu cần
            }
        });
    }

    /**
     * Cập nhật danh sách Objet trưng bày (Showcase)
     */
    public void updateShowcase(java.util.List<String> newIds) {
        UserStats stats = userStats.getValue();
        if (stats == null) return;

        // 1. Cập nhật Local DB ngay lập tức (Optimistic UI)
        stats.setShowcaseCardIds(newIds);
        AppExecutors.getInstance().diskIO().execute(() -> {
            userStatsDao.insertUserStats(stats);
        });

        // 2. Đồng bộ lên Server với DTO chuẩn
        com.vn.jet.mosco.network.UpdateProfileRequest request = new com.vn.jet.mosco.network.UpdateProfileRequest();
        request.setShowcaseCardIds(newIds);

        gameApiService.updateProfile(request).enqueue(new Callback<com.vn.jet.mosco.model.ApiResponse<UserStats>>() {
            @Override
            public void onResponse(Call<com.vn.jet.mosco.model.ApiResponse<UserStats>> call, Response<com.vn.jet.mosco.model.ApiResponse<UserStats>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    // [STORAGE FIX] Lưu dữ liệu "Sự thật từ Server" vào Local DB
                    AppExecutors.getInstance().diskIO().execute(() -> {
                        userStatsDao.insertUserStats(response.body().getData());
                    });
                }
            }

            @Override
            public void onFailure(Call<com.vn.jet.mosco.model.ApiResponse<UserStats>> call, Throwable t) {
                // Rollback hoặc thông báo lỗi nếu cần
            }
        });
    }
}
