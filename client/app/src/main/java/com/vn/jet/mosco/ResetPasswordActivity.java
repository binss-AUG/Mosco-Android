package com.vn.jet.mosco;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.vn.jet.mosco.model.AuthResponse;
import com.vn.jet.mosco.model.ResetPasswordRequest;
import com.vn.jet.mosco.network.ApiClient;
import com.vn.jet.mosco.network.AuthApiService;
import com.vn.jet.mosco.utils.AuthUIHelper;
import com.vn.jet.mosco.utils.ClickDebounce;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * ResetPasswordActivity - Bước thứ 3 trong luồng lấy lại mật khẩu.
 * Nhận email và mã OTP từ các bước trước đó và thực hiện gọi API để đặt lại mật khẩu mới.
 * Tuân thủ 100% Java cho Android Client.
 */
public class ResetPasswordActivity extends AppCompatActivity {

    private TextInputEditText edtPassword;
    private TextInputEditText edtConfirmPassword;
    private TextInputLayout tilPassword;
    private TextInputLayout tilConfirmPassword;
    private com.vn.jet.mosco.widget.MoscoButton btnReset;
    private LottieAnimationView loadingProgress;
    private ImageButton btnBack;

    private String email;
    private String code;

    private AuthApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);

        // Kích hoạt hiệu ứng cực quang (aurora) cho nền chuẩn ngân hà
        AuthUIHelper.animateAurora(this);

        // Nhận dữ liệu truyền từ bước xác thực OTP để gửi lên API đặt lại mật khẩu
        Intent intent = getIntent();
        if (intent != null) {
            email = intent.getStringExtra("email");
            code = intent.getStringExtra("code");
        }

        // Kiểm tra an toàn dữ liệu đầu vào để tránh NullPointerException khi gọi API
        if (email == null || code == null) {
            Toast.makeText(this, "Missing email or verification code!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Khởi tạo các view thành phần
        edtPassword = findViewById(R.id.edt_password);
        edtConfirmPassword = findViewById(R.id.edt_confirm_password);
        tilPassword = findViewById(R.id.til_password);
        tilConfirmPassword = findViewById(R.id.til_confirm_password);
        btnReset = findViewById(R.id.btn_reset);
        loadingProgress = findViewById(R.id.loading_progress);
        btnBack = findViewById(R.id.btn_back);

        apiService = ApiClient.getClient(this).create(AuthApiService.class);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        if (btnReset != null) {
            btnReset.setOnClickListener(new ClickDebounce() {
                @Override
                public void onDebouncedClick(View v) {
                    handleResetPassword();
                }
            });
        }
    }

    private void handleResetPassword() {
        if (edtPassword == null || edtConfirmPassword == null || tilPassword == null || tilConfirmPassword == null) {
            return;
        }

        String password = edtPassword.getText() != null ? edtPassword.getText().toString().trim() : "";
        String confirmPassword = edtConfirmPassword.getText() != null ? edtConfirmPassword.getText().toString().trim() : "";

        tilPassword.setError(null);
        tilConfirmPassword.setError(null);

        // Kiểm tra độ dài mật khẩu mới để đảm bảo độ bảo mật tối thiểu của hệ thống
        if (password.isEmpty()) {
            tilPassword.setError(getString(R.string.auth_error_empty_field));
            return;
        }
        if (password.length() < 6) {
            tilPassword.setError(getString(R.string.auth_error_short_password));
            return;
        }

        // Xác thực việc gõ lại mật khẩu để tránh người dùng gõ sai mật khẩu mong muốn
        if (confirmPassword.isEmpty()) {
            tilConfirmPassword.setError(getString(R.string.auth_error_empty_field));
            return;
        }
        if (!password.equals(confirmPassword)) {
            tilConfirmPassword.setError(getString(R.string.auth_msg_passwords_not_match));
            return;
        }

        setLoading(true);

        ResetPasswordRequest request = new ResetPasswordRequest(email, code, password);
        apiService.resetPassword(request).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(@NonNull Call<AuthResponse> call, @NonNull Response<AuthResponse> response) {
                setLoading(false);
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Toast.makeText(ResetPasswordActivity.this, getString(R.string.auth_msg_reset_password_success), Toast.LENGTH_LONG).show();

                    // Xóa toàn bộ stack và điều hướng về SignInActivity để bắt người dùng đăng nhập lại bằng mật khẩu mới
                    Intent intent = new Intent(ResetPasswordActivity.this, SignInActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                } else {
                    String msg = (response.body() != null) ? response.body().getMessage() : getString(R.string.auth_msg_invalid_code);
                    Toast.makeText(ResetPasswordActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<AuthResponse> call, @NonNull Throwable t) {
                setLoading(false);
                Toast.makeText(ResetPasswordActivity.this, getString(R.string.common_error_network), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setLoading(boolean isLoading) {
        if (loadingProgress != null) {
            loadingProgress.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
        if (btnReset != null) {
            btnReset.setEnabled(!isLoading);
            if (isLoading) {
                btnReset.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.mosco_btn_disabled)));
                btnReset.setTextColor(ContextCompat.getColor(this, R.color.lg_text_disabled));
            } else {
                btnReset.setBackgroundTintList(null);
                btnReset.setTextColor(ContextCompat.getColor(this, R.color.white));
            }
        }
        if (edtPassword != null) {
            edtPassword.setEnabled(!isLoading);
        }
        if (edtConfirmPassword != null) {
            edtConfirmPassword.setEnabled(!isLoading);
        }
        if (btnBack != null) {
            btnBack.setEnabled(!isLoading);
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
        AuthUIHelper.saveAnimationState();
    }
}
