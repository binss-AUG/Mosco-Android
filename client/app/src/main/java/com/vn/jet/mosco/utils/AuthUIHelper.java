package com.vn.jet.mosco.utils;

import android.app.Activity;

/**
 * Helper class for luxury Auth UI effects.
 * Now simplified to use GalacticBackgroundView for 2026 "Planet & Particle" aesthetic.
 */
public class AuthUIHelper {

    /**
     * Initializes any necessary UI effects for Auth activities.
     * With GalacticBackgroundView, animation is handled inside the View itself.
     */
    public static void animateAurora(Activity activity) {
        // Legacy animations removed. 
        // GalacticBackgroundView in layout_auth_background handles rendering and animation.
    }

    /**
     * Call this in onPause or before finishing an activity to save state if needed.
     */
    public static void saveAnimationState() {
        // No longer needed for GalacticBackgroundView but kept for API compatibility
    }
}
