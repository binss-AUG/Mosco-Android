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
                        userStatsDao.insertUserStats(response.body());
                    });
                }
            }

            @Override
            public void onFailure(Call<UserStats> call, Throwable t) {
                // Xử lý lỗi nếu cần
            }
        });
    }
}
