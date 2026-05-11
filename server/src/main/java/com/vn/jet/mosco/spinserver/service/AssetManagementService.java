package com.vn.jet.mosco.spinserver.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Service quản lý Tài nguyên Metadata (Lean Version).
 * Đã loại bỏ cơ chế Bundling và Image Sync để tiết kiệm 10GB dung lượng.
 */
@Service
public class AssetManagementService {

    private static final Logger log = LoggerFactory.getLogger(AssetManagementService.class);
    private final OkHttpClient client;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final String dataDir;
    private final String databaseJson;
    private final String manifestJson;
    private final String apiUrl;

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private volatile String syncStatus = "IDLE";
    private volatile String syncDetail = "";

    public AssetManagementService(
            @org.springframework.beans.factory.annotation.Value("${ASSET_DATA_DIR:data/assets/}") String dataDir,
            @org.springframework.beans.factory.annotation.Value("${OBJEKT_API_URL:https://objekt.top/api/collection?artist=tripleS}") String apiUrl) {

        this.dataDir = dataDir;
        this.databaseJson = dataDir + "database.json";
        this.manifestJson = dataDir + "manifest.json";
        this.apiUrl = apiUrl;

        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        try {
            Files.createDirectories(Paths.get(dataDir));
        } catch (IOException e) {
            log.error("Không thể khởi tạo thư mục dữ liệu: {}", e.getMessage());
        }
    }

    /**
     * Tự động chạy ngay khi Server vừa khởi động xong
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("🏠 [STARTUP] Server đã sẵn sàng. Khởi chạy đồng bộ Metadata lần đầu...");
        fullSyncProcess();
    }

    @Scheduled(cron = "0 0 * * * *")
    public void scheduledSync() {
        log.info("🚀 [AUTO] Bắt đầu chu kỳ cập nhật Metadata hàng giờ...");
        fullSyncProcess();
    }

    public void fullSyncProcess() {
        if (!"IDLE".equals(syncStatus)) {
            log.warn("⚠️ Đang có tiến trình đồng bộ khác chạy, bỏ qua lần này.");
            return;
        }

        try {
            syncStatus = "SCRAPING";
            syncDetail = "Đang cào metadata mới nhất từ objekt.top...";

            String jsonContent = fetchLatestMetadata();
            if (jsonContent == null) {
                syncStatus = "IDLE";
                return;
            }

            List<JsonObject> sortedCollections = parseAndSort(jsonContent);
            log.info("✅ Đã cào xong {} Objet. Cập nhật file database.json...", sortedCollections.size());

            saveSortedDatabase(jsonContent, sortedCollections);

            // Cập nhật Manifest cơ bản (chỉ chứa metadata info)
            generateManifest(sortedCollections.size());

            syncStatus = "IDLE";
            syncDetail = "Cập nhật Metadata hoàn tất lúc " + java.time.LocalDateTime.now().toString();
            log.info("🎉 Cập nhật Metadata thành công!");

        } catch (Exception e) {
            log.error("❌ Lỗi trong quá trình đồng bộ: {}", e.getMessage());
            syncStatus = "IDLE";
            syncDetail = "Lỗi: " + e.getMessage();
        }
    }

    private String fetchLatestMetadata() {
        Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("Server API từ chối: {}", response.code());
                return null;
            }
            return response.body().string();
        } catch (IOException e) {
            log.error("Lỗi kết nối tới objekt.top: {}", e.getMessage());
            return null;
        }
    }

    private List<JsonObject> parseAndSort(String jsonContent) {
        JsonObject data = gson.fromJson(jsonContent, JsonObject.class);
        JsonArray collectionsArray = data.has("collections") ? data.getAsJsonArray("collections") : new JsonArray();

        List<JsonObject> collections = new ArrayList<>();
        for (JsonElement e : collectionsArray) collections.add(e.getAsJsonObject());

        collections.sort((o1, o2) -> {
            String date1 = o1.get("createdAt").getAsString();
            String date2 = o2.get("createdAt").getAsString();
            return date2.compareTo(date1);
        });

        return collections;
    }

    private void saveSortedDatabase(String originalJson, List<JsonObject> sortedCollections) throws IOException {
        JsonObject data = gson.fromJson(originalJson, JsonObject.class);
        JsonArray sortedArray = new JsonArray();
        for (JsonObject obj : sortedCollections) sortedArray.add(obj);
        data.add("collections", sortedArray);
        Files.writeString(Paths.get(databaseJson), gson.toJson(data));
    }

    private void generateManifest(int totalObjet) {
        JsonObject manifest = new JsonObject();
        manifest.addProperty("lastSync", System.currentTimeMillis());
        manifest.addProperty("totalObjet", totalObjet);
        // SealedBundles và Patches để trống vì đã loại bỏ cơ chế này
        manifest.add("sealedBundles", new JsonArray());
        manifest.add("patches", new JsonArray());

        try {
            Files.writeString(Paths.get(manifestJson), gson.toJson(manifest));
        } catch (IOException e) {
            log.error("Lỗi tạo manifest: {}", e.getMessage());
        }
    }

    public String getSyncStatus() { return syncStatus; }
    public String getSyncDetail() { return syncDetail; }
    public String getManifestPath() { return manifestJson; }
    public String getJsonPath() { return databaseJson; }
}
