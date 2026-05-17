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
import com.vn.jet.mosco.model.UserStats;
import com.vn.jet.mosco.network.ApiClient;
import com.vn.jet.mosco.network.GameApiService;
import com.vn.jet.mosco.utils.SessionManager;
import com.vn.jet.mosco.utils.NumberUtils;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import android.util.Log;
import android.widget.ProgressBar;
import com.vn.jet.mosco.fragment.ShopFragment;

import com.google.android.material.bottomnavigation.BottomNavigationItemView;
import com.google.android.material.bottomnavigation.BottomNavigationMenuView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.vn.jet.mosco.fragment.CollectionFragment;
import com.vn.jet.mosco.fragment.HomeFragment;
import com.vn.jet.mosco.fragment.ProfileFragment;
import com.vn.jet.mosco.fragment.SpinFragment;
import com.vn.jet.mosco.fragment.UpgradeFragment;
import java.util.List;
import com.vn.jet.mosco.model.UserMail;

/**
 * MainActivity - Main entry point for authenticated users.
 * Manages BottomNavigationView and Fragment switching.
 * Standardized English comments and removed parallax effects (bithw).
 */
public class MainActivity extends MoscoBaseActivity {
    private long lastNavClickTime = 0;
    
    private TextView tvCoins;
    private TextView tvDiamonds;
    private TextView tvUsername;
    private TextView tvLevel;
    private ProgressBar pbHomeXp;
    private ImageView ivHomeAvatar;
    private TextView tvStreakAvatarVal;
    private com.airbnb.lottie.LottieAnimationView lottieStreakAvatar;
    private View flStreakAvatar;
    private View btnHeaderBack;
    private View llHeaderButtons;
    private View llShopCurrencies;
    private View llInternalCurrencies;
    private TextView tvShopCoins;
    private TextView tvShopDiamonds;
    private TextView tvBadgeFriends;
    private TextView tvBadgeMailbox;
    private TextView tvBadgeShop;
    
    public static final int TOP_BAR_MODE_HOME = 0;
    public static final int TOP_BAR_MODE_SHOP = 1;
    
    private GameApiService gameApiService;
    private SessionManager sessionManager;
    
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

        sessionManager = new SessionManager(this);
        gameApiService = ApiClient.getClient(this).create(GameApiService.class);
        
        setupHeader();
        loadUserData();

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
                // Chỉ hiện Header ở Home, các tab khác ẩn
                setTopBarVisible(itemId == R.id.nav_home, TOP_BAR_MODE_HOME);
                
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
        
