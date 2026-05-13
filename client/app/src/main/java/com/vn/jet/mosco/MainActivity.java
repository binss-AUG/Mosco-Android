package com.vn.jet.mosco;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationItemView;
import com.google.android.material.bottomnavigation.BottomNavigationMenuView;
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
public class MainActivity extends MoscoBaseActivity {
    private long lastNavClickTime = 0;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // Chỉ set top padding, bottom để trống vì nav bar floating tự quản lý
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
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

        // 2. Make Spin tab prominent
        makeSpinTabProminent(bottomNav);

        // 2. Handle navigation item selection with Spam prevention
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            
            // Nếu click vào tab đang hiển thị thì không làm gì (Tránh dựt lag)
            if (itemId == bottomNav.getSelectedItemId()) {
                return false;
            }

            // Debounce để tránh chuyển tab quá nhanh liên tục
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastNavClickTime < 500) {
                return false;
            }
            lastNavClickTime = currentTime;

            Fragment selectedFragment = null;
            if (itemId == R.id.nav_home) {
                selectedFragment = new HomeFragment();
            } else if (itemId == R.id.nav_stage) {
                selectedFragment = new com.vn.jet.mosco.fragment.StageFragment();
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
                        .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                        .replace(R.id.frame_layout, selectedFragment)
                        .commit();
                return true;
            }
            return false;
        });
        
        // Initialize on Home tab by default (Premium User Flow)
        bottomNav.setSelectedItemId(R.id.nav_home);

        // --- 🚀 EXIT CONFIRMATION SYSTEM ---
        setupExitConfirmation();
    }

    private void setupExitConfirmation() {
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                    getSupportFragmentManager().popBackStack();
                    com.vn.jet.mosco.utils.NavigationUtils.handleBackPress();
                } else {
                    showExitConfirmationDialog();
                }
            }
        });
    }

    private void showExitConfirmationDialog() {
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_exit_confirm, null);
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setView(dialogView);
        
        android.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0.85f);
        }

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_exit).setOnClickListener(v -> {
            dialog.dismiss();
            finishAffinity(); // Đóng toàn bộ ứng dụng sạch sẽ
        });

        dialog.show();
    }



    /**
     * Cho phép các Fragment gọi chuyển tab (ví dụ: click Avatar -> Profile)
     */
    public void selectTab(int itemId) {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(itemId);
        }
    }

    private void makeSpinTabProminent(BottomNavigationView bottomNav) {
        try {
            BottomNavigationMenuView menuView = (BottomNavigationMenuView) bottomNav.getChildAt(0);
            int childCount = menuView.getChildCount();
            
            for (int i = 0; i < childCount; i++) {
                BottomNavigationItemView itemView = (BottomNavigationItemView) menuView.getChildAt(i);
                
                if (itemView.getId() == R.id.nav_spin) {
                    for (int j = 0; j < itemView.getChildCount(); j++) {
                        View child = itemView.getChildAt(j);
                        if (child instanceof ImageView) {
                            ViewGroup.LayoutParams params = child.getLayoutParams();
                            params.width = (int) (getResources().getDisplayMetrics().density * 32);
                            params.height = (int) (getResources().getDisplayMetrics().density * 32);
                            child.setLayoutParams(params);
                        } else if (child instanceof TextView) {
                            TextView labelView = (TextView) child;
                            labelView.setTextSize(13);
                            labelView.setTypeface(labelView.getTypeface(), android.graphics.Typeface.BOLD);
                        }
                    }
                    
                    itemView.setPadding(0, (int) (-getResources().getDisplayMetrics().density * 8), 0, 0);
                    break;
                }
            }
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error making Spin tab prominent", e);
        }
    }

    @Override
    public void onBackPressed() {
        // Nếu không có Fragment nào trong backstack (chỉ còn Home), hiện Dialog thoát app
        if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
            com.vn.jet.mosco.utils.MoscoDialogHelper.showExitDialog(this, new com.vn.jet.mosco.utils.MoscoDialogHelper.DialogCallback() {
                @Override
                public void onPositive() {
                    finish();
                }
            });
        } else {
            super.onBackPressed();
        }
    }
}