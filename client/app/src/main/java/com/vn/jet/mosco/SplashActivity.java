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
import android.net.Network;
import android.net.NetworkRequest;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import com.vn.jet.mosco.utils.CardAssetManager;
import com.vn.jet.mosco.utils.DatabaseLoader;
import com.vn.jet.mosco.utils.SessionManager;
import com.vn.jet.mosco.network.ApiClient;
import com.vn.jet.mosco.network.GameApiService;

public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";

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
    private volatile boolean isCheckStarted = false;
    private volatile boolean isSyncing = false;

    private long splashStartTime;
    private long downloadStartTime;
    private ConnectivityManager.NetworkCallback networkCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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

        layoutDownloadProgress.setVisibility(View.GONE);
        tvDownloadStatus.setVisibility(View.VISIBLE);
        tvDownloadStatus.setText(R.string.splash_status_connecting);

        // [BUG 6] Logo Professional Animation
        if (lottieSplash != null) {
            lottieSplash.setAlpha(0f);
            lottieSplash.animate().alpha(1f).setDuration(1200).start();
        }

        btnRetryConnection.setOnClickListener(v -> {
            isCheckStarted = false;
            layoutRetryConnection.setVisibility(View.GONE);
            lottieSplash.setVisibility(View.VISIBLE);
            tvDownloadStatus.setVisibility(View.VISIBLE);
            new Thread(this::checkAndLoadResources).start();
        });

        setupNetworkMonitoring();

        mainHandler.postDelayed(() -> {
            new Thread(this::checkAndLoadResources).start();
        }, 800);
    }

    private void setupNetworkMonitoring() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return;

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onLost(Network network) {
                if (isSyncing) {
                    mainHandler.post(() -> showConnectionError());
                }
            }
        };

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        cm.registerNetworkCallback(request, networkCallback);
    }

    private void checkAndLoadResources() {
        if (isCheckStarted) return;
        isCheckStarted = true;
        isResourceLoadFinished = false;
        isErrorShown = false;
        
        if (!isNetworkAvailable()) {
            showConnectionError();
            return;
        }

        // 1. Kiểm tra và tải Starter Pack nếu cần
        if (!com.vn.jet.mosco.utils.StarterPackManager.isDbInitialized(this)) {
            mainHandler.post(() -> {
                layoutDownloadProgress.setVisibility(View.VISIBLE);
                tvDownloadStatus.setText(R.string.splash_status_downloading);
            });
            
            com.vn.jet.mosco.utils.StarterPackManager.downloadAndInitDb(this, new com.vn.jet.mosco.utils.StarterPackManager.ProgressListener() {
                @Override
                public void onProgress(int percent) {
                    mainHandler.post(() -> pbDownload.setProgress(percent));
                }

                @Override
                public void onComplete() {
                    mainHandler.post(() -> {
                        layoutDownloadProgress.setVisibility(View.GONE);
                        // Chỉ vào tiếp khi DB đã sẵn sàng
                        new Thread(SplashActivity.this::continueLoading).start();
                    });
                }

                @Override
                public void onError(String error) {
                    mainHandler.post(() -> showConnectionError());
                }
            });
        } else {
            continueLoading();
        }
    }

    private void continueLoading() {
        // 1. Master Data (Blocking on this background thread)
        DatabaseLoader.initMasterDataSync(getApplicationContext());
        
        // 2. Pre-fetch Session (Concurrent)
        GameApiService apiService = ApiClient.getClient(this).create(GameApiService.class);
        preFetchUserSession(apiService);
        
        // 3. Galactic Metadata Sync (Priority)
        mainHandler.post(() -> {
            tvDownloadStatus.setText(R.string.splash_status_checking);
            
            DatabaseLoader.syncMetadataWithServer(this, new DatabaseLoader.SyncCallback() {
                @Override
                public void onUpdateAvailable(long remoteTimestamp, float sizeMb) {
                    showUpdateConfirmationDialog(remoteTimestamp, sizeMb);
                }

                @Override
                public void onNoUpdate() {
                    // Tiếp tục tải asset ảnh nếu cần
                    syncAssets();
                }

                @Override
                public void onProgress(int percent) {
                    mainHandler.post(() -> {
                        layoutDownloadProgress.setVisibility(View.VISIBLE);
                        pbDownload.setProgress(percent);
                    });
                }

                @Override
                public void onComplete() {
                    mainHandler.post(() -> {
                        tvDownloadStatus.setText(R.string.splash_status_loading);
                        syncAssets();
                    });
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "Sync Error: " + error);
                    syncAssets(); // Vẫn cho vào app bằng data cũ
                }
            });
        });
    }

    private void showUpdateConfirmationDialog(long timestamp, float sizeMb) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_update_metadata, null);
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.setContentView(dialogView);
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            // Làm mờ nền phía sau
            dialog.getWindow().setDimAmount(0.8f);
        }

        TextView tvMsg = dialogView.findViewById(R.id.tv_dialog_message);
        TextView tvSize = dialogView.findViewById(R.id.tv_update_size);
        com.vn.jet.mosco.widget.MoscoButton btnExit = dialogView.findViewById(R.id.btn_exit);
        com.vn.jet.mosco.widget.MoscoButton btnUpdate = dialogView.findViewById(R.id.btn_update_now);

        tvMsg.setText(R.string.splash_dialog_update_msg_premium); 
        tvSize.setText(String.format(getString(R.string.splash_dialog_update_size_format), sizeMb));

        btnExit.setOnClickListener(v -> {
            dialog.dismiss();
            finishAffinity();
            System.exit(0);
        });

        btnUpdate.setOnClickListener(v -> {
            dialog.dismiss();
            mainHandler.post(() -> {
                tvDownloadStatus.setText(R.string.splash_status_downloading);
                layoutDownloadProgress.setVisibility(View.VISIBLE);
            });
            DatabaseLoader.pullFullDatabase(getApplicationContext(), timestamp, new DatabaseLoader.SyncCallback() {
                @Override public void onUpdateAvailable(long t, float s) {}
                @Override public void onNoUpdate() {}
                @Override public void onProgress(int p) { mainHandler.post(() -> pbDownload.setProgress(p)); }
                @Override public void onComplete() { 
                    mainHandler.post(() -> {
                        tvDownloadStatus.setText(R.string.splash_status_loading);
                        syncAssets(); 
                    });
                }
                @Override public void onError(String e) { syncAssets(); }
            });
        });

        dialog.setCancelable(false);
        dialog.show();

        // ÉP CHIỀU NGANG: Quan trọng để tránh lỗi "cây tăm"
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }

    private void syncAssets() {
        mainHandler.post(() -> tvDownloadStatus.setText(R.string.splash_status_preparing));
        // Tải ảnh ngầm (không block UI nữa)
        CardAssetManager.DownloadInfo info = CardAssetManager.getPendingDownloadInfo(this);
        if (info.pendingCount > 0) {
            // Chỉ chạy ngầm, không hiện dialog cellular ở đây để tránh phiền
            // Glide sẽ lo việc nạp ảnh khi cần.
            new Thread(() -> CardAssetManager.startDownloadWithInfo(this, info, isWifiConnected(), null)).start();
        }
        
        mainHandler.post(() -> {
            tvDownloadStatus.setText(R.string.splash_status_loading);
            isResourceLoadFinished = true;
            navigateToNextScreen();
        });
    }

    private void startActualDownload(CardAssetManager.DownloadInfo info, boolean isWifi) {
        isSyncing = true;
        downloadStartTime = System.currentTimeMillis();
        final Object downloadLock = new Object();
        final int[] realPercent = {0};
        final int[] displayedProgress = {0};

        mainHandler.post(() -> {
            layoutDownloadProgress.setVisibility(View.VISIBLE);
            tvDownloadStatus.setText(R.string.splash_status_downloading);
            pbDownload.setProgress(0);
        });

        // Fast Smooth Progress Thread
        new Thread(() -> {
            while (displayedProgress[0] < 100 && isSyncing) {
                try {
                    Thread.sleep(40);
                    int increment = 0;
                    if (displayedProgress[0] < 80) {
                        increment = (int) (Math.random() * 3) + 1;
                    } else if (displayedProgress[0] < 98) {
                        if (Math.random() > 0.8) increment = 1;
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
            synchronized (downloadLock) { downloadLock.notifyAll(); }
        }).start();

        CardAssetManager.startDownloadWithInfo(this, info, isWifi, new CardAssetManager.DownloadProgressListener() {
            @Override
            public void onProgress(int downloaded, int total, String imageId) {
                if (!isSyncing) return;
                realPercent[0] = (int) ((downloaded / (float) total) * 100);
                long elapsedSeconds = (System.currentTimeMillis() - downloadStartTime) / 1000;
                mainHandler.post(() -> {
                    String statusText = String.format("#%s | %ds", 
                            imageId != null ? imageId.substring(0, Math.min(6, imageId.length())) : "...", 
                            elapsedSeconds);
                    tvDownloadCount.setText(statusText);
                });
            }
            @Override public void onComplete() { 
                realPercent[0] = 100; 
                isSyncing = false;
            }
            @Override public void onError(String errorMessage) {
                isSyncing = false;
                mainHandler.post(() -> showConnectionError());
            }
        });

        // Bỏ lệnh wait() để đạt mục tiêu 1.5s app entry. 
        // Việc tải ảnh sẽ diễn ra ngầm trong khi người dùng đã vào Home.
        isResourceLoadFinished = true;
        mainHandler.post(() -> {
            tvDownloadStatus.setText(R.string.splash_status_loading);
            navigateToNextScreen();
        });
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnected();
    }

    private boolean isWifiConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;
    }

    private void showCellularConfirmationDialog(CardAssetManager.DownloadInfo info) {
        mainHandler.post(() -> {
            new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle(getString(R.string.splash_dialog_cellular_title))
                .setMessage(String.format(getString(R.string.splash_dialog_cellular_msg), info.estimatedSizeMB))
                .setPositiveButton(getString(R.string.splash_action_download), (dialog, which) -> {
                    new Thread(() -> startActualDownload(info, false)).start();
                })
                .setNegativeButton(getString(R.string.splash_action_exit), (dialog, which) -> finish())
                .setCancelable(false)
                .show();
        });
    }

    private void preFetchUserSession(GameApiService apiService) {
        SessionManager sm = new SessionManager(this);
        if (sm.isLoggedIn()) {
            Long userId = sm.getUserId();
            DatabaseLoader.loadInventoryFromLocal(this, userId);
            
            // Chuyen sang Enqueue de khong block luong hien tai
            apiService.getUserStats(userId).enqueue(new retrofit2.Callback<com.vn.jet.mosco.model.UserStats>() {
                @Override
                public void onResponse(retrofit2.Call<com.vn.jet.mosco.model.UserStats> call, retrofit2.Response<com.vn.jet.mosco.model.UserStats> response) {
                    if (response.isSuccessful()) {
                        DatabaseLoader.reloadInventoryFromServer(SplashActivity.this, userId, apiService);
                    }
                }
                @Override public void onFailure(retrofit2.Call<com.vn.jet.mosco.model.UserStats> call, Throwable t) {
                    Log.w(TAG, "Failed to pre-fetch session, continuing with local data");
                }
            });
        }
    }

    private void navigateToNextScreen() {
        if (!isResourceLoadFinished || isNavigating) return;
        long waitTime = Math.max(0, 2500 - (System.currentTimeMillis() - splashStartTime));
        mainHandler.postDelayed(() -> {
            if (isErrorShown || isNavigating || isFinishing()) return;
            isNavigating = true;
            SessionManager sm = new SessionManager(this);
            Intent intent = sm.isLoggedIn() ? 
                    (sm.getIngameName() == null || sm.getIngameName().isEmpty() ? new Intent(this, DisplayNameSetupActivity.class) : new Intent(this, MainActivity.class)) : 
                    new Intent(this, OnboardingActivity.class);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, waitTime);
    }

    private void showConnectionError() {
        if (isErrorShown) return;
        isErrorShown = true;
        isSyncing = false; 
        mainHandler.post(() -> {
            layoutDownloadProgress.setVisibility(View.GONE);
            tvDownloadStatus.setVisibility(View.GONE); // Hide "Downloading..." status
            lottieSplash.setVisibility(View.GONE);
            layoutRetryConnection.setVisibility(View.VISIBLE);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (networkCallback != null) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm != null) cm.unregisterNetworkCallback(networkCallback);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        AuthUIHelper.saveAnimationState();
    }
}
