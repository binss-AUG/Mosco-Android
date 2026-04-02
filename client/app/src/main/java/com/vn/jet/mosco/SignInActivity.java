package com.vn.jet.mosco;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputEditText;
import com.vn.jet.mosco.utils.Resource;
import com.vn.jet.mosco.utils.SessionManager;

public class SignInActivity extends AppCompatActivity {

    private TextInputEditText edtUsername, edtPassword;
    private TextView tvGoToSignUp;
    private Button btnSignIn;
    private ProgressBar loadingProgress;

    private SignInViewModel viewModel;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);

        edtUsername = findViewById(R.id.edt_username);
        edtPassword = findViewById(R.id.edt_password);
        tvGoToSignUp = findViewById(R.id.tv_go_to_signup);
        btnSignIn = findViewById(R.id.btn_signin);
        loadingProgress = findViewById(R.id.loading_progress);

        viewModel = new ViewModelProvider(this).get(SignInViewModel.class);
        sessionManager = new SessionManager(this);

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
        tvGoToSignUp.setOnClickListener(v ->
                startActivity(new Intent(this, SignUpActivity.class)));

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
        findViewById(R.id.tv_forgot_password).setOnClickListener(v ->
                Toast.makeText(this, getString(R.string.msg_forgot_password_placeholder),
                        Toast.LENGTH_SHORT).show());

        // --- Sign In ---
        btnSignIn.setOnClickListener(v -> validateAndSignIn());

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

    private void validateAndSignIn() {
        String username = edtUsername.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();

        if (username.isEmpty()) {
            edtUsername.setError(getString(R.string.error_empty_field));
            return;
        }
        if (password.isEmpty()) {
            edtPassword.setError(getString(R.string.error_empty_field));
            return;
        }
        if (password.length() < 6) {
            edtPassword.setError(getString(R.string.error_short_password));
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
