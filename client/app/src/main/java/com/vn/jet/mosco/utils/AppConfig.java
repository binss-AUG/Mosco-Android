package com.vn.jet.mosco.utils;

/**
 * AppConfig - Centralized configuration for the Mosco app.
 * Used for sensitive keys and network endpoints to avoid strings.xml exposure.
 */
public class AppConfig {
    // API Endpoints
    public static final String BASE_URL = "http://192.168.1.86:8080/";

    // Social Auth Config (Replace with real values from Firebase/Discord Console)
    public static final String GOOGLE_WEB_CLIENT_ID = "241886304917-7ngj2t444l4avu4h3cv5oka4u6h67nc4.apps.googleusercontent.com";
    public static final String DISCORD_CLIENT_ID = "1498983915631935618";
    public static final String DISCORD_REDIRECT_URI = "mosco://discord";
    public static final String DISCORD_AUTH_URL_BASE = "https://discord.com/api/oauth2/authorize";
    public static final String DISCORD_SCOPE = "identify email";

    // Gameplay Constants
    public static final int STAGE_SPEED_UP_COST_PER_HOUR = 10;
    public static final long MS_PER_HOUR = 3600000L;

    // Prevent instantiation
    private AppConfig() {}
}
