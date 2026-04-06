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
public class MainActivity extends AppCompatActivity {
    private long lastNavClickTime = 0;
    
    // UI Elements for Network HUD
    private View llNetworkStatus;
    private View llNetworkContainer;
    private ImageView ivNetworkIcon;
    private TextView tvNetworkMessage;
    private android.view.animation.Animation pulseAnim;

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

        // --- 🌐 NETWORK MONITOR SETUP ---
        setupNetworkMonitor();

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
                showExitConfirmationDialog();
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
     * Tích hợp hệ thống giám sát kết nối thời gian thực.
     */
    private void setupNetworkMonitor() {
        llNetworkStatus = findViewById(R.id.ll_network_status);
        llNetworkContainer = findViewById(R.id.ll_network_container);
        ivNetworkIcon = findViewById(R.id.iv_network_icon);
        tvNetworkMessage = findViewById(R.id.tv_network_message);
        pulseAnim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.anim_pulse_warning);

        com.vn.jet.mosco.utils.NetworkMonitor.getInstance(this).getIsConnected()
                .observe(this, isConnected -> {
            if (isConnected) {
                showOnlineStatus();
            } else {
                showOfflineStatus();
            }
        });
    }

    private void showOfflineStatus() {
        if (llNetworkStatus == null) return;
        
        llNetworkStatus.setVisibility(View.VISIBLE);
        llNetworkContainer.setBackgroundResource(R.drawable.bg_network_lost_glass);
        ivNetworkIcon.setImageResource(R.drawable.ic_close);
        tvNetworkMessage.setText("CONNECTION LOST");
        tvNetworkMessage.startAnimation(pulseAnim);
    }

    private void showOnlineStatus() {
        if (llNetworkStatus == null || llNetworkStatus.getVisibility() == View.GONE) return;

        // Xóa hiệu ứng cảnh báo và đổi sang màu xanh thành công
        tvNetworkMessage.clearAnimation();
        llNetworkContainer.setBackgroundResource(R.drawable.bg_network_back_online_glass);
        ivNetworkIcon.setImageResource(R.drawable.ic_check);
        tvNetworkMessage.setText("BACK ONLINE");

        // Biến mất sau 2.5 giây để người dùng kịp nhìn thấy trạng thái đã khôi phục
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            llNetworkStatus.animate()
                    .alpha(0f)
                    .setDuration(500)
                    .withEndAction(() -> {
                        llNetworkStatus.setVisibility(View.GONE);
                        llNetworkStatus.setAlpha(1f); // Reset alpha cho lần sau
                    })
                    .start();
        }, 2500);
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
}