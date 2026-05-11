package com.vn.jet.mosco.network;

import android.content.Context;

import com.vn.jet.mosco.R;
import com.vn.jet.mosco.utils.SessionManager;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Singleton Retrofit client with double-checked locking for thread safety.
 * Includes:
 * - OkHttp interceptor for auto-attaching Bearer auth token
 * - Explicit timeout configuration (15s connect, 30s read, 30s write)
 */
public class ApiClient {
    private static volatile Retrofit retrofit = null;

    private ApiClient() {}

    public static Retrofit getClient(Context context) {
        if (retrofit == null) {
            synchronized (ApiClient.class) {
                if (retrofit == null) {
                    Context appContext = context.getApplicationContext();
                    SessionManager sessionManager = new SessionManager(appContext);

                    OkHttpClient client = new OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
                            .writeTimeout(30, TimeUnit.SECONDS)
                            .addInterceptor(chain -> {
                                Request original = chain.request();
                                Request.Builder builder = original.newBuilder();

                                String token = sessionManager.getToken();
                                // Ngăn chặn Spam Backend khi dùng Bypass: Chỉ đính kèm Token nếu nó là JWT (chứa dấu chấm)
                                if (token != null && !token.isEmpty() && token.contains(".")) {
                                    builder.header("Authorization", "Bearer " + token);
                                }

                                okhttp3.Response response = chain.proceed(builder.build());
                                
                                // [PHASE 2] Session Expired / Dual Login Kick / Server Offline — 401 Interceptor
                                if (response.code() == 401 && !original.url().encodedPath().contains("/api/auth/")) {
                                    // Đọc message từ server để phân biệt loại lỗi (JWT hết hạn vs Login trùng)
                                    String errorMsg = null;
                                    okhttp3.ResponseBody peekBody = response.peekBody(2048);
                                    if (peekBody != null) {
                                        try {
                                            org.json.JSONObject errJson = new org.json.JSONObject(peekBody.string());
                                            errorMsg = errJson.optString("message", null);
                                        } catch (Exception ignored) {}
                                    }

                                    // Broadcast sự kiện SessionExpired để Activity đang hiển thị bắt và show Dialog
                                    android.content.Intent expiredIntent = new android.content.Intent("com.vn.jet.mosco.SESSION_EXPIRED");
                                    if (errorMsg != null) {
                                        expiredIntent.putExtra("message", errorMsg);
                                    }
                                    androidx.localbroadcastmanager.content.LocalBroadcastManager
                                            .getInstance(appContext).sendBroadcast(expiredIntent);
                                }
                                
                                return response;
                            })
                            .addInterceptor(chain -> {
                                Request request = chain.request();
                                // Chỉ ép WebP cho domain Cloudflare
                                if (request.url().host().contains("imagedelivery.net")) {
                                    request = request.newBuilder()
                                            .header("Accept", "image/webp")
                                            .build();
                                }
                                return chain.proceed(request);
                            })
                            .build();

                    retrofit = new Retrofit.Builder()
                            .baseUrl(com.vn.jet.mosco.utils.AppConfig.BASE_URL)
                            .client(client)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();
                }
            }
        }
        return retrofit;
    }
}
