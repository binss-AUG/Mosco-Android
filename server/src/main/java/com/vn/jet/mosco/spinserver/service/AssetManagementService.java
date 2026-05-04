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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class AssetManagementService {

    private static final Logger log = LoggerFactory.getLogger(AssetManagementService.class);
    private final OkHttpClient client;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final ExecutorService downloadPool = Executors.newFixedThreadPool(32);

    // Cấu hình từ .env — Dễ dàng thay đổi khi scale lên production
    private final String dataDir;
    private final String imagesDir;
    private final String bundlesDir;
    private final String databaseJson;
    private final String manifestJson;
    private final String apiUrl;
    private final int objetPerBundle;

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    // Trạng thái đồng bộ — Admin Dashboard poll giá trị này
    private volatile String syncStatus = "IDLE";
    private volatile String syncDetail = "";

    public AssetManagementService(
            @org.springframework.beans.factory.annotation.Value("${ASSET_DATA_DIR:data/assets/}") String dataDir,
            @org.springframework.beans.factory.annotation.Value("${OBJEKT_API_URL:https://objekt.top/api/collection?artist=tripleS}") String apiUrl,
            @org.springframework.beans.factory.annotation.Value("${OBJET_PER_BUNDLE:2000}") int objetPerBundle) {

        this.dataDir = dataDir;
        this.imagesDir = dataDir + "images/";
        this.bundlesDir = dataDir + "bundles/";
        this.databaseJson = dataDir + "database.json";
        this.manifestJson = dataDir + "manifest.json";
        this.apiUrl = apiUrl;
        this.objetPerBundle = objetPerBundle;

        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        // Khởi tạo thư mục
        try {
            Files.createDirectories(Paths.get(imagesDir));
            Files.createDirectories(Paths.get(bundlesDir));

        } catch (IOException e) {
            log.error("Không thể khởi tạo thư mục dữ liệu: {}", e.getMessage());
        }
    }

    // ============================
    // SCHEDULED & PUBLIC TRIGGERS
    // ============================

    /**
     * Tự động chạy cào dữ liệu và đóng gói vào 3h, 9h, 12h, 18h hàng ngày
     */
    @Scheduled(cron = "0 0 3,9,12,18 * * *")
    public void scheduledSync() {
        log.info("🚀 [AUTO] Bắt đầu chu kỳ đồng bộ tài nguyên tự động...");
        fullSyncProcess();
    }

    /**
     * Luồng chính: Cào JSON → Tải ảnh mới → Tạo Patch → Cập nhật Manifest
     */
    public void fullSyncProcess() {
        if (!"IDLE".equals(syncStatus)) {
            log.warn("⚠️ Đang có tiến trình đồng bộ khác chạy, bỏ qua lần này.");
            return;
        }

        try {
            syncStatus = "SCRAPING";
            syncDetail = "Đang cào metadata từ objekt.top...";

            // 1. Tải database.json mới nhất
            String jsonContent = fetchLatestMetadata();
            if (jsonContent == null) {
                syncStatus = "IDLE";
                return;
            }

            // 2. Parse và sort theo createdAt (mới nhất ở đầu)
            List<JsonObject> sortedCollections = parseAndSort(jsonContent);
            log.info("✅ Đã cào xong {} Objet (sắp xếp mới nhất ở đầu).", sortedCollections.size());

            // 3. Lưu database.json đã sort
            saveSortedDatabase(jsonContent, sortedCollections);

            // 4. Tải ảnh mới từ Cloudflare
            syncStatus = "DOWNLOADING";
            List<String> newImageIds = syncImages(sortedCollections);

            // 5. Tạo Patch cho ảnh mới (nếu có)
            if (!newImageIds.isEmpty()) {
                syncStatus = "BUNDLING";
                syncDetail = "Đang tạo gói patch cho " + newImageIds.size() + " ảnh mới...";
                generatePatch(newImageIds);
            }

            // 6. Cập nhật Manifest
            generateManifest(sortedCollections.size());

            syncStatus = "IDLE";
            syncDetail = "Hoàn tất lúc " + java.time.LocalDateTime.now().toString();
            log.info("🎉 Chu kỳ đồng bộ hoàn tất trọn vẹn!");

        } catch (Exception e) {
            log.error("❌ Lỗi trong quá trình đồng bộ: {}", e.getMessage());
            syncStatus = "IDLE";
            syncDetail = "Lỗi: " + e.getMessage();
        }
    }

    /**
     * Nén lại toàn bộ Sealed Bundles từ đầu (dùng khi cần rebuild)
     */
    public void rebuildAllBundles() {
        if (!"IDLE".equals(syncStatus)) {
            log.warn("⚠️ Đang bận, không thể rebuild.");
            return;
        }

        try {
            syncStatus = "REBUILDING";
            syncDetail = "Đang rebuild toàn bộ Sealed Bundles...";

            // Đọc database.json để lấy danh sách đã sort
            String jsonContent = Files.readString(Paths.get(databaseJson));
            List<JsonObject> sorted = parseAndSort(jsonContent);

            generateSealedBundles(sorted);

            // Cập nhật manifest
            generateManifest(sorted.size());

            syncStatus = "IDLE";
            syncDetail = "Rebuild hoàn tất lúc " + java.time.LocalDateTime.now().toString();
            log.info("🎉 Rebuild toàn bộ Sealed Bundles hoàn tất!");

        } catch (Exception e) {
            log.error("❌ Lỗi rebuild: {}", e.getMessage());
            syncStatus = "IDLE";
            syncDetail = "Lỗi rebuild: " + e.getMessage();
        }
    }

    // ============================
    // CORE LOGIC
    // ============================

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

    /**
     * Parse JSON và sort theo createdAt giảm dần (mới nhất ở đầu)
     */
    private List<JsonObject> parseAndSort(String jsonContent) {
        JsonObject data = gson.fromJson(jsonContent, JsonObject.class);
        JsonArray collectionsArray = data.has("collections") ? data.getAsJsonArray("collections") : new JsonArray();

        List<JsonObject> collections = new ArrayList<>();
        for (JsonElement e : collectionsArray) collections.add(e.getAsJsonObject());

        // Sort mới nhất ở đầu
        collections.sort((o1, o2) -> {
            String date1 = o1.get("createdAt").getAsString();
            String date2 = o2.get("createdAt").getAsString();
            return date2.compareTo(date1);
        });

        return collections;
    }

    /**
     * Lưu lại database.json với collections đã sort
     */
    private void saveSortedDatabase(String originalJson, List<JsonObject> sortedCollections) throws IOException {
        JsonObject data = gson.fromJson(originalJson, JsonObject.class);
        JsonArray sortedArray = new JsonArray();
        for (JsonObject obj : sortedCollections) sortedArray.add(obj);
        data.add("collections", sortedArray);
        Files.writeString(Paths.get(databaseJson), gson.toJson(data));
        log.info("✅ Đã lưu database.json ({} Objet, sắp xếp mới nhất ở đầu).", sortedCollections.size());
    }

    /**
     * Tải ảnh mới từ Cloudflare — Trả về danh sách ID ảnh mới tải được
     */
    private List<String> syncImages(List<JsonObject> collections) {
        syncDetail = "Đang quét " + collections.size() + " Objet...";
        log.info("🔍 Đang kiểm tra {} Objet...", collections.size());

        AtomicInteger downloadedCount = new AtomicInteger(0);
        AtomicInteger scannedCount = new AtomicInteger(0);
        // Danh sách ID ảnh mới tải được, thread-safe
        ConcurrentLinkedQueue<String> newIds = new ConcurrentLinkedQueue<>();
        CountDownLatch latch = new CountDownLatch(collections.size());

        for (JsonObject obj : collections) {
            downloadPool.execute(() -> {
                try {
                    String id = obj.get("id").getAsString();

                    int d1 = downloadImageIfNotExist(obj.get("frontImage").getAsString(), id + "_front.img");
                    int d2 = downloadImageIfNotExist(obj.get("backImage").getAsString(), id + "_back.img");

                    if (d1 > 0 || d2 > 0) {
                        downloadedCount.addAndGet(d1 + d2);
                        // Ghi nhận ID mới
                        if (d1 > 0) newIds.add(id + "_front.img");
                        if (d2 > 0) newIds.add(id + "_back.img");
                    }

                    int scanned = scannedCount.incrementAndGet();
                    if (scanned % 500 == 0) {
                        syncDetail = "Đã quét " + scanned + "/" + collections.size() + " Objet, tải mới " + downloadedCount.get() + " ảnh";
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            // Chờ tối đa 30 phút
            if (!latch.await(30, TimeUnit.MINUTES)) {
                log.warn("⚠️ Quá thời gian chờ tải ảnh.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        List<String> result = new ArrayList<>(newIds);
        if (!result.isEmpty()) {
            log.info("✅ Đã tải thêm {} ảnh mới.", result.size());
        } else {
            log.info("ℹ️ Không có ảnh mới nào cần tải.");
        }
        return result;
    }

    private int downloadImageIfNotExist(String url, String filename) {
        File file = new File(imagesDir + filename);
        if (file.exists() && file.length() > 1024) return 0;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", USER_AGENT)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                Files.copy(response.body().byteStream(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return 1;
            }
        } catch (IOException e) {
            log.warn("Không thể tải ảnh {}: {}", filename, e.getMessage());
        }
        return 0;
    }

    // ============================
    // BUNDLING — Chia để trị
    // ============================

    /**
     * Tạo Sealed Bundles: Chia toàn bộ kho ảnh thành các gói cố định, mỗi gói 2000 Objet.
     * Thứ tự: Objet CŨ nhất ở bundle_0000, MỚI nhất ở bundle cuối.
     * Lý do: Bundle cũ ít thay đổi → "sealed" vĩnh viễn.
     */
    private void generateSealedBundles(List<JsonObject> sortedCollections) throws IOException {
        // sortedCollections đang sort MỚI nhất ở đầu
        // Đảo ngược lại để CŨ nhất ở đầu → bundle_0000 chứa Objet cũ nhất
        List<JsonObject> reversed = new ArrayList<>(sortedCollections);
        java.util.Collections.reverse(reversed);

        // Xóa bundles cũ trước khi rebuild
        File bundlesFolder = new File(bundlesDir);
        File[] oldBundles = bundlesFolder.listFiles((dir, name) -> name.startsWith("bundle_"));
        if (oldBundles != null) {
            for (File f : oldBundles) f.delete();
        }

        int totalObjet = reversed.size();
        int bundleCount = (int) Math.ceil((double) totalObjet / objetPerBundle);

        log.info("📦 Bắt đầu tạo {} Sealed Bundles (mỗi gói {} Objet)...", bundleCount, objetPerBundle);

        for (int i = 0; i < bundleCount; i++) {
            int fromIdx = i * objetPerBundle;
            int toIdx = Math.min(fromIdx + objetPerBundle, totalObjet);
            List<JsonObject> chunk = reversed.subList(fromIdx, toIdx);

            String bundleName = String.format("bundle_%04d.zip", i);
            syncDetail = "Đang nén " + bundleName + " (" + chunk.size() + " Objet)...";

            createZipFromObjetList(bundleName, chunk);
            log.info("✅ Đã tạo {} ({} Objet: index {} → {})", bundleName, chunk.size(), fromIdx, toIdx - 1);
        }
    }

    /**
     * Tạo Patch: Chỉ nén những ảnh MỚI vào 1 file zip nhỏ.
     * Tên file: patch_NNNN.zip (số thứ tự tự tăng)
     */
    private void generatePatch(List<String> newImageFilenames) throws IOException {
        // Tìm số thứ tự patch tiếp theo
        int nextPatchNum = getNextPatchNumber();
        String patchName = String.format("patch_%04d.zip", nextPatchNum);

        log.info("📦 Đang tạo {} ({} ảnh mới)...", patchName, newImageFilenames.size());

        File patchFile = new File(bundlesDir + patchName);
        byte[] buffer = new byte[1024 * 64];

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(patchFile))) {
            for (String filename : newImageFilenames) {
                File imgFile = new File(imagesDir + filename);
                if (!imgFile.exists()) continue;

                zos.putNextEntry(new ZipEntry(filename));
                try (FileInputStream fis = new FileInputStream(imgFile)) {
                    int len;
                    while ((len = fis.read(buffer)) >= 0) {
                        zos.write(buffer, 0, len);
                    }
                }
                zos.closeEntry();
            }
        }

        long sizeMB = patchFile.length() / (1024 * 1024);
        log.info("✅ Đã tạo {} (Dung lượng: {}MB, {} ảnh)", patchName, sizeMB, newImageFilenames.size());
    }

    /**
     * Nén 1 nhóm Objet (front + back) thành 1 file zip
     */
    private void createZipFromObjetList(String zipName, List<JsonObject> objetList) throws IOException {
        File zipFile = new File(bundlesDir + zipName);
        byte[] buffer = new byte[1024 * 64];
        int fileCount = 0;

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            for (JsonObject obj : objetList) {
                String id = obj.get("id").getAsString();

                // Front image
                fileCount += addFileToZip(zos, buffer, id + "_front.img");
                // Back image
                fileCount += addFileToZip(zos, buffer, id + "_back.img");

                // Flush mỗi 200 Objet để giải phóng bộ nhớ
                if (fileCount % 400 == 0) {
                    zos.flush();
                }
            }
        }
    }

    /**
     * Thêm 1 file vào ZipOutputStream, trả về 1 nếu thành công, 0 nếu file không tồn tại
     */
    private int addFileToZip(ZipOutputStream zos, byte[] buffer, String filename) throws IOException {
        File imgFile = new File(imagesDir + filename);
        if (!imgFile.exists()) return 0;

        zos.putNextEntry(new ZipEntry(filename));
        try (FileInputStream fis = new FileInputStream(imgFile)) {
            int len;
            while ((len = fis.read(buffer)) >= 0) {
                zos.write(buffer, 0, len);
            }
        }
        zos.closeEntry();
        return 1;
    }

    /**
     * Tìm số thứ tự patch tiếp theo (scan thư mục bundles)
     */
    private int getNextPatchNumber() {
        File bundlesFolder = new File(bundlesDir);
        File[] patches = bundlesFolder.listFiles((dir, name) -> name.startsWith("patch_") && name.endsWith(".zip"));
        if (patches == null || patches.length == 0) return 1;

        int maxNum = 0;
        for (File f : patches) {
            try {
                String numStr = f.getName().replace("patch_", "").replace(".zip", "");
                int num = Integer.parseInt(numStr);
                if (num > maxNum) maxNum = num;
            } catch (NumberFormatException ignored) {}
        }
        return maxNum + 1;
    }

    // ============================
    // MANIFEST — "Bản đồ kho báu"
    // ============================

    /**
     * Tạo manifest.json mô tả toàn bộ danh sách bundles + patches
     */
    private void generateManifest(int totalObjet) {
        JsonObject manifest = new JsonObject();
        manifest.addProperty("lastSync", System.currentTimeMillis());
        manifest.addProperty("totalObjet", totalObjet);

        // Liệt kê Sealed Bundles
        JsonArray bundlesArray = new JsonArray();
        File bundlesFolder = new File(bundlesDir);
        File[] bundles = bundlesFolder.listFiles((dir, name) -> name.startsWith("bundle_") && name.endsWith(".zip"));
        if (bundles != null) {
            Arrays.sort(bundles);
            int idx = 0;
            for (File f : bundles) {
                JsonObject b = new JsonObject();
                b.addProperty("name", f.getName());
                b.addProperty("fromIndex", idx * objetPerBundle);
                b.addProperty("toIndex", Math.min((idx + 1) * objetPerBundle - 1, totalObjet - 1));
                b.addProperty("size", f.length());
                bundlesArray.add(b);
                idx++;
            }
        }
        manifest.add("sealedBundles", bundlesArray);

        // Liệt kê Patches
        JsonArray patchesArray = new JsonArray();
        File[] patches = bundlesFolder.listFiles((dir, name) -> name.startsWith("patch_") && name.endsWith(".zip"));
        if (patches != null) {
            Arrays.sort(patches);
            int baseIndex = bundlesArray.size() * objetPerBundle;
            for (File f : patches) {
                JsonObject p = new JsonObject();
                p.addProperty("name", f.getName());
                p.addProperty("size", f.length());
                patchesArray.add(p);
            }
        }
        manifest.add("patches", patchesArray);

        // Tổng số file ảnh đã đóng gói
        int totalImages = countImagesInDirectory();
        manifest.addProperty("totalImages", totalImages);

        try {
            Files.writeString(Paths.get(manifestJson), gson.toJson(manifest));
            log.info("✅ Đã cập nhật manifest.json ({} bundles, {} patches, {} ảnh)",
                    bundlesArray.size(), patchesArray.size(), totalImages);
        } catch (IOException e) {
            log.error("Lỗi tạo manifest: {}", e.getMessage());
        }
    }

    private int countImagesInDirectory() {
        File dir = new File(imagesDir);
        File[] files = dir.listFiles();
        return files != null ? files.length : 0;
    }

    // ============================
    // GETTERS — Cho Controller sử dụng
    // ============================

    public String getSyncStatus() { return syncStatus; }
    public String getSyncDetail() { return syncDetail; }
    public String getManifestPath() { return manifestJson; }
    public String getJsonPath() { return databaseJson; }
    public String getBundlesDir() { return bundlesDir; }
}
