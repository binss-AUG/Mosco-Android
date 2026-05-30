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
    private final EtlService etlService;
    private final CardDataService cardDataService;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final String dataDir;
    private final String databaseJson;
    private final String manifestJson;
    private final String apiUrl;

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private volatile String syncStatus = "IDLE";
    private volatile String syncDetail = "";

    public AssetManagementService(
            EtlService etlService,
            CardDataService cardDataService,
            @org.springframework.beans.factory.annotation.Value("${ASSET_DATA_DIR:data/assets/}") String dataDir,
            @org.springframework.beans.factory.annotation.Value("${OBJEKT_API_URL:https://objekt.top/api/collection?artist=tripleS&limit=20000}") String apiUrl) {

        this.etlService = etlService;
        this.cardDataService = cardDataService;
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
            log.error("Failed to initialize asset data directory: {}", e.getMessage());
        }
    }

    /**
     * Tự động chạy ngay khi Server vừa khởi động xong
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("[STARTUP] Server is ready. Initializing metadata sync...");
        fullSyncProcess();
    }

    @Scheduled(cron = "0 0 * * * *")
    public void scheduledSync() {
        log.info("[AUTO] Starting hourly metadata sync cycle...");
        fullSyncProcess();
    }

    public void fullSyncProcess() {
        if (!"IDLE".equals(syncStatus)) {
            log.warn("Another sync process is already running, skipping this cycle.");
            return;
        }

        try {
            syncStatus = "SCRAPING";
            syncDetail = "Scraping latest metadata from objekt.top...";

            String jsonContent = fetchLatestMetadata();
            if (jsonContent == null) {
                syncStatus = "IDLE";
                return;
            }

            List<JsonObject> allCollections = parseAndSort(jsonContent);
            
            // Giữ lại toàn bộ danh sách cào được để người dùng có đầy đủ bộ sưu tập,
            // không lọc bỏ theo bất kỳ Class hay Artist nào nhằm đảm bảo đồng bộ 100% dữ liệu gốc.
            List<JsonObject> filteredCollections = allCollections;

            log.info("Metadata statistics: Total scraped: {}. Updating database.json...", 
                    allCollections.size());

            long oldSize = 0;
            File dbFile = new File(databaseJson);
            if (dbFile.exists()) oldSize = dbFile.length();

            saveSortedDatabase(jsonContent, filteredCollections);
            long newSize = new File(databaseJson).length();

            // Cập nhật Manifest cơ bản với số lượng đã lọc (chỉ khi có sự thay đổi thực tế)
            if (oldSize != newSize) {
                generateManifest(filteredCollections.size());
            }

            // Kích hoạt ETL để nạp dữ liệu từ JSON vừa tải vào MySQL
            etlService.runEtlJob();

            // Làm mới cache trong CardDataService để CollectionBook trả về đúng số lượng đã lọc
            cardDataService.reload();

            syncStatus = "IDLE";
            syncDetail = "Metadata update completed at " + java.time.LocalDateTime.now().toString();
            log.info("Metadata updated successfully! Total count: {}", filteredCollections.size());
        } catch (Exception e) {
            log.error("Error occurred during metadata sync: {}", e.getMessage());
            syncStatus = "IDLE";
            syncDetail = "Error: " + e.getMessage();
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
                log.error("API server rejected request with status: {}", response.code());
                return null;
            }
            return response.body().string();
        } catch (IOException e) {
            log.error("Connection failed to objekt.top: {}", e.getMessage());
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
            log.error("Failed to generate manifest: {}", e.getMessage());
        }
    }

    public String getSyncStatus() { return syncStatus; }
    public String getSyncDetail() { return syncDetail; }
    public String getManifestPath() { return manifestJson; }
    public String getJsonPath() { return databaseJson; }
}
