package com.vn.jet.mosco.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CardAssetManager — Quản lý việc tải và lưu trữ ảnh thẻ bài (2x) vào bộ nhớ thiết bị.
 *
 * Chiến thuật: Tải toàn bộ ảnh frontImage dạng 2x về thư mục nội bộ (Internal Storage).
 * Khi cần hiển thị dạng Grid (1x), Glide sẽ scale down từ bản 2x local.
 * Khi cần hiển thị Detail/Result (4x/original), gọi URL trực tiếp từ Cloudflare.
 */
public class CardAssetManager {

    private static final String TAG = "CardAssetManager";

    // Thư mục lưu ảnh trong Internal Storage (chỉ app truy cập được)
    private static final String CARDS_DIR = "card_assets";

    // Key SharedPreferences để lưu phiên bản dữ liệu đã tải
    private static final String PREFS_NAME = "card_asset_prefs";
    private static final String KEY_TOTAL_DOWNLOADED = "total_downloaded";
    private static final String KEY_TOTAL_EXPECTED = "total_expected";

    // Số luồng tải đồng thời — tối ưu hóa tối đa cho 10k ảnh
    private static final int DOWNLOAD_THREADS = 32;

    /**
     * Callback để thông báo tiến trình tải về SplashActivity.
     */
    public interface DownloadProgressListener {
        void onProgress(int downloaded, int total, String currentFile);
        void onComplete();
        void onError(String errorMessage);
    }

    /**
     * Trả về thư mục chứa ảnh thẻ bài.
     */
    public static File getCardsDirectory(Context context) {
        File dir = new File(context.getFilesDir(), CARDS_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * Trích xuất Image ID từ URL Cloudflare.
     * Ví dụ: "https://imagedelivery.net/.../dfbcfefa-79cb-4928-6156-d257fdb7ca00/original"
     * → Trả về: "dfbcfefa-79cb-4928-6156-d257fdb7ca00"
     */
    public static String extractImageId(String url) {
        if (url == null || url.isEmpty()) return null;
        try {
            // URL dạng: https://imagedelivery.net/{account}/{imageId}/{variant}
            String[] parts = url.split("/");
            // imageId nằm ở vị trí áp chót (trước variant "original/2x/1x")
            if (parts.length >= 2) {
                return parts[parts.length - 2];
            }
        } catch (Exception e) {
            Log.w(TAG, "Không thể trích xuất ImageId từ URL: " + url);
        }
        return null;
    }

    /**
     * Chuyển đổi URL từ variant gốc (original) sang variant mong muốn (2x, 1x).
     * Ví dụ: ".../original" → ".../2x"
     */
    public static String convertToVariant(String url, String variant) {
        if (url == null || url.isEmpty()) return url;
        // Thay thế phần cuối URL (sau dấu / cuối cùng)
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash > 0) {
            return url.substring(0, lastSlash + 1) + variant;
        }
        return url;
    }

    /**
     * Lấy đường dẫn File local của ảnh thẻ bài (nếu đã tải).
     * @return File object (có thể chưa tồn tại — cần kiểm tra .exists())
     */
    public static File getLocalFile(Context context, String imageUrl) {
        String imageId = extractImageId(imageUrl);
        if (imageId == null) return null;
        return new File(getCardsDirectory(context), imageId + ".img");
    }

    /**
     * Kiểm tra xem ảnh đã được tải về local chưa.
     */
    public static boolean isDownloaded(Context context, String imageUrl) {
        File file = getLocalFile(context, imageUrl);
        return file != null && file.exists() && file.length() > 0;
    }

    public static boolean ensureAssetReady(Context context, String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty() || "null".equalsIgnoreCase(imageUrl)) return true;
        if (isDownloaded(context, imageUrl)) return true;
        return downloadSingleImage(context, imageUrl);
    }

