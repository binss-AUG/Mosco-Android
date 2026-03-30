package com.vn.jet.mosco;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Patterns;
import android.view.View;
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

        viewModel = new ViewModelProvider(this).get(SignUpViewModel.class);
        sessionManager = new SessionManager(this);

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
            Toast.makeText(this, getString(R.string.msg_code_sent), Toast.LENGTH_SHORT).show();
            viewModel.startCountdown();
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

        // --- Sign Up button ---
        btnSignUp.setOnClickListener(v -> validateAndSignUp());

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

        tvGoToSignIn.setOnClickListener(v -> finish());
        btnBack.setOnClickListener(v -> finish());
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
        viewModel.signUpUser(username, email, pass);
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