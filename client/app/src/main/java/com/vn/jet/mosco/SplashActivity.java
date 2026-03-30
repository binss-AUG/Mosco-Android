package com.vn.jet.mosco;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.vn.jet.mosco.utils.SessionManager;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Wait 3 seconds then decide where to navigate
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            SessionManager sessionManager = new SessionManager(this);
            Intent intent;

            if (sessionManager.isLoggedIn()) {
                // Already logged in → skip Onboarding & SignIn, go straight to Main
                intent = new Intent(SplashActivity.this, MainActivity.class);
            } else {
                // First time / logged out → show Onboarding flow
                intent = new Intent(SplashActivity.this, OnboardingActivity.class);
            }

            startActivity(intent);
            finish();
        }, 3000);
    }
}