        // Mặc định hiện Header ở Home
        setTopBarVisible(true);
    }
    
    public void setTopBarVisible(boolean visible) {
        setTopBarVisible(visible, TOP_BAR_MODE_HOME);
    }

    public void setTopBarVisible(boolean visible, int mode) {
        View header = findViewById(R.id.cl_header_row);
        if (header != null) {
            header.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        updateTopBarMode(mode);
    }

    private void updateTopBarMode(int mode) {
        if (btnHeaderBack != null) btnHeaderBack.setVisibility(mode == TOP_BAR_MODE_SHOP ? View.VISIBLE : View.GONE);
        if (llHeaderButtons != null) llHeaderButtons.setVisibility(mode == TOP_BAR_MODE_HOME ? View.VISIBLE : View.GONE);
        if (llShopCurrencies != null) llShopCurrencies.setVisibility(mode == TOP_BAR_MODE_SHOP ? View.VISIBLE : View.GONE);
        if (llInternalCurrencies != null) llInternalCurrencies.setVisibility(mode == TOP_BAR_MODE_HOME ? View.VISIBLE : View.GONE);
        
        // Ẩn/hiện cl_header (Avatar, Name, Level) tùy chế độ
        View clHeader = findViewById(R.id.cl_header);
        if (clHeader != null) {
            clHeader.setVisibility(mode == TOP_BAR_MODE_SHOP ? View.GONE : View.VISIBLE);
        }
    }

    private void setupHeader() {
        tvCoins    = findViewById(R.id.tv_home_coins);
        tvDiamonds = findViewById(R.id.tv_home_diamonds);
        tvUsername = findViewById(R.id.tv_home_username);
        tvLevel = findViewById(R.id.tv_home_level);
        pbHomeXp = findViewById(R.id.pb_home_xp);
        ivHomeAvatar = findViewById(R.id.iv_home_avatar);
        tvStreakAvatarVal = findViewById(R.id.tv_streak_avatar_val);
        lottieStreakAvatar = findViewById(R.id.lottie_streak_avatar);
        flStreakAvatar = findViewById(R.id.fl_streak_avatar);
        btnHeaderBack = findViewById(R.id.btn_header_back);
        llHeaderButtons = findViewById(R.id.ll_header_buttons);
        llShopCurrencies = findViewById(R.id.ll_shop_currencies);
        llInternalCurrencies = findViewById(R.id.ll_internal_currencies);
        tvShopCoins = findViewById(R.id.tv_shop_coins);
        tvShopDiamonds = findViewById(R.id.tv_shop_diamonds);
        tvBadgeFriends = findViewById(R.id.tv_badge_friends);
        tvBadgeMailbox = findViewById(R.id.tv_badge_mailbox);
        tvBadgeShop = findViewById(R.id.tv_badge_shop);

        if (btnHeaderBack != null) {
            btnHeaderBack.setOnClickListener(v -> {
                getOnBackPressedDispatcher().onBackPressed();
            });
        }

        // Cài đặt tương tác cho các phần tử
        if (flStreakAvatar != null) {
            flStreakAvatar.setOnClickListener(v -> {
                if (cachedStats != null) {
                    showStreakDetail(cachedStats.getStreak(), cachedStats.getBestStreak(), cachedStats.getStreakRestoresThisMonth());
                }
            });
        }
        
        View btnFriends = findViewById(R.id.btn_header_friends);
        if (btnFriends != null) {
            btnFriends.setOnClickListener(v -> {
                startActivity(new android.content.Intent(this, com.vn.jet.mosco.FriendActivity.class));
            });
        }
        
        View btnMailbox = findViewById(R.id.btn_header_mailbox);
        if (btnMailbox != null) {
            btnMailbox.setOnClickListener(v -> {
                com.vn.jet.mosco.utils.NavigationUtils.openMailbox(this);
            });
        }
        
        View btnShop = findViewById(R.id.btn_header_shop);
        if (btnShop != null) {
            btnShop.setOnClickListener(v -> {
                openShop();
            });
        }
        
        if (ivHomeAvatar != null) {
            ivHomeAvatar.setOnClickListener(v -> {
                com.vn.jet.mosco.utils.NavigationUtils.openProfile(this, null);
            });
        }
    }

    private void showStreakDetail(int currentStreak, int bestStreak, int restores) {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.CustomBottomSheetDialogTheme);
        View view = android.view.LayoutInflater.from(this).inflate(R.layout.bottom_sheet_streak_detail, null);
        
        TextView tvCurrent = view.findViewById(R.id.tv_current_streak);
        TextView tvBest = view.findViewById(R.id.tv_best_streak);
        android.widget.Button btnRestore = view.findViewById(R.id.btn_restore_streak);
        com.airbnb.lottie.LottieAnimationView ivIcon = view.findViewById(R.id.iv_streak_icon);
        
        if (ivIcon != null) {
            ivIcon.setMinAndMaxFrame(0, 24);
            ivIcon.playAnimation();
            if (currentStreak >= 1000) {
                // Hiệu ứng RGB cầu vồng cho streak khủng
                android.animation.ValueAnimator rgbAnim = android.animation.ValueAnimator.ofFloat(0f, 360f);
                rgbAnim.setDuration(3000);
                rgbAnim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                rgbAnim.addUpdateListener(anim -> {
                    com.vn.jet.mosco.utils.StreakColorHelper.applyRGBEffect(ivIcon, (float) anim.getAnimatedValue());
                });
                rgbAnim.start();
                dialog.setOnDismissListener(d -> rgbAnim.cancel());
            } else {
                com.vn.jet.mosco.utils.StreakColorHelper.applyStreakColor(ivIcon, currentStreak);
            }
        }
        
        tvCurrent.setText(getString(R.string.rank_format_streak, currentStreak));
        tvBest.setText(getString(R.string.rank_format_streak, bestStreak));
        btnRestore.setText(restores < 3 ? "RESTORE (FREE " + (3 - restores) + "/3)" : "RESTORE (500 DIAMONDS)");

        btnRestore.setOnClickListener(v -> {
            btnRestore.setEnabled(false);
            gameApiService.restoreStreak().enqueue(new Callback<com.vn.jet.mosco.model.ApiResponse<UserStats>>() {
                @Override
                public void onResponse(Call<com.vn.jet.mosco.model.ApiResponse<UserStats>> call, Response<com.vn.jet.mosco.model.ApiResponse<UserStats>> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        loadUserData();
                        dialog.dismiss();
                    } else { btnRestore.setEnabled(true); }
                }
                @Override
                public void onFailure(Call<com.vn.jet.mosco.model.ApiResponse<UserStats>> call, Throwable t) { btnRestore.setEnabled(true); }
            });
        });
        dialog.setContentView(view);
        dialog.show();
    }

    private UserStats cachedStats;
    public void loadUserData() {
        Long userId = sessionManager.getUserId();
        if (userId == null) return;
        
        gameApiService.getUserStats(userId).enqueue(new Callback<UserStats>() {
            @Override
            public void onResponse(Call<UserStats> call, Response<UserStats> response) {
                if (response.isSuccessful() && response.body() != null) {
                    UserStats stats = response.body();
                    cachedStats = stats;
                    if (tvCoins != null) tvCoins.setText(NumberUtils.format(MainActivity.this, stats.getCoins()));
                    if (tvDiamonds != null) tvDiamonds.setText(NumberUtils.format(MainActivity.this, stats.getDiamonds()));
                    if (tvShopCoins != null) tvShopCoins.setText(NumberUtils.format(MainActivity.this, stats.getCoins()));
                    if (tvShopDiamonds != null) tvShopDiamonds.setText(NumberUtils.format(MainActivity.this, stats.getDiamonds()));
                    
                    String displayName = sessionManager.getIngameName();
                    if (displayName == null || displayName.isEmpty()) displayName = sessionManager.getUsername();
                    if (tvUsername != null) {
                        tvUsername.setText(displayName);
                        tvUsername.setSelected(true);
                    }
                    
                    if (tvLevel != null) tvLevel.setText(getString(R.string.format_level, stats.getLevel()));
                    
                    long nextLevelXp = stats.getLevel() * 1000L;
                    if (nextLevelXp == 0) nextLevelXp = 1000;
                    int progress = (int) ((stats.getExp() * 100) / nextLevelXp);
                    if (pbHomeXp != null) pbHomeXp.setProgress(progress);
                    
                    String avatarId = sessionManager.getAvatarId();
                    com.vn.jet.mosco.utils.AvatarUtils.loadAvatar(MainActivity.this, ivHomeAvatar, userId, avatarId);
                    
                    if (tvStreakAvatarVal != null) tvStreakAvatarVal.setText(String.valueOf(stats.getStreak()));
                    if (lottieStreakAvatar != null) {
                        lottieStreakAvatar.setMinAndMaxFrame(0, 24);
                        if (!lottieStreakAvatar.isAnimating()) lottieStreakAvatar.playAnimation();
                        com.vn.jet.mosco.utils.StreakColorHelper.applyStreakColor(lottieStreakAvatar, stats.getStreak());
                    }
                    
                    // Cập nhật thông báo
                    updateHeaderBadges(stats.getFriendsCount(), 0, 0); // Tạm thời mail/shop là 0
                    fetchExtraNotificationCounts();
                }
            }
            @Override
            public void onFailure(Call<UserStats> call, Throwable t) {
                Log.e("MainActivity", "Failed to fetch stats", t);
            }
        });
    }

    public void openShop() {
        setTopBarVisible(true, TOP_BAR_MODE_SHOP);
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.anim_slide_in_right, R.anim.anim_slide_out_left, R.anim.anim_slide_in_left, R.anim.anim_slide_out_right)
                .replace(R.id.frame_layout, new ShopFragment())
                .addToBackStack("Shop")
                .commit();
    }


    private void setupExitConfirmation() {
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                    getSupportFragmentManager().popBackStack();
                    com.vn.jet.mosco.utils.NavigationUtils.handleBackPress();
                    
                    // Sau khi pop, kiểm tra lại xem có đang ở Home không để hiện Top Bar
                    // Đợi fragment chuyển xong
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.frame_layout);
                        if (currentFragment instanceof HomeFragment) {
                            setTopBarVisible(true, TOP_BAR_MODE_HOME);
                        } else if (currentFragment instanceof ShopFragment) {
                            setTopBarVisible(true, TOP_BAR_MODE_SHOP);
                        } else {
                            setTopBarVisible(false);
                        }
                    }, 200);
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

    private void updateHeaderBadges(int friendsCount, int mailCount, int shopNewCount) {
        if (tvBadgeFriends != null && friendsCount != -1) {
            if (friendsCount > 0) {
                tvBadgeFriends.setText(friendsCount > 99 ? "99+" : String.valueOf(friendsCount));
                tvBadgeFriends.setVisibility(View.VISIBLE);
                animateBadgePop(tvBadgeFriends);
            } else {
                tvBadgeFriends.setVisibility(View.GONE);
            }
        }

        if (tvBadgeMailbox != null && mailCount != -1) {
            if (mailCount > 0) {
                tvBadgeMailbox.setText(mailCount > 99 ? "99+" : String.valueOf(mailCount));
                tvBadgeMailbox.setVisibility(View.VISIBLE);
                animateBadgePop(tvBadgeMailbox);
            } else {
                tvBadgeMailbox.setVisibility(View.GONE);
            }
        }

        if (tvBadgeShop != null && shopNewCount != -1) {
            if (shopNewCount > 0) {
                tvBadgeShop.setText("N");
                tvBadgeShop.setVisibility(View.VISIBLE);
            } else {
                tvBadgeShop.setVisibility(View.GONE);
            }
        }
    }

    private void animateBadgePop(View badge) {
        badge.setScaleX(0.5f);
        badge.setScaleY(0.5f);
        badge.setAlpha(0f);
        badge.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(180)
            .setInterpolator(new android.view.animation.OvershootInterpolator())
            .start();
    }

    private void fetchExtraNotificationCounts() {
        // Mock fetch or call actual APIs
        gameApiService.getUserMails(sessionManager.getUserId()).enqueue(new Callback<List<com.vn.jet.mosco.model.UserMail>>() {
            @Override
            public void onResponse(Call<List<com.vn.jet.mosco.model.UserMail>> call, Response<List<com.vn.jet.mosco.model.UserMail>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    int unread = 0;
                    for (com.vn.jet.mosco.model.UserMail mail : response.body()) {
                        if (!mail.isReceived()) unread++;
                    }
                    updateHeaderBadges(-1, unread, -1);
                }
            }
            @Override public void onFailure(Call<List<com.vn.jet.mosco.model.UserMail>> call, Throwable t) {}
        });
    }
}