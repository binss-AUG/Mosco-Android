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

    // Cached list of every card from database.json
    private static List<JSONObject> cachedAllCardsRaw = null;

    // Fast O(1) lookup maps
    private static java.util.Map<String, JSONObject> cachedCardMap = null;
    private static java.util.Map<String, JSONObject> cachedSlugMap = null;

    // The Global Cache for User's actual cards (To perform Instant Load on views)
    public static class UserInventoryItem {
        public Long id;
        public String collectionId;
        public String frontImage;
        public int level;
        public int exp;
        public int upgradeLevel;

        public UserInventoryItem(Long id, String collectionId, String frontImage, int level, int exp, int upgradeLevel) {
            this.id = id;
            this.collectionId = collectionId;
            this.frontImage = frontImage;
            this.level = level;
            this.exp = exp;
            this.upgradeLevel = upgradeLevel;
        }
    }
    public static List<UserInventoryItem> cachedUserInventory = null;

    public static void clearUserCache() {
        cachedUserInventory = null;
    }

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
            List<JSONObject> rawCards = loadEveryCard(context);

            java.util.Set<String> seen = new java.util.HashSet<>();

            for (JSONObject card : rawCards) {
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
     * Loads EVERY card from database.json without optimization/filtering.
     * Uses a robust buffer-based reading method for large files.
     */
    public static List<JSONObject> loadEveryCard(Context context) {
        if (cachedAllCardsRaw != null && !cachedAllCardsRaw.isEmpty()) {
            return cachedAllCardsRaw;
        }

        List<JSONObject> cards = new ArrayList<>();
        java.util.Map<String, JSONObject> cardMap = new java.util.HashMap<>();
        java.util.Map<String, JSONObject> slugMap = new java.util.HashMap<>();

        try {
            InputStream is = context.getAssets().open(FILE_NAME);
            int size = is.available();
            byte[] buffer = new byte[size];
            int bytesRead = 0;
            int totalRead = 0;
            while (totalRead < size && (bytesRead = is.read(buffer, totalRead, size - totalRead)) != -1) {
                totalRead += bytesRead;
            }
            is.close();
            
            String jsonString = new String(buffer, StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(jsonString);
            JSONArray collections = root.getJSONArray("collections");

            for (int i = 0; i < collections.length(); i++) {
                JSONObject card = collections.getJSONObject(i);
                cards.add(card);
                
                String id = card.optString("id");
                if (id != null && !id.isEmpty()) cardMap.put(id, card);
                
                String slug = card.optString("slug");
                if (slug != null && !slug.isEmpty()) slugMap.put(slug, card);
            }
            Log.d(TAG, "Successfully loaded " + cards.size() + " total cards from " + FILE_NAME);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load all cards from " + FILE_NAME, e);
        }
        cachedAllCardsRaw = cards;
        cachedCardMap = cardMap;
        cachedSlugMap = slugMap;
        return cards;
    }

    /**
     * Finds a card by its unique ID from database.json.
     */
    public static JSONObject findById(Context context, String id) {
        if (cachedCardMap == null) {
            loadEveryCard(context);
        }
        if (cachedCardMap != null) {
            return cachedCardMap.get(id);
        }
        return null;
    }

    /**
     * Finds a card by its slug (e.g. "binary02-yeonji-333a").
     *
     * @param context Context for asset access
     * @param slug    The slug to search for
     * @return The matching JSONObject, or null if not found
     */
    public static JSONObject findBySlug(Context context, String slug) {
        if (cachedSlugMap == null) {
            loadEveryCard(context);
        }
        if (cachedSlugMap != null) {
            return cachedSlugMap.get(slug);
        }
        return null;
    }

    /**
     * Loads artist names from assets/game_config.json.
     *
     * @param context Application or Activity context
     * @return List of artist names. Empty list on failure.
     */
    public static List<String> loadArtistNames(Context context) {
        List<String> artistNames = new ArrayList<>();
        try {
            InputStream is = context.getAssets().open("game_config.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String jsonString = new String(buffer, StandardCharsets.UTF_8);

            JSONObject root = new JSONObject(jsonString);
            JSONArray artists = root.getJSONArray("artists");

            for (int i = 0; i < artists.length(); i++) {
                JSONObject artist = artists.getJSONObject(i);
                String name = artist.optString("name", "");
                if (!name.isEmpty()) {
                    artistNames.add(name);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load artist names from game_config.json", e);
        }
        return artistNames;
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

    public static void clearCache() {
        cachedCards = null;
        cachedAllCardsRaw = null;
        cachedCardMap = null;
        cachedSlugMap = null;
    }
}
