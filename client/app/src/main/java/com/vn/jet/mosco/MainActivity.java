package com.vn.jet.mosco;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.vn.jet.mosco.fragment.CollectionFragment;
import com.vn.jet.mosco.fragment.HomeFragment;
import com.vn.jet.mosco.fragment.ProfileFragment;
import com.vn.jet.mosco.fragment.SpinFragment;
import com.vn.jet.mosco.fragment.UpgradeFragment;

/**
 * MainActivity - Main entry point for authenticated users.
 * Manages BottomNavigationView and Fragment switching.
 * Standardized English comments and removed parallax effects (bithw).
 */
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

        // 1. Initial configuration: Show HomeFragment by default
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.frame_layout, new HomeFragment())
                    .commit();
            bottomNav.setSelectedItemId(R.id.nav_home);
        }

        // 2. Handle navigation item selection
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
                selectedFragment = new ProfileFragment();
            }

            // 3. Perform Fragment transaction
            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.frame_layout, selectedFragment)
                        .commit();
                return true;
            }
            return false;
        });
        
        // Initialize on Home tab by default (Premium User Flow)
        bottomNav.setSelectedItemId(R.id.nav_home);
    }
}