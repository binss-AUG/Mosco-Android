package com.vn.jet.mosco;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.vn.jet.mosco.fragment.HomeFragment;
import com.vn.jet.mosco.fragment.SpinFragment;

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
                // Tạm thời comment lại nếu chưa có file Java này
                // selectedFragment = new StageFragment();
            } else if (itemId == R.id.nav_collect) {
                // Tạm thời comment lại nếu chưa có file Java này
                // selectedFragment = new CollectFragment();
            } else if (itemId == R.id.nav_spin) {
                // ĐÂY LÀ CHỖ GỌI SPIN FRAGMENT CỦA BẠN LÊN NÈ!
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
        // Mặc định chọn Profile khi mới vào như trong ảnh của bạn
        bottomNav.setSelectedItemId(R.id.nav_profile);
    }
}