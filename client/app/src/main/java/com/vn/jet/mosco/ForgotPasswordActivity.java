package com.vn.jet.mosco;

import com.vn.jet.mosco.utils.AuthUIHelper;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.airbnb.lottie.LottieAnimationView;
import com.vn.jet.mosco.model.AuthResponse;
import com.vn.jet.mosco.model.ResetPasswordRequest;
import com.vn.jet.mosco.network.ApiClient;
import com.vn.jet.mosco.network.AuthApiService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * ForgotPasswordActivity - Handles password reset flow with Galactic effects.
 * Re-enabled parallax background and breathing effects for aesthetics.
 * Fully standardized and professionally localized in English.
 */
public class ForgotPasswordActivity extends AppCompatActivity {

    private TextInputEditText edtEmail;
    private TextInputLayout tilEmail;
    private Button btnResetPassword;
    private LottieAnimationView loadingProgress;
    private AuthApiService apiService;
    private ImageView btnBack;
    

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        AuthUIHelper.animateAurora(this);

        // Map views
        edtEmail = findViewById(R.id.edt_email);
        tilEmail = findViewById(R.id.til_email);
        btnResetPassword = findViewById(R.id.btn_reset_password);
        loadingProgress = findViewById(R.id.loading_progress);
        btnBack = findViewById(R.id.btn_back);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        apiService = ApiClient.getClient(this).create(AuthApiService.class);

        btnResetPassword.setOnClickListener(new com.vn.jet.mosco.utils.ClickDebounce() {
            @Override
            public void onDebouncedClick(View v) {
                handleSendCode();
            }
        });

        // --- Spannable "Log In" link ---
        android.widget.TextView tvGoToSignIn = findViewById(R.id.tv_go_to_signin);
        String text = getString(R.string.auth_msg_already_have_account);
        android.text.SpannableString spannable = new android.text.SpannableString(text);
        int start = text.indexOf(getString(R.string.auth_action_sign_in));
        if (start != -1) {
            spannable.setSpan(
                    new android.text.style.ForegroundColorSpan(androidx.core.content.ContextCompat.getColor(this, R.color.lg_accent_primary)),
                    start, start + getString(R.string.auth_action_sign_in).length(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        tvGoToSignIn.setText(spannable);
        tvGoToSignIn.setOnClickListener(v -> {
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        // --- Handle Settings Invocation ---
        boolean fromSettings = getIntent().getBooleanExtra("from_settings", false);
        if (fromSettings) {
            tvGoToSignIn.setVisibility(View.GONE);
            android.widget.TextView tvTitle = findViewById(R.id.tv_title_forgot);
            android.widget.TextView tvSubtitle = findViewById(R.id.tv_subtitle_forgot);
            if (tvTitle != null) tvTitle.setText(getString(R.string.settings_action_change_password));
            if (tvSubtitle != null) tvSubtitle.setText("Update your password to keep your account secure.");
        }
    }

    private void handleSendCode() {
        String email = edtEmail.getText().toString().trim();
        tilEmail.setError(null);

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError(getString(R.string.auth_error_invalid_email));
            return;
        }

        setLoading(true);
        apiService.forgotPassword(email).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                setLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    Toast.makeText(ForgotPasswordActivity.this, response.body().getMessage(), Toast.LENGTH_LONG).show();
                    // Chuyển sang màn hình xác thực OTP chuyên dụng
                    android.content.Intent intent = new android.content.Intent(ForgotPasswordActivity.this, OtpVerificationActivity.class);
                    intent.putExtra("flow_type", "forgot_password");
                    intent.putExtra("email", email);
                    startActivity(intent);
                } else {
                    Toast.makeText(ForgotPasswordActivity.this, getString(R.string.auth_msg_email_not_found), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                setLoading(false);
                Toast.makeText(ForgotPasswordActivity.this, getString(R.string.common_error_network), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setLoading(boolean isLoading) {
        loadingProgress.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnResetPassword.setEnabled(!isLoading);
        edtEmail.setEnabled(!isLoading);

        if (isLoading) {
            btnResetPassword.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(this, R.color.mosco_btn_disabled)));
            btnResetPassword.setTextColor(ContextCompat.getColor(this, R.color.lg_text_disabled));
        } else {
            btnResetPassword.setBackgroundTintList(null);
            btnResetPassword.setTextColor(ContextCompat.getColor(this, R.color.white));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        com.vn.jet.mosco.utils.GalacticBackgroundView galacticBg = findViewById(R.id.galactic_bg);
        if (galacticBg != null) {
            galacticBg.setMode(com.vn.jet.mosco.utils.GalacticBackgroundView.Mode.RECOVERY);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        com.vn.jet.mosco.utils.AuthUIHelper.saveAnimationState();
    }
}

