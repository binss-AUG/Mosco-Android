package com.vn.jet.mosco.utils;

import java.util.Arrays;
import java.util.List;

/**
 * AppConfig - Centralized configuration for the Mosco app.
 * Dung de quan ly cac thong so mang va key bao mat.
 */
public class AppConfig {

    // 1. API Endpoints
    // Neu dung GIA LAP (Emulator), hay dung: "http://10.0.2.2:8080/"
    // Neu dung MAY THAT, hay dung IP LAN cua may tinh (vi du: 192.168.1.13)
    public static final String BASE_URL = "http://192.168.1.13:8080/";

    // WebSocket URL - Tu dong chuyen doi tu BASE_URL (Khong duoc xoa)
    public static final String WS_URL = BASE_URL.replace("http", "ws") + "ws-mosco/websocket";

    // 2. Social Auth Config (Firebase, Discord)
    public static final String GOOGLE_WEB_CLIENT_ID = "241886304917-7ngj2t444l4avu4h3cv5oka4u6h67nc4.apps.googleusercontent.com";
    public static final String DISCORD_CLIENT_ID = "1498983915631935618";
    public static final String DISCORD_REDIRECT_URI = "mosco://discord";
    public static final String DISCORD_AUTH_URL_BASE = "https://discord.com/api/oauth2/authorize";
    public static final String DISCORD_SCOPE = "identify email";

    // 3. File & Cache Constants
    public static final String AVATAR_CROP_CACHE_NAME = "avatar_crop.webp";
    public static final String DEFAULT_AVATAR_ID = "1";

    // 4. Danh sach Official Artists (S1-S24)
    public static final List<String> OFFICIAL_ARTISTS = Arrays.asList(
            "SeoYeon", "HyeRin", "JiWoo", "ChaeYeon", "YooYeon", "SooMin", "NaKyoung", "YuBin",
            "Kaede", "DaHyun", "Kotone", "YeonJi", "Nien", "SoHyun", "Xinyu", "Mayu",
            "Lynn", "JooBin", "HaYeon", "ShiOn", "ChaeWon", "Sullin", "SeoAh", "JiYeon");

    // Tránh khởi tạo đối tượng
    private AppConfig() {
    }
}
