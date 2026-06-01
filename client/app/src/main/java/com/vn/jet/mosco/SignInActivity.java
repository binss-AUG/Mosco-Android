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
import com.vn.jet.mosco.utils.ErrorTranslator;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.browser.customtabs.CustomTabsIntent;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.AuthCredential;
import com.vn.jet.mosco.model.AuthResponse;
import android.net.Uri;

public class SignInActivity extends AppCompatActivity {

    private TextInputEditText edtEmail, edtPassword;
    private TextInputLayout tilEmail, tilPassword;
    private TextView tvGoToSignUp;
    private android.widget.CheckBox cbRememberMe;
    private Button btnSignIn;
    private com.airbnb.lottie.LottieAnimationView loadingProgress;

    private SignInViewModel viewModel;
    private SessionManager sessionManager;
    private boolean isSigningIn = false; 

    private GoogleSignInClient mGoogleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private FirebaseAuth mAuth;

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
        mAuth = FirebaseAuth.getInstance();

        // Tại sao (WHY): Tự động điền thông tin đăng nhập đã lưu từ phiên làm việc trước nếu Remember me được chọn
        if (sessionManager.isRememberMeEnabled()) {
            cbRememberMe.setChecked(true);
            String savedUser = sessionManager.getSavedUsernameOrEmail();
            if (savedUser != null) {
                edtEmail.setText(savedUser);
                edtPassword.requestFocus();
            }
        } else {
            cbRememberMe.setChecked(false);
        }

        initGoogleSignIn();
        handleIntent(getIntent());

