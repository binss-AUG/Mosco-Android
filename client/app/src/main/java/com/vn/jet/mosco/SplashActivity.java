package com.vn.jet.mosco;

import com.vn.jet.mosco.utils.AuthUIHelper;
import com.vn.jet.mosco.utils.GalacticBackgroundView;

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

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import com.vn.jet.mosco.utils.CardAssetManager;
import com.vn.jet.mosco.utils.DatabaseLoader;
import com.vn.jet.mosco.utils.SessionManager;
import com.vn.jet.mosco.network.ApiClient;
import com.vn.jet.mosco.network.GameApiService;

public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";

    public static final int OBJET_WIDTH = 300;
    public static final int OBJET_HEIGHT = 462;

    private LinearLayout layoutDownloadProgress;
    private View layoutRetryConnection;
    private com.google.android.material.button.MaterialButton btnRetryConnection;
    private ProgressBar pbDownload;
    private TextView tvDownloadStatus;
    private TextView tvDownloadCount;
    private com.airbnb.lottie.LottieAnimationView lottieSplash;
    
    private Handler mainHandler;
    private volatile boolean isResourceLoadFinished = false;
    private volatile boolean isNavigating = false; 
    private volatile boolean isErrorShown = false;
    private static volatile boolean isCheckStarted = false;

    private long splashStartTime;
    private java.util.Map<String, Long> renderMetrics = new java.util.HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            isCheckStarted = false;
        }
        super.onCreate(savedInstanceState);
        splashStartTime = System.currentTimeMillis();
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);

        GalacticBackgroundView galacticBg = findViewById(R.id.galactic_bg);
        if (galacticBg != null) {
            galacticBg.setMode(GalacticBackgroundView.Mode.SPLASH);
        }

        AuthUIHelper.animateAurora(this);

        SessionManager sessionManager = new SessionManager(this);
        int targetMode = sessionManager.isDarkMode() ? 
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES : 
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
        
        if (androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode() != targetMode) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(targetMode);
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        layoutDownloadProgress = findViewById(R.id.layout_download_progress);
        layoutRetryConnection = findViewById(R.id.layout_retry_connection);
        btnRetryConnection = findViewById(R.id.btn_retry_connection);
        pbDownload = findViewById(R.id.pb_download);
        tvDownloadStatus = findViewById(R.id.tv_download_status);
        tvDownloadCount = findViewById(R.id.tv_download_count);
        lottieSplash = findViewById(R.id.iv_logo_lottie); 
        
        mainHandler = new Handler(Looper.getMainLooper());

        btnRetryConnection.setOnClickListener(v -> {
            isCheckStarted = false; // Allow retry
            layoutRetryConnection.setVisibility(View.GONE);
            lottieSplash.setVisibility(View.VISIBLE);
            new Thread(this::checkAndLoadResources).start();
        });

        trackRenderTime("Init", () -> {
            new Thread(this::checkAndLoadResources).start();
        });
    }

    private void trackRenderTime(String component, Runnable action) {
        long start = System.currentTimeMillis();
        action.run();
        long end = System.currentTimeMillis();
        renderMetrics.put(component, end - start);
    }

    private void checkAndLoadResources() {
        if (isCheckStarted) return;
        isCheckStarted = true;
        
        isResourceLoadFinished = false;
        isErrorShown = false;
        
        Runnable timeoutRunnable = () -> {
            if (!isResourceLoadFinished && !isFinishing() && !isNavigating) {
                showConnectionError();
            }
        };

        mainHandler.postDelayed(timeoutRunnable, 8000);

        if (!isNetworkAvailable()) {
            showConnectionError();
            return;
        }

        // Kích hoạt nạp Master Data (O(1) lookup cho 10k thẻ)
        DatabaseLoader.initMasterData(getApplicationContext());

        boolean allReady = CardAssetManager.isAllAssetsReadyQuick(getApplicationContext());

        if (!allReady) {
            // Kiểm tra sâu: Quét từng file 2x dựa trên database.json (Master Data)
            allReady = CardAssetManager.isAllAssetsReady(getApplicationContext());
        }

        if (!allReady) {
            mainHandler.removeCallbacks(timeoutRunnable);
            mainHandler.postDelayed(timeoutRunnable, 600000);

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
                        Thread.sleep(30);
                        int increment = 0;
                        if (displayedProgress[0] < 80) {
                            increment = (int) (Math.random() * 5) + 3;
                        } else if (displayedProgress[0] < 98) {
                            if (Math.random() > 0.5) increment = 1;
                        } else {
                            if (realPercent[0] >= 100) increment = 1;
                        }

                        if (increment > 0) {
                            displayedProgress[0] += increment;
                            if (displayedProgress[0] > 100) displayedProgress[0] = 100;
                            final int val = displayedProgress[0];
                            mainHandler.post(() -> pbDownload.setProgress(val));
                        }
                    } catch (InterruptedException e) { break; }
                }
            }).start();

            CardAssetManager.downloadAllAssets(getApplicationContext(), new CardAssetManager.DownloadProgressListener() {
                @Override
                public void onProgress(int downloaded, int total, String currentFile) {
                    realPercent[0] = (int) ((downloaded / (float) total) * 100);
                    mainHandler.post(() -> tvDownloadCount.setText(formatNumber(downloaded) + " / " + formatNumber(total)));
                }

                @Override
                public void onComplete() {
                    realPercent[0] = 100;
                    synchronized (downloadLock) { downloadLock.notifyAll(); }
                }

                @Override
                public void onError(String errorMessage) {
                    realPercent[0] = 100;
                    synchronized (downloadLock) { downloadLock.notifyAll(); }
                }
            });

            synchronized (downloadLock) {
                try { downloadLock.wait(600000); } catch (InterruptedException ignored) {}
            }
            
            displayedProgress[0] = 100;
            mainHandler.post(() -> {
                pbDownload.setProgress(100);
                tvDownloadStatus.setText("Sẵn sàng!");
            });
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        }

        mainHandler.post(() -> layoutDownloadProgress.setVisibility(View.GONE));

        SessionManager sessionManager = new SessionManager(SplashActivity.this);
        Long userId = sessionManager.getUserId();
        if (userId != null && sessionManager.isLoggedIn()) {
            DatabaseLoader.loadInventoryFromLocal(SplashActivity.this, userId);
            try {
                com.vn.jet.mosco.network.GameApiService apiService =
                        com.vn.jet.mosco.network.ApiClient.getClient(SplashActivity.this)
                                .create(com.vn.jet.mosco.network.GameApiService.class);
                retrofit2.Response<java.util.List<com.vn.jet.mosco.model.UserCard>> response =
                        apiService.getUserCards(userId).execute();
                if (response.isSuccessful() && response.body() != null) {
                    java.util.List<DatabaseLoader.UserInventoryItem> newList = new java.util.ArrayList<>();
                    for (com.vn.jet.mosco.model.UserCard userCard : response.body()) {
                        newList.add(com.vn.jet.mosco.utils.DatabaseLoader.UserInventoryItem.fromUserCard(userCard));
                    }
                    DatabaseLoader.cachedUserInventory = newList;
                    DatabaseLoader.saveInventoryToLocal(SplashActivity.this, userId, newList);
                }
            } catch (Exception e) {
                Log.w(TAG, "Lỗi kết nối Server: " + e.getMessage() + ". Sử dụng dữ liệu cache.");
            }
        }
        
        isResourceLoadFinished = true;

        long totalLoadingTime = System.currentTimeMillis() - splashStartTime;
        long waitTime = Math.max(0, 2000 - totalLoadingTime);

        mainHandler.postDelayed(() -> {
            if (isErrorShown || !isResourceLoadFinished || isNavigating || isFinishing()) return;
            isNavigating = true;
            
            SessionManager sm = new SessionManager(SplashActivity.this);
            Intent intent;
            if (sm.isLoggedIn()) {
                if (sm.getIngameName() == null || sm.getIngameName().isEmpty()) {
                    intent = new Intent(SplashActivity.this, DisplayNameSetupActivity.class);
                } else {
                    intent = new Intent(SplashActivity.this, MainActivity.class);
                }
            } else {
                intent = new Intent(SplashActivity.this, OnboardingActivity.class);
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

    private String formatNumber(int number) {
        return String.format("%,d", number);
    }

    @Override
    protected void onPause() {
        super.onPause();
        com.vn.jet.mosco.utils.AuthUIHelper.saveAnimationState();
    }
}
