package com.vn.jet.mosco;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import com.vn.jet.mosco.utils.CardAssetManager;
import com.vn.jet.mosco.utils.DatabaseLoader;
import com.vn.jet.mosco.utils.SessionManager;
import com.vn.jet.mosco.network.ApiClient;
import com.vn.jet.mosco.network.GameApiService;

public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";

    // Kích thước chuẩn duy nhất cho toàn app — đảm bảo Cache Hit 100%
    public static final int OBJET_WIDTH = 300;
    public static final int OBJET_HEIGHT = 462;

    private LinearLayout layoutDownloadProgress;
    private View layoutRetryConnection;
    private com.google.android.material.button.MaterialButton btnRetryConnection;
    private ProgressBar pbDownload;
    private TextView tvDownloadStatus;
    private TextView tvDownloadCount;
    private com.airbnb.lottie.LottieAnimationView lottieSplash;
    private ImageView ivBackground;
    private ObjectAnimator driftX, driftY;
    private Handler mainHandler;
    private volatile boolean isResourceLoadFinished = false;
    private volatile boolean isNavigating = false; // Flag to prevent double launch
    private volatile boolean isErrorShown = false;

    // Performance Monitoring
    private long splashStartTime;
    private java.util.Map<String, Long> renderMetrics = new java.util.HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        splashStartTime = System.currentTimeMillis();
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);

        // --- 🌔 APPLY THEME SETTINGS ---
        SessionManager sessionManager = new SessionManager(this);
        if (sessionManager.isDarkMode()) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Ánh xạ View
        layoutDownloadProgress = findViewById(R.id.layout_download_progress);
        layoutRetryConnection = findViewById(R.id.layout_retry_connection);
        btnRetryConnection = findViewById(R.id.btn_retry_connection);
        pbDownload = findViewById(R.id.pb_download);
        tvDownloadStatus = findViewById(R.id.tv_download_status);
        tvDownloadCount = findViewById(R.id.tv_download_count);
        lottieSplash = findViewById(R.id.iv_logo_lottie); 
        ivBackground = findViewById(R.id.iv_background_parallax);
        mainHandler = new Handler(Looper.getMainLooper());

        btnRetryConnection.setOnClickListener(v -> {
            layoutRetryConnection.setVisibility(View.GONE);
            lottieSplash.setVisibility(View.VISIBLE);
            new Thread(this::checkAndLoadResources).start();
        });

        setupParallax();

        // Bắt đầu kiểm tra tài nguyên và tối ưu hóa render
        trackRenderTime("Init", () -> {
            new Thread(this::checkAndLoadResources).start();
        });
    }

    /**
     * Monitoring: Track render time của từng component
     */
    private void trackRenderTime(String component, Runnable action) {
        long start = System.currentTimeMillis();
        action.run();
        long end = System.currentTimeMillis();
        renderMetrics.put(component, end - start);
        Log.d(TAG, "Render Monitoring - " + component + ": " + (end - start) + "ms");
    }

    /**
     * Luồng chính: Kiểm tra kết nối → Tài nguyên → Vào game.
     */
    private void checkAndLoadResources() {
        isResourceLoadFinished = false;
        isErrorShown = false;
        
        // --- ⏲️ TIMEOUT MECHANISM ---
        Runnable timeoutRunnable = () -> {
            if (!isResourceLoadFinished && !isFinishing() && !isNavigating) {
                showConnectionError();
            }
        };

        // Bắt đầu với 8s - dành cho trường hợp server off hoặc API không phản hồi.
        mainHandler.postDelayed(timeoutRunnable, 8000);

        if (!isNetworkAvailable()) {
            showConnectionError();
            return;
        }

        // Không hiện Progress Bar ở đây — chỉ hiện khi thật sự cần tải ảnh
        
        trackRenderTime("DatabaseLoad", () -> {
            DatabaseLoader.loadEveryCard(getApplicationContext());
        });

        boolean allReady = CardAssetManager.isAllAssetsReady(getApplicationContext());

        if (!allReady) {
            // Có update data (phải tải lượng lớn hình ảnh), hủy 8s và gia hạn thành 10 phút.
            mainHandler.removeCallbacks(timeoutRunnable);
            mainHandler.postDelayed(timeoutRunnable, 600000);

            // Hiện thanh tiến trình tải
            mainHandler.post(() -> {
                layoutDownloadProgress.setVisibility(View.VISIBLE);
                tvDownloadStatus.setText("Đang đồng bộ dữ liệu...");
                pbDownload.setProgress(0);
                tvDownloadCount.setText("0%");
            });

            final int[] displayedProgress = {0};
            final int[] realPercent = {0};
            final Object downloadLock = new Object();

            new Thread(() -> {
                while (displayedProgress[0] < 100) {
                    try {
                        Thread.sleep(60); 
                        int increment = 0;
                        if (displayedProgress[0] < 60) {
                            increment = (int) (Math.random() * 3) + 2; 
                        } else if (displayedProgress[0] < 92) {
                            if (Math.random() > 0.8) increment = 1;
                        } else {
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

            trackRenderTime("AssetDownload", () -> {
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
            });
            
            displayedProgress[0] = 100;
            mainHandler.post(() -> {
                pbDownload.setProgress(100);
                tvDownloadCount.setText("100%");
                tvDownloadStatus.setText("Sẵn sàng!");
            });
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        }

        // Ẩn thanh tải (nếu đang hiện)
        mainHandler.post(() -> layoutDownloadProgress.setVisibility(View.GONE));

        // Nạp Inventory từ Server (chỉ cần gọi API nhẹ — không decode ảnh)
        trackRenderTime("InventoryCache", () -> {
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
                                // OVR trực tiếp từ Server (Server Truth)
                                int ovr = userCard.getOvr();

                                cachedList.add(new com.vn.jet.mosco.utils.DatabaseLoader.UserInventoryItem(
                                        userCard.getId(),
                                        userCard.getCollectionId(),
                                        frontImage,
                                        userCard.getLevel(),
                                        userCard.getExp(),
                                        userCard.getUpgradeLevel(),
                                        ovr
                                ));
                            }
                        }
                        DatabaseLoader.cachedUserInventory = cachedList;
                    } else {
                        showConnectionError();
                        return;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Lỗi kết nối Server: " + e.getMessage());
                    showConnectionError();
                    return;
                }
            }
        });
        
        isResourceLoadFinished = true;

        // ShowcaseAssetPreload & GPUTextureWarmup đã bị loại bỏ.
        // Glide tự quản lý Disk Cache + Memory Cache, HomeFragment sẽ tự load khi cần.
        // Việc decode hàng trăm bitmap tại Splash gây lag 10-30 giây mỗi lần mở app là không chấp nhận được.

        // Đảm bảo splash hiện đủ lâu để Lottie animation trông mượt
        long totalLoadingTime = System.currentTimeMillis() - splashStartTime;
        Log.d(TAG, "Tổng thời gian render Splash: " + totalLoadingTime + "ms");
        
        long waitTime = Math.max(0, 2000 - totalLoadingTime);

        mainHandler.postDelayed(() -> {
            if (isErrorShown || !isResourceLoadFinished || isNavigating || isFinishing()) return;
            isNavigating = true;
            
            SessionManager sessionManager = new SessionManager(SplashActivity.this);
            Intent intent;
            if (sessionManager.isLoggedIn()) {
                if (sessionManager.getIngameName() == null || sessionManager.getIngameName().isEmpty()) {
                    intent = new Intent(SplashActivity.this, DisplayNameSetupActivity.class);
                } else {
                    intent = new Intent(SplashActivity.this, MainActivity.class);
                }
            } else {
                intent = new Intent(SplashActivity.this, OnboardingActivity.class);
            }
            
            if (driftX != null && driftY != null) {
                intent.putExtra("EXTRA_PLAY_TIME_X", driftX.getCurrentPlayTime());
                intent.putExtra("EXTRA_PLAY_TIME_Y", driftY.getCurrentPlayTime());
            }
            
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, waitTime);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return false;
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void showConnectionError() {
        if (isErrorShown) return;
        isErrorShown = true;
        
        mainHandler.post(() -> {
            isResourceLoadFinished = false;
            layoutDownloadProgress.setVisibility(View.GONE);
            lottieSplash.setVisibility(View.GONE);
            layoutRetryConnection.setVisibility(View.VISIBLE);
        });
    }

    private void setupParallax() {
        if (ivBackground != null) {
            ivBackground.setScaleType(ImageView.ScaleType.CENTER_CROP);
            ivBackground.setScaleX(1.3f);
            ivBackground.setScaleY(1.3f);

            driftX = ObjectAnimator.ofFloat(ivBackground, "translationX", -60f, 60f);
            driftX.setDuration(15000); 
            driftX.setRepeatMode(ValueAnimator.REVERSE);
            driftX.setRepeatCount(ValueAnimator.INFINITE);

            driftY = ObjectAnimator.ofFloat(ivBackground, "translationY", -40f, 40f);
            driftY.setDuration(20000); 
            driftY.setRepeatMode(ValueAnimator.REVERSE);
            driftY.setRepeatCount(ValueAnimator.INFINITE);

            driftX.start();
            driftY.start();
        }
    }

    /**
     * Format số với dấu phẩy phân cách hàng nghìn: 1234 → "1,234"
     */
    private String formatNumber(int number) {
        return String.format("%,d", number);
    }

    /**
     * Preload showcase images to GPU texture memory.
     * This forces Glide to decode bitmaps and upload them to GPU during splash,
     * so when HomeFragment opens, images are already in GPU memory and display instantly.
     */
    private void preloadShowcaseToGPU(java.util.List<String> assetUrls) {
        if (assetUrls.isEmpty()) return;
        
        try {
            int targetWidth = OBJET_WIDTH;
            int targetHeight = OBJET_HEIGHT;
            
            for (String url : assetUrls) {
                if (url == null || url.isEmpty()) continue;
                
                java.io.File localFile = com.vn.jet.mosco.utils.CardAssetManager.getLocalFile(getApplicationContext(), url);
                
                if (localFile != null && localFile.exists()) {
                    android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
                    options.inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888;
                    options.inPreferQualityOverSpeed = true;
                    
                    android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(localFile.getAbsolutePath(), options);
                    
                    if (bitmap != null) {
                        android.graphics.Bitmap scaledBitmap = android.graphics.Bitmap.createScaledBitmap(
                            bitmap, targetWidth, targetHeight, true
                        );
                        
                        if (scaledBitmap != bitmap) {
                            bitmap.recycle();
                        }
                        
                        scaledBitmap.prepareToDraw();
                        
                        com.bumptech.glide.Glide.with(getApplicationContext())
                            .asBitmap()
                            .load(scaledBitmap)
                            .override(targetWidth, targetHeight)
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                            .skipMemoryCache(false)
                            .submit()
                            .get();
                    }
                } else {
                    com.bumptech.glide.Glide.with(getApplicationContext())
                        .asBitmap()
                        .load(url)
                        .override(targetWidth, targetHeight)
                        .priority(com.bumptech.glide.Priority.HIGH)
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.RESOURCE)
                        .submit()
                        .get();
                }
            }
            
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error preloading showcase to GPU: " + e.getMessage());
        }
    }
}
