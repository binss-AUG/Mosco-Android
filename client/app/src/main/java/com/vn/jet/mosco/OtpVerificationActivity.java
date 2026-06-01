package com.vn.jet.mosco;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.vn.jet.mosco.model.ApiResponse;
import com.vn.jet.mosco.model.AuthRequest;
import com.vn.jet.mosco.model.AuthResponse;
import com.vn.jet.mosco.model.ResetPasswordRequest;
import com.vn.jet.mosco.network.ApiClient;
import com.vn.jet.mosco.network.AuthApiService;
import com.vn.jet.mosco.network.GameApiService;
import com.vn.jet.mosco.utils.AuthUIHelper;
import com.vn.jet.mosco.utils.ClickDebounce;
import com.vn.jet.mosco.utils.SessionManager;
import com.vn.jet.mosco.utils.ErrorTranslator;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * OtpVerificationActivity - Handles dedicated OTP verification flow.
 * Supports Sign Up, Forgot Password, and Delete Account verification.
 * 100% pure Java, Vietnamese explanations of complex logic.
 */
public class OtpVerificationActivity extends AppCompatActivity {

    private TextInputEditText edtVerificationCode;
    private TextInputLayout tilVerificationCode;
    private TextView tvSubtitleVerify, tvTimerLabel, btnResend;
    private com.vn.jet.mosco.widget.MoscoButton btnVerify;
    private com.airbnb.lottie.LottieAnimationView loadingProgress;
    private ImageView btnBack;

    private String flowType; // "signup", "forgot_password", or "delete_account"
    private String email;
    private String username;
    private String password;

