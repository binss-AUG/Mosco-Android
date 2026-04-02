package com.vn.jet.mosco.utils;

import android.graphics.Color;
import androidx.core.graphics.ColorUtils;

public class ColorProcessor {

    /**
     * Calculates "SurfaceColor": Takes the original color but reduces Opacity to 10-15%
     * and mixes it with the black background (#121212) to be used for the Stats/EXP Background.
     */
    public static int getSurfaceColor(android.content.Context context, String hex) {
        int baseColor = Color.parseColor(hex);
        int bgColor = androidx.core.content.ContextCompat.getColor(context, com.vn.jet.mosco.R.color.mosco_surface_container_low);
        // Blend with 15% opacity of the base color
        return ColorUtils.blendARGB(bgColor, baseColor, 0.15f);
    }

    /**
     * Calculates "AccentColor": If the original color is too bright (Luminance > 0.7),
     * darkens it slightly to use it as a border or Progress bar color.
     */
    public static int getAccentColor(String hex) {
        int baseColor = Color.parseColor(hex);
        double luminance = ColorUtils.calculateLuminance(baseColor);
        if (luminance > 0.7) {
            // Darken it (blend with 40% black)
            return ColorUtils.blendARGB(baseColor, Color.BLACK, 0.4f);
        }
        return baseColor;
    }

    /**
     * Auto check: If the original color is used as a button background,
     * if the background is bright, the text should be Black,
     * if the background is dark, the text should be White.
     */
    public static int getContrastTextColor(int backgroundColor) {
        double luminance = ColorUtils.calculateLuminance(backgroundColor);
        return luminance > 0.5 ? Color.BLACK : Color.WHITE;
    }
}
