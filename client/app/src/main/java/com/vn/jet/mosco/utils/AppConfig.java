package com.vn.jet.mosco.utils;

/**
 * AppConfig - Cấu hình tập trung cho ứng dụng Mosco.
 * Tránh để lộ các key nhạy cảm và cấu hình mạng trực tiếp trong strings.xml.
 */
public class AppConfig {
    // API Endpoints
    // Lưu ý: Nếu dùng giả lập (Emulator), hãy dùng "http://10.0.2.2:8080/"
    // Nếu dùng máy thật cùng mạng WiFi, dùng IP LAN bên dưới.
    public static final String BASE_URL = "http://192.168.1.13:8080/";

    // Cấu hình Social Auth (Thay thế bằng giá trị thực từ Console)
    public static final String GOOGLE_WEB_CLIENT_ID = "241886304917-7ngj2t444l4avu4h3cv5oka4u6h67nc4.apps.googleusercontent.com";
    public static final String DISCORD_CLIENT_ID = "1498983915631935618";
    public static final String DISCORD_REDIRECT_URI = "mosco://discord";
    public static final String DISCORD_AUTH_URL_BASE = "https://discord.com/api/oauth2/authorize";
    public static final String DISCORD_SCOPE = "identify email";

    // Hằng số File & Cache
    public static final String AVATAR_CROP_CACHE_NAME = "avatar_crop.webp";
    public static final String DEFAULT_AVATAR_ID = "1";

    // [VIP] Danh sách 24 thành viên chính thức (S1-S24) - Dùng để lọc UI Filter Tabs
    public static final java.util.List<String> OFFICIAL_ARTISTS = java.util.Arrays.asList(
            "SeoYeon", "HyeRin", "JiWoo", "ChaeYeon", "YooYeon", "SooMin", "NaKyoung", "YuBin",
            "Kaede", "DaHyun", "Kotone", "YeonJi", "Nien", "SoHyun", "Xinyu", "Mayu",
            "Lynn", "JooBin", "HaYeon", "ShiOn", "ChaeWon", "Sullin", "SeoAh", "JiYeon"
    );

    // Tránh khởi tạo đối tượng
    private AppConfig() {
    }
}
