package com.vn.jet.mosco;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Patterns;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputEditText;
import com.vn.jet.mosco.utils.Resource;
import com.vn.jet.mosco.utils.SessionManager;

public class SignUpActivity extends AppCompatActivity {

    private TextInputEditText edtUsername, edtEmail, edtVerificationCode;
    private TextInputEditText edtPassword, edtConfirmPassword;
    private Button btnSendCode, btnSignUp;
    private ProgressBar loadingProgress;
    private TextView tvGoToSignIn;
    private ImageView ivBackground;
    private ObjectAnimator driftX, driftY;

    private SignUpViewModel viewModel;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        edtUsername = findViewById(R.id.edt_username);
        edtEmail = findViewById(R.id.edt_email);
        edtPassword = findViewById(R.id.edt_password);
        edtConfirmPassword = findViewById(R.id.edt_confirm_password);
        edtVerificationCode = findViewById(R.id.edt_verification_code);
        btnSendCode = findViewById(R.id.btn_send_code);
        btnSignUp = findViewById(R.id.btn_signup);
        tvGoToSignIn = findViewById(R.id.tv_go_to_signin);
        loadingProgress = findViewById(R.id.loading_progress);
        ImageView btnBack = findViewById(R.id.btn_back);
        ivBackground = findViewById(R.id.iv_background_parallax);

        viewModel = new ViewModelProvider(this).get(SignUpViewModel.class);
        sessionManager = new SessionManager(this);

        // Nhận thời gian chạy Animation từ màn trước
        long playTimeX = getIntent().getLongExtra("EXTRA_PLAY_TIME_X", 0L);
        long playTimeY = getIntent().getLongExtra("EXTRA_PLAY_TIME_Y", 0L);

        // --- 🚀 Kích hoạt cơ chế siêu cấp WOW 2026 ---
        setupAmbientEffects(playTimeX, playTimeY);

        // --- Send Code button ---
        btnSendCode.setOnClickListener(v -> {
            String email = edtEmail.getText().toString().trim();
            if (email.isEmpty()) {
                edtEmail.setError(getString(R.string.error_empty_field));
                return;
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                edtEmail.setError(getString(R.string.error_invalid_email));
                return;
            }
            // Gọi API gửi mã thực tế
            viewModel.sendVerificationCode(email);
        });

