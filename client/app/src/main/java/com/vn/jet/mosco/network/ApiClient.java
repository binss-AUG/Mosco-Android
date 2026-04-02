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
                                if (token != null && !token.isEmpty()) {
                                    builder.header("Authorization", "Bearer " + token);
                                }

                                return chain.proceed(builder.build());
                            })
                            .build();

                    String baseUrl = appContext.getString(R.string.base_url);
                    retrofit = new Retrofit.Builder()
                            .baseUrl(baseUrl)
                            .client(client)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();
                }
            }
        }
        return retrofit;
    }
}
