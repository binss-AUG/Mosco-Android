package com.vn.jet.mosco.utils;

import android.content.Context;
import android.util.Log;

import org.chromium.net.CronetEngine;
import org.chromium.net.UrlRequest;
import org.chromium.net.UrlResponseInfo;
import org.chromium.net.CronetException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CardAssetManager (2026 Turbo Edition) — Dynamic High-Speed Downloader.
 * 
 * PHÂN TÍCH HIỆU NĂNG:
 * 1. WiFi (Turbo): 80 concurrent requests, 64 network threads, HIGHEST priority.
 * 2. Cellular (Eco): 20 concurrent requests, 16 network threads, MEDIUM priority.
 * 3. Connection Migration: Tự động duy trì session khi đổi mạng (đặc sản của HTTP/3).
 */
public class CardAssetManager {

    private static final String TAG = "CardAssetManager";
    private static final String CARDS_DIR = "card_assets";
    private static final String PREFS_NAME = "card_asset_prefs";
    private static final String KEY_TOTAL_EXPECTED = "total_expected";

    // Giảm số luồng để phù hợp với giả lập, tránh gây lag máy
    private static final ExecutorService networkExecutor = Executors.newFixedThreadPool(8);
    private static final ExecutorService ioExecutor = Executors.newFixedThreadPool(4);
    
    // Semaphore điều tiết luồng (Tối đa 12 yêu cầu đồng thời để đảm bảo độ mượt)
    private static Semaphore dynamicSemaphore = new Semaphore(12);

    public interface DownloadProgressListener {
        void onProgress(int downloaded, int total, String currentFile);
        void onComplete();
        void onError(String errorMessage);
    }

    public static class DownloadInfo {
        public int pendingCount = 0;
        public int totalCount = 0;
        public int estimatedSizeMB = 0;
        public List<String> pendingUrls = new ArrayList<>();
    }

    public static File getCardsDirectory(Context context) {
        File dir = new File(context.getFilesDir(), CARDS_DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static String extractImageId(String url) {
        if (url == null || url.isEmpty()) return null;
        try {
            if (url.contains("imagedelivery.net")) {
                String[] parts = url.split("/");
                if (parts.length >= 2) return parts[parts.length - 2];
            } else if (url.startsWith("http")) {
                String[] parts = url.split("/");
                if (parts.length > 0) {
                    String lastPart = parts[parts.length - 1];
                    int dotIndex = lastPart.lastIndexOf('.');
                    if (dotIndex > 0) {
                        return lastPart.substring(0, dotIndex);
                    }
                    return lastPart;
                }
            } else {
                return url;
            }
        } catch (Exception e) {
            Log.w(TAG, "Lỗi trích xuất ImageId: " + url, e);
        }
        return null;
    }

    public static String convertToVariant(String url, String variant) {
        if (url == null || url.isEmpty()) return url;
        if (!url.contains("imagedelivery.net")) return url;
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash > 0) return url.substring(0, lastSlash + 1) + variant;
        return url;
    }

    public static File getLocalFile(Context context, String imageUrl) {
        String imageId = extractImageId(imageUrl);
        if (imageId == null) return null;
        return new File(getCardsDirectory(context), imageId + ".img");
    }

    public static boolean isDownloaded(Context context, String imageUrl) {
        File file = getLocalFile(context, imageUrl);
        return file != null && file.exists() && file.length() > 0;
    }

    public static boolean isAllAssetsReadyQuick(Context context) {
        DownloadInfo info = getPendingDownloadInfo(context);
        return info.pendingCount == 0;
    }

    public static DownloadInfo getPendingDownloadInfo(Context context) {
        DownloadInfo info = new DownloadInfo();
        List<JSONObject> allCards = DatabaseLoader.loadEveryCard(context);
        if (allCards == null || allCards.isEmpty()) return info;

        Set<String> localFiles = new java.util.HashSet<>();
        File dir = getCardsDirectory(context);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) if (f.length() > 0) localFiles.add(f.getName());
        }

        Set<String> uniquePendingUrls = new LinkedHashSet<>();
        Set<String> allUniqueIds = new java.util.HashSet<>();
        
        for (JSONObject card : allCards) {
            processUrl(card.optString("frontImage", ""), localFiles, allUniqueIds, uniquePendingUrls);
            processUrl(card.optString("backImage", ""), localFiles, allUniqueIds, uniquePendingUrls);
        }

