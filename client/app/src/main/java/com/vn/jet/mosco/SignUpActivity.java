package com.vn.jet.mosco;

import com.vn.jet.mosco.utils.AuthUIHelper;

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
import com.google.android.material.textfield.TextInputLayout;
import com.vn.jet.mosco.utils.Resource;
import com.vn.jet.mosco.utils.SessionManager;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.browser.customtabs.CustomTabsIntent;
import android.net.Uri;

public class SignUpActivity extends AppCompatActivity {

    private TextInputEditText edtUsername, edtEmail, edtVerificationCode;
    private TextInputEditText edtPassword, edtConfirmPassword;
    private TextInputLayout tilUsername, tilEmail, tilVerificationCode, tilPassword, tilConfirmPassword;
    private Button btnSendCode, btnSignUp;
    private ProgressBar loadingProgress;
    private TextView tvGoToSignIn;

    private SignUpViewModel viewModel;
    private SessionManager sessionManager;
    private boolean isSigningUp = false;

    private GoogleSignInClient mGoogleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        AuthUIHelper.animateAurora(this);

        edtUsername = findViewById(R.id.edt_username);
        edtEmail = findViewById(R.id.edt_email);
        edtPassword = findViewById(R.id.edt_password);
        edtConfirmPassword = findViewById(R.id.edt_confirm_password);
        edtVerificationCode = findViewById(R.id.edt_verification_code);
        tilUsername = findViewById(R.id.til_username);
        tilEmail = findViewById(R.id.til_email);
        tilPassword = findViewById(R.id.til_password);
        tilConfirmPassword = findViewById(R.id.til_confirm_password);
        tilVerificationCode = findViewById(R.id.til_verification_code);
        btnSendCode = findViewById(R.id.btn_send_code);
        btnSignUp = findViewById(R.id.btn_signup);
        tvGoToSignIn = findViewById(R.id.tv_go_to_signin);
        loadingProgress = findViewById(R.id.loading_progress);
        

        viewModel = new ViewModelProvider(this).get(SignUpViewModel.class);
        sessionManager = new SessionManager(this);

        initGoogleSignIn();
        handleIntent(getIntent());

        // Nhận thời gian chạy Animation từ màn trước
        
        

        // --- 🚀 Activate Super-Premium Galactic Effects 2026 ---
        // setupAmbientEffects(playTimeX, playTimeY);

