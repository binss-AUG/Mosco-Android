package com.vn.jet.mosco.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import androidx.browser.customtabs.CustomTabsIntent;

import org.json.JSONObject;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class DiscordAuthManager {

    private static final String PREF_NAME = "DiscordOAuth2";
    private static final String KEY_VERIFIER = "code_verifier";
    private static final String TAG = "DiscordAuthManager";

    public interface DiscordAuthCallback {
        void onSuccess(String id, String username, String email, String accessToken, String avatarUrl);
        void onError(String error);
    }

    public static void startDiscordLogin(Activity activity) {
        // 1. Sinh code_verifier (chuỗi ngẫu nhiên)
        String codeVerifier = generateCodeVerifier();
        
        // 2. Lưu lại để dùng khi exchange code
        SharedPreferences prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_VERIFIER, codeVerifier).apply();

        // 3. Tạo code_challenge bằng SHA-256 từ code_verifier
        String codeChallenge = generateCodeChallenge(codeVerifier);

        // 4. Build URL chuẩn chỉnh với PKCE
        Uri authUri = Uri.parse(AppConfig.DISCORD_AUTH_URL_BASE).buildUpon()
                .appendQueryParameter("client_id", AppConfig.DISCORD_CLIENT_ID)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", AppConfig.DISCORD_REDIRECT_URI)
                .appendQueryParameter("scope", AppConfig.DISCORD_SCOPE)
                .appendQueryParameter("code_challenge", codeChallenge)
                .appendQueryParameter("code_challenge_method", "S256")
                .build();

        // Mở Custom Tabs
        CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
        customTabsIntent.launchUrl(activity, authUri);
    }

    public static void handleCallback(Activity activity, Uri data, DiscordAuthCallback callback) {
        if (data == null || !"mosco".equals(data.getScheme())) return;

        String error = data.getQueryParameter("error");
        if (error != null) {
            callback.onError(error);
            return;
        }

        String code = data.getQueryParameter("code");
        if (code == null || code.isEmpty()) {
            callback.onError("No code returned from Discord");
            return;
        }

        SharedPreferences prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String codeVerifier = prefs.getString(KEY_VERIFIER, null);

        if (codeVerifier == null) {
            callback.onError("Security error: Code verifier not found");
            return;
        }

        // Thực hiện Exchange Token
        exchangeCodeForToken(code, codeVerifier, new DiscordAuthCallback() {
            @Override
            public void onSuccess(String id, String username, String email, String accessToken, String avatarUrl) {
                // Xóa verifier sau khi dùng xong
                prefs.edit().remove(KEY_VERIFIER).apply();
                
                // Quay lại Main Thread
                new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(id, username, email, accessToken, avatarUrl));
            }

            @Override
            public void onError(String errorMsg) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onError(errorMsg));
            }
        });
    }

    private static void exchangeCodeForToken(String code, String codeVerifier, DiscordAuthCallback callback) {
        OkHttpClient client = new OkHttpClient();

        RequestBody body = new FormBody.Builder()
                .add("client_id", AppConfig.DISCORD_CLIENT_ID)
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", AppConfig.DISCORD_REDIRECT_URI)
                .add("code_verifier", codeVerifier)
                .build();

        Request request = new Request.Builder()
                .url("https://discord.com/api/oauth2/token")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String jsonResponse = response.body().string();
                        JSONObject jsonObject = new JSONObject(jsonResponse);
                        String accessToken = jsonObject.getString("access_token");

                        // Lấy thông tin user ngay sau khi có token
                        fetchDiscordUserInfo(accessToken, callback);

                    } catch (Exception e) {
                        callback.onError("JSON Parse Error: " + e.getMessage());
                    }
                } else {
                    String errBody = response.body() != null ? response.body().string() : "";
                    Log.e(TAG, "Token Error: " + errBody);
                    callback.onError("Token Exchange Failed: " + response.code());
                }
            }
        });
    }

    private static void fetchDiscordUserInfo(String accessToken, DiscordAuthCallback callback) {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url("https://discord.com/api/users/@me")
                .header("Authorization", "Bearer " + accessToken)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Failed to fetch user data: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String jsonResponse = response.body().string();
                        JSONObject json = new JSONObject(jsonResponse);
                        
                        String username = json.optString("username", "Discord User");
                        String email = json.optString("email", "no-email@discord.com");
                        String id = json.optString("id", "");
                        String avatarHash = json.optString("avatar", "");
                        
                        String avatarUrl = "";
                        if (!id.isEmpty() && !avatarHash.isEmpty()) {
                            avatarUrl = "https://cdn.discordapp.com/avatars/" + id + "/" + avatarHash + ".png";
                        }

                        callback.onSuccess(id, username, email, accessToken, avatarUrl);

                    } catch (Exception e) {
                        callback.onError("User Data Parse Error: " + e.getMessage());
                    }
                } else {
                    callback.onError("User Info Request Failed: " + response.code());
                }
            }
        });
    }

    private static String generateCodeVerifier() {
        SecureRandom sr = new SecureRandom();
        byte[] code = new byte[32];
        sr.nextBytes(code);
        return Base64.encodeToString(code, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static String generateCodeChallenge(String verifier) {
        try {
            byte[] bytes = verifier.getBytes("US-ASCII");
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(bytes, 0, bytes.length);
            byte[] digest = md.digest();
            return Base64.encodeToString(digest, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}
