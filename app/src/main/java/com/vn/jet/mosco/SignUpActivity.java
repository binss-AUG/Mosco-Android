package com.vn.jet.mosco;

import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.text.style.ForegroundColorSpan;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class SignUpActivity extends AppCompatActivity {

    private EditText edtPassword, edtConfirmPassword;
    private Button btnSendCode;
    private CountDownTimer countDownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        edtPassword = findViewById(R.id.edt_password);
        edtConfirmPassword = findViewById(R.id.edt_confirm_password);
        btnSendCode = findViewById(R.id.btn_send_code);
        TextView tvGoToSignIn = findViewById(R.id.tv_go_to_signin);
        Button btnSignUp = findViewById(R.id.btn_signup);
        ImageView btnBack = findViewById(R.id.btn_back);

        // 1. Cài đặt chức năng con mắt (ẩn/hiện) cho cả 2 ô mật khẩu
        setupPasswordVisibility(edtPassword);
        setupPasswordVisibility(edtConfirmPassword);

        // 2. Logic đếm ngược 60s cho nút Gửi Mã Xác Nhận
        btnSendCode.setOnClickListener(v -> {
            // TODO: Chèn API gọi gửi Email/SMS OTP ở đây
            Toast.makeText(this, "Đã gửi mã xác nhận!", Toast.LENGTH_SHORT).show();
            startCountdown();
        });

        // 3. Nút đăng ký (Kiểm tra xem mật khẩu có khớp không)
        btnSignUp.setOnClickListener(v -> {
            String pass = edtPassword.getText().toString();
            String confirmPass = edtConfirmPassword.getText().toString();

            if (!pass.equals(confirmPass)) {
                Toast.makeText(this, "Mật khẩu không khớp!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Tạo tài khoản thành công!", Toast.LENGTH_SHORT).show();
                finish(); // Trở về SignIn
            }
        });

        // 4. Highlight chữ Sign In và nút Back
        String text = "Already have an account? Sign In";
        SpannableString spannableString = new SpannableString(text);
        spannableString.setSpan(new ForegroundColorSpan(Color.parseColor("#0066FF")), 25, 32, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvGoToSignIn.setText(spannableString);

        tvGoToSignIn.setOnClickListener(v -> finish());
        btnBack.setOnClickListener(v -> finish());
    }

    // --- HÀM HỖ TRỢ ĐẾM NGƯỢC 60S ---
    private void startCountdown() {
        btnSendCode.setEnabled(false); // Khóa nút không cho ấn liên tục
        btnSendCode.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#41455E"))); // Đổi màu xám đi

        countDownTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                // Cập nhật số giây còn lại
                btnSendCode.setText((millisUntilFinished / 1000) + "s");
            }

            @Override
            public void onFinish() {
                // Hết 60s, cho phép ấn lại
                btnSendCode.setText("Gửi lại");
                btnSendCode.setEnabled(true);
                btnSendCode.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#0066FF"))); // Trả lại màu xanh
            }
        }.start();
    }

    // --- CẬP NHẬT HÀM HỖ TRỢ ẨN/HIỆN MẬT KHẨU ---
    private void setupPasswordVisibility(EditText editText) {
        // Mảng 1 phần tử để lách luật biến final trong lambda expression
        final boolean[] isVisible = {false};

        editText.setOnTouchListener((v, event) -> {
            final int DRAWABLE_RIGHT = 2; // Vị trí của icon mắt (End)
            if (event.getAction() == MotionEvent.ACTION_UP) {
                // Kiểm tra chạm vào icon con mắt
                if (editText.getCompoundDrawables()[DRAWABLE_RIGHT] != null && 
                    event.getRawX() >= (editText.getRight() - editText.getCompoundDrawables()[DRAWABLE_RIGHT].getBounds().width() - 50)) {

                    if (isVisible[0]) {
                        // ĐANG HIỆN -> CHUYỂN SANG ẨN
                        // Mật khẩu bị che = ic_eye
                        editText.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_lock, 0, R.drawable.ic_eye, 0);
                        editText.setTransformationMethod(PasswordTransformationMethod.getInstance());
                        isVisible[0] = false;
                    } else {
                        // ĐANG ẨN -> CHUYỂN SANG HIỆN
                        // Mật khẩu hiện = ic_uneye
                        editText.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_lock, 0, R.drawable.ic_uneye, 0);
                        editText.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                        isVisible[0] = true;
                    }

                    // Đưa con trỏ chuột về cuối đoạn text
                    editText.setSelection(editText.getText().length());
                    return true;
                }
            }
            return false;
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Hủy timer nếu người dùng thoát màn hình SignUp giữa chừng để tránh rò rỉ bộ nhớ
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}