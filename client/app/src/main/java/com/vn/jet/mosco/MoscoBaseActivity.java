package com.vn.jet.mosco;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.vn.jet.mosco.utils.NetworkMonitor;
import com.vn.jet.mosco.utils.SessionManager;

/**
 * MoscoBaseActivity — Base Activity cho toàn bộ các màn hình NGOÀI Auth/Splash.
 * Xử lý tập trung:
 * 1. Session Expired broadcast (401 từ ApiClient) → Blocking Dialog → SignIn
 * 2. Connection Lost → Blocking Dialog (Retry / Exit)
 * 
 * Các Activity kế thừa: MainActivity, GiftActivity, FriendActivity, FormationActivity...
 * KHÔNG kế thừa: SignInActivity, SignUpActivity, ForgotPasswordActivity, SplashActivity
 *   (Các màn Auth tự quản lý connection riêng để tránh dialog chồng lặp)
 */
public class MoscoBaseActivity extends AppCompatActivity {

    private AlertDialog sessionExpiredDialog;
    private AlertDialog connectionLostDialog;

    // Receiver xử lý 401 Session Expired từ ApiClient
    private final BroadcastReceiver sessionExpiredReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (sessionExpiredDialog != null && sessionExpiredDialog.isShowing()) return;
            
            String serverMsg = intent.getStringExtra("message");
            showSessionExpiredDialog(serverMsg);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Đăng ký lắng nghe Session Expired
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(sessionExpiredReceiver, new IntentFilter("com.vn.jet.mosco.SESSION_EXPIRED"));

        // [BUG 9] INSTANT KICK: Check session validity on every Resume
        // Bằng cách gọi 1 API nhẹ, nếu token đã bị máy khác chiếm, server trả 401 ngay lập tức.
        // Interceptor trong ApiClient sẽ bắn broadcast SESSION_EXPIRED.
        checkSessionValidity();