    private AuthApiService apiService;
    private SessionManager sessionManager;
    private CountDownTimer countDownTimer;
    private boolean isTimerRunning = false;
    private boolean isVerifying = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp_verification);

        AuthUIHelper.animateAurora(this);

        // Đọc dữ liệu từ Intent
        Intent intent = getIntent();
        if (intent != null) {
            flowType = intent.getStringExtra("flow_type");
            email = intent.getStringExtra("email");
            username = intent.getStringExtra("username");
            password = intent.getStringExtra("password");
        }

        if (email == null) {
            Toast.makeText(this, getString(R.string.settings_delete_email_missing), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Khởi tạo các view
        edtVerificationCode = findViewById(R.id.edt_verification_code);
        tilVerificationCode = findViewById(R.id.til_verification_code);
        tvSubtitleVerify = findViewById(R.id.tv_subtitle_verify);
        tvTimerLabel = findViewById(R.id.tv_timer_label);
        btnResend = findViewById(R.id.btn_resend);
        btnVerify = findViewById(R.id.btn_verify);
        loadingProgress = findViewById(R.id.loading_progress);
        btnBack = findViewById(R.id.btn_back);

        apiService = ApiClient.getClient(this).create(AuthApiService.class);
        sessionManager = new SessionManager(this);

        // Hiển thị email trong phụ đề hướng dẫn nhập mã
        tvSubtitleVerify.setText(getString(R.string.auth_subtitle_verify, email));

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // Bắt đầu đếm ngược thời gian gửi lại mã khi vừa vào màn hình
        startCountdown();

        btnResend.setOnClickListener(new ClickDebounce() {
            @Override
            public void onDebouncedClick(View v) {
                if (isTimerRunning || isVerifying) return;
                handleResendCode();
            }
        });

        btnVerify.setOnClickListener(new ClickDebounce() {
            @Override
            public void onDebouncedClick(View v) {
                if (isVerifying) return;
                handleVerify();
            }
        });
    }

    /**
     * Bắt đầu đếm ngược 60 giây trước khi cho phép gửi lại mã OTP.
     * Giải thích: Chức năng này giúp tránh spam API gửi mã OTP lên Server.
     */
    private void startCountdown() {
        if (isTimerRunning) return;

        isTimerRunning = true;
        btnResend.setEnabled(false);
        btnResend.setTextColor(ContextCompat.getColor(this, R.color.lg_text_disabled));

        countDownTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                btnResend.setText(String.format(getString(R.string.auth_format_resend_timer), millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                isTimerRunning = false;
                btnResend.setEnabled(true);
                btnResend.setText(getString(R.string.auth_action_resend));
                btnResend.setTextColor(ContextCompat.getColor(OtpVerificationActivity.this, R.color.lg_accent_primary));
            }
        }.start();
    }

    private void handleResendCode() {
        setLoading(true);
        Call<AuthResponse> call;
        if ("signup".equals(flowType) || "delete_account".equals(flowType)) {
            // TẠI SAO: Flow signup và delete_account cùng dùng sendCode để gửi OTP xác thực email
            call = apiService.sendCode(email);
        } else {
            call = apiService.forgotPassword(email);
        }

        call.enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(@NonNull Call<AuthResponse> call, @NonNull Response<AuthResponse> response) {
                setLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    Toast.makeText(OtpVerificationActivity.this, ErrorTranslator.translate(OtpVerificationActivity.this, response.body().getMessage()), Toast.LENGTH_SHORT).show();
                    startCountdown();
                } else {
                    String errorMsg = response.body() != null ? response.body().getMessage() : getString(R.string.settings_delete_otp_send_failed);
                    Toast.makeText(OtpVerificationActivity.this, ErrorTranslator.translate(OtpVerificationActivity.this, errorMsg), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<AuthResponse> call, @NonNull Throwable t) {
                setLoading(false);
                Toast.makeText(OtpVerificationActivity.this, getString(R.string.common_error_network), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleVerify() {
        String code = edtVerificationCode.getText().toString().trim();
        tilVerificationCode.setError(null);

        if (code.isEmpty()) {
            tilVerificationCode.setError(getString(R.string.auth_error_empty_field));
            return;
        }
        if (code.length() < 6) {
            tilVerificationCode.setError(getString(R.string.auth_error_short_code));
            return;
        }

        setLoading(true);
        if ("signup".equals(flowType)) {
            AuthRequest request = new AuthRequest(username, email, password, code);
            apiService.signup(request).enqueue(new Callback<AuthResponse>() {
                @Override
                public void onResponse(@NonNull Call<AuthResponse> call, @NonNull Response<AuthResponse> response) {
                    setLoading(false);
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        sessionManager.saveSession(response.body().getData());
                        Toast.makeText(OtpVerificationActivity.this, getString(R.string.auth_msg_create_account_success), Toast.LENGTH_SHORT).show();

                        Intent intent;
                        String ingame = (response.body().getData() != null) ? response.body().getData().getIngameName() : null;
                        if (ingame == null || ingame.isEmpty()) {
                            intent = new Intent(OtpVerificationActivity.this, DisplayNameSetupActivity.class);
                        } else {
                            intent = new Intent(OtpVerificationActivity.this, MainActivity.class);
                        }
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        String msg = (response.body() != null) ? response.body().getMessage() : getString(R.string.common_error_unknown);
                        tilVerificationCode.setError(msg);
                    }
                }

                @Override
                public void onFailure(@NonNull Call<AuthResponse> call, @NonNull Throwable t) {
                    setLoading(false);
                    Toast.makeText(OtpVerificationActivity.this, getString(R.string.common_error_network), Toast.LENGTH_SHORT).show();
                }
            });
        } else if ("delete_account".equals(flowType)) {
            // Luồng xóa tài khoản: Gửi OTP lên server để thực hiện Soft Delete
            GameApiService gameApiService = ApiClient.getClient(this).create(GameApiService.class);
            gameApiService.deleteAccount(code).enqueue(new Callback<ApiResponse<Void>>() {
                @Override
                public void onResponse(@NonNull Call<ApiResponse<Void>> call, @NonNull Response<ApiResponse<Void>> response) {
                    setLoading(false);
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        Toast.makeText(OtpVerificationActivity.this, getString(R.string.settings_delete_success), Toast.LENGTH_LONG).show();
                        // Xóa phiên đăng nhập hiện tại và quay về màn hình đăng nhập
                        sessionManager.clearSession();
                        Intent intent = new Intent(OtpVerificationActivity.this, SignInActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        String msg = (response.body() != null) ? response.body().getMessage() : getString(R.string.settings_delete_verify_failed);
                        tilVerificationCode.setError(msg);
                    }
                }

                @Override
                public void onFailure(@NonNull Call<ApiResponse<Void>> call, @NonNull Throwable t) {
                    setLoading(false);
                    Toast.makeText(OtpVerificationActivity.this, getString(R.string.common_error_network), Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            // flowType = "forgot_password"
            // Khi người dùng bấm Verify ở luồng quên mật khẩu, chuyển tiếp sang màn hình đặt mật khẩu mới
            setLoading(false);
            Intent nextIntent = new Intent(OtpVerificationActivity.this, ResetPasswordActivity.class);
            nextIntent.putExtra("email", email);
            nextIntent.putExtra("code", code);
            startActivity(nextIntent);
        }
    }

    private void setLoading(boolean isLoading) {
        isVerifying = isLoading;
        loadingProgress.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnVerify.setEnabled(!isLoading);
        edtVerificationCode.setEnabled(!isLoading);
        btnBack.setEnabled(!isLoading);

        if (isLoading) {
            btnVerify.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.mosco_btn_disabled)));
            btnVerify.setTextColor(ContextCompat.getColor(this, R.color.lg_text_disabled));
        } else {
            btnVerify.setBackgroundTintList(null);
            btnVerify.setTextColor(ContextCompat.getColor(this, R.color.white));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        com.vn.jet.mosco.utils.GalacticBackgroundView galacticBg = findViewById(R.id.galactic_bg);
        if (galacticBg != null) {
            galacticBg.setMode(com.vn.jet.mosco.utils.GalacticBackgroundView.Mode.SIGN_UP);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        AuthUIHelper.saveAnimationState();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}
