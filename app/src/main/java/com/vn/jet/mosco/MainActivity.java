package com.vn.jet.mosco;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

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

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                // Chuyển màn Home
                return true;
            } else if (id == R.id.nav_stage) {
                // Chuyển màn Stage
                return true;
            } else if (id == R.id.nav_collect) {
                // Chuyển màn Collect
                return true;
            } else if (id == R.id.nav_spin) {
                // Chuyển màn Spin
                return true;
            } else if (id == R.id.nav_profile) {
                // Chuyển màn Profile
                return true;
            }
            return false;
        });

        // Mặc định chọn Profile khi mới vào như trong ảnh của bạn
        bottomNav.setSelectedItemId(R.id.nav_profile);
    }
}