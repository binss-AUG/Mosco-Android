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
 * DatabaseLoader — Đọc và parse bộ sưu tập thẻ từ assets/database.json.
 * OVR do Server tính sẵn và trả về trong API, Client KHÔNG tính OVR.
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
    private static java.util.Map<String, JSONObject> cachedCollectionMap = null;

    // The Global Cache for User's actual cards (To perform Instant Load on views)
    public static class UserInventoryItem {
        public Long id;
        public String collectionId;
        public String frontImage;
        public int level;
        public int exp;
        public int upgradeLevel;
        public int ovr; // OVR từ Server (Server Truth)

        public UserInventoryItem(Long id, String collectionId, String frontImage, int level, int exp, int upgradeLevel, int ovr) {
            this.id = id;
            this.collectionId = collectionId;
            this.frontImage = frontImage;
            this.level = level;
            this.exp = exp;
            this.upgradeLevel = upgradeLevel;
            this.ovr = ovr;
        }
    }
    public static List<UserInventoryItem> cachedUserInventory = null;
    public static Long cachedInventoryUserId = null; // ID của người dùng sở hữu cache hiện tại

    public interface OnInventoryChangeListener {
        void onInventoryChanged();
    }
    private static final List<OnInventoryChangeListener> inventoryChangeListeners = new ArrayList<>();

    public static void registerInventoryChangeListener(OnInventoryChangeListener listener) {
        if (!inventoryChangeListeners.contains(listener)) {
            inventoryChangeListeners.add(listener);
        }
    }

    public static void unregisterInventoryChangeListener(OnInventoryChangeListener listener) {
        inventoryChangeListeners.remove(listener);
    }

    private static void notifyInventoryChanged() {
        for (OnInventoryChangeListener listener : new ArrayList<>(inventoryChangeListeners)) {
            listener.onInventoryChanged();
        }
    }

    /**
     * Galactic Data Purge: Xóa sạch bộ nhớ tạm của User cũ để đón User mới.
     * Đảm bảo không có dữ liệu rác "râu ông nọ cắm cằm bà kia".
     */
    public static void clearUserCache() {
        Log.d(TAG, "Executing Galactic Cache Purge for user: " + cachedInventoryUserId);
        cachedUserInventory = null;
        cachedInventoryUserId = null;
        notifyInventoryChanged();
    }

    /**
     * Nạp lại inventory từ Server.
     * OVR lấy trực tiếp từ API response (Server Truth), Client KHÔNG tính.
     */
    public static void reloadInventoryFromServer(Context context, Long userId, com.vn.jet.mosco.network.GameApiService apiService) {
        if (userId == null || apiService == null) {
            notifyInventoryChanged();
            return;
        }

        // Nếu tải cho user mới, xóa cache cũ ngay lập tức
        if (cachedInventoryUserId != null && !cachedInventoryUserId.equals(userId)) {
            cachedUserInventory = null;
        }
        cachedInventoryUserId = userId;

        apiService.getUserCards(userId).enqueue(new retrofit2.Callback<List<com.vn.jet.mosco.model.UserCard>>() {
            @Override
            public void onResponse(retrofit2.Call<List<com.vn.jet.mosco.model.UserCard>> call, retrofit2.Response<List<com.vn.jet.mosco.model.UserCard>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<com.vn.jet.mosco.model.UserCard> userCards = response.body();
                    List<UserInventoryItem> cachedList = new ArrayList<>();
                    for (com.vn.jet.mosco.model.UserCard userCard : userCards) {
                        org.json.JSONObject cardJson = findById(context, userCard.getCollectionId());
                        if (cardJson != null) {
                            String img = cardJson.optString("frontImage", "");
                            // OVR trực tiếp từ Server — Single Source of Truth
                            int ovr = userCard.getOvr();
                            cachedList.add(new UserInventoryItem(userCard.getId(), userCard.getCollectionId(), img, userCard.getLevel(), userCard.getExp(), userCard.getUpgradeLevel(), ovr));
                        }
                    }
                    cachedUserInventory = cachedList;
                }
                notifyInventoryChanged();
            }

            @Override
            public void onFailure(retrofit2.Call<List<com.vn.jet.mosco.model.UserCard>> call, Throwable t) {
                notifyInventoryChanged();
            }
        });
    }

    /**
     * Loads all card entries from assets/database.json.
     * Results are cached in memory after the first load.
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
     */
    public static List<JSONObject> loadEveryCard(Context context) {
        if (cachedAllCardsRaw != null && !cachedAllCardsRaw.isEmpty()) {
            return cachedAllCardsRaw;
        }

        List<JSONObject> cards = new ArrayList<>();
        java.util.Map<String, JSONObject> cardMap = new java.util.HashMap<>();
        java.util.Map<String, JSONObject> slugMap = new java.util.HashMap<>();
        java.util.Map<String, JSONObject> collectionMap = new java.util.HashMap<>();

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

                String collectionId = card.optString("collectionId");
                if (collectionId != null && !collectionId.isEmpty()) collectionMap.put(collectionId, card);
            }
            Log.d(TAG, "Successfully loaded " + cards.size() + " total cards from " + FILE_NAME);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load all cards from " + FILE_NAME, e);
        }
        cachedAllCardsRaw = cards;
        cachedCardMap = cardMap;
        cachedSlugMap = slugMap;
        cachedCollectionMap = collectionMap;
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
     * Finds a card by its slug.
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
     * Finds a card by its collectionId (e.g. "Binary02 JiYeon 503Z").
     */
    public static JSONObject findByCollectionId(Context context, String collectionId) {
        if (cachedCollectionMap == null) {
            loadEveryCard(context);
        }
        if (cachedCollectionMap != null) {
            return cachedCollectionMap.get(collectionId);
        }
        return null;
    }

    /**
     * Loads artist names from assets/game_config.json.
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
