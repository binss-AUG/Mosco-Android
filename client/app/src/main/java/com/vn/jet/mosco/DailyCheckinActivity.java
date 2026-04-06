package com.vn.jet.mosco;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.vn.jet.mosco.network.ApiClient;
import com.vn.jet.mosco.network.GameApiService;

import org.json.JSONObject;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Màn hình Điểm danh hằng ngày — 3 slot theo khung giờ.
 * Gọi API để lấy trạng thái slot và claim phần thưởng.
 */
public class DailyCheckinActivity extends AppCompatActivity {

    private static final String TAG = "DailyCheckinActivity";
    private GameApiService apiService;

    private Button btnMorning, btnNoon, btnEvening;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daily_checkin);

        apiService = ApiClient.getClient(this).create(GameApiService.class);

        // Ánh xạ view
        btnMorning = findViewById(R.id.btn_claim_morning);
        btnNoon = findViewById(R.id.btn_claim_noon);
        btnEvening = findViewById(R.id.btn_claim_evening);

        // Nút back
        findViewById(R.id.btn_back_daily).setOnClickListener(v -> finish());

        // Nút claim cho mỗi slot
        btnMorning.setOnClickListener(v -> claimSlot());
        btnNoon.setOnClickListener(v -> claimSlot());
        btnEvening.setOnClickListener(v -> claimSlot());

        // Tải trạng thái slot
        loadDailyStatus();
    }

    /**
     * Lấy trạng thái 3 slot từ Server.
     * Cập nhật UI: disable nút đã claim, highlight slot đang mở.
     */
    private void loadDailyStatus() {
        apiService.getDailyStatus().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject json = new JSONObject(response.body().string());
                        JSONObject data = json.optJSONObject("data");
                        if (data == null) return;

                        JSONObject statuses = data.optJSONObject("slotStatuses");
                        if (statuses == null) return;

                        // Cập nhật UI cho từng slot
                        updateSlotButton(btnMorning, statuses.optString("0", "locked"));
                        updateSlotButton(btnNoon, statuses.optString("1", "locked"));
                        updateSlotButton(btnEvening, statuses.optString("2", "locked"));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing daily status", e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "Connection error: daily/status", t);
            }
        });
    }

    /**
     * Cập nhật giao diện nút Claim theo trạng thái slot.
     */
    private void updateSlotButton(Button btn, String status) {
        if (btn == null) return;
        switch (status) {
            case "claimed":
                btn.setText("CLAIMED");
                btn.setEnabled(false);
                btn.setAlpha(0.5f);
                break;
            case "available":
                btn.setText("CLAIM");
                btn.setEnabled(true);
                btn.setAlpha(1f);
                break;
            case "locked":
            default:
                btn.setText("EXPIRED");
                btn.setEnabled(false);
                btn.setAlpha(0.3f);
                break;
        }
    }

    /**
     * Gọi API claim cho slot hiện tại.
     * Server tự xác định slot dựa trên giờ hệ thống.
     */
    private void claimSlot() {
        apiService.claimDaily().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject json = new JSONObject(response.body().string());
                        String message = json.optString("message", "Check-in successful!");
                        Toast.makeText(DailyCheckinActivity.this, message, Toast.LENGTH_SHORT).show();

                        // Reload lại trạng thái để cập nhật UI
                        loadDailyStatus();
                    } else {
                        // Parse lỗi từ Server
                        String errorMsg = "Cannot check in";
                        if (response.errorBody() != null) {
                            JSONObject errJson = new JSONObject(response.errorBody().string());
                            errorMsg = errJson.optString("message", errorMsg);
                        }
                        Toast.makeText(DailyCheckinActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing claim response", e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "Connection error: daily/claim", t);
                Toast.makeText(DailyCheckinActivity.this, "Connection error", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
