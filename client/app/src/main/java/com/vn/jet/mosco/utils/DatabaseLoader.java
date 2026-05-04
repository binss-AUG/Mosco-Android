package com.vn.jet.mosco.utils;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
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
    private static final String INTERNAL_DB_NAME = "database_sync.json";
    private static final String PREF_DB_VERSION = "db_version_hash";

    // Cached list to avoid re-reading the 7MB file every time
    private static List<JSONObject> cachedCards = null;

    // Cached list of every card from database.json
    private static List<JSONObject> cachedAllCardsRaw = null;

    // Fast O(1) lookup maps
    private static java.util.Map<String, JSONObject> cachedMasterMap = null; // Map chứa TẤT CẢ thẻ từ database.json
    private static java.util.Map<String, JSONObject> cachedCollectionMap = null; // Map chứa thẻ người dùng đang có
    private static boolean isMasterDataLoading = false;
    private static boolean isMasterDataLoaded = false;

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
        public String status;

        public UserInventoryItem() {}

        public UserInventoryItem(Long id, String collectionId, String frontImage, String backImage, int level, int exp, int upgradeLevel, int ovr, 
                                 String cardClass, String member, String season, String collectionNo, String slug, String backgroundColor, String textColor, 
                                 List<String> availableTags, String dimension, String status) {
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
            this.status = status;
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
                userCard.getDimension(),
                userCard.getStatus()
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
        cachedCollectionMap = null; // Clear map lookup của inventory
        notifyInventoryChanged();
    }

    /**
     * Nạp Master Data (database.json) từ Assets ở Background Thread.
     * Giải quyết triệt để vấn đề đơ app 3-5s khi vào Album.
     */
    public static void initMasterData(Context context) {
        if (isMasterDataLoaded || isMasterDataLoading) return;
        isMasterDataLoading = true;
        
        new Thread(() -> {
            Log.d(TAG, "Starting Galactic Master Data Loading...");
            long startTime = System.currentTimeMillis();
            try {
                String json = loadJSONFromAsset(context, FILE_NAME);
                if (json != null) {
                    JSONObject root = new JSONObject(json);
                    JSONArray cardsArray = root.optJSONArray("collections");
                    if (cardsArray == null) cardsArray = root.optJSONArray("cards");

                    if (cardsArray != null) {
                        int len = cardsArray.length();
                        java.util.Map<String, JSONObject> tempMasterMap = new java.util.HashMap<>(len);
                        for (int i = 0; i < len; i++) {
                            JSONObject card = cardsArray.optJSONObject(i);
                            if (card != null) {
                                String id = card.optString("id");
                                if (id.isEmpty()) id = card.optString("collectionId");
                                if (!id.isEmpty()) tempMasterMap.put(id, card);
                            }
                        }
                        cachedMasterMap = tempMasterMap;
                        isMasterDataLoaded = true;
                        Log.d(TAG, "Master Data Loaded: " + len + " cards in " + (System.currentTimeMillis() - startTime) + "ms");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Lỗi nạp Master Data: " + e.getMessage());
            } finally {
                isMasterDataLoading = false;
            }
        }).start();
    }

    private static String loadJSONFromAsset(Context context, String fileName) {
        try {
            InputStream is;
            File internalFile = new File(context.getFilesDir(), INTERNAL_DB_NAME);
            
            if (internalFile.exists() && internalFile.length() > 0) {
                Log.d(TAG, "Loading Galactic Database from Internal Storage (Dynamic Sync)");
                is = new java.io.FileInputStream(internalFile);
            } else {
                Log.d(TAG, "Loading Galactic Database from Assets (Default)");
                is = context.getAssets().open(fileName);
            }

            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            return new String(buffer, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "Error reading database: " + fileName, e);
            return null;
        }
    }

    /**
     * Cập nhật file database mới từ server vào Internal Storage.
     */
    public static boolean updateInternalDatabase(Context context, String json, String versionHash) {
        try {
            File internalFile = new File(context.getFilesDir(), INTERNAL_DB_NAME);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(internalFile);
            fos.write(json.getBytes(StandardCharsets.UTF_8));
            fos.close();
            
            // Lưu lại hash version để lần sau không tải lại
            context.getSharedPreferences("mosco_db_prefs", Context.MODE_PRIVATE)
                    .edit().putString(PREF_DB_VERSION, versionHash).apply();
            
            Log.d(TAG, "Galactic Database Updated Successfully to version: " + versionHash);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to update internal database", e);
            return false;
        }
    }

    /**
     * Lấy version hiện tại đang lưu ở máy.
     */
    public static String getLocalDatabaseVersion(Context context) {
        return context.getSharedPreferences("mosco_db_prefs", Context.MODE_PRIVATE)
                .getString(PREF_DB_VERSION, "0");
    }

    /**
     * Nạp lại inventory từ Server.
     */
    public static void reloadInventoryFromServer(Context context, Long userId, com.vn.jet.mosco.network.GameApiService apiService) {
        if (userId == null || apiService == null) {
            notifyInventoryChanged();
            return;
        }

        cachedInventoryUserId = userId;
        Log.d(TAG, "Refreshing inventory from server for user: " + userId);

        apiService.getUserCards(userId).enqueue(new retrofit2.Callback<List<com.vn.jet.mosco.model.UserCard>>() {
            @Override
            public void onResponse(retrofit2.Call<List<com.vn.jet.mosco.model.UserCard>> call, retrofit2.Response<List<com.vn.jet.mosco.model.UserCard>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    new Thread(() -> {
                        List<com.vn.jet.mosco.model.UserCard> userCards = response.body();
                        List<UserInventoryItem> newList = new ArrayList<>(userCards.size());
                        java.util.Map<String, JSONObject> newMap = new java.util.HashMap<>(userCards.size());

                        for (com.vn.jet.mosco.model.UserCard uc : userCards) {
                            UserInventoryItem item = UserInventoryItem.fromUserCard(uc);
                            newList.add(item);
                            newMap.put(item.collectionId, convertToJSONObject(item));
                        }
                        
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            cachedUserInventory = newList;
                            cachedCollectionMap = newMap;
                            saveInventoryToLocal(context, userId, newList);
                            notifyInventoryChanged();
                            Log.d(TAG, "Inventory refreshed and cache updated: " + newList.size() + " items");
                        });
                    }).start();
                } else {
                    notifyInventoryChanged();
                }
            }

            @Override
            public void onFailure(retrofit2.Call<List<com.vn.jet.mosco.model.UserCard>> call, Throwable t) {
                if (cachedUserInventory == null) loadInventoryFromLocal(context, userId);
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
                    obj.put("status", item.status);
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
                        obj.optString("dimension", ""),
                        obj.optString("status", "AVAILABLE")
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
        if (cachedAllCardsRaw != null) return cachedAllCardsRaw;
        
        List<JSONObject> cards = new ArrayList<>();
        try {
            String json = loadJSONFromAsset(context, FILE_NAME);
            if (json != null) {
                JSONObject root = new JSONObject(json);
                JSONArray array = root.optJSONArray("collections");
                if (array == null) array = root.optJSONArray("cards");
                
                if (array != null) {
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject card = array.optJSONObject(i);
                        if (card != null) cards.add(card);
                    }
                }
                cachedAllCardsRaw = cards;
            }
        } catch (Exception e) {
            Log.e(TAG, "Lỗi loadEveryCard: " + e.getMessage());
        }
        return cards;
    }

    /**
     * Finds a card by its unique ID.
     * Cơ chế Hybrid: Tìm trong Master Data trước để lấy Metadata đầy đủ (backImage, artist...),
     * sau đó nếu thẻ có trong Inventory thì ghi đè các thông số level/ovr động.
     */
    public static JSONObject findById(Context context, String id) {
        if (id == null) return null;

        // 1. Lấy dữ liệu gốc từ Master Data (Để luôn có backImage, info gốc)
        JSONObject masterCard = null;
        if (cachedMasterMap != null && cachedMasterMap.containsKey(id)) {
            masterCard = cachedMasterMap.get(id);
        }

        // 2. Kiểm tra dữ liệu thực tế trong Inventory (Level, OVR hiện tại)
        JSONObject inventoryCard = null;
        if (cachedCollectionMap != null && cachedCollectionMap.containsKey(id)) {
            inventoryCard = cachedCollectionMap.get(id);
        }

        // Nếu không có Master, trả về Inventory (fallback)
        if (masterCard == null) return inventoryCard;
        
        // Nếu có cả hai, ta clone Master và merge thông tin Inventory vào
        if (inventoryCard != null) {
            try {
                // Ta dùng master làm gốc để hưởng backImage
                JSONObject merged = new JSONObject(masterCard.toString());
                merged.put("level", inventoryCard.optInt("level", 1));
                merged.put("ovr", inventoryCard.optInt("ovr", 0));
                merged.put("upgradeLevel", inventoryCard.optInt("upgradeLevel", 0));
                return merged;
            } catch (Exception e) {
                return inventoryCard;
            }
        }

        return masterCard;
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
        cachedMasterMap = null;
        cachedCollectionMap = null;
        isMasterDataLoaded = false;
        isMasterDataLoading = false;
    }
}
