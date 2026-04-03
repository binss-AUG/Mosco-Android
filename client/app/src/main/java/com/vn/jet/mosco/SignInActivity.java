package com.vn.jet.mosco;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
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

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.vn.jet.mosco.utils.Resource;
import com.vn.jet.mosco.utils.SessionManager;

public class SignInActivity extends AppCompatActivity {

    private TextInputEditText edtUsername, edtPassword;
    private TextInputLayout tilUsername, tilPassword;
    private TextView tvGoToSignUp;
    private Button btnSignIn;
    private ProgressBar loadingProgress;
    private ImageView ivBackground;
    private ObjectAnimator driftX, driftY;

    private SignInViewModel viewModel;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);

        edtUsername = findViewById(R.id.edt_username);
        edtPassword = findViewById(R.id.edt_password);
        tilUsername = findViewById(R.id.til_username);
        tilPassword = findViewById(R.id.til_password);
        tvGoToSignUp = findViewById(R.id.tv_go_to_signup);
        btnSignIn = findViewById(R.id.btn_signin);
        loadingProgress = findViewById(R.id.loading_progress);

        viewModel = new ViewModelProvider(this).get(SignInViewModel.class);
        sessionManager = new SessionManager(this);

        // Nhận thời gian chạy Animation từ màn trước
        long playTimeX = getIntent().getLongExtra("EXTRA_PLAY_TIME_X", 0L);
        long playTimeY = getIntent().getLongExtra("EXTRA_PLAY_TIME_Y", 0L);

        // --- 🚀 Activate Super-Premium Galactic Effects 2026 ---
        setupAmbientEffects(playTimeX, playTimeY);

        // --- Spannable link for "Sign Up" ---
        String text = getString(R.string.msg_new_user_sign_up);
        SpannableString spannable = new SpannableString(text);
        int start = text.indexOf(getString(R.string.title_sign_up));
        if (start != -1) {
            spannable.setSpan(
                    new ForegroundColorSpan(ContextCompat.getColor(this, R.color.mosco_link)),
                    start, start + getString(R.string.title_sign_up).length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        tvGoToSignUp.setText(spannable);

        // --- Navigation ---
        tvGoToSignUp.setOnClickListener(new com.vn.jet.mosco.utils.ClickDebounce() {
            @Override
            public void onDebouncedClick(View v) {
                Intent intent = new Intent(SignInActivity.this, SignUpActivity.class);
                // Transfer heartbeat to the next screen
                if (driftX != null && driftY != null) {
                    intent.putExtra("EXTRA_PLAY_TIME_X", driftX.getCurrentPlayTime());
                    intent.putExtra("EXTRA_PLAY_TIME_Y", driftY.getCurrentPlayTime());
                }
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        });

        // Use OnBackPressedCallback instead of deprecated onBackPressed()
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
        findViewById(R.id.btn_back).setOnClickListener(v ->
                getOnBackPressedDispatcher().onBackPressed());

        // --- Forgot Password ---
        findViewById(R.id.tv_forgot_password).setOnClickListener(new com.vn.jet.mosco.utils.ClickDebounce() {
            @Override
            public void onDebouncedClick(View v) {
                Intent intent = new Intent(SignInActivity.this, ForgotPasswordActivity.class);
                // Continue the cosmic drift
                if (driftX != null && driftY != null) {
                    intent.putExtra("EXTRA_PLAY_TIME_X", driftX.getCurrentPlayTime());
                    intent.putExtra("EXTRA_PLAY_TIME_Y", driftY.getCurrentPlayTime());
                }
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        });

        // --- Sign In ---
        btnSignIn.setOnClickListener(new com.vn.jet.mosco.utils.ClickDebounce() {
            @Override
            public void onDebouncedClick(View v) {
                validateAndSignIn();
            }
        });

        // Lắng nghe sự kiện "Done" từ bàn phím để tự động đăng nhập (UX tối ưu)
        edtPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                validateAndSignIn();
                return true;
            }
            return false;
        });

        // --- Observe API result ---
        viewModel.getSignInResult().observe(this, resource -> {
            switch (resource.getStatus()) {
                case LOADING:
                    setLoading(true);
                    break;

                case SUCCESS:
                    setLoading(false);
                    if (resource.getData() != null && resource.getData().isSuccess()) {
                        // Save session
                        sessionManager.saveSession(resource.getData().getData());
                        Toast.makeText(this, getString(R.string.msg_sign_in_success),
                                Toast.LENGTH_SHORT).show();
                        // Navigate to MainActivity
                        Intent intent = new Intent(this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        
                        // Transfer cosmic heartbeat to MainActivity
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
    }

    private void setupAmbientEffects(long playTimeX, long playTimeY) {
        // 1. Hiệu ứng Parallax trôi nền vũ trụ (Floating Nebula)
        ivBackground = findViewById(R.id.iv_background_parallax);
        if (ivBackground != null) {
            // Đảm bảo phủ đủ chiều rộng để trôi (Scale 1.3x)
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

        // 2. Hiệu ứng nhịp thở cho Glass Card
        View glassCard = findViewById(R.id.glass_container);
        if (glassCard != null) {
            Animation breathing = AnimationUtils.loadAnimation(this, R.anim.anim_neon_breathing);
            glassCard.startAnimation(breathing);
        }
    }

    private void validateAndSignIn() {
        String username = edtUsername.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();

        tilUsername.setError(null);
        tilPassword.setError(null);

        if (username.isEmpty()) {
            tilUsername.setError(getString(R.string.error_empty_field));
            return;
        }
        if (password.isEmpty()) {
            tilPassword.setError(getString(R.string.error_empty_field));
            return;
        }
        if (password.length() < 6) {
            tilPassword.setError(getString(R.string.error_short_password));
            return;
        }

        viewModel.signIn(username, password);
    }

    private void setLoading(boolean isLoading) {
        loadingProgress.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnSignIn.setEnabled(!isLoading);
        tvGoToSignUp.setEnabled(!isLoading);
        edtUsername.setEnabled(!isLoading);
        edtPassword.setEnabled(!isLoading);

        if (isLoading) {
            btnSignIn.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(this, R.color.mosco_btn_disabled)));
            btnSignIn.setTextColor(ContextCompat.getColor(this, R.color.mosco_text_disabled));
        } else {
            btnSignIn.setBackgroundTintList(null);
            btnSignIn.setTextColor(ContextCompat.getColor(this, R.color.white));
        }
    }
}
