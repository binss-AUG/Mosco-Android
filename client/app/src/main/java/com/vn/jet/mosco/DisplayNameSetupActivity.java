package com.vn.jet.mosco;

import com.vn.jet.mosco.utils.AuthUIHelper;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.OvershootInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.vn.jet.mosco.network.ApiClient;
import com.vn.jet.mosco.network.GameApiService;
import com.vn.jet.mosco.utils.SessionManager;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Màn hình đặt tên hiển thị trong game.
 * Hiển thị sau đăng ký/đăng nhập nếu user chưa có ingameName.
 */
public class DisplayNameSetupActivity extends AppCompatActivity {

    private TextInputEditText edtDisplayName;
    private TextInputLayout tilDisplayName;
    private View btnEnter;
    private ProgressBar loadingProgress;
    private View welcomeOverlay;
    private TextView tvWelcome;
    private com.airbnb.lottie.LottieAnimationView lottieConfetti;

    private SessionManager sessionManager;
    private GameApiService apiService;
    private boolean isSubmitting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_name_setup);
        AuthUIHelper.animateAurora(this);

        // Bind views
        edtDisplayName = findViewById(R.id.edt_display_name);
        tilDisplayName = findViewById(R.id.til_display_name);
        btnEnter = findViewById(R.id.btn_enter_galaxy);
        loadingProgress = findViewById(R.id.loading_progress);
        welcomeOverlay = findViewById(R.id.welcome_overlay);
        tvWelcome = findViewById(R.id.tv_welcome);
        lottieConfetti = findViewById(R.id.lottie_confetti);

        sessionManager = new SessionManager(this);
        apiService = ApiClient.getClient(this).create(GameApiService.class);

        // Pre-fill với username hiện tại (mặc định)
        String username = sessionManager.getUsername();
        if (username != null && !username.isEmpty()) {
            // Bảo vệ chống Crash: Cắt ngắn nếu dài hơn maxLength (16) của EditText
            if (username.length() > 16) {
                username = username.substring(0, 16);
            }
            edtDisplayName.setText(username);
            edtDisplayName.setSelection(username.length());
        }

        // Nút Enter Galaxy
        btnEnter.setOnClickListener(new com.vn.jet.mosco.utils.ClickDebounce() {
            @Override
            public void onDebouncedClick(View v) {
                validateAndSubmit();
            }
        });

        // Bấm Done trên bàn phím cũng submit (UX Premium)
        edtDisplayName.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                if (!isSubmitting) {
                    validateAndSubmit();
                }
                return true;
            }
            return false;
        });

        // Hiệu ứng nhịp thở cho glass card
        View glassCard = findViewById(R.id.glass_container);
        if (glassCard != null) {
            Animation breathing = AnimationUtils.loadAnimation(this, R.anim.anim_neon_breathing);
            // glassCard.startAnimation(breathing);
        }
    }

    private void setupAmbientEffects() {
        // GalacticBackgroundView handles its own animation now.
    }

    /**
     * Validate client-side rồi gọi API.
     */
    private void validateAndSubmit() {
        if (isSubmitting) return;

        String name = edtDisplayName.getText() != null
                ? edtDisplayName.getText().toString().trim() : "";
        tilDisplayName.setError(null);

        if (name.isEmpty()) {
            tilDisplayName.setError(getString(R.string.error_empty_field));
            return;
        }
        if (name.length() < 2 || name.length() > 16) {
            tilDisplayName.setError(getString(R.string.error_display_name_length));
            return;
        }

        setLoading(true);

        Map<String, String> body = new HashMap<>();
        body.put("ingameName", name);

        apiService.setDisplayName(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                setLoading(false);
                if (response.isSuccessful()) {
                    sessionManager.setIngameName(name);
                    showWelcomeEffect(name);
                } else {
                    String errorMsg = parseServerError(response);
                    tilDisplayName.setError(errorMsg);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                setLoading(false);
                Toast.makeText(DisplayNameSetupActivity.this,
                        getString(R.string.msg_network_error), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showWelcomeEffect(String displayName) {
        welcomeOverlay.setVisibility(View.VISIBLE);
        welcomeOverlay.setAlpha(0f);
        welcomeOverlay.animate().alpha(1f).setDuration(400).start();

        tvWelcome.setText(getString(R.string.setup_welcome, displayName));

        boolean hasConfetti = false;
        try {
            java.io.InputStream is = getAssets().open("confetti.json");
            is.close();
            hasConfetti = true;
        } catch (Exception ignored) {}

        if (hasConfetti) {
            try {
                lottieConfetti.setAnimation("confetti.json");
                lottieConfetti.setRepeatCount(0);
                lottieConfetti.playAnimation();
            } catch (Exception ignored) {}
        }

        tvWelcome.setScaleX(0.3f);
        tvWelcome.setScaleY(0.3f);
        tvWelcome.setAlpha(0f);

        tvWelcome.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(800)
                .setStartDelay(300)
                .setInterpolator(new OvershootInterpolator(1.5f))
                .start();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            ObjectAnimator pulse = ObjectAnimator.ofFloat(tvWelcome, "alpha", 1f, 0.7f, 1f);
            pulse.setDuration(1200);
            pulse.setRepeatCount(2);
            pulse.start();
        }, 1200);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, 3500);
    }

    private void setLoading(boolean isLoading) {
        this.isSubmitting = isLoading;
        loadingProgress.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnEnter.setEnabled(!isLoading);
        edtDisplayName.setEnabled(!isLoading);

        if (isLoading) {
            ((android.widget.Button) btnEnter).setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(this, R.color.mosco_btn_disabled)));
            ((android.widget.Button) btnEnter).setTextColor(
                    ContextCompat.getColor(this, R.color.mosco_text_disabled));
        } else {
            ((android.widget.Button) btnEnter).setBackgroundTintList(null);
            ((android.widget.Button) btnEnter).setTextColor(
                    ContextCompat.getColor(this, R.color.white));
        }
    }

    private String parseServerError(Response<ResponseBody> response) {
        try {
            if (response.errorBody() != null) {
                String body = response.errorBody().string();
                JSONObject json = new JSONObject(body);
                String msg = json.optString("message", "");
                if (!msg.isEmpty()) return msg;
            }
        } catch (Exception ignored) {}
        return "Không thể đặt tên này. Vui lòng thử lại.";
    }

    @Override
    protected void onPause() {
        super.onPause();
        com.vn.jet.mosco.utils.AuthUIHelper.saveAnimationState();
    }
}