        // --- Spannable link for "Sign Up" ---
        String text = getString(R.string.auth_msg_new_user);
        SpannableString spannable = new SpannableString(text);
        int start = text.indexOf(getString(R.string.auth_action_sign_up));
        if (start != -1) {
            spannable.setSpan(
                    new ForegroundColorSpan(ContextCompat.getColor(this, R.color.lg_accent_primary)),
                    start, start + getString(R.string.auth_action_sign_up).length(),
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
                signInWithGoogle();
            }
        });

        findViewById(R.id.btn_discord).setOnClickListener(new com.vn.jet.mosco.utils.ClickDebounce() {
            @Override
            public void onDebouncedClick(View v) {
                signInWithDiscord();
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
                        // Tại sao (WHY): Lưu thông tin đăng nhập tự động khi người dùng chọn "Remember me"
                        sessionManager.saveRememberMe(cbRememberMe.isChecked(), edtEmail.getText().toString().trim());
                        sessionManager.saveSession(resource.getData().getData());
                        Toast.makeText(this, getString(R.string.auth_msg_sign_in_success),
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
                        com.vn.jet.mosco.model.AuthResponse authResponse = resource.getData();
                        if (authResponse != null && authResponse.getDeletionPending() != null && authResponse.getDeletionPending()) {
                            showAccountDeletionPendingDialog(authResponse.getEmail(), authResponse.getDaysRemaining());
                        } else {
                            String msg = (authResponse != null) ? authResponse.getMessage() : getString(R.string.common_error_unknown);
                            Toast.makeText(this, ErrorTranslator.translate(this, msg), Toast.LENGTH_SHORT).show();
                        }
                    }
                    break;

                case ERROR:
                    setLoading(false);
                    String errorMsg = resource.getMessage() != null ? resource.getMessage() : getString(R.string.common_error_network);
                    Toast.makeText(this, ErrorTranslator.translate(this, errorMsg), Toast.LENGTH_LONG).show();
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
        
        String usernameOrEmail = edtEmail.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();

        tilEmail.setError(null);
        tilPassword.setError(null);

        if (usernameOrEmail.isEmpty()) {
            tilEmail.setError(getString(R.string.auth_error_empty_field));
            return;
        }
        if (password.isEmpty()) {
            tilPassword.setError(getString(R.string.auth_error_empty_field));
            return;
        }
        if (password.length() < 6) {
            tilPassword.setError(getString(R.string.auth_error_short_password));
            return;
        }

        viewModel.signIn(usernameOrEmail, password);
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
            btnSignIn.setTextColor(ContextCompat.getColor(this, R.color.lg_text_disabled));
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

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        Uri data = intent.getData();
        if (data == null) return;

        if (!"mosco".equals(data.getScheme())) return;

        setLoading(true);
        com.vn.jet.mosco.utils.DiscordAuthManager.handleCallback(this, data, new com.vn.jet.mosco.utils.DiscordAuthManager.DiscordAuthCallback() {
            @Override
            public void onSuccess(String id, String username, String email, String accessToken, String avatarUrl) {
                // TẮT BYPASS: Gọi thẳng API Backend để lấy ID thật từ SQL Database (1, 2, 3...)
                viewModel.socialLogin(new com.vn.jet.mosco.model.SocialAuthRequest("discord", accessToken, email));
            }

            @Override
            public void onError(String error) {
                setLoading(false);
                Toast.makeText(SignInActivity.this, getString(R.string.auth_error_discord_general, error), Toast.LENGTH_LONG).show();
            }
        });
    }



    /**
     * Hỗ trợ đăng nhập nhanh bằng Social để kịp nộp bài (Bypass Server verify)
     */
    private void handleSuccessLogin(String discordId, String name, String email, String token) {
        setLoading(false);
        com.vn.jet.mosco.model.AuthResponse.UserData dummyUser = new com.vn.jet.mosco.model.AuthResponse.UserData();
        
        long parsedId = System.currentTimeMillis();
        try {
            if (discordId != null && !discordId.isEmpty()) {
                parsedId = Long.parseLong(discordId);
            }
        } catch (NumberFormatException ignored) {}
        
        dummyUser.setId(parsedId);
        dummyUser.setUsername(name);
        dummyUser.setEmail(email);
        dummyUser.setIngameName(name); // Set mặc định để không bị đá vào màn hình Setup Name
        dummyUser.setToken(token);
        dummyUser.setAvatarId("1");

        sessionManager.saveSession(dummyUser);
        
        Toast.makeText(this, getString(R.string.auth_msg_welcome_galaxy), Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void initGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(com.vn.jet.mosco.utils.AppConfig.GOOGLE_WEB_CLIENT_ID)
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == android.app.Activity.RESULT_OK) {
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        handleGoogleSignInResult(task);
                    } else {
                        setLoading(false);
                        Toast.makeText(this, getString(R.string.auth_error_sign_in_cancelled), Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void signInWithGoogle() {
        if (isSigningIn) return;
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
                firebaseAuthWithGoogle(idToken);
            } else {
                setLoading(false);
                Toast.makeText(this, getString(R.string.auth_error_google_token_null), Toast.LENGTH_LONG).show();
            }
        } catch (ApiException e) {
            setLoading(false);
            android.util.Log.e("MoscoAuth", "Google sign in failed", e);
            Toast.makeText(this, getString(R.string.auth_error_google_general, String.valueOf(e.getStatusCode())), Toast.LENGTH_LONG).show();
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // TẮT BYPASS: Gọi thẳng API Backend để lấy ID thật từ SQL Database (1, 2, 3...)
                            viewModel.socialLogin(new com.vn.jet.mosco.model.SocialAuthRequest("google", idToken, user.getEmail()));
                        }
                    } else {
                        setLoading(false);
                        Toast.makeText(SignInActivity.this, "Firebase Auth Failed: " + 
                                task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void signInWithDiscord() {
        if (isSigningIn) return;
        com.vn.jet.mosco.utils.DiscordAuthManager.startDiscordLogin(this);
    }

    /**
     * Hiển thị Dialog thông báo tài khoản đang chờ xóa và cho phép khôi phục
     * Tại sao (WHY): Thông tin trực quan để người dùng biết thời gian khôi phục còn lại
     */
    private void showAccountDeletionPendingDialog(String email, int daysRemaining) {
        if (isFinishing() || isDestroyed()) return;

        com.vn.jet.mosco.utils.MoscoDialogHelper.showConfirmDialog(
            this,
            getString(R.string.auth_dialog_recovery_pending_title),
            getString(R.string.auth_dialog_recovery_pending_msg, daysRemaining),
            getString(R.string.auth_action_restore),
            getString(R.string.action_cancel),
            new com.vn.jet.mosco.utils.MoscoDialogHelper.DialogCallback() {
                @Override
                public void onPositive() {
                    sendRestoreAccountOtp(email);
                }
            }
        );
    }

    /**
     * Gửi OTP khôi phục tài khoản về email
     */
    private void sendRestoreAccountOtp(String email) {
        setLoading(true);
        com.vn.jet.mosco.network.AuthApiService authApiService = 
            com.vn.jet.mosco.network.ApiClient.getClient(this).create(com.vn.jet.mosco.network.AuthApiService.class);

        authApiService.sendCode(email).enqueue(new retrofit2.Callback<com.vn.jet.mosco.model.AuthResponse>() {
            @Override
            public void onResponse(retrofit2.Call<com.vn.jet.mosco.model.AuthResponse> call, retrofit2.Response<com.vn.jet.mosco.model.AuthResponse> response) {
                setLoading(false);
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Toast.makeText(SignInActivity.this, getString(R.string.auth_msg_recovery_otp_sent), Toast.LENGTH_SHORT).show();
                    showRestoreAccountOtpDialog(email);
                } else {
                    String msg = response.body() != null ? response.body().getMessage() : getString(R.string.auth_msg_recovery_otp_failed);
                    Toast.makeText(SignInActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(retrofit2.Call<com.vn.jet.mosco.model.AuthResponse> call, Throwable t) {
                setLoading(false);
                // TẠI SAO: Cần định dạng thông điệp lỗi kèm lý do lỗi mạng chi tiết để hỗ trợ chẩn đoán
                Toast.makeText(SignInActivity.this, getString(R.string.auth_error_connection_prefix, t.getMessage()), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Popup nhập OTP xác nhận khôi phục tài khoản
     */
    private void showRestoreAccountOtpDialog(String email) {
        if (isFinishing() || isDestroyed()) return;
        
        View dialogView = android.view.LayoutInflater.from(this).inflate(R.layout.layout_mosco_dialog_base, null);
        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        android.widget.FrameLayout flContent = dialogView.findViewById(R.id.fl_dialog_content);
        com.vn.jet.mosco.widget.MoscoButton btnPositive = dialogView.findViewById(R.id.btn_positive);
        com.vn.jet.mosco.widget.MoscoButton btnNegative = dialogView.findViewById(R.id.btn_negative);

        tvTitle.setText(R.string.auth_dialog_recovery_title);
        flContent.removeAllViews();

        android.widget.EditText etOtp = new android.widget.EditText(this);
        etOtp.setHint(R.string.auth_dialog_recovery_hint);
        etOtp.setHintTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.mosco_white_40));
        etOtp.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.white));
        etOtp.setBackgroundResource(R.drawable.lg_input_bg);
        etOtp.setGravity(android.view.Gravity.CENTER);
        etOtp.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etOtp.setMaxLines(1);
        
        android.text.InputFilter[] filters = new android.text.InputFilter[1];
        filters[0] = new android.text.InputFilter.LengthFilter(6);
        etOtp.setFilters(filters);

        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        etOtp.setPadding(padding, padding, padding, padding);

        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            );
        etOtp.setLayoutParams(lp);
        flContent.addView(etOtp);

        btnPositive.setText(R.string.action_confirm);
        btnNegative.setText(R.string.action_cancel);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            dialog.getWindow().setWindowAnimations(android.R.style.Animation_Dialog);
        }

        btnPositive.setOnClickListener(v -> {
            String code = etOtp.getText().toString().trim();
            if (code.length() != 6) {
                Toast.makeText(SignInActivity.this, getString(R.string.auth_error_otp_length), Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            requestRestoreAccount(email, code);
        });

        btnNegative.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    /**
     * Gửi request gọi API khôi phục lên Server
     */
    private void requestRestoreAccount(String email, String code) {
        setLoading(true);
        com.vn.jet.mosco.network.AuthApiService authApiService = 
            com.vn.jet.mosco.network.ApiClient.getClient(this).create(com.vn.jet.mosco.network.AuthApiService.class);

        authApiService.restoreAccount(email, code).enqueue(new retrofit2.Callback<com.vn.jet.mosco.model.AuthResponse>() {
            @Override
            public void onResponse(retrofit2.Call<com.vn.jet.mosco.model.AuthResponse> call, retrofit2.Response<com.vn.jet.mosco.model.AuthResponse> response) {
                setLoading(false);
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Toast.makeText(SignInActivity.this, getString(R.string.auth_msg_recovery_success), Toast.LENGTH_LONG).show();
                } else {
                    String msg = response.body() != null ? response.body().getMessage() : getString(R.string.auth_msg_recovery_failed);
                    Toast.makeText(SignInActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(retrofit2.Call<com.vn.jet.mosco.model.AuthResponse> call, Throwable t) {
                setLoading(false);
                // TẠI SAO: Cần định dạng thông điệp lỗi kèm lý do lỗi mạng chi tiết để hỗ trợ chẩn đoán
                Toast.makeText(SignInActivity.this, getString(R.string.auth_error_connection_prefix, t.getMessage()), Toast.LENGTH_SHORT).show();
            }
        });
    }
}

