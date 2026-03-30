package com.vn.jet.mosco.utils;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * DatabaseLoader — Reads and parses the card collection from assets/database.json.
 *
 * The JSON structure is:
 * {
 *   "collections": [
 *     { "id": "...", "member": "...", "backgroundColor": "#ea88b4", ... },
 *     ...
 *   ]
 * }
 *
 * Usage:
 *   List<JSONObject> cards = DatabaseLoader.loadAllCards(context);
 *   JSONObject card = cards.get(position);
 *   String bgColor = card.optString("backgroundColor");
 */
public class DatabaseLoader {

    private static final String TAG = "DatabaseLoader";
    private static final String FILE_NAME = "database.json";

    // Cached list to avoid re-reading the 7MB file every time
    private static List<JSONObject> cachedCards = null;

    /**
     * Loads all card entries from assets/database.json.
     * Results are cached in memory after the first load.
     *
     * @param context Application or Activity context
     * @return List of JSONObject, one per card. Empty list on failure.
     */
    public static List<JSONObject> loadAllCards(Context context) {
        if (cachedCards != null) {
            return cachedCards;
        }

        List<JSONObject> cards = new ArrayList<>();
        try {
            // Read raw JSON from assets
            InputStream is = context.getAssets().open(FILE_NAME);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String jsonString = new String(buffer, StandardCharsets.UTF_8);

            // Parse root object -> "collections" array
            JSONObject root = new JSONObject(jsonString);
            JSONArray collections = root.getJSONArray("collections");

            java.util.Set<String> seen = new java.util.HashSet<>();

            for (int i = 0; i < collections.length(); i++) {
                JSONObject card = collections.getJSONObject(i);
                String season = card.optString("season", "");
                String cardClass = card.optString("class", "");
                String key = season + "_" + cardClass;

                // Chỉ load 1 thẻ duy nhất cho mỗi tổ hợp (Mùa + Class) theo yêu cầu Optimize Data
                if (!seen.contains(key)) {
                    seen.add(key);
                    cards.add(card);
                }
            }

            Log.d(TAG, "Loaded " + cards.size() + " unique class/season cards from " + FILE_NAME);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load " + FILE_NAME, e);
        }

        cachedCards = cards;
        return cards;
    }

    /**
     * Finds a card by its slug (e.g. "binary02-yeonji-333a").
     *
     * @param context Context for asset access
     * @param slug    The slug to search for
     * @return The matching JSONObject, or null if not found
     */
    public static JSONObject findBySlug(Context context, String slug) {
        for (JSONObject card : loadAllCards(context)) {
            if (slug.equals(card.optString("slug"))) {
                return card;
            }
        }
        return null;
    }

    /**
     * Finds a card by its index in the collections array.
     *
     * @param context  Context for asset access
     * @param position Index in the array
     * @return The JSONObject at that position, or null if out of bounds
     */
    public static JSONObject getCardAt(Context context, int position) {
        List<JSONObject> cards = loadAllCards(context);
        if (position >= 0 && position < cards.size()) {
            return cards.get(position);
        }
        return null;
    }

    /**
     * Returns the total number of cards available.
     */
    public static int getCardCount(Context context) {
        return loadAllCards(context).size();
    }

    /**
     * Clears the in-memory cache. Call if you need to reload from disk.
     */
    public static void clearCache() {
        cachedCards = null;
    }
}
