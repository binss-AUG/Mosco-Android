package com.vn.jet.mosco;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.airbnb.lottie.LottieAnimationView;
import com.vn.jet.mosco.model.AuthResponse;
import com.vn.jet.mosco.model.ResetPasswordRequest;
import com.vn.jet.mosco.network.ApiClient;
import com.vn.jet.mosco.network.AuthApiService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ForgotPasswordActivity extends AppCompatActivity {

    private TextInputEditText edtEmail, edtVerificationCode, edtPassword;
    private Button btnSendCode, btnResetPassword;
    private LottieAnimationView loadingProgress;
    private ImageView ivBackground;
    private ObjectAnimator driftX, driftY;
    private AuthApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        // Ánh xạ views
        edtEmail = findViewById(R.id.edt_email);
        edtVerificationCode = findViewById(R.id.edt_verification_code);
        edtPassword = findViewById(R.id.edt_password);
        btnSendCode = findViewById(R.id.btn_send_code);
        btnResetPassword = findViewById(R.id.btn_reset_password);
        loadingProgress = findViewById(R.id.loading_progress);
        ImageView btnBack = findViewById(R.id.btn_back);
        ivBackground = findViewById(R.id.iv_background_parallax);

        apiService = ApiClient.getClient(this).create(AuthApiService.class);

        // Nhận thời gian chạy Animation từ màn trước
        long playTimeX = getIntent().getLongExtra("EXTRA_PLAY_TIME_X", 0L);
        long playTimeY = getIntent().getLongExtra("EXTRA_PLAY_TIME_Y", 0L);

        // Kích hoạt cơ chế trôi nền đồng bộ 2026
        setupAmbientEffects(playTimeX, playTimeY);

        btnSendCode.setOnClickListener(v -> handleSendCode());
        btnResetPassword.setOnClickListener(v -> handleResetPassword());
        btnBack.setOnClickListener(v -> finish());
    }

    private void setupAmbientEffects(long playTimeX, long playTimeY) {
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
            driftX.setCurrentPlayTime(playTimeX);
            
            driftY.start();
            driftY.setCurrentPlayTime(playTimeY);
        }

        View glassCard = findViewById(R.id.glass_container);
        if (glassCard != null) {
            Animation breathing = AnimationUtils.loadAnimation(this, R.anim.anim_neon_breathing);
            glassCard.startAnimation(breathing);
        }
    }

    private void handleSendCode() {
        String email = edtEmail.getText().toString().trim();
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            edtEmail.setError("Email không hợp lệ!");
            return;
        }

        setLoading(true);
        apiService.forgotPassword(email).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                setLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    Toast.makeText(ForgotPasswordActivity.this, response.body().getMessage(), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(ForgotPasswordActivity.this, "Email không tồn tại hoặc lỗi kết nối!", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                setLoading(false);
                Toast.makeText(ForgotPasswordActivity.this, "Lỗi server: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleResetPassword() {
        String email = edtEmail.getText().toString().trim();
        String code = edtVerificationCode.getText().toString().trim();
        String pass = edtPassword.getText().toString().trim();

        if (email.isEmpty() || code.length() < 6 || pass.length() < 6) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin (Code 6 số, Password >= 6 ký tự)", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        ResetPasswordRequest request = new ResetPasswordRequest(email, code, pass);
        apiService.resetPassword(request).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                setLoading(false);
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Toast.makeText(ForgotPasswordActivity.this, "Đổi mật khẩu thành công!", Toast.LENGTH_LONG).show();
                    finish(); // Quay lại màn hình đăng nhập
                } else {
                    String msg = (response.body() != null) ? response.body().getMessage() : "Mã xác nhận sai hoặc hết hạn!";
                    Toast.makeText(ForgotPasswordActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                handleResetFailure(t);
            }
        });
    }
    
    // Fallback cho onFailure Reset
    private void handleResetFailure(Throwable t) {
        setLoading(false);
        Toast.makeText(ForgotPasswordActivity.this, "Lỗi: " + t.getMessage(), Toast.LENGTH_SHORT).show();
    }

    private void setLoading(boolean isLoading) {
        loadingProgress.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnResetPassword.setEnabled(!isLoading);
        btnSendCode.setEnabled(!isLoading);
    }
}
