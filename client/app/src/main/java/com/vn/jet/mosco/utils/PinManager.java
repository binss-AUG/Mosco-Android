package com.vn.jet.mosco.utils;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

/**
 * [QUIET LUXURY] Manager for pinned cards (local cache only).
 * Pinned cards are stored in SharedPreferences to avoid server overhead.
 */
public class PinManager {
    private static final String PREF_NAME = "mosco_pins";
    private static final String KEY_PINNED_IDS = "pinned_card_ids";

    public static void togglePin(Context context, String cardId) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> pinnedIds = new HashSet<>(prefs.getStringSet(KEY_PINNED_IDS, new HashSet<>()));
        
        if (pinnedIds.contains(cardId)) {
            pinnedIds.remove(cardId);
        } else {
            pinnedIds.add(cardId);
        }
        
        prefs.edit().putStringSet(KEY_PINNED_IDS, pinnedIds).apply();
        DatabaseLoader.notifyInventoryChanged();
    }

    public static boolean isPinned(Context context, String cardId) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> pinnedIds = prefs.getStringSet(KEY_PINNED_IDS, new HashSet<>());
        return pinnedIds.contains(cardId);
    }

    public static Set<String> getAllPinnedIds(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return new HashSet<>(prefs.getStringSet(KEY_PINNED_IDS, new HashSet<>()));
    }

    public static Set<String> getPinnedIds(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getStringSet(KEY_PINNED_IDS, new HashSet<>());
    }
}
