package com.vn.jet.mosco.utils;

/**
 * AppConfig - Centralized configuration for the Mosco app.
 * Used for sensitive keys and network endpoints to avoid strings.xml exposure.
 */
public class AppConfig {
    // API Endpoints
    // LÆ°u Ã½: Náº¿u dÃ¹ng giáº£ láº­p (Emulator), hÃ£y dÃ¹ng "http://10.0.2.2:8080/"
    // Náº¿u dÃ¹ng mÃ¡y tháº­t cÃ¹ng máº¡ng WiFi, dÃ¹ng IP LAN bÃªn dÆ°á»›i.
    $1192.168.1.13:8080/";

    // Social Auth Config (Replace with real values from Firebase/Discord Console)
    public static final String GOOGLE_WEB_CLIENT_ID = "241886304917-7ngj2t444l4avu4h3cv5oka4u6h67nc4.apps.googleusercontent.com";
    public static final String DISCORD_CLIENT_ID = "1498983915631935618";
    public static final String DISCORD_REDIRECT_URI = "mosco://discord";
    public static final String DISCORD_AUTH_URL_BASE = "https://discord.com/api/oauth2/authorize";
    public static final String DISCORD_SCOPE = "identify email";

    // File & Cache Constants
    public static final String AVATAR_CROP_CACHE_NAME = "avatar_crop.webp";
    public static final String DEFAULT_AVATAR_ID = "1";

    // [VIP] Danh sÃ¡ch 24 thÃ nh viÃªn chÃ­nh thá»©c (S1-S24) - DÃ¹ng Ä‘á»ƒ lá»c UI Filter Tabs
    public static final java.util.List<String> OFFICIAL_ARTISTS = java.util.Arrays.asList(
            "SeoYeon", "HyeRin", "JiWoo", "ChaeYeon", "YooYeon", "SooMin", "NaKyoung", "YuBin",
            "Kaede", "DaHyun", "Kotone", "YeonJi", "Nien", "SoHyun", "Xinyu", "Mayu",
            "Lynn", "JooBin", "HaYeon", "ShiOn", "ChaeWon", "Sullin", "SeoAh", "JiYeon"
    );

    // Prevent instantiation
    private AppConfig() {
    }
}


