package com.vn.jet.mosco.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.vn.jet.mosco.database.AppDatabase;
import com.vn.jet.mosco.dto.CardSummaryDto;
import com.vn.jet.mosco.model.CardEntity;
import com.vn.jet.mosco.network.ApiClient;
import com.vn.jet.mosco.network.GameApiService;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Response;

/**
 * Manager xử lý việc đồng bộ dữ liệu Delta từ Server vào Room DB.
 */
public class SyncManager {
    private static final String TAG = "SyncManager";
    private static final String PREF_NAME = "sync_prefs";
    private static final String KEY_LAST_SYNC = "last_sync_time";

    public static void startDeltaSync(Context context) {
        new Thread(() -> {
            try {
                SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                long lastSync = prefs.getLong(KEY_LAST_SYNC, 0);

                GameApiService apiService = ApiClient.getClient(context).create(GameApiService.class);
                Response<List<CardSummaryDto>> response = apiService.getCardsSync(lastSync).execute();

                if (response.isSuccessful() && response.body() != null) {
                    List<CardSummaryDto> updates = response.body();
                    if (!updates.isEmpty()) {
                        Log.i(TAG, "Phát hiện " + updates.size() + " thay đổi từ Server. Đang cập nhật Room DB...");
                        
                        List<CardEntity> entities = new ArrayList<>();
                        for (CardSummaryDto dto : updates) {
                            CardEntity entity = new CardEntity();
                            entity.setId(dto.getId());
                            entity.setMemberName(dto.getMemberName());
                            entity.setSeasonName(dto.getSeasonName());
                            entity.setFrontImageId(dto.getThumbnailId());
                            // Mặc định các trường khác nếu chưa có trong summary
                            entity.setRarityClass("Standard"); 
                            entities.add(entity);
                        }
                        
                        AppDatabase.getInstance(context).cardDao().upsertAll(entities);
                        
                        // Lưu timestamp mới nhất
                        prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply();
                        Log.i(TAG, "Đồng bộ Delta hoàn tất.");
                    } else {
                        Log.i(TAG, "Dữ liệu đã là mới nhất (No Delta).");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Lỗi khi đồng bộ Delta", e);
            }
        }).start();
    }
}