        // --- Send Code button ---
        btnSendCode.setOnClickListener(new com.vn.jet.mosco.utils.ClickDebounce() {
            @Override
            public void onDebouncedClick(View v) {
                String email = edtEmail.getText().toString().trim();
                tilEmail.setError(null);

                if (email.isEmpty()) {
                    tilEmail.setError(getString(R.string.error_empty_field));
                    return;
                }
                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    tilEmail.setError(getString(R.string.error_invalid_email));
                    return;
                }
                // Call real API to send code
                viewModel.sendVerificationCode(email);
            }
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
        // --- Observe Send Code result ---
        viewModel.getSendCodeResult().observe(this, resource -> {
            switch (resource.getStatus()) {
                case LOADING:
                    btnSendCode.setEnabled(false);
                    btnSendCode.setText("...");
                    break;
                case SUCCESS:
                    if (resource.getData() != null) {
                        Toast.makeText(this, resource.getData().getMessage(), Toast.LENGTH_SHORT).show();
                    }
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
        btnSignUp.setOnClickListener(new com.vn.jet.mosco.utils.ClickDebounce() {
            @Override
            public void onDebouncedClick(View v) {
                validateAndSignUp();
            }
        });

        // --- Social Login buttons ---
        findViewById(R.id.btn_google).setOnClickListener(new com.vn.jet.mosco.utils.ClickDebounce() {
            @Override
            public void onDebouncedClick(View v) {
                signInWithGoogle();
            }
        });

        findViewById(R.id.btn_discord).setOnClickListener(new com.vn.jet.mosco.utils.ClickDebounce() {
            @Override
            public void onDebouncedClick(View v) {
                signInWithDiscord();
            }
        });

        // Automatic Sign Up on Done action (Premium UX)
        edtVerificationCode.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                if (!isSigningUp) {
                    validateAndSignUp();
                }
                return true;
            }
            return false;
        });

        // --- Observe API result ---
        viewModel.getSignUpResult().observe(this, resource -> {
            if (resource == null) return;
            
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

                        Intent intent;
                        String ingame = resource.getData().getData() != null
                                ? resource.getData().getData().getIngameName() : null;
                        if (ingame == null || ingame.isEmpty()) {
                            intent = new Intent(this, DisplayNameSetupActivity.class);
                        } else {
                            intent = new Intent(this, MainActivity.class);
                        }
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
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

        // --- Spannable "Sign In" link ---
        String text = getString(R.string.msg_already_have_account);
        SpannableString spannable = new SpannableString(text);
        int start = text.indexOf(getString(R.string.action_sign_in));
        if (start != -1) {
            spannable.setSpan(
                    new ForegroundColorSpan(ContextCompat.getColor(this, R.color.mosco_primary)),
                    start, start + getString(R.string.action_sign_in).length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        tvGoToSignIn.setText(spannable);

        tvGoToSignIn.setOnClickListener(v -> {
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
    }

    private void setupAmbientEffects(long playTimeX, long playTimeY) {
        // GalacticBackgroundView handles its own animation now.
    }

    private void validateAndSignUp() {
        if (isSigningUp) return;
        
        String username = edtUsername.getText().toString().trim();
        String email = edtEmail.getText().toString().trim();
        String pass = edtPassword.getText().toString().trim();
        String confirmPass = edtConfirmPassword.getText().toString().trim();
        String code = edtVerificationCode.getText().toString().trim();

        // Clear all previous errors
        tilUsername.setError(null);
        tilEmail.setError(null);
        tilPassword.setError(null);
        tilConfirmPassword.setError(null);
        tilVerificationCode.setError(null);

        if (username.isEmpty()) {
            tilUsername.setError(getString(R.string.error_empty_field));
            return;
        }
        if (email.isEmpty()) {
            tilEmail.setError(getString(R.string.error_empty_field));
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError(getString(R.string.error_invalid_email));
            return;
        }
        if (pass.isEmpty()) {
            tilPassword.setError(getString(R.string.error_empty_field));
            return;
        }
        if (pass.length() < 6) {
            tilPassword.setError(getString(R.string.error_short_password));
            return;
        }
        if (!pass.equals(confirmPass)) {
            tilConfirmPassword.setError(getString(R.string.msg_passwords_not_match));
            return;
        }
        if (code.isEmpty()) {
            tilVerificationCode.setError(getString(R.string.error_empty_field));
            return;
        }

        // Call real API via ViewModel
        viewModel.signUpUser(username, email, pass, code);
    }

    private void setLoading(boolean isLoading) {
        this.isSigningUp = isLoading;
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
        com.vn.jet.mosco.utils.AuthUIHelper.saveAnimationState();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        Uri data = intent.getData();
        if (data != null && data.getScheme().equals("mosco")) {
            String fragment = data.getFragment();
            if (fragment != null && fragment.contains("access_token=")) {
                setLoading(true);
                String accessToken = fragment.split("access_token=")[1].split("&")[0];
                viewModel.socialLogin(new com.vn.jet.mosco.model.SocialAuthRequest("discord", accessToken, null));
            }
        }
    }

    private void initGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == android.app.Activity.RESULT_OK) {
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        handleGoogleSignInResult(task);
                    }
                }
        );
    }

    private void signInWithGoogle() {
        if (isSigningUp) return;
        setLoading(true);
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        googleSignInLauncher.launch(signInIntent);
    }

    private void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            String idToken = account.getIdToken();
            String email = account.getEmail();
            
            if (idToken != null) {
                viewModel.socialLogin(new com.vn.jet.mosco.model.SocialAuthRequest("google", idToken, email));
            } else {
                setLoading(false);
                Toast.makeText(this, "Google Token is null. Check Web Client ID.", Toast.LENGTH_LONG).show();
            }
        } catch (ApiException e) {
            setLoading(false);
            android.util.Log.e("MoscoAuth", "Google sign in failed", e);
            Toast.makeText(this, "Google Error: " + e.getStatusCode() + " (Check Client ID/SHA1)", Toast.LENGTH_LONG).show();
        }
    }

    private void signInWithDiscord() {
        if (isSigningUp) return;
        
        String clientId = getString(R.string.discord_client_id);
        String redirectUri = getString(R.string.discord_redirect_uri);
        String authUrl = "https://discord.com/api/oauth2/authorize" +
                "?client_id=" + clientId +
                "&redirect_uri=" + Uri.encode(redirectUri) +
                "&response_type=token" +
                "&scope=identify%20email";

        CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
        customTabsIntent.launchUrl(this, Uri.parse(authUrl));
    }
}