    public static void preloadAssetsBlocking(Context context, List<String> imageUrls, int maxThreads) {
        if (context == null || imageUrls == null || imageUrls.isEmpty()) return;

        Set<String> deduplicated = new LinkedHashSet<>();
        for (String url : imageUrls) {
            if (url != null && !url.isEmpty() && !"null".equalsIgnoreCase(url)) {
                deduplicated.add(url);
            }
        }
        if (deduplicated.isEmpty()) return;

        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, maxThreads));
        for (String url : deduplicated) {
            executor.submit(() -> ensureAssetReady(context, url));
        }
        executor.shutdown();
        try {
            executor.awaitTermination(180, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Kiểm tra xem TẤT CẢ ảnh đã tải đầy đủ chưa.
     * So sánh số ảnh đã tải với tổng số ảnh trong database.json.
     */
    public static boolean isAllAssetsReady(Context context) {
        List<JSONObject> allCards = DatabaseLoader.loadEveryCard(context);
        int totalExpected = allCards.size();
        int totalDownloaded = countDownloadedFiles(context);

        Log.d(TAG, "Kiểm tra Assets: " + totalDownloaded + "/" + totalExpected + " ảnh sẵn sàng.");
        return totalDownloaded >= totalExpected;
    }

    /**
     * Đếm số file ảnh đã tải trong thư mục local.
     */
    public static int countDownloadedFiles(Context context) {
        File dir = getCardsDirectory(context);
        File[] files = dir.listFiles();
        return files != null ? files.length : 0;
    }

    /**
     * Tải toàn bộ ảnh thẻ bài (bản 2x) về bộ nhớ thiết bị.
     * Chỉ tải những ảnh chưa có — hỗ trợ resume khi bị gián đoạn.
     */
    public static void downloadAllAssets(Context context, DownloadProgressListener listener) {
        new Thread(() -> {
            try {
                // Bước 1: Parse database.json để lấy danh sách tất cả URL
                List<JSONObject> allCards = DatabaseLoader.loadEveryCard(context);
                if (allCards == null || allCards.isEmpty()) {
                    if (listener != null) listener.onError("Không thể đọc database.json");
                    return;
                }

                // Bước 2: Lọc ra những ảnh chưa tải
                List<String> urlsToDownload = new ArrayList<>();
                for (JSONObject card : allCards) {
                    String frontImage = card.optString("frontImage", "");
                    if (!frontImage.isEmpty() && !isDownloaded(context, frontImage)) {
                        urlsToDownload.add(frontImage);
                    }
                }

                int totalExpected = allCards.size();
                int alreadyDownloaded = totalExpected - urlsToDownload.size();

                Log.d(TAG, "Cần tải thêm: " + urlsToDownload.size() + "/" + totalExpected
                        + " (đã có: " + alreadyDownloaded + ")");

                // Nếu đã đủ → hoàn tất ngay
                if (urlsToDownload.isEmpty()) {
                    if (listener != null) {
                        listener.onProgress(totalExpected, totalExpected, "");
                        listener.onComplete();
                    }
                    return;
                }

                // Bước 3: Tải đồng thời với ThreadPool
                AtomicInteger downloadedCount = new AtomicInteger(alreadyDownloaded);
                AtomicInteger errorCount = new AtomicInteger(0);
                ExecutorService executor = Executors.newFixedThreadPool(DOWNLOAD_THREADS);

                for (String originalUrl : urlsToDownload) {
                    executor.submit(() -> {
                        boolean success = downloadSingleImage(context, originalUrl);
                        int current = downloadedCount.incrementAndGet();

                        if (!success) {
                            errorCount.incrementAndGet();
                        }

                        if (listener != null) {
                            String imageId = extractImageId(originalUrl);
                            listener.onProgress(current, totalExpected,
                                    imageId != null ? imageId.substring(0, Math.min(8, imageId.length())) + "..." : "");
                        }

                        // Kiểm tra hoàn tất
                        if (current >= totalExpected) {
                            executor.shutdown();
                            if (listener != null) {
                                listener.onComplete();
                            }
                        }
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "Lỗi tải tài nguyên", e);
                if (listener != null) listener.onError("Lỗi: " + e.getMessage());
            }
        }).start();
    }

    // OKHTTP Client — Dùng chung cho toàn bộ tiến trình tải (tự động Connection Pooling)
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .dispatcher(new okhttp3.Dispatcher())
            .build();

    static {
        // Tối ưu hóa dispatcher cho phép 32 luồng tải cùng lúc tới 1 host (Cloudflare)
        client.dispatcher().setMaxRequests(64);
        client.dispatcher().setMaxRequestsPerHost(32);
    }

    /**
     * Tải 1 ảnh từ Cloudflare (bản 2x) và lưu vào thư mục Internal Storage dùng OKHTTP.
     * @return true nếu tải thành công
     */
    private static boolean downloadSingleImage(Context context, String originalUrl) {
        String imageId = extractImageId(originalUrl);
        if (imageId == null) return false;

        // Chuyển URL sang bản 2x để tải
        String downloadUrl = convertToVariant(originalUrl, "2x");
        File outputFile = new File(getCardsDirectory(context), imageId + ".img");

        // Nếu đã tồn tại → bỏ qua
        if (outputFile.exists() && outputFile.length() > 0) return true;

        Request request = new Request.Builder()
                .url(downloadUrl)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                Log.w(TAG, "Lỗi HTTP " + response.code() + " cho: " + imageId);
                return false;
            }

            if (response.body() == null) return false;

            try (InputStream inputStream = response.body().byteStream();
                 FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
                return true;
            }
        } catch (Exception e) {
            if (outputFile.exists()) outputFile.delete();
            Log.w(TAG, "OkHttp Tải thất bại: " + imageId + " → " + e.getMessage());
            return false;
        }
    }

    /**
     * Xóa toàn bộ ảnh đã tải (dùng khi cần reset hoặc cập nhật dữ liệu mới).
     */
    public static void clearAllAssets(Context context) {
        File dir = getCardsDirectory(context);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                f.delete();
            }
        }
        Log.d(TAG, "Đã xóa toàn bộ ảnh local.");
    }
}
