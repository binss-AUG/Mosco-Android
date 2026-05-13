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
import com.vn.jet.mosco.database.MasterObjetDao;

/**
 * DatabaseLoader — Đọc và parse bộ sưu tập thẻ từ assets/database.json.
 * OVR do Server tính sẵn và trả về trong API, Client KHÔNG tính OVR.
 */
public class DatabaseLoader {

    private static final String TAG = "DatabaseLoader";
    private static final String FILE_NAME = "database.json";
    private static final String PREF_DB_VERSION = "db_version_hash";

    // Cached list to avoid re-reading the 7MB file every time
    private static List<JSONObject> cachedCards = null;

    // Cached list of every card from database.json
    private static List<JSONObject> cachedAllCardsRaw = null;

    // Fast O(1) lookup maps
    private static java.util.Map<String, JSONObject> cachedMasterMap = null; // Map chứa TẤT CẢ thẻ từ database.json
    public static java.util.Map<String, JSONObject> cachedCollectionMap = null; // Map chứa thẻ người dùng đang có
    private static volatile boolean isMasterDataLoading = false;
    private static volatile boolean isMasterDataLoaded = false;
    private static volatile boolean isRoomSyncing = false;

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
        public String createdAt;

        public UserInventoryItem() {
        }

        public UserInventoryItem(Long id, String collectionId, String frontImage, String backImage, int level, int exp,
                int upgradeLevel, int ovr,
                String cardClass, String member, String season, String collectionNo, String slug,
                String backgroundColor, String textColor,
                List<String> availableTags, String dimension, String status, String createdAt) {
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
            this.createdAt = createdAt;
        }

        /**
         * Chuyển đổi từ UserCard DTO sang Cache Item
         */
        public static UserInventoryItem fromUserCard(com.vn.jet.mosco.model.UserCard userCard) {
            // [UUID FIX] Ưu tiên dùng UUID từ Server, fallback về collectionId
            String bestId = userCard.getUuid() != null && !userCard.getUuid().isEmpty() ? 
                    userCard.getUuid() : userCard.getCollectionId();
            
            return new UserInventoryItem(
                    userCard.getId(),
                    bestId,
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
                    userCard.getStatus(),
                    userCard.getCreatedAt());
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
        if (isMasterDataLoaded || isMasterDataLoading)
            return;
        new Thread(() -> initMasterDataSync(context)).start();
    }

