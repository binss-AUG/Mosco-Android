package com.vn.jet.mosco;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.text.style.ForegroundColorSpan;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class SignInActivity extends AppCompatActivity {

    private boolean isPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);

        EditText edtPassword = findViewById(R.id.edt_password);
        TextView tvGoToSignUp = findViewById(R.id.tv_go_to_signup);
        Button btnSignIn = findViewById(R.id.btn_signin);

        // --- 2. Làm nổi bật chữ "Sign Up" và bắt sự kiện Click ---
        String text = "I’m a new user. Sign Up";
        SpannableString spannableString = new SpannableString(text);
        // Tô màu xanh (#0066FF) cho chữ "Sign Up" (từ vị trí ký tự 16 đến 23)
        spannableString.setSpan(new ForegroundColorSpan(Color.parseColor("#0066FF")), 16, 23, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvGoToSignUp.setText(spannableString);

        // Chuyển sang màn hình SignUp
        tvGoToSignUp.setOnClickListener(v -> {
            Intent intent = new Intent(SignInActivity.this, SignUpActivity.class);
            startActivity(intent);
        });

        // Nút Back trên góc màn hình
        findViewById(R.id.btn_back).setOnClickListener(v -> onBackPressed());

        // Thiết lập ẩn/hiện mật khẩu cho ô Password
        setupPasswordVisibility(edtPassword);
    }

    // --- CẬP NHẬT HÀM HỖ TRỢ ẨN/HIỆN MẬT KHẨU SINH ĐỘNG ---
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
                        // 1. Đổi icon thành ic_eye khi mật khẩu bị che
                        editText.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_lock, 0, R.drawable.ic_eye, 0);
                        // 2. Ẩn mật khẩu (dùng PasswordTransformationMethod)
                        editText.setTransformationMethod(PasswordTransformationMethod.getInstance());
                        isVisible[0] = false;
                    } else {
                        // ĐANG ẨN -> CHUYỂN SANG HIỆN
                        // 1. Đổi icon thành ic_uneye khi mật khẩu hiện
                        editText.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_lock, 0, R.drawable.ic_uneye, 0);
                        // 2. Hiện mật khẩu (dùng HideReturnsTransformationMethod)
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
}
