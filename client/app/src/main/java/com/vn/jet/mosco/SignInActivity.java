package com.vn.jet.mosco;

import com.vn.jet.mosco.utils.AuthUIHelper;

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

    private TextInputEditText edtEmail, edtPassword;
    private TextInputLayout tilEmail, tilPassword;
    private TextView tvGoToSignUp;
    private android.widget.CheckBox cbRememberMe;
    private Button btnSignIn;
    private ProgressBar loadingProgress;

    private SignInViewModel viewModel;
    private SessionManager sessionManager;
    private boolean isSigningIn = false; // Cờ kiểm soát trạng thái đăng nhập

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);
        AuthUIHelper.animateAurora(this);

        edtEmail = findViewById(R.id.edt_email);
        edtPassword = findViewById(R.id.edt_password);
        tilEmail = findViewById(R.id.til_email);
        tilPassword = findViewById(R.id.til_password);
        cbRememberMe = findViewById(R.id.cb_remember_me);
        tvGoToSignUp = findViewById(R.id.tv_go_to_signup);
        btnSignIn = findViewById(R.id.btn_signin);
        loadingProgress = findViewById(R.id.loading_progress);

        viewModel = new ViewModelProvider(this).get(SignInViewModel.class);
        sessionManager = new SessionManager(this);

        // --- Spannable link for "Sign Up" ---
        String text = getString(R.string.msg_new_user_sign_up);
        SpannableString spannable = new SpannableString(text);
        int start = text.indexOf(getString(R.string.action_sign_up));
        if (start != -1) {
            spannable.setSpan(
                    new ForegroundColorSpan(ContextCompat.getColor(this, R.color.mosco_primary)),
                    start, start + getString(R.string.action_sign_up).length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        tvGoToSignUp.setText(spannable);

        // --- Navigation ---
        tvGoToSignUp.setOnClickListener(new com.vn.jet.mosco.utils.ClickDebounce() {
            @Override
            public void onDebouncedClick(View v) {
                if (isSigningIn) return;
                Intent intent = new Intent(SignInActivity.this, SignUpActivity.class);
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

        // --- Forgot Password ---
        findViewById(R.id.tv_forgot_password).setOnClickListener(new com.vn.jet.mosco.utils.ClickDebounce() {
            @Override
            public void onDebouncedClick(View v) {
                if (isSigningIn) return;
                Intent intent = new Intent(SignInActivity.this, ForgotPasswordActivity.class);
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

        // --- Social Login Placeholders ---
        findViewById(R.id.btn_google).setOnClickListener(new com.vn.jet.mosco.utils.ClickDebounce() {
            @Override
            public void onDebouncedClick(View v) {
                Toast.makeText(SignInActivity.this, "Google Sign-In coming soon", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btn_discord).setOnClickListener(new com.vn.jet.mosco.utils.ClickDebounce() {
            @Override
            public void onDebouncedClick(View v) {
                Toast.makeText(SignInActivity.this, "Discord Sign-In coming soon", Toast.LENGTH_SHORT).show();
            }
        });

        // Lắng nghe sự kiện "Done" từ bàn phím để tự động đăng nhập
        edtPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                if (!isSigningIn) {
                    validateAndSignIn();
                }
                return true;
            }
            return false;
        });

        // --- Observe API result ---
        viewModel.getSignInResult().observe(this, resource -> {
            if (resource == null) return;
            
            switch (resource.getStatus()) {
                case LOADING:
                    setLoading(true);
                    break;

                case SUCCESS:
                    setLoading(false);
                    if (resource.getData() != null && resource.getData().isSuccess()) {
                        sessionManager.saveSession(resource.getData().getData());
                        Toast.makeText(this, getString(R.string.msg_sign_in_success),
                                Toast.LENGTH_SHORT).show();

                        Intent intent;
                        String ingame = resource.getData().getData() != null
                                ? resource.getData().getData().getIngameName() : null;
                        if (ingame == null || ingame.isEmpty()) {
                            intent = new Intent(this, DisplayNameSetupActivity.class);
                        } else {
                            intent = new Intent(this, MainActivity.class);
                        }
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        String msg = (resource.getData() != null) ? resource.getData().getMessage() : getString(R.string.label_error);
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    }
                    break;

                case ERROR:
                    setLoading(false);
                    String errorMsg = resource.getMessage() != null ? resource.getMessage() : getString(R.string.msg_network_error);
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                    break;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        com.vn.jet.mosco.utils.GalacticBackgroundView galacticBg = findViewById(R.id.galactic_bg);
        if (galacticBg != null) {
            galacticBg.setMode(com.vn.jet.mosco.utils.GalacticBackgroundView.Mode.SIGN_IN);
        }
    }

    private void validateAndSignIn() {
        if (isSigningIn) return;
        
        String email = edtEmail.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();

        tilEmail.setError(null);
        tilPassword.setError(null);

        if (email.isEmpty()) {
            tilEmail.setError(getString(R.string.error_empty_field));
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

        viewModel.signIn(email, password);
    }

    private void setLoading(boolean isLoading) {
        this.isSigningIn = isLoading;
        loadingProgress.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnSignIn.setEnabled(!isLoading);
        tvGoToSignUp.setEnabled(!isLoading);
        edtEmail.setEnabled(!isLoading);
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

    @Override
    protected void onPause() {
        super.onPause();
        com.vn.jet.mosco.utils.AuthUIHelper.saveAnimationState();
    }
}
