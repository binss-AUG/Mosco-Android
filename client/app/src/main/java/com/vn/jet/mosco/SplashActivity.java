package com.vn.jet.mosco;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.vn.jet.mosco.utils.CardAssetManager;
import com.vn.jet.mosco.utils.DatabaseLoader;
import com.vn.jet.mosco.utils.SessionManager;

public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";

    // Kích thước chuẩn duy nhất cho toàn app — đảm bảo Cache Hit 100%
    public static final int OBJET_WIDTH = 300;
    public static final int OBJET_HEIGHT = 462;

    private LinearLayout layoutDownloadProgress;
    private ProgressBar pbDownload;
    private TextView tvDownloadStatus;
    private TextView tvDownloadCount;
    private com.airbnb.lottie.LottieAnimationView lottieSplash;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Ánh xạ View
        layoutDownloadProgress = findViewById(R.id.layout_download_progress);
        pbDownload = findViewById(R.id.pb_download);
        tvDownloadStatus = findViewById(R.id.tv_download_status);
        tvDownloadCount = findViewById(R.id.tv_download_count);
        lottieSplash = findViewById(R.id.iv_logo_lottie); // Now the main logo Lottie
        mainHandler = new Handler(Looper.getMainLooper());

        // Bắt đầu kiểm tra tài nguyên trên Thread riêng
        new Thread(this::checkAndLoadResources).start();
    }

    /**
     * Luồng chính: Kiểm tra tài nguyên → Tải nếu thiếu → Nạp Inventory → Vào game.
     */
    private void checkAndLoadResources() {
        long startTime = System.currentTimeMillis();

        mainHandler.post(() -> tvDownloadStatus.setText("Khởi động hệ thống..."));
        DatabaseLoader.loadEveryCard(getApplicationContext());

        boolean allReady = CardAssetManager.isAllAssetsReady(getApplicationContext());

        if (!allReady) {
            // Hiện thanh tiến trình tải
            mainHandler.post(() -> {
                layoutDownloadProgress.setVisibility(View.VISIBLE);
                tvDownloadStatus.setText("Đang đồng bộ dữ liệu...");
                pbDownload.setProgress(0);
                tvDownloadCount.setText("0%");
            });

            // Logic "Loading ảo" - Đã được smooth hóa
            final int[] displayedProgress = {0};
            final int[] realPercent = {0};
            final Object downloadLock = new Object();

            new Thread(() -> {
                while (displayedProgress[0] < 100) {
                    try {
                        Thread.sleep(60); 
                        
                        int increment = 0;
                        if (displayedProgress[0] < 60) {
                            // FAST PHASE (0 - 60%) — Chạy nhanh và mượt để kích thích người dùng
                            increment = (int) (Math.random() * 3) + 2; 
                        } else if (displayedProgress[0] < 92) {
                            // DEEP LOAD PHASE (60 - 92%) — Chậm dần nhưng không được đứng yên
                            if (Math.random() > 0.8) increment = 1;
                        } else {
                            // SYNC PHASE (> 92%) — Chờ tín hiệu thật từ mạng
                            if (realPercent[0] >= 100 || realPercent[0] > displayedProgress[0]) {
                                increment = 1;
                            }
                        }

                        if (increment > 0) {
                            displayedProgress[0] += increment;
                            if (displayedProgress[0] > 100) displayedProgress[0] = 100;
                            
                            final int val = displayedProgress[0];
                            mainHandler.post(() -> {
                                pbDownload.setProgress(val);
                                tvDownloadCount.setText(val + "%");
                            });
                        }
                    } catch (InterruptedException e) { break; }
                }
            }).start();

            // Tải ảnh thật (32 luồng + OkHttp)
            CardAssetManager.downloadAllAssets(getApplicationContext(), new CardAssetManager.DownloadProgressListener() {
                @Override
                public void onProgress(int downloaded, int total, String currentFile) {
                    realPercent[0] = (int) ((downloaded / (float) total) * 100);
                }

                @Override
                public void onComplete() {
                    realPercent[0] = 100;
                    synchronized (downloadLock) {
                        downloadLock.notifyAll();
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    realPercent[0] = 100;
                    synchronized (downloadLock) {
                        downloadLock.notifyAll();
                    }
                }
            });

            synchronized (downloadLock) {
                try {
                    downloadLock.wait(600000); 
                } catch (InterruptedException ignored) {}
            }
            
            displayedProgress[0] = 100;
            mainHandler.post(() -> {
                pbDownload.setProgress(100);
                tvDownloadCount.setText("100%");
                tvDownloadStatus.setText("Sẵn sàng!");
            });
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        } else {
            Log.d(TAG, "Tất cả ảnh đã sẵn sàng.");
        }

        // Bật lại Loading chung nếu cần ẩn Progress
        mainHandler.post(() -> {
            tvDownloadStatus.setText("Đang nạp túi đồ...");
            layoutDownloadProgress.setVisibility(View.GONE);
        });

        SessionManager sessionManager = new SessionManager(SplashActivity.this);
        Long userId = sessionManager.getUserId();
        if (userId != null && sessionManager.isLoggedIn()) {
            try {
                com.vn.jet.mosco.network.GameApiService apiService =
                        com.vn.jet.mosco.network.ApiClient.getClient(SplashActivity.this)
                                .create(com.vn.jet.mosco.network.GameApiService.class);
                retrofit2.Response<java.util.List<com.vn.jet.mosco.model.UserCard>> response =
                        apiService.getUserCards(userId).execute();

                if (response.isSuccessful() && response.body() != null) {
                    java.util.List<DatabaseLoader.UserInventoryItem> cachedList = new java.util.ArrayList<>();
                    for (com.vn.jet.mosco.model.UserCard userCard : response.body()) {
                        org.json.JSONObject meta = DatabaseLoader.findById(SplashActivity.this, userCard.getCollectionId());
                        if (meta != null) {
                            String frontImage = meta.optString("frontImage", "");
                            cachedList.add(new com.vn.jet.mosco.utils.DatabaseLoader.UserInventoryItem(
                                    userCard.getId(),
                                    userCard.getCollectionId(),
                                    frontImage,
                                    userCard.getLevel(),
                                    userCard.getExp(),
                                    userCard.getUpgradeLevel()
                            ));
                        }
                    }
                    DatabaseLoader.cachedUserInventory = cachedList;
                    Log.d(TAG, "Inventory cache loaded: " + cachedList.size() + " items.");
                }
            } catch (Exception e) {
                Log.w(TAG, "Lỗi nạp Inventory: " + e.getMessage());
            }
        }

        // ═══════════════════════════════════════════════════════════
        // BƯỚC 4: Đảm bảo Splash hiện ít nhất 2s rồi mới chuyển màn hình
        // ═══════════════════════════════════════════════════════════
        long elapsed = System.currentTimeMillis() - startTime;
        long waitTime = Math.max(0, 2000 - elapsed);

        mainHandler.postDelayed(() -> {
            Intent intent;
            if (sessionManager.isLoggedIn()) {
                intent = new Intent(SplashActivity.this, MainActivity.class);
            } else {
                intent = new Intent(SplashActivity.this, OnboardingActivity.class);
            }
            startActivity(intent);
            finish();
        }, waitTime);
    }

    /**
     * Format số với dấu phẩy phân cách hàng nghìn: 1234 → "1,234"
     */
    private String formatNumber(int number) {
        return String.format("%,d", number);
    }
}