        // Observe network connectivity — hiển thị Connection Lost dialog
        NetworkMonitor.getInstance(this).getIsConnected().observe(this, connected -> {
            if (connected != null && !connected) {
                showConnectionLostDialog();
            } else {
                dismissConnectionLostDialog();
            }
        });
    }

    private void checkSessionValidity() {
        SessionManager sm = new SessionManager(this);
        if (sm.isLoggedIn()) {
            // [BUG 5] Thực hiện 1 API call nhẹ để server verify token. 
            // Nếu Token bị máy khác chiếm (server invalidate), ta sẽ nhận 401.
            com.vn.jet.mosco.network.ApiClient.getClient(this)
                .create(com.vn.jet.mosco.network.GameApiService.class)
                .getUserStats(sm.getUserId())
                .enqueue(new retrofit2.Callback<com.vn.jet.mosco.model.UserStats>() {
                    @Override
                    public void onResponse(retrofit2.Call<com.vn.jet.mosco.model.UserStats> call, retrofit2.Response<com.vn.jet.mosco.model.UserStats> response) {
                        // Success -> Token vẫn ổn
                    }
                    @Override
                    public void onFailure(retrofit2.Call<com.vn.jet.mosco.model.UserStats> call, Throwable t) {
                        // Network error -> BaseActivity NetworkMonitor sẽ lo
                    }
                });
        } else {
            // TẠI SAO: Nếu chưa đăng nhập nhưng vẫn bị lỗi mạng/server offline, gọi API public nhẹ để xác minh kết nối
            com.vn.jet.mosco.network.ApiClient.getClient(this)
                .create(com.vn.jet.mosco.network.GameApiService.class)
                .getAssetManifest()
                .enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
                    @Override
                    public void onResponse(retrofit2.Call<okhttp3.ResponseBody> call, retrofit2.Response<okhttp3.ResponseBody> response) {
                        // Success -> Server hoạt động ổn định
                    }
                    @Override
                    public void onFailure(retrofit2.Call<okhttp3.ResponseBody> call, Throwable t) {
                        // Lỗi mạng
                    }
                });
        }
    }

    /**
     * [BUG 3] Standardized Messaging System
     * Hiển thị thông báo dạng Snackbar với style Mosco (Quiet Luxury)
     */
    public void showMoscoMessage(String message) {
        View decorView = getWindow().getDecorView();
        com.google.android.material.snackbar.Snackbar snackbar = 
            com.google.android.material.snackbar.Snackbar.make(decorView, message, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT);
        
        // [BUG 2] Premium Style: White Background, Black Text, Top Layer
        View snackbarView = snackbar.getView();
        snackbarView.setBackgroundColor(Color.WHITE);
        snackbarView.setElevation(100f); // Ensure it's on top
        
        TextView textView = snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);
        if (textView != null) {
            textView.setTextColor(Color.BLACK);
            textView.setTextSize(14f);
            textView.setAllCaps(false);
            textView.setGravity(android.view.Gravity.CENTER);
        }
        snackbar.show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(sessionExpiredReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Dọn dẹp dialog tránh window leak
        if (sessionExpiredDialog != null && sessionExpiredDialog.isShowing()) {
            sessionExpiredDialog.dismiss();
        }
        if (connectionLostDialog != null && connectionLostDialog.isShowing()) {
            connectionLostDialog.dismiss();
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  SESSION EXPIRED DIALOG (401 — JWT hết hạn / Login trùng)
    // ════════════════════════════════════════════════════════════════

    /**
     * Hiển thị dialog blocking thông báo phiên đăng nhập hết hạn.
     * Không dismiss được → phải nhấn "SIGN IN" → về màn Login.
     */
    private void showSessionExpiredDialog(@Nullable String serverMessage) {
        if (isFinishing() || isDestroyed()) return;

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_session_expired, null);
        
        // Ghi đè message nếu server gửi thông tin cụ thể (VD: "Login from another device")
        if (serverMessage != null && !serverMessage.isEmpty()) {
            TextView tvMsg = dialogView.findViewById(R.id.tv_dialog_message);
            if (tvMsg != null) tvMsg.setText(serverMessage);
        }

        sessionExpiredDialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)  // Blocking — không cho dismiss
                .create();

        if (sessionExpiredDialog.getWindow() != null) {
            sessionExpiredDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        dialogView.findViewById(R.id.btn_ok).setOnClickListener(v -> {
            sessionExpiredDialog.dismiss();
            
            // Xóa session và đá về SignIn
            new SessionManager(this).clearSession();
            Intent intent = new Intent(this, SignInActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        sessionExpiredDialog.show();
    }

    // ════════════════════════════════════════════════════════════════
    //  CONNECTION LOST DIALOG (Mất mạng — Blocking)
    // ════════════════════════════════════════════════════════════════

    /**
     * Hiển thị dialog blocking khi mất kết nối mạng.
     * Có 2 nút: Retry (kiểm tra lại) và Exit (thoát app).
     */
    private void showConnectionLostDialog() {
        if (isFinishing() || isDestroyed()) return;
        if (connectionLostDialog != null && connectionLostDialog.isShowing()) return;

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_connection_lost, null);

        connectionLostDialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false) // Blocking — không cho dismiss bằng nút Back
                .create();

        if (connectionLostDialog.getWindow() != null) {
            connectionLostDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        dialogView.findViewById(R.id.btn_exit).setOnClickListener(v -> {
            connectionLostDialog.dismiss();
            finishAffinity();
        });

        dialogView.findViewById(R.id.btn_retry).setOnClickListener(v -> {
            // [BUG 3] Ẩn dialog ngay lập tức
            dismissConnectionLostDialog();
            
            // TẠI SAO: Gọi lại API kiểm tra kết nối server để cập nhật LiveData
            checkSessionValidity();
            
            // Đợi 1s sau mới kiểm tra và hiện lại nếu vẫn mất kết nối
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                Boolean connected = NetworkMonitor.getInstance(this).getIsConnected().getValue();
                if (connected == null || !connected) {
                    showConnectionLostDialog();
                }
            }, 1000);
        });

        connectionLostDialog.show();
    }

    private void dismissConnectionLostDialog() {
        if (connectionLostDialog != null && connectionLostDialog.isShowing()) {
            connectionLostDialog.dismiss();
            connectionLostDialog = null;
        }
    }
}