    public static void initMasterDataSync(Context context) {
        // Nếu đã load xong → không cần làm gì
        if (isMasterDataLoaded) return;

        // Nếu đang loading ở thread khác → đợi (tối đa 5s) thay vì return null
        if (isMasterDataLoading) {
            long waitStart = System.currentTimeMillis();
            while (isMasterDataLoading && (System.currentTimeMillis() - waitStart) < 5000) {
                try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            }
            return;
        }
        isMasterDataLoading = true;
        Log.d(TAG, "Starting Galactic Master Data Loading (Sync)...");
        long startTime = System.currentTimeMillis();
        try {
            String json = loadJSONFromAsset(context, FILE_NAME);
            if (json != null) {
                JSONObject root = new JSONObject(json);
                JSONArray cardsArray = root.optJSONArray("collections");
                if (cardsArray == null)
                    cardsArray = root.optJSONArray("cards");

                if (cardsArray != null) {
                    int len = cardsArray.length();
                    java.util.Map<String, JSONObject> tempMasterMap = new java.util.HashMap<>(len);
                    java.util.Set<String> uniqueUuids = new java.util.HashSet<>(len);

                    for (int i = 0; i < len; i++) {
                        JSONObject card = cardsArray.optJSONObject(i);
                        if (card != null) {
                            String uuid = card.optString("id");
                            String readableId = card.optString("collectionId");
                            
                            if (!uuid.isEmpty()) {
                                tempMasterMap.put(uuid, card);
                                uniqueUuids.add(uuid);
                            }
                            if (!readableId.isEmpty()) {
                                tempMasterMap.put(readableId, card);
                            }
                        }
                    }
                    // [FIX] Chỉ đánh dấu hoàn tất nạp Map sau khi đã xử lý xong list
                    cachedMasterMap = tempMasterMap;
                    isMasterDataLoaded = true;
                    Log.d(TAG, "Master Data Loaded: " + len + " cards indexed in "
                            + (System.currentTimeMillis() - startTime) + "ms");

                    // [PHASE 4/5] Room Metadata Persistence & Sync
                    com.vn.jet.mosco.database.AppDatabase db = com.vn.jet.mosco.database.AppDatabase.getInstance(context.getApplicationContext());
                    
                    // [OPTIMIZE] Tính toán jsonCount từ list duy nhất thay vì duyệt Map values (tránh lặp)
                    int jsonCount = uniqueUuids.size();
                    int currentCount = db.masterObjetDao().getCount();
                    
                    Log.d(TAG, "Sync Check: Room=" + currentCount + ", JSON=" + jsonCount);

                    // [RELIABILITY] Nếu Room trống hoặc lệch số lượng, ép đồng bộ lại
                    if (currentCount == 0 || currentCount != jsonCount) { 
                        isRoomSyncing = true;
                        Log.i(TAG, "🌀 Starting Room Database Sync (" + currentCount + " -> " + jsonCount + ")...");
                        try {
                            db.masterObjetDao().deleteAll();
                            
                            java.util.List<com.vn.jet.mosco.model.MasterObjetEntity> entities = new java.util.ArrayList<>();
                            for (String uuid : uniqueUuids) {
                                JSONObject card = tempMasterMap.get(uuid);
                                if (card == null) continue;

                                com.vn.jet.mosco.model.MasterObjetEntity entity = new com.vn.jet.mosco.model.MasterObjetEntity();
                                // Ưu tiên dùng id (UUID) làm định danh chính nếu có thể, hoặc dùng collectionId
                                String colId = card.optString("collectionId");
                                if (colId.isEmpty()) colId = uuid;
                                
                                entity.setCollectionId(colId);
                                entity.setMemberName(card.optString("member"));
                                entity.setSeasonName(card.optString("season"));
                                entity.setRarityClass(card.optString("class"));
                                entity.setFrontImageId(card.optString("frontImage"));
                                entity.setBackImageId(card.optString("backImage"));
                                entity.setBaseOvr(card.optInt("ovr"));
                                entities.add(entity);
                            }
                            db.masterObjetDao().insertAll(entities);
                            Log.d(TAG, "✅ Room Sync Complete: " + entities.size() + " objects saved.");
                        } catch (Exception e) {
                            Log.e(TAG, "❌ Error syncing Master Data to Room: " + e.getMessage());
                        } finally {
                            isRoomSyncing = false;
                        }
                    } else {
                        Log.d(TAG, "✅ Room Database is already in sync with JSON.");
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Lỗi nạp Master Data: " + e.getMessage());
            isMasterDataLoaded = false; // Reset nếu lỗi nặng
        } finally {
            isMasterDataLoading = false;
        }
    }

    /**
     * [QUIET LUXURY] Helper: Mapping class từ UI sang database key.
     * Tránh lặp lại code ở Fragment.
     */
    public static String mapClassToTypeKey(String cardClass) {
        if (cardClass == null) return "First";
        String key = cardClass.trim();

        if (key.equalsIgnoreCase("Welcome")) return "Welcome";
        if (key.equalsIgnoreCase("First")) return "First";
        if (key.equalsIgnoreCase("Double")) return "Double";
        if (key.equalsIgnoreCase("Premier")) return "Premier";
        if (key.equalsIgnoreCase("Special")) return "Special";
        if (key.equalsIgnoreCase("Unit")) return "Unit";

        // Hỗ trợ hạ cấp các kiểu cũ (Legacy support)
        if (key.contains("Welcome")) return "Welcome";
        if (key.contains("Unit")) return "Unit";
        if (key.equalsIgnoreCase("SpecialUnit")) return "Special";
        if (key.equalsIgnoreCase("FirstWelcome")) return "First";

        return "First";
    }

    /**
     * Ranking class để sort (Premier > Special/Unit > Double > First/Welcome)
     */
    public static int getCardClassRank(String cardClass) {
        if (cardClass == null) return 0;
        String key = mapClassToTypeKey(cardClass).toLowerCase();
        if (key.equals("premier")) return 4;
        if (key.equals("special") || key.equals("unit")) return 3;
        if (key.equals("double")) return 2;
        if (key.equals("first") || key.equals("welcome")) return 1;
        return 0;
    }

    public static boolean isStatus(String f) {
        if (f == null) return false;
        String lower = f.toLowerCase();
        return java.util.Arrays.asList("tất cả", "đã sở hữu", "chưa sở hữu", "all", "owned", "missing").contains(lower);
    }

    public static boolean isArtist(String f) {
        if (f == null) return false;
        // Kiểm tra xem f có nằm trong danh sách Artist chính thức không
        for (String artist : AppConfig.OFFICIAL_ARTISTS) {
            if (artist.equalsIgnoreCase(f)) return true;
        }
        return false;
    }

    public static boolean isClass(String f) {
        if (f == null) return false;
        return java.util.Arrays.asList("First", "Welcome", "Double", "Premier", "Special", "Unit", "SpecialUnit").contains(f);
    }

    private static String loadJSONFromAsset(Context context, String fileName) {
        try {
            InputStream is;
            File internalFile = new File(context.getFilesDir(), FILE_NAME);

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

    public interface SyncCallback {
        void onUpdateAvailable(long remoteTimestamp, float sizeMb);
        void onNoUpdate();
        void onProgress(int percent);
        void onComplete();
        void onError(String error);
    }

    /**
     * Kiểm tra phiên bản dữ liệu với Server và đồng bộ nếu có bản mới.
     * [PHASE 5] Galactic Sync Mechanism - Now with UI Callback support.
     */
    public static void syncMetadataWithServer(Context context, SyncCallback callback) {
        if (context == null) return;
        
        final Context appContext = context.getApplicationContext();
        Log.d(TAG, "🚀 Checking for Galactic Metadata updates from Server...");

        com.vn.jet.mosco.network.GameApiService apiService = 
                com.vn.jet.mosco.network.ApiClient.getClient(appContext).create(com.vn.jet.mosco.network.GameApiService.class);

        apiService.getAssetManifest().enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(retrofit2.Call<okhttp3.ResponseBody> call, retrofit2.Response<okhttp3.ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String manifestStr = response.body().string();
                        org.json.JSONObject manifest = new org.json.JSONObject(manifestStr);
                        long remoteSyncTime = manifest.optLong("lastSync", 0);
                        
                        android.content.SharedPreferences prefs = appContext.getSharedPreferences("mosco_db_prefs", Context.MODE_PRIVATE);
                        long localSyncTime = prefs.getLong("last_sync_timestamp", 0);

                        if (remoteSyncTime > localSyncTime || localSyncTime == 0) {
                            // Giả định metadata JSON nặng khoảng 10MB chưa nén, nén xong còn 2MB.
                            // Thực tế ta có thể lấy size từ manifest nếu cần.
                            float sizeMb = 2.0f; 
                            Log.i(TAG, "✨ New metadata detected. Notifying UI callback...");
                            if (callback != null) callback.onUpdateAvailable(remoteSyncTime, sizeMb);
                        } else {
                            Log.d(TAG, "✅ Galactic Metadata is already up to date.");
                            if (callback != null) callback.onNoUpdate();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing manifest: " + e.getMessage());
                        if (callback != null) callback.onNoUpdate();
                    }
                } else {
                    if (callback != null) callback.onNoUpdate();
                }
            }

            @Override
            public void onFailure(retrofit2.Call<okhttp3.ResponseBody> call, Throwable t) {
                Log.w(TAG, "Could not reach Server for Metadata sync. Using local cache.");
                if (callback != null) callback.onNoUpdate();
            }
        });
    }

    public static void pullFullDatabase(Context appContext, long newTimestamp, SyncCallback callback) {
        com.vn.jet.mosco.network.GameApiService apiService = 
                com.vn.jet.mosco.network.ApiClient.getClient(appContext).create(com.vn.jet.mosco.network.GameApiService.class);

        // [CACHE BUSTER] Thêm timestamp vào query để ép tải bản mới nhất từ Cloudflare/Server
        String cacheBuster = "t=" + System.currentTimeMillis();
        apiService.getFullDatabase(cacheBuster).enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(retrofit2.Call<okhttp3.ResponseBody> call, retrofit2.Response<okhttp3.ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    new Thread(() -> {
                        try {
                            if (callback != null) callback.onProgress(20);
                            String json = response.body().string();
                            if (callback != null) callback.onProgress(60);

                            if (json.length() < 100) {
                                if (callback != null) callback.onError("Invalid database JSON");
                                return;
                            }

                            // 1. Lưu vào Internal Storage
                            boolean success = updateInternalDatabase(appContext, json, String.valueOf(newTimestamp));
                            
                            if (success) {
                                // 2. Reload Master Data Cache (IMPORTANT: Clear cache before reload)
                                clearCache();
                                isMasterDataLoaded = false;
                                
                                // [CRITICAL] Chờ Room Sync hoàn tất trước khi báo Complete
                                new Thread(() -> {
                                    initMasterDataSync(appContext);
                                    long waitStart = System.currentTimeMillis();
                                    while (isRoomSyncing && (System.currentTimeMillis() - waitStart) < 10000) {
                                        try { Thread.sleep(200); } catch (Exception ignored) {}
                                    }
                                    
                                    // 3. Cập nhật timestamp (Lưu sau khi đã load xong data mới để đảm bảo tính nhất quán)
                                    appContext.getSharedPreferences("mosco_db_prefs", Context.MODE_PRIVATE)
                                            .edit().putLong("last_sync_timestamp", newTimestamp).apply();

                                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                        if (callback != null) {
                                            callback.onProgress(100);
                                            callback.onComplete();
                                        }
                                        notifyInventoryChanged();
                                    });
                                }).start();
                            } else {
                                if (callback != null) callback.onError("Failed to save local database");
                            }
                        } catch (Exception e) {
                            if (callback != null) callback.onError(e.getMessage());
                        }
                    }).start();
                } else {
                    if (callback != null) callback.onError("Server response error");
                }
            }