        // --- Countdown timer observers ---
        viewModel.getIsTimerRunning().observe(this, isRunning -> {
            boolean sentOnce = Boolean.TRUE.equals(viewModel.getCodeSentOnce().getValue());
            if (isRunning) {
                btnSendCode.setEnabled(false);
                btnSendCode.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(
                                ContextCompat.getColor(this, R.color.mosco_btn_disabled)));
                btnSendCode.setTextColor(
                        ContextCompat.getColor(this, R.color.mosco_text_disabled));
            } else {
                btnSendCode.setText(sentOnce ? getString(R.string.action_resend) : getString(R.string.action_send_code));
                btnSendCode.setEnabled(true);
                btnSendCode.setBackgroundTintList(null);
                btnSendCode.setTextColor(ContextCompat.getColor(this, R.color.white));
            }
        });

        viewModel.getTimeLeftMillis().observe(this, millis -> {
            if (Boolean.TRUE.equals(viewModel.getIsTimerRunning().getValue())) {
                btnSendCode.setText(String.format(
                        getString(R.string.format_resend_timer), millis / 1000));
            }
        });

        viewModel.getCodeSentOnce().observe(this, sentOnce -> {
            if (!Boolean.TRUE.equals(viewModel.getIsTimerRunning().getValue())) {
                btnSendCode.setText(sentOnce ? getString(R.string.action_resend) : getString(R.string.action_send_code));
            }
        });

        // --- Observe Send Code result ---
        viewModel.getSendCodeResult().observe(this, resource -> {
            switch (resource.getStatus()) {
                case LOADING:
                    btnSendCode.setEnabled(false);
                    btnSendCode.setText("...");
                    break;
                case SUCCESS:
                    Toast.makeText(this, resource.getMessage(), Toast.LENGTH_SHORT).show();
                    viewModel.startCountdown();
                    break;
                case ERROR:
                    btnSendCode.setEnabled(true);
                    btnSendCode.setText(getString(R.string.action_send_code));
                    Toast.makeText(this, resource.getMessage(), Toast.LENGTH_SHORT).show();
                    break;
            }
        });

        // --- Sign Up button ---
        btnSignUp.setOnClickListener(v -> validateAndSignUp());

        // Tự động Sign Up khi ấn Done (UX cao cấp)
        edtVerificationCode.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                validateAndSignUp();
                return true;
            }
            return false;
        });

        // --- Observe API result ---
        viewModel.getSignUpResult().observe(this, resource -> {
            switch (resource.getStatus()) {
                case LOADING:
                    setLoading(true);
                    break;

                case SUCCESS:
                    setLoading(false);
                    if (resource.getData() != null && resource.getData().isSuccess()) {
                        sessionManager.saveSession(resource.getData().getData());
                        Toast.makeText(this,
                                getString(R.string.msg_create_account_success),
                                Toast.LENGTH_SHORT).show();
                        // Navigate directly to MainActivity (user is already authenticated)
                        Intent intent = new Intent(this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        
                        // Truyền "Nhịp tim" vũ trụ sang tận Trang chủ
                        if (driftX != null && driftY != null) {
                            intent.putExtra("EXTRA_PLAY_TIME_X", driftX.getCurrentPlayTime());
                            intent.putExtra("EXTRA_PLAY_TIME_Y", driftY.getCurrentPlayTime());
                        }
                        
                        startActivity(intent);
                        finish();
                    }
                    break;

                case ERROR:
                    setLoading(false);
                    Toast.makeText(this, resource.getMessage(),
                            Toast.LENGTH_LONG).show();
                    break;
            }
        });

        // --- Spannable "Sign In" link ---
        String text = getString(R.string.msg_already_have_account);
        SpannableString spannable = new SpannableString(text);
        int start = text.indexOf(getString(R.string.action_sign_in));
        if (start != -1) {
            spannable.setSpan(
                    new ForegroundColorSpan(ContextCompat.getColor(this, R.color.mosco_link)),
                    start, start + getString(R.string.action_sign_in).length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        tvGoToSignIn.setText(spannable);

        tvGoToSignIn.setOnClickListener(v -> {
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
        btnBack.setOnClickListener(v -> {
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
    }

    private void setupAmbientEffects(long playTimeX, long playTimeY) {
        // 1. Hiệu ứng Parallax trôi nền vũ trụ (Floating Nebula)
        if (ivBackground != null) {
            // Đảm bảo phủ đủ chiều rộng (Scale 1.3x)
            ivBackground.setScaleType(ImageView.ScaleType.CENTER_CROP);
            ivBackground.setScaleX(1.3f);
            ivBackground.setScaleY(1.3f);

            driftX = ObjectAnimator.ofFloat(ivBackground, "translationX", -60f, 60f);
            driftX.setDuration(15000); // Đồng bộ 15 giây
            driftX.setRepeatMode(ValueAnimator.REVERSE);
            driftX.setRepeatCount(ValueAnimator.INFINITE);

            driftY = ObjectAnimator.ofFloat(ivBackground, "translationY", -40f, 40f);
            driftY.setDuration(20000); // Đồng bộ 20 giây
            driftY.setRepeatMode(ValueAnimator.REVERSE);
            driftY.setRepeatCount(ValueAnimator.INFINITE);

            driftX.start();
            driftX.setCurrentPlayTime(playTimeX);
            
            driftY.start();
            driftY.setCurrentPlayTime(playTimeY);
        }

        // 2. Hiệu ứng nhịp thở cho Glass Card
        View glassCard = findViewById(R.id.glass_container);
        if (glassCard != null) {
            Animation breathing = AnimationUtils.loadAnimation(this, R.anim.anim_neon_breathing);
            glassCard.startAnimation(breathing);
        }
    }

    private void validateAndSignUp() {
        String username = edtUsername.getText().toString().trim();
        String email = edtEmail.getText().toString().trim();
        String pass = edtPassword.getText().toString().trim();
        String confirmPass = edtConfirmPassword.getText().toString().trim();
        String code = edtVerificationCode.getText().toString().trim();

        if (username.isEmpty()) {
            edtUsername.setError(getString(R.string.error_empty_field));
            return;
        }
        if (email.isEmpty()) {
            edtEmail.setError(getString(R.string.error_empty_field));
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            edtEmail.setError(getString(R.string.error_invalid_email));
            return;
        }
        if (pass.isEmpty()) {
            edtPassword.setError(getString(R.string.error_empty_field));
            return;
        }
        if (pass.length() < 6) {
            edtPassword.setError(getString(R.string.error_short_password));
            return;
        }
        if (!pass.equals(confirmPass)) {
            edtConfirmPassword.setError(getString(R.string.msg_passwords_not_match));
            return;
        }
        if (code.isEmpty()) {
            edtVerificationCode.setError(getString(R.string.error_empty_field));
            return;
        }

        // Call real API via ViewModel
        viewModel.signUpUser(username, email, pass, code);
    }

    private void setLoading(boolean isLoading) {
        loadingProgress.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnSignUp.setEnabled(!isLoading);
        btnSendCode.setEnabled(!isLoading
                && !Boolean.TRUE.equals(viewModel.getIsTimerRunning().getValue()));
        edtUsername.setEnabled(!isLoading);
        edtEmail.setEnabled(!isLoading);
        edtPassword.setEnabled(!isLoading);
        edtConfirmPassword.setEnabled(!isLoading);
        edtVerificationCode.setEnabled(!isLoading);
        tvGoToSignIn.setEnabled(!isLoading);

        if (isLoading) {
            btnSignUp.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(this, R.color.mosco_btn_disabled)));
            btnSignUp.setTextColor(
                    ContextCompat.getColor(this, R.color.mosco_text_disabled));
        } else {
            btnSignUp.setBackgroundTintList(null);
            btnSignUp.setTextColor(ContextCompat.getColor(this, R.color.white));
        }
    }
}