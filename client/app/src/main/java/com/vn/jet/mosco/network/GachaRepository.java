package com.vn.jet.mosco.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.vn.jet.mosco.model.ApiResponse;
import com.vn.jet.mosco.model.GachaRollRequest;
import com.vn.jet.mosco.model.GachaRollResponse;
import com.vn.jet.mosco.model.GachaSpinRequest;
import com.vn.jet.mosco.model.GachaSpinResponse;

import org.json.JSONObject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Client-side repository for Gacha API calls.
 * Features:
 * - Automatic retry with exponential backoff (3 attempts: 1s → 2s → 4s)
 * - Structured error parsing for all HTTP status codes
 * - Trade Card for Card (Spin mechanism)
 */
public class GachaRepository {

    private static final String TAG = "GachaRepository";
    private static final int MAX_RETRIES = 3;

    private final GameApiService apiService;

    public GachaRepository(Context context) {
        this.apiService = ApiClient.getClient(context).create(GameApiService.class);
    }

    /**
     * Callback interface for gacha roll results.
     */
    public interface GachaCallback<T> {
        void onSuccess(T response);
        void onError(int httpCode, String errorMessage);
    }

    /**
     * Execute a gacha roll with automatic retry on network failures.
     */
    public void rollGacha(GachaRollRequest request, GachaCallback<GachaRollResponse> callback) {
        executeRollWithRetry(request, callback, 0);
    }

    /**
     * Mở Pack (Đơn hoặc Hàng loạt). Tối đa 36 pack.
     */
    public void openPack(Long userId, String packCode, int quantity, GachaCallback<java.util.Map<String, Object>> callback) {
        apiService.openPack(userId, packCode, quantity).enqueue(new retrofit2.Callback<ApiResponse<java.util.Map<String, Object>>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<java.util.Map<String, Object>>> call, retrofit2.Response<ApiResponse<java.util.Map<String, Object>>> response) {
                handleResponse(response, callback);
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<java.util.Map<String, Object>>> call, Throwable t) {
                callback.onError(-1, "Lỗi kết nối: " + t.getMessage());
            }
        });
    }

    private void executeRollWithRetry(GachaRollRequest request, GachaCallback<GachaRollResponse> callback, int attempt) {
        apiService.rollGacha(request).enqueue(new Callback<ApiResponse<GachaRollResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<GachaRollResponse>> call, Response<ApiResponse<GachaRollResponse>> response) {
                handleResponse(response, callback);
            }

            @Override
            public void onFailure(Call<ApiResponse<GachaRollResponse>> call, Throwable t) {
                if (attempt < MAX_RETRIES - 1) {
                    retry(() -> executeRollWithRetry(request, callback, attempt + 1), attempt);
                } else {
                    callback.onError(-1, "Network error: " + t.getMessage());
                }
            }
        });
    }

    /**
     * Execute a gacha spin (Trade Thẻ Đổi Thẻ) with automatic retry.
     */
    public void spinCard(GachaSpinRequest request, GachaCallback<GachaSpinResponse> callback) {
        executeSpinWithRetry(request, callback, 0);
    }

    private void executeSpinWithRetry(GachaSpinRequest request, GachaCallback<GachaSpinResponse> callback, int attempt) {
        apiService.spinCard(request).enqueue(new Callback<ApiResponse<GachaSpinResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<GachaSpinResponse>> call, Response<ApiResponse<GachaSpinResponse>> response) {
                handleResponse(response, callback);
            }

            @Override
            public void onFailure(Call<ApiResponse<GachaSpinResponse>> call, Throwable t) {
                if (attempt < MAX_RETRIES - 1) {
                    retry(() -> executeSpinWithRetry(request, callback, attempt + 1), attempt);
                } else {
                    callback.onError(-1, "Network error: " + t.getMessage());
                }
            }
        });
    }

    private <T> void handleResponse(Response<ApiResponse<T>> response, GachaCallback<T> callback) {
        if (response.isSuccessful() && response.body() != null) {
            ApiResponse<T> body = response.body();
            if (body.getStatus() == 200 && body.getData() != null) {
                callback.onSuccess(body.getData());
            } else {
                callback.onError(body.getStatus(), body.getMessage());
            }
        } else {
            callback.onError(response.code(), parseErrorBody(response));
        }
    }

    private void retry(Runnable task, int attempt) {
        long delayMs = (long) Math.pow(2, attempt) * 1000L;
        new Handler(Looper.getMainLooper()).postDelayed(task, delayMs);
    }

    /**
     * Parse the JSON error body from an HTTP error response.
     * Supports both { "error": "..." } and { "message": "..." } formats.
     */
    private String parseErrorBody(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                String body = response.errorBody().string();
                JSONObject json = new JSONObject(body);
                // Try "message" first (GachaRollResponse format), then "error" (filter format)
                String msg = json.optString("message", "");
                if (msg.isEmpty()) {
                    msg = json.optString("error", "");
                }
                if (!msg.isEmpty()) {
                    return msg;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing error body", e);
        }

        // Fallback to standard HTTP error descriptions
        switch (response.code()) {
            case 400: return "Invalid request";
            case 401: return "Session expired, please login again";
            case 403: return "Access denied";
            case 429: return "Too many requests, please wait";
            case 500: return "Server error, please try again later";
            default:  return "Error " + response.code();
        }
    }
}
