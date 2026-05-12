package com.vn.jet.mosco.utils;

/**
 * AppConfig - Centralized configuration for the Mosco app.
 * Used for sensitive keys and network endpoints to avoid strings.xml exposure.
 */
public class AppConfig {
    // API Endpoints
    // LÃ†Â°u ÃƒÂ½: NÃ¡ÂºÂ¿u dÃƒÂ¹ng giÃ¡ÂºÂ£ lÃ¡ÂºÂ­p (Emulator), hÃƒÂ£y dÃƒÂ¹ng "http://10.0.2.2:8080/"
    // NÃ¡ÂºÂ¿u dÃƒÂ¹ng mÃƒÂ¡y thÃ¡ÂºÂ­t cÃƒÂ¹ng mÃ¡ÂºÂ¡ng WiFi, dÃƒÂ¹ng IP LAN bÃƒÂªn dÃ†Â°Ã¡Â»â€ºi.
    public static final String BASE_URL = "http://192.168.1.13:8080/";

    // Social Auth Config (Replace with real values from Firebase/Discord Console)
    public static final String GOOGLE_WEB_CLIENT_ID = "241886304917-7ngj2t444l4avu4h3cv5oka4u6h67nc4.apps.googleusercontent.com";
    public static final String DISCORD_CLIENT_ID = "1498983915631935618";
    public static final String DISCORD_REDIRECT_URI = "mosco://discord";
    public static final String DISCORD_AUTH_URL_BASE = "https://discord.com/api/oauth2/authorize";
    public static final String DISCORD_SCOPE = "identify email";

    // File & Cache Constants
    public static final String AVATAR_CROP_CACHE_NAME = "avatar_crop.webp";
    public static final String DEFAULT_AVATAR_ID = "1";

    // [VIP] Danh sÃƒÂ¡ch 24 thÃƒÂ nh viÃƒÂªn chÃƒÂ­nh thÃ¡Â»Â©c (S1-S24) - DÃƒÂ¹ng Ã„â€˜Ã¡Â»Æ’ lÃ¡Â»Âc UI Filter Tabs
    public static final java.util.List<String> OFFICIAL_ARTISTS = java.util.Arrays.asList(
            "SeoYeon", "HyeRin", "JiWoo", "ChaeYeon", "YooYeon", "SooMin", "NaKyoung", "YuBin",
            "Kaede", "DaHyun", "Kotone", "YeonJi", "Nien", "SoHyun", "Xinyu", "Mayu",
            "Lynn", "JooBin", "HaYeon", "ShiOn", "ChaeWon", "Sullin", "SeoAh", "JiYeon"
    );

    // Prevent instantiation
    private AppConfig() {
    }
}







