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
    private static final String KEY_AVATAR = "user_avatar";
    private static final String KEY_INGAME_NAME = "ingame_name";
    private static final String KEY_AVATAR_ID = "avatar_id";
    private static final String KEY_SELECTED_SHOWCASE_ID = "selected_showcase_id"; 
    private static final String KEY_AVATAR_CROP_PARAMS = "avatar_crop_params"; 
    
    // --- SETTINGS KEYS ---
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_MUSIC_ENABLED = "music_enabled";
    private static final String KEY_SFX_ENABLED = "sfx_enabled";
    private static final String KEY_AUTO_BACKUP = "auto_backup";
    private static final String KEY_BACKUP_INTERVAL = "backup_interval";
    private static final String KEY_LANGUAGE = "language_code";
    private static final String KEY_NOTI_PRIVATE_CHAT = "noti_private_chat";
    private static final String KEY_NOTI_STREAK = "noti_streak";
    
    // --- REMEMBER ME KEYS ---
    private static final String KEY_REMEMBER_ME = "remember_me";
    private static final String KEY_SAVED_USERNAME_OR_EMAIL = "saved_username_or_email";

    private final SharedPreferences prefs;
    private final Context context;

    public SessionManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Lưu ID thẻ bài sếp chọn làm trung tâm màn hình Home.
     * Giải pháp này giúp duy trì trạng thái Showcase ngay cả khi chuyển Tab.
     */
    public void setSelectedShowcaseId(long cardId) {
        prefs.edit().putLong(KEY_SELECTED_SHOWCASE_ID, cardId).apply();
    }

    public long getSelectedShowcaseId() {
        return prefs.getLong(KEY_SELECTED_SHOWCASE_ID, -1L);
    }

    /**
     * Saves user session after a successful sign in / sign up.
     * Applies changes asynchronously with apply() for better performance.
     *
     * @param userData The user data returned from the auth API.
     */
    public void saveSession(AuthResponse.UserData userData) {
        if (userData == null) return;
        
        long newUserId = userData.getId() != null ? userData.getId() : -1L;
        long oldUserId = prefs.getLong(KEY_USER_ID, -1L);
        
        // --- 🔄 FORCE SYNC LOGIC ---
        // Nếu phát hiện đăng nhập bằng tài khoản khác, thực hiện dọn dẹp Cache RAM ngay lập tức
        if (oldUserId != -1L && oldUserId != newUserId) {
            DatabaseLoader.clearUserCache();
            // Xóa đường dẫn Avatar cũ để đảm bảo không load nhầm cache đĩa của Glide
            prefs.edit().remove(KEY_AVATAR).apply();
        }

        prefs.edit()
                .putString(KEY_TOKEN, userData.getToken())
                .putLong(KEY_USER_ID, newUserId)
                .putString(KEY_EMAIL, userData.getEmail())
                .putString(KEY_USERNAME, userData.getUsername())
                .putString(KEY_INGAME_NAME, userData.getIngameName())
                .putString(KEY_AVATAR_ID, userData.getAvatarId())
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

    public void setUsername(String username) {
        prefs.edit().putString(KEY_USERNAME, username).apply();
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public String getAvatar() {
        return prefs.getString(KEY_AVATAR, null);
    }

    public void setAvatar(String avatarUrl) {
        prefs.edit().putString(KEY_AVATAR, avatarUrl).apply();
    }

    public String getIngameName() {
        return prefs.getString(KEY_INGAME_NAME, null);
    }

    public void setIngameName(String name) {
        prefs.edit().putString(KEY_INGAME_NAME, name).apply();
    }

    public String getAvatarId() {
        return prefs.getString(KEY_AVATAR_ID, "1"); // Default to Objet #1
    }

    public void setAvatarId(String avatarId) {
        prefs.edit().putString(KEY_AVATAR_ID, avatarId).apply();
    }

    public String getAvatarCropParams() {
        return prefs.getString(KEY_AVATAR_CROP_PARAMS, null);
    }

    public void setAvatarCropParams(String params) {
        prefs.edit().putString(KEY_AVATAR_CROP_PARAMS, params).apply();
    }

    /**
     * Lấy đường dẫn file Avatar cố định cho User hiện tại trong Internal Storage.
     * Giải pháp này giúp ảnh "bền vững" hơn so với lưu trong Cache.
     */
    public String getAvatarPathForUser() {
        Long userId = getUserId();
        if (userId == null) return null;
        return context.getFilesDir().getAbsolutePath() + "/avatar_" + userId + ".jpg";
    }

    /**
     * Clears all session data. Call on logout.
     */
    public void clearSession() {
        prefs.edit().clear().apply();
        // Dọn dẹp Cache RAM khi logout để đảm bảo an toàn dữ liệu
        DatabaseLoader.clearUserCache();
    }

    // --- SETTINGS PERSISTENCE ---

    public void setDarkMode(boolean enabled) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply();
    }

    public boolean isDarkMode() {
        return prefs.getBoolean(KEY_DARK_MODE, true); // Mặc định Dark Mode
    }

    public void setMusicEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_MUSIC_ENABLED, enabled).apply();
    }

    public boolean isMusicEnabled() {
        return prefs.getBoolean(KEY_MUSIC_ENABLED, true);
    }

    public void setSfxEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SFX_ENABLED, enabled).apply();
    }

    public boolean isSfxEnabled() {
        return prefs.getBoolean(KEY_SFX_ENABLED, true);
    }

    public void setAutoBackupEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_BACKUP, enabled).apply();
    }

    public boolean isAutoBackupEnabled() {
        return prefs.getBoolean(KEY_AUTO_BACKUP, false); // Default OFF
    }

    public void setBackupInterval(int hours) {
        prefs.edit().putInt(KEY_BACKUP_INTERVAL, hours).apply();
    }

    public int getBackupInterval() {
        return prefs.getInt(KEY_BACKUP_INTERVAL, 72); // Default 72 hours (3 days)
    }

    // Tại sao (WHY): Lưu thông tin đăng nhập tự động khi người dùng chọn "Remember me"
    public void saveRememberMe(boolean enabled, String usernameOrEmail) {
        prefs.edit()
                .putBoolean(KEY_REMEMBER_ME, enabled)
                .putString(KEY_SAVED_USERNAME_OR_EMAIL, enabled ? usernameOrEmail : null)
                .apply();
    }

    public boolean isRememberMeEnabled() {
        return prefs.getBoolean(KEY_REMEMBER_ME, false);
    }

    public String getSavedUsernameOrEmail() {
        return prefs.getString(KEY_SAVED_USERNAME_OR_EMAIL, null);
    }

    public void setLanguage(String langCode) {
        prefs.edit().putString(KEY_LANGUAGE, langCode).apply();
    }

    public String getLanguage() {
        // TẠI SAO: Nếu chưa từng lưu cài đặt ngôn ngữ (lần đầu vào app), kiểm tra locale thiết bị.
        // Nếu thiết bị đang dùng tiếng Việt thì đặt mặc định là "vi", ngược lại là "en".
        if (!prefs.contains(KEY_LANGUAGE)) {
            String deviceLang = java.util.Locale.getDefault().getLanguage();
            String defaultLang = "vi".equals(deviceLang) ? "vi" : "en";
            prefs.edit().putString(KEY_LANGUAGE, defaultLang).apply();
            return defaultLang;
        }
        return prefs.getString(KEY_LANGUAGE, "en");
    }

    public void setPrivateChatNotificationEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTI_PRIVATE_CHAT, enabled).apply();
    }

    public boolean isPrivateChatNotificationEnabled() {
        return prefs.getBoolean(KEY_NOTI_PRIVATE_CHAT, true);
    }

    public void setStreakNotificationEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTI_STREAK, enabled).apply();
    }

    public boolean isStreakNotificationEnabled() {
        return prefs.getBoolean(KEY_NOTI_STREAK, true);
    }
}
