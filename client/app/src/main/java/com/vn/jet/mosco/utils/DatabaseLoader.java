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
        public String backImage;
        public int level;
        public int exp;
        public int upgradeLevel;
        public int ovr; // OVR từ Server (Server Truth)
        public String cardClass;
        public String member;
        public String season;
        public String collectionNo;
        public String slug;
        public String backgroundColor;
        public String textColor;
        public List<String> availableTags;
        public String dimension;

        public UserInventoryItem() {}

        public UserInventoryItem(Long id, String collectionId, String frontImage, String backImage, int level, int exp, int upgradeLevel, int ovr, 
                                 String cardClass, String member, String season, String collectionNo, String slug, String backgroundColor, String textColor, 
                                 List<String> availableTags, String dimension) {
            this.id = id;
            this.collectionId = collectionId;
            this.frontImage = frontImage;
            this.backImage = backImage;
            this.level = level;
            this.exp = exp;
            this.upgradeLevel = upgradeLevel;
            this.ovr = ovr;
            this.cardClass = cardClass;
            this.member = member;
            this.season = season;
            this.collectionNo = collectionNo;
            this.slug = slug;
            this.backgroundColor = backgroundColor;
            this.textColor = textColor;
            this.availableTags = availableTags;
            this.dimension = dimension;
        }

        /**
         * Chuyển đổi từ UserCard DTO sang Cache Item
         */
        public static UserInventoryItem fromUserCard(com.vn.jet.mosco.model.UserCard userCard) {
            return new UserInventoryItem(
                userCard.getId(),
                userCard.getCollectionId(),
                userCard.getFrontImage(),
                userCard.getBackImage(),
                userCard.getLevel(),
                userCard.getExp(),
                userCard.getUpgradeLevel(),
                userCard.getOvr(),
                userCard.getCardClass(),
                userCard.getMember(),
                userCard.getSeason(),
                userCard.getCollectionNo(),
                userCard.getSlug(),
                userCard.getBackgroundColor(),
                userCard.getTextColor(),
                userCard.getAvailableTags(),
                userCard.getDimension()
            );
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
                    // Dùng background thread để không block main thread khi map danh sách lớn
                    new Thread(() -> {
                        List<com.vn.jet.mosco.model.UserCard> userCards = response.body();
                        List<UserInventoryItem> cachedList = new ArrayList<>(userCards.size());
                        for (com.vn.jet.mosco.model.UserCard uc : userCards) {
                            cachedList.add(new UserInventoryItem(
                                    uc.getId(),
                                    uc.getCollectionId(),
                                    uc.getFrontImage(),
                                    uc.getBackImage(),
                                    uc.getLevel(),
                                    uc.getExp(),
                                    uc.getUpgradeLevel(),
                                    uc.getOvr(),
                                    uc.getCardClass(),
                                    uc.getMember(),
                                    uc.getSeason(),
                                    uc.getCollectionNo(),
                                    uc.getSlug(),
                                    uc.getBackgroundColor(),
                                    uc.getTextColor(),
                                    uc.getAvailableTags(),
                                    uc.getDimension()
                            ));
                        }
                        
                        // Optimize hash map for O(1) lookup
                        java.util.Map<String, JSONObject> tempMap = new java.util.HashMap<>(cachedList.size());
                        for (UserInventoryItem item : cachedList) {
                            tempMap.put(item.collectionId, convertToJSONObject(item));
                        }
                        
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            cachedUserInventory = cachedList;
                            cachedCollectionMap = tempMap;
                            saveInventoryToLocal(context, userId, cachedList);
                            notifyInventoryChanged();
                        });
                    }).start();
                } else {
                    notifyInventoryChanged();
                }
            }

            @Override
            public void onFailure(retrofit2.Call<List<com.vn.jet.mosco.model.UserCard>> call, Throwable t) {
                // Thử nạp từ cache cục bộ nếu server tèo
                if (cachedUserInventory == null) {
                    loadInventoryFromLocal(context, userId);
                }
                notifyInventoryChanged();
            }
        });
    }

    /**
     * Lưu Inventory vào file cục bộ để chống mất dữ liệu khi xóa memory.
     */
    public static void saveInventoryToLocal(Context context, Long userId, List<UserInventoryItem> items) {
        if (userId == null || items == null) return;
        new Thread(() -> {
            try {
                org.json.JSONArray array = new org.json.JSONArray();
                for (UserInventoryItem item : items) {
                    org.json.JSONObject obj = new org.json.JSONObject();
                    obj.put("id", item.id);
                    obj.put("collectionId", item.collectionId);
                    obj.put("frontImage", item.frontImage);
                    obj.put("backImage", item.backImage);
                    obj.put("level", item.level);
                    obj.put("exp", item.exp);
                    obj.put("upgradeLevel", item.upgradeLevel);
                    obj.put("ovr", item.ovr);
                    obj.put("cardClass", item.cardClass);
                    obj.put("member", item.member);
                    obj.put("season", item.season);
                    obj.put("collectionNo", item.collectionNo);
                    obj.put("slug", item.slug);
                    obj.put("backgroundColor", item.backgroundColor);
                    obj.put("textColor", item.textColor);
                    if (item.availableTags != null) {
                        obj.put("availableTags", new org.json.JSONArray(item.availableTags));
                    }
                    obj.put("dimension", item.dimension);
                    array.put(obj);
                }
                java.io.File file = new java.io.File(context.getFilesDir(), "inventory_cache_" + userId + ".json");
                java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
                fos.write(array.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                fos.close();
                Log.d(TAG, "Đã lưu Inventory vào bộ nhớ máy cho user: " + userId);
            } catch (Exception e) {
                Log.e(TAG, "Lỗi lưu Inventory local: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Nạp Inventory từ file cục bộ (Dùng khi vào app hoặc mất mạng).
     */
    public static void loadInventoryFromLocal(Context context, Long userId) {
        if (userId == null) return;
        try {
            java.io.File file = new java.io.File(context.getFilesDir(), "inventory_cache_" + userId + ".json");
            if (!file.exists()) return;

            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();

            String json = new String(data, java.nio.charset.StandardCharsets.UTF_8);
            org.json.JSONArray array = new org.json.JSONArray(json);
            List<UserInventoryItem> items = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                org.json.JSONObject obj = array.getJSONObject(i);
                
                List<String> tags = new ArrayList<>();
                if (obj.has("availableTags")) {
                    org.json.JSONArray tagArray = obj.getJSONArray("availableTags");
                    for (int j = 0; j < tagArray.length(); j++) tags.add(tagArray.getString(j));
                }

                items.add(new UserInventoryItem(
                        obj.getLong("id"),
                        obj.getString("collectionId"),
                        obj.getString("frontImage"),
                        obj.optString("backImage", ""),
                        obj.getInt("level"),
                        obj.getInt("exp"),
                        obj.getInt("upgradeLevel"),
                        obj.getInt("ovr"),
                        obj.optString("cardClass", ""),
                        obj.optString("member", ""),
                        obj.optString("season", ""),
                        obj.optString("collectionNo", ""),
                        obj.optString("slug", ""),
                        obj.optString("backgroundColor", "#FFFFFF"),
                        obj.optString("textColor", "#000000"),
                        tags,
                        obj.optString("dimension", "")
                ));
            }
            cachedUserInventory = items;
            cachedInventoryUserId = userId;
            
            // Build map for O(1) lookups
            cachedCollectionMap = new java.util.HashMap<>(items.size());
            for (UserInventoryItem item : items) {
                cachedCollectionMap.put(item.collectionId, convertToJSONObject(item));
            }
            
            Log.d(TAG, "Đã khôi phục " + items.size() + " thẻ từ bộ nhớ máy.");
        } catch (Exception e) {
            Log.e(TAG, "Lỗi nạp Inventory local: " + e.getMessage());
        }
    }

    public static List<JSONObject> loadAllCards(Context context) {
        if (cachedUserInventory == null) return new ArrayList<>();
        List<JSONObject> list = new ArrayList<>();
        for (UserInventoryItem item : cachedUserInventory) {
            list.add(convertToJSONObject(item));
        }
        return list;
    }

    public static List<JSONObject> loadEveryCard(Context context) {
        return loadAllCards(context);
    }

    /**
     * Finds a card by its unique ID from cachedUserInventory.
     * (Replaces local database.json lookup)
     */
    public static JSONObject findById(Context context, String id) {
        if (cachedCollectionMap != null && cachedCollectionMap.containsKey(id)) {
            return cachedCollectionMap.get(id);
        }
        
        if (cachedUserInventory == null) return null;
        for (UserInventoryItem item : cachedUserInventory) {
            if (item.collectionId != null && item.collectionId.equalsIgnoreCase(id)) {
                return convertToJSONObject(item);
            }
        }
        return null;
    }

    /**
     * Finds a card by its slug.
     */
    public static JSONObject findBySlug(Context context, String slug) {
        if (cachedUserInventory == null) return null;
        for (UserInventoryItem item : cachedUserInventory) {
            if (item.slug != null && item.slug.equalsIgnoreCase(slug)) {
                return convertToJSONObject(item);
            }
        }
        return null;
    }

    /**
     * Finds a card by its collectionId.
     */
    public static JSONObject findByCollectionId(Context context, String collectionId) {
        return findById(context, collectionId);
    }

    private static JSONObject convertToJSONObject(UserInventoryItem item) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("id", item.collectionId);
            obj.put("collectionId", item.collectionId);
            obj.put("frontImage", item.frontImage);
            obj.put("backImage", item.backImage);
            obj.put("class", item.cardClass);
            obj.put("member", item.member);
            obj.put("season", item.season);
            obj.put("collectionNo", item.collectionNo);
            obj.put("slug", item.slug);
            obj.put("backgroundColor", item.backgroundColor);
            obj.put("textColor", item.textColor);
            obj.put("dimension", item.dimension);
            if (item.availableTags != null) {
                obj.put("availableTags", new JSONArray(item.availableTags));
            }
            return obj;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Loads artist names from cached inventory.
     */
    public static List<String> loadArtistNames(Context context) {
        List<String> artistNames = new ArrayList<>();
        if (cachedUserInventory != null) {
            java.util.Set<String> set = new java.util.HashSet<>();
            for (UserInventoryItem item : cachedUserInventory) {
                if (item.member != null) set.add(item.member);
            }
            artistNames.addAll(set);
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
