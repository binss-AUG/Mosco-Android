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
                                
                                // TẠM THỜI VÔ HIỆU HÓA REDIRECT 401 ĐỂ NỘP BÀI (Tránh bị văng ra khi Server local chưa sync kịp)
                                /*
                                if (response.code() == 401 && !original.url().encodedPath().contains("/api/auth/")) {
                                    sessionManager.clearSession();
                                    
                                    // Bắn intent đá thẳng màn hình về SignInActivity
                                    android.content.Intent intent = new android.content.Intent(appContext, com.vn.jet.mosco.SignInActivity.class);
                                    // Force new task vì gọi từ appContext
                                    intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    appContext.startActivity(intent);
                                }
                                */
                                
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
