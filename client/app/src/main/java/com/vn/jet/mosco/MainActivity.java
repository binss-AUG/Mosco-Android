package com.vn.jet.mosco;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.vn.jet.mosco.fragment.CollectionFragment;
import com.vn.jet.mosco.fragment.HomeFragment;
import com.vn.jet.mosco.fragment.ShopFragment;
import com.vn.jet.mosco.fragment.SpinFragment;
import com.vn.jet.mosco.fragment.UpgradeFragment;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Nhận "Nhịp tim" Animation từ màn trước
        long playTimeX = getIntent().getLongExtra("EXTRA_PLAY_TIME_X", 0L);
        long playTimeY = getIntent().getLongExtra("EXTRA_PLAY_TIME_Y", 0L);

        setupParallax(playTimeX, playTimeY);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

        // 1. Mặc định lúc vừa vào App sẽ hiển thị màn hình Home
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().replace(R.id.frame_layout, new HomeFragment()).commit();
            bottomNav.setSelectedItemId(R.id.nav_home); // Làm sáng tab Home
        }

        // 2. Bắt sự kiện khi bấm vào các Tab
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.nav_home) {
                selectedFragment = new HomeFragment();
            } else if (itemId == R.id.nav_stage) {
                selectedFragment = new UpgradeFragment();
            } else if (itemId == R.id.nav_collect) {
                    selectedFragment = new CollectionFragment();
            } else if (itemId == R.id.nav_spin) {
                selectedFragment = new SpinFragment();
            } else if (itemId == R.id.nav_profile) {
                // Tạm thời comment lại nếu chưa có file Java này
                // selectedFragment = new ProfileFragment();
            }

            // 3. Nhét Fragment vừa chọn vào cái Khung (frame_layout)
            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.frame_layout, selectedFragment)
                        .commit();
                return true; // Trả về true để thanh tab sáng lên
            }
            return false;
        });
        bottomNav.setSelectedItemId(R.id.nav_profile);
    }

    private void setupParallax(long playTimeX, long playTimeY) {
        ImageView ivBackground = findViewById(R.id.iv_background_parallax);
        if (ivBackground != null) {
            ivBackground.setScaleType(ImageView.ScaleType.CENTER_CROP);
            ivBackground.setScaleX(1.3f);
            ivBackground.setScaleY(1.3f);
            
            ObjectAnimator driftX = ObjectAnimator.ofFloat(ivBackground, "translationX", -60f, 60f);
            driftX.setDuration(15000);
            driftX.setRepeatMode(ValueAnimator.REVERSE);
            driftX.setRepeatCount(ValueAnimator.INFINITE);

            ObjectAnimator driftY = ObjectAnimator.ofFloat(ivBackground, "translationY", -40f, 40f);
            driftY.setDuration(20000);
            driftY.setRepeatMode(ValueAnimator.REVERSE);
            driftY.setRepeatCount(ValueAnimator.INFINITE);

            driftX.start();
            driftY.start();

            // Tiếp tục nhịp phim
            driftX.setCurrentPlayTime(playTimeX);
            driftY.setCurrentPlayTime(playTimeY);
        }
    }
}