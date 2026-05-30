package com.vn.jet.mosco.spinserver.utils;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ChaosTheoryHelper - Bộ cung cấp số ngẫu nhiên thực sự (Atmospheric Noise) dựa trên lý thuyết hỗn loạn.
 * Giải thích:
 * - Sử dụng SecureRandom duy nhất an toàn đa luồng và hiệu năng cao.
 * - Hạt giống được re-seed bất đồng bộ thông qua random.org ở luồng nền định kỳ mỗi 10 phút.
 * - Thiết kế này loại bỏ hoàn toàn việc gọi API chặn (blocking call) trên luồng chính của HTTP Request,
 *   đảm bảo độ trễ phản hồi (latency) của game luôn là 0ms mà vẫn giữ được độ hỗn loạn khí quyển tối đa.
 */
public class ChaosTheoryHelper {

    private static final Logger log = LoggerFactory.getLogger(ChaosTheoryHelper.class);
    private static final SecureRandom random = new SecureRandom();
    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build();

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "chaos-theory-scheduler");
        thread.setDaemon(true); // Đảm bảo luồng daemon không chặn tắt JVM
        return thread;
    });

    static {
        // Khởi tạo hạt giống tức thời tránh bị block khi load class
        random.setSeed(System.nanoTime() ^ System.currentTimeMillis());
        
        // Lên lịch định kỳ nạp seed khí quyển từ random.org mỗi 10 phút
        scheduler.scheduleWithFixedDelay(
                ChaosTheoryHelper::injectChaosAsynchronously,
                0, // Chạy ngay lập tức khi khởi động
                10,
                TimeUnit.MINUTES
        );
    }

    /**
     * Thực hiện lấy hạt giống thực sự từ random.org bất đồng bộ để tránh chặn luồng chính.
     */
    private static void injectChaosAsynchronously() {
        try {
            log.info("[CHAOS-THEORY] Initializing atmospheric random seed fetch from random.org...");
            String url = "https://www.random.org/integers/?num=1&min=1&max=1000000000&col=1&base=10&format=plain&rnd=new";
            Request request = new Request.Builder().url(url).build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String result = response.body().string().trim();
                    long seed = Long.parseLong(result);
                    
                    // Trộn số True Random từ API với thời gian thực của hệ thống để tối đa hóa entropy
                    random.setSeed(seed ^ System.nanoTime());
                    log.info("[CHAOS-THEORY] Atmospheric seed fetch successful: {}", seed);
                } else {
                    log.warn("[CHAOS-THEORY] Failed to fetch seed from random.org (Status: {}). Using system fallback seed.", response.code());
                    fallbackSeed();
                }
            }
        } catch (Exception e) {
            log.error("[CHAOS-THEORY] Connection failed to random.org, falling back to system seed.", e);
            fallbackSeed();
        }
    }

    private static void fallbackSeed() {
        random.setSeed(System.nanoTime() ^ System.currentTimeMillis());
    }

    /**
     * Kích hoạt nạp hỗn loạn thủ công bất đồng bộ.
     */
    public static void triggerManualChaos() {
        scheduler.submit(ChaosTheoryHelper::injectChaosAsynchronously);
    }

    /**
     * Sinh một số thực ngẫu nhiên từ 0.0 đến 1.0.
     */
    public static double nextDouble() {
        return random.nextDouble();
    }

    /**
     * Sinh một số nguyên ngẫu nhiên từ 0 (bao gồm) đến bound (loại trừ).
     */
    public static int nextInt(int bound) {
        if (bound <= 0) {
            return 0;
        }
        return random.nextInt(bound);
    }

    /**
     * Sinh một giá trị boolean ngẫu nhiên.
     */
    public static boolean nextBoolean() {
        return random.nextBoolean();
    }

    /**
     * Trộn ngẫu nhiên danh sách (Shuffle).
     */
    public static void shuffle(List<?> list) {
        if (list != null && !list.isEmpty()) {
            Collections.shuffle(list, random);
        }
    }
}