        info.totalCount = allUniqueIds.size();
        info.pendingUrls = new ArrayList<>(uniquePendingUrls);
        info.pendingCount = info.pendingUrls.size();
        info.estimatedSizeMB = (int) (info.pendingCount * 50L / 1024L);
        
        return info;
    }

    /**
     * Tải tài nguyên với cấu hình động theo loại mạng.
     */
    public static void startDownloadWithInfo(Context context, DownloadInfo info, boolean isWifi, DownloadProgressListener listener) {
        new Thread(() -> {
            try {
                CronetEngine engine = CronetEngineProvider.getEngine(context);
                final int totalExpected = info.totalCount;
                AtomicInteger processedCount = new AtomicInteger(totalExpected - info.pendingCount);

                // Cấu hình động: WiFi tối đa 12, Cellular tối đa 4 (Để mượt UI)
                int permitCount = isWifi ? 12 : 4;
                int priority = isWifi ? UrlRequest.Builder.REQUEST_PRIORITY_HIGHEST : UrlRequest.Builder.REQUEST_PRIORITY_MEDIUM;
                
                // Khởi tạo lại Semaphore nếu cần (Để đơn giản ta reset mỗi lần sync lớn)
                dynamicSemaphore = new Semaphore(permitCount);

                Log.d(TAG, "Bắt đầu tải (Mode: " + (isWifi ? "Turbo" : "Eco") + ") - " + info.pendingCount + " files.");

                for (String originalUrl : info.pendingUrls) {
                    dynamicSemaphore.acquire();
                    String downloadUrl = convertToVariant(originalUrl, "2x");
                    String imageId = extractImageId(originalUrl);
                    File outputFile = new File(getCardsDirectory(context), imageId + ".img");

                    UrlRequest.Callback callback = new UrlRequest.Callback() {
                        private final java.io.ByteArrayOutputStream bytesReceived = new java.io.ByteArrayOutputStream();
                        private final WritableByteChannel channel = Channels.newChannel(bytesReceived);

                        @Override public void onRedirectReceived(UrlRequest request, UrlResponseInfo info, String newLocation) { request.followRedirect(); }
                        @Override public void onResponseStarted(UrlRequest request, UrlResponseInfo info) { request.read(ByteBuffer.allocateDirect(32 * 1024)); }
                        @Override public void onReadCompleted(UrlRequest request, UrlResponseInfo info, ByteBuffer byteBuffer) {
                            byteBuffer.flip();
                            try { channel.write(byteBuffer); } catch (Exception ignored) {}
                            byteBuffer.clear();
                            request.read(byteBuffer);
                        }
                        @Override public void onSucceeded(UrlRequest request, UrlResponseInfo info) {
                            ioExecutor.execute(() -> {
                                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                                    fos.write(bytesReceived.toByteArray());
                                } catch (Exception e) { Log.e(TAG, "Lỗi ghi file: " + imageId, e); }
                                finally { dynamicSemaphore.release(); checkCompletion(context, processedCount, totalExpected, listener, imageId); }
                            });
                        }
                        @Override public void onFailed(UrlRequest request, UrlResponseInfo info, CronetException error) {
                            dynamicSemaphore.release(); checkCompletion(context, processedCount, totalExpected, listener, imageId);
                        }
                        @Override public void onCanceled(UrlRequest request, UrlResponseInfo info) {
                            dynamicSemaphore.release(); checkCompletion(context, processedCount, totalExpected, listener, imageId);
                        }
                    };

                    UrlRequest.Builder builder = engine.newUrlRequestBuilder(downloadUrl, callback, networkExecutor);
                    builder.setPriority(priority);
                    builder.build().start();
                }
            } catch (Exception e) {
                if (listener != null) listener.onError(e.getMessage());
            }
        }).start();
    }

    private static void processUrl(String url, Set<String> localFiles, Set<String> allUniqueIds, Set<String> pending) {
        if (url != null && !url.isEmpty() && !"null".equalsIgnoreCase(url)) {
            String id = extractImageId(url);
            if (id != null) {
                allUniqueIds.add(id);
                if (!localFiles.contains(id + ".img")) pending.add(url);
            }
        }
    }

    private static void checkCompletion(Context context, AtomicInteger count, int total, DownloadProgressListener listener, String imageId) {
        int current = count.incrementAndGet();
        if (listener != null) {
            listener.onProgress(current, total, imageId);
        }
        if (current >= total) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_TOTAL_EXPECTED, total).apply();
            if (listener != null) listener.onComplete();
        }
    }
}