            @Override
            public void onFailure(retrofit2.Call<okhttp3.ResponseBody> call, Throwable t) {
                if (callback != null) callback.onError(t.getMessage());
            }
        });
    }

    /**
     * Cập nhật file database mới từ server vào Internal Storage.
     */
    public static boolean updateInternalDatabase(Context context, String json, String versionHash) {
        try {
            File internalFile = new File(context.getFilesDir(), FILE_NAME);
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
    public static void reloadInventoryFromServer(Context context, Long userId,
            com.vn.jet.mosco.network.GameApiService apiService) {
        if (userId == null || apiService == null) {
            notifyInventoryChanged();
            return;
        }

        cachedInventoryUserId = userId;
        Log.d(TAG, "Refreshing inventory from server for user: " + userId);

        apiService.getUserCards(userId).enqueue(new retrofit2.Callback<List<com.vn.jet.mosco.model.UserCard>>() {
            @Override
            public void onResponse(retrofit2.Call<List<com.vn.jet.mosco.model.UserCard>> call,
                    retrofit2.Response<List<com.vn.jet.mosco.model.UserCard>> response) {
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
                if (cachedUserInventory == null)
                    loadInventoryFromLocal(context, userId);
                notifyInventoryChanged();
            }
        });
    }

    /**
     * Lưu Inventory vào file cục bộ để chống mất dữ liệu khi xóa memory.
     */
    public static void saveInventoryToLocal(Context context, Long userId, List<UserInventoryItem> items) {
        if (userId == null || items == null)
            return;
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
        if (userId == null)
            return;
        try {
            java.io.File file = new java.io.File(context.getFilesDir(), "inventory_cache_" + userId + ".json");
            if (!file.exists())
                return;

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
                    for (int j = 0; j < tagArray.length(); j++)
                        tags.add(tagArray.getString(j));
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
                        obj.optString("status", "AVAILABLE"),
                        obj.optString("createdAt", "")));
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
        if (cachedUserInventory == null)
            return new ArrayList<>();
        List<JSONObject> list = new ArrayList<>();
        for (UserInventoryItem item : cachedUserInventory) {
            list.add(convertToJSONObject(item));
        }
        return list;
    }

    public static List<JSONObject> loadEveryCard(Context context) {
        if (cachedAllCardsRaw != null)
            return cachedAllCardsRaw;

        List<JSONObject> cards = new ArrayList<>();
        try {
            String json = loadJSONFromAsset(context, FILE_NAME);
            if (json != null) {
                JSONObject root = new JSONObject(json);
                JSONArray array = root.optJSONArray("collections");
                if (array == null)
                    array = root.optJSONArray("cards");

                if (array != null) {
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject card = array.optJSONObject(i);
                        if (card != null)
                            cards.add(card);
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
     * Cơ chế Hybrid: Tìm trong Master Data trước để lấy Metadata đầy đủ (backImage,
     * artist...),
     * sau đó nếu thẻ có trong Inventory thì ghi đè các thông số level/ovr động.
     */
    public static JSONObject findById(Context context, String id) {
        if (id == null)
            return null;

        // 1. Lấy dữ liệu gốc từ Master Data (Để luôn có backImage, info gốc)
        JSONObject masterCard = null;
        if (cachedMasterMap != null && cachedMasterMap.containsKey(id)) {
            masterCard = cachedMasterMap.get(id);
        }

        // [PHASE 4] Room Fallback
        if (masterCard == null && context != null) {
            try {
                com.vn.jet.mosco.model.MasterObjetEntity entity = com.vn.jet.mosco.database.AppDatabase.getInstance(context)
                        .masterObjetDao().findById(id);
                if (entity != null) {
                    masterCard = new JSONObject();
                    masterCard.put("id", id);
                    masterCard.put("collectionId", entity.getCollectionId());
                    masterCard.put("member", entity.getMemberName());
                    masterCard.put("season", entity.getSeasonName());
                    masterCard.put("class", entity.getRarityClass());
                    masterCard.put("frontImage", entity.getFrontImageId());
                    masterCard.put("backImage", entity.getBackImageId());
                    masterCard.put("ovr", entity.getBaseOvr());
                    
                    // Cập nhật lại Map để lần sau truy cập nhanh hơn
                    if (cachedMasterMap != null) {
                        cachedMasterMap.put(id, masterCard);
                    }
                }
            } catch (Exception ignored) {}
        }

        // 2. Kiểm tra dữ liệu thực tế trong Inventory (Level, OVR hiện tại)
        JSONObject inventoryCard = null;
        if (cachedCollectionMap != null && cachedCollectionMap.containsKey(id)) {
            inventoryCard = cachedCollectionMap.get(id);
        }

        // Nếu không có Master, trả về Inventory (fallback)
        if (masterCard == null)
            return inventoryCard;

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
        if (cachedUserInventory == null)
            return null;
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
            obj.put("name", item.member); // Fallback to member name
            obj.put("collectionNo", item.collectionNo);
            obj.put("upgradeLevel", item.upgradeLevel);
            obj.put("slug", item.slug);
            obj.put("backgroundColor", item.backgroundColor);
            obj.put("textColor", item.textColor);
            obj.put("dimension", item.dimension);
            obj.put("status", item.status);
            obj.put("createdAt", item.createdAt);
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
                if (item.member != null)
                    set.add(item.member);
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

    /**
     * Lấy danh sách thành viên kèm ảnh đại diện (ảnh thẻ mới nhất) để lọc
     */
    public static List<MemberFilterItem> getUniqueMembers(Context context) {
        // [QUIET LUXURY] Đảm bảo Master Data đã được nạp trước khi lấy danh sách
        if (!isMasterDataLoaded) {
            initMasterDataSync(context);
        }

        // [WAIT] Nếu đang đồng bộ DB thì đợi một chút (max 10s)
        if (isRoomSyncing) {
            Log.d(TAG, "getUniqueMembers: Waiting for Room Sync...");
            long waitStart = System.currentTimeMillis();
            while (isRoomSyncing && (System.currentTimeMillis() - waitStart) < 10000) {
                try { Thread.sleep(200); } catch (Exception ignored) {}
            }
        }
        
        List<MemberFilterItem> items = new ArrayList<>();
        try {
            List<MasterObjetDao.MemberAvatar> avatars = com.vn.jet.mosco.database.AppDatabase.getInstance(context)
                    .masterObjetDao().getUniqueMembers();
            
            // [FALLBACK 1] Nếu Room rỗng, lấy từ cachedMasterMap (Memory Truth)
            if ((avatars == null || avatars.isEmpty()) && cachedMasterMap != null) {
                Log.w(TAG, "getUniqueMembers: Room is empty, falling back to Master Map...");
                java.util.Set<String> members = new java.util.HashSet<>();
                // Lấy ra list card duy nhất để tránh duyệt trùng lặp gây chậm
                java.util.Collection<JSONObject> allCards = cachedMasterMap.values();
                for (JSONObject card : allCards) {
                    String m = card.optString("member");
                    if (!m.isEmpty() && members.add(m)) {
                        // Chỉ thêm nếu là Artist chính thức (tránh rác từ database)
                        if (isArtist(m)) {
                            items.add(new MemberFilterItem(m, card.optString("frontImage")));
                        }
                    }
                }
            } else if (avatars != null) {
                for (MasterObjetDao.MemberAvatar avatar : avatars) {
                    if (isArtist(avatar.memberName)) {
                        items.add(new MemberFilterItem(avatar.memberName, avatar.frontImageId));
                    }
                }
            }

            // [SORT] Sắp xếp theo thứ tự S1-S24 định nghĩa trong AppConfig
            java.util.Collections.sort(items, (o1, o2) -> {
                int index1 = -1, index2 = -1;
                for (int i = 0; i < AppConfig.OFFICIAL_ARTISTS.size(); i++) {
                    if (AppConfig.OFFICIAL_ARTISTS.get(i).equalsIgnoreCase(o1.name)) index1 = i;
                    if (AppConfig.OFFICIAL_ARTISTS.get(i).equalsIgnoreCase(o2.name)) index2 = i;
                }
                return Integer.compare(index1, index2);
            });

            return items;
        } catch (Exception e) {
            Log.e(TAG, "Error getting unique members: " + e.getMessage());
            return items;
        }
    }

    /**
     * Lấy danh sách tất cả các mùa thẻ hiện có trong Database
     */
    public static List<String> getUniqueSeasons(Context context) {
        if (!isMasterDataLoaded) {
            initMasterDataSync(context);
        }

        if (isRoomSyncing) {
            Log.d(TAG, "getUniqueSeasons: Waiting for Room Sync...");
            long waitStart = System.currentTimeMillis();
            while (isRoomSyncing && (System.currentTimeMillis() - waitStart) < 10000) {
                try { Thread.sleep(200); } catch (Exception ignored) {}
            }
        }
        try {
            List<String> results = com.vn.jet.mosco.database.AppDatabase.getInstance(context)
                    .masterObjetDao().getUniqueSeasons();
            
            if ((results == null || results.isEmpty()) && cachedMasterMap != null) {
                java.util.Set<String> seasons = new java.util.HashSet<>();
                for (JSONObject card : cachedMasterMap.values()) {
                    String s = card.optString("season");
                    if (!s.isEmpty()) seasons.add(s);
                }
                results = new ArrayList<>(seasons);
            }
            
            return (results != null) ? results : new ArrayList<>();
        } catch (Exception e) {
            Log.e(TAG, "Error getting unique seasons: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Lấy danh sách tất cả các Class thẻ hiện có (đã lọc Whitelist từ Server)
     */
    public static List<String> getUniqueClasses(Context context) {
        if (!isMasterDataLoaded) {
            initMasterDataSync(context);
        }

        if (isRoomSyncing) {
            Log.d(TAG, "getUniqueClasses: Waiting for Room Sync...");
            long waitStart = System.currentTimeMillis();
            while (isRoomSyncing && (System.currentTimeMillis() - waitStart) < 10000) {
                try { Thread.sleep(200); } catch (Exception ignored) {}
            }
        }
        try {
            List<String> results = com.vn.jet.mosco.database.AppDatabase.getInstance(context)
                    .masterObjetDao().getUniqueClasses();
            
            if ((results == null || results.isEmpty()) && cachedMasterMap != null) {
                java.util.Set<String> classes = new java.util.HashSet<>();
                for (JSONObject card : cachedMasterMap.values()) {
                    String c = card.optString("class");
                    if (!c.isEmpty()) classes.add(c);
                }
                results = new ArrayList<>(classes);
            }

            return (results != null) ? results : new ArrayList<>();
        } catch (Exception e) {
            Log.e(TAG, "Error getting unique classes: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public static class MemberFilterItem {
        public String name;
        public String imageUrl;

        public MemberFilterItem(String name, String imageUrl) {
            this.name = name;
            this.imageUrl = imageUrl;
        }
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
