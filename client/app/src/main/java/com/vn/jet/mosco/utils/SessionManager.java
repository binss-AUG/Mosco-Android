package com.vn.jet.mosco.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.vn.jet.mosco.model.AuthResponse;

/**
 * Manages user session data using SharedPreferences.
 * Stores authentication token and user profile information.
 *
 * Uses Application Context internally to prevent memory leaks.
 * In production, upgrade to EncryptedSharedPreferences from
 * {@code androidx.security:security-crypto} for storing sensitive tokens.
 */
public class SessionManager {

    private static final String PREF_NAME = "mosco_session";
    private static final String KEY_TOKEN = "auth_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_EMAIL = "user_email";
    private static final String KEY_USERNAME = "user_name";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Saves user session after a successful sign in / sign up.
     * Applies changes asynchronously with apply() for better performance.
     *
     * @param userData The user data returned from the auth API.
     */
    public void saveSession(AuthResponse.UserData userData) {
        if (userData == null) return;
        prefs.edit()
                .putString(KEY_TOKEN, userData.getToken())
                .putLong(KEY_USER_ID, userData.getId() != null ? userData.getId() : -1L)
                .putString(KEY_EMAIL, userData.getEmail())
                .putString(KEY_USERNAME, userData.getUsername())
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .apply();
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public Long getUserId() {
        long id = prefs.getLong(KEY_USER_ID, -1L);
        return id == -1L ? null : id;
    }

    public String getEmail() {
        return prefs.getString(KEY_EMAIL, null);
    }

    public String getUsername() {
        return prefs.getString(KEY_USERNAME, null);
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    /**
     * Clears all session data. Call on logout.
     */
    public void clearSession() {
        prefs.edit().clear().apply();
    }
}
