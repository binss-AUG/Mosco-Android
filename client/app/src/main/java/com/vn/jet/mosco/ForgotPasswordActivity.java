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

    private TextInputEditText edtEmail, edtVerificationCode, edtPassword;
    private TextInputLayout tilEmail, tilVerificationCode, tilPassword;
    private Button btnSendCode, btnResetPassword;
    private LottieAnimationView loadingProgress;
    private AuthApiService apiService;
    

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        AuthUIHelper.animateAurora(this);

        // Map views
        edtEmail = findViewById(R.id.edt_email);
        edtVerificationCode = findViewById(R.id.edt_verification_code);
        edtPassword = findViewById(R.id.edt_password);
        tilEmail = findViewById(R.id.til_email);
        tilVerificationCode = findViewById(R.id.til_verification_code);
        tilPassword = findViewById(R.id.til_password);
        btnSendCode = findViewById(R.id.btn_send_code);
        btnResetPassword = findViewById(R.id.btn_reset_password);
        loadingProgress = findViewById(R.id.loading_progress);
        

        apiService = ApiClient.getClient(this).create(AuthApiService.class);



        btnSendCode.setOnClickListener(new com.vn.jet.mosco.utils.ClickDebounce() {
            @Override
            public void onDebouncedClick(View v) {
                handleSendCode();
            }
        });
        btnResetPassword.setOnClickListener(new com.vn.jet.mosco.utils.ClickDebounce() {
            @Override
            public void onDebouncedClick(View v) {
                handleResetPassword();
            }
        });

        // --- Spannable "Log In" link ---
        android.widget.TextView tvGoToSignIn = findViewById(R.id.tv_go_to_signin);
        String text = getString(R.string.auth_msg_already_have_account);
        android.text.SpannableString spannable = new android.text.SpannableString(text);
        int start = text.indexOf(getString(R.string.auth_action_sign_in));
        if (start != -1) {
            spannable.setSpan(
                    new android.text.style.ForegroundColorSpan(androidx.core.content.ContextCompat.getColor(this, R.color.mosco_primary)),
                    start, start + getString(R.string.auth_action_sign_in).length(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        tvGoToSignIn.setText(spannable);
        tvGoToSignIn.setOnClickListener(v -> {
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
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

    private void handleResetPassword() {
        String email = edtEmail.getText().toString().trim();
        String code = edtVerificationCode.getText().toString().trim();
        String pass = edtPassword.getText().toString().trim();

        tilEmail.setError(null);
        tilVerificationCode.setError(null);
        tilPassword.setError(null);

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError(getString(R.string.auth_error_invalid_email));
            return;
        }
        if (code.length() < 6) {
            tilVerificationCode.setError(getString(R.string.auth_error_short_code));
            return;
        }
        if (pass.length() < 6) {
            tilPassword.setError(getString(R.string.auth_error_short_password));
            return;
        }

        setLoading(true);
        ResetPasswordRequest request = new ResetPasswordRequest(email, code, pass);
        apiService.resetPassword(request).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                setLoading(false);
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Toast.makeText(ForgotPasswordActivity.this, getString(R.string.auth_msg_reset_password_success), Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    String msg = (response.body() != null) ? response.body().getMessage() : getString(R.string.auth_msg_invalid_code);
                    Toast.makeText(ForgotPasswordActivity.this, msg, Toast.LENGTH_LONG).show();
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
        btnSendCode.setEnabled(!isLoading);
        edtEmail.setEnabled(!isLoading);
        edtVerificationCode.setEnabled(!isLoading);
        edtPassword.setEnabled(!isLoading);

        if (isLoading) {
            btnResetPassword.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(this, R.color.mosco_btn_disabled)));
            btnResetPassword.setTextColor(ContextCompat.getColor(this, R.color.mosco_text_disabled));

            btnSendCode.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(this, R.color.mosco_btn_disabled)));
            btnSendCode.setTextColor(ContextCompat.getColor(this, R.color.mosco_text_disabled));
        } else {
            btnResetPassword.setBackgroundTintList(null);
            btnResetPassword.setTextColor(ContextCompat.getColor(this, R.color.white));

            btnSendCode.setBackgroundTintList(null);
            btnSendCode.setTextColor(ContextCompat.getColor(this, R.color.white));
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
