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
import androidx.fragment.app.FragmentManager;
import com.vn.jet.mosco.model.UserStats;
import com.vn.jet.mosco.network.ApiClient;
import com.vn.jet.mosco.network.WebSocketManager;
import com.vn.jet.mosco.model.PrivateChatMessage;
import com.vn.jet.mosco.model.CoupleStreakDto;
import com.vn.jet.mosco.fragment.ChatPrivateFragment;
import io.reactivex.disposables.Disposable;
import com.vn.jet.mosco.network.GameApiService;
import com.vn.jet.mosco.utils.SessionManager;
import com.vn.jet.mosco.utils.NumberUtils;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.PopupWindow;
import android.graphics.drawable.ColorDrawable;
import com.vn.jet.mosco.fragment.ShopFragment;
import java.util.List;
import com.vn.jet.mosco.model.UserMail;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.facebook.shimmer.ShimmerFrameLayout;
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
    private View btnHeaderMore;
    private View llShopCurrencies;
    private View llInternalCurrencies;
    private TextView tvShopCoins;
    private TextView tvShopDiamonds;
    private com.facebook.shimmer.ShimmerFrameLayout shimmerHeader;

    private int badgeFriendCount;
    private int badgeMailCount;
    private int badgeShopCount;

    public static final int TOP_BAR_MODE_HOME = 0;
    public static final int TOP_BAR_MODE_SHOP = 1;
    
    private GameApiService gameApiService;
    private SessionManager sessionManager;
    
    private Disposable privateChatDisposable;
    private Disposable streakDisposable;
    private Disposable errorDisposable;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            boolean keyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            
            int topPadding = systemBars.top;
            if (topPadding == 0) {
                int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
                if (resourceId > 0) {
                    topPadding = getResources().getDimensionPixelSize(resourceId);
                }
            }
            
            // Nếu có bàn phím ảo hiển thị thì chừa bottom padding cho bàn phím, ngược lại để 0 để floating bottom nav tự quản lý
            v.setPadding(systemBars.left, topPadding, systemBars.right, keyboardVisible ? systemBars.bottom : 0);
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

        // 2. Setup custom Liquid Glass bottom navigation bar (Visual & functional mapping)
        setupCustomBottomNavigation(bottomNav);

        // ---  AUTO-BACKUP SYSTEM (PHASE 3) ---
        com.vn.jet.mosco.utils.WorkScheduler.scheduleAutoBackup(this);

        // ---  EXIT CONFIRMATION SYSTEM ---
        setupExitConfirmation();

        // Đồng bộ UI lần đầu — nếu khởi tạo mới thì mặc định Home
        if (savedInstanceState == null) {
            setTopBarVisible(true, TOP_BAR_MODE_HOME);
            setBottomNavVisible(true);
        } else {
            syncUiWithFragment();
        }

        // Setup AI Assistant FAB
        com.vn.jet.mosco.widget.DraggableFab fabAi = findViewById(R.id.fab_ai_assistant);
        if (fabAi != null) {
            fabAi.setOnClickListener(v -> {
                com.vn.jet.mosco.fragment.AiChatBottomSheet sheet = new com.vn.jet.mosco.fragment.AiChatBottomSheet();
                sheet.show(getSupportFragmentManager(), "AiChatBottomSheet");
            });
            updateAiFabAvatar();
        }

        // Listener trung tâm — tự động ẩn/hiện Header & Bottom Nav dựa trên BackStack
        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(MainActivity.this::syncUiWithFragment, 100);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // TẠI SAO: Khởi động WebSocket và đăng ký lắng nghe tin nhắn/streak thời gian thực khi vào app
        if (sessionManager != null && sessionManager.isLoggedIn() && sessionManager.getUserId() != null) {
            String userIdStr = String.valueOf(sessionManager.getUserId());
            
            // Đảm bảo kết nối WebSocket Stomp
            WebSocketManager.getInstance().connect();
            
            // Đăng ký nhận tin nhắn riêng
            privateChatDisposable = WebSocketManager.getInstance().subscribeToPrivateChat(
                userIdStr,
                this::onReceivePrivateMessage
            );
            
            // Đăng ký nhận cập nhật chuỗi ngày (streak)
            streakDisposable = WebSocketManager.getInstance().subscribeToStreakUpdates(
                userIdStr,
                this::onReceiveStreakUpdate
            );

            // Đăng ký nhận thông báo lỗi cấm chat từ Moderator
            errorDisposable = WebSocketManager.getInstance().subscribeToErrors(
                userIdStr,
                this::onReceiveSystemError
            );
        }
    }

    private void onReceiveSystemError(String errorMessage) {
        com.vn.jet.mosco.utils.MoscoDialogHelper.showConfirmDialog(
            this,
            "Cảnh Báo Hệ Thống",
            errorMessage,
            "Đã hiểu",
            null,
            null
        );
    }

    @Override
    protected void onPause() {
        super.onPause();
        
        // TẠI SAO: Giải phóng RxJava subscription để ngăn rò rỉ bộ nhớ khi MainActivity chuyển sang chế độ tạm dừng
        if (privateChatDisposable != null && !privateChatDisposable.isDisposed()) {
            privateChatDisposable.dispose();
        }
        if (streakDisposable != null && !streakDisposable.isDisposed()) {
            streakDisposable.dispose();
        }
        if (errorDisposable != null && !errorDisposable.isDisposed()) {
            errorDisposable.dispose();
        }
    }

    private void onReceivePrivateMessage(PrivateChatMessage msg) {
        if (msg == null) return;

        // TẠI SAO: Ẩn các tin nhắn hệ thống (SYSTEM_FRIEND) hoặc nội dung là FRIEND_UPDATE dùng để trigger refresh giao diện
        if ("SYSTEM_FRIEND".equals(msg.getSenderId()) || "FRIEND_UPDATE".equals(msg.getContent())) {
            return;
        }

        // TẠI SAO: Kiểm tra cấu hình thông báo tin nhắn riêng có được bật không
        if (!sessionManager.isPrivateChatNotificationEnabled()) {
            return;
        }

        // TẠI SAO: Bỏ qua thông báo nếu người dùng đang mở chính phòng chat với người gửi đó
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.frame_layout);
        if (currentFragment instanceof ChatPrivateFragment) {
            Long currentPartnerId = ((ChatPrivateFragment) currentFragment).getPartnerId();
            if (currentPartnerId != null && String.valueOf(currentPartnerId).equals(msg.getSenderId())) {
                return;
            }
        }

        // TẠI SAO: Hiển thị banner HUD neon luxury thông báo cho người dùng và phản hồi rung tactile
        String senderName = msg.getSenderName() != null ? msg.getSenderName() : "User";
        // TẠI SAO: Giải mã các ký tự HTML (VD: ch&agrave;o -> chào) để hiển thị thông báo mượt mà
        String decodedContent = msg.getContent();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            decodedContent = android.text.Html.fromHtml(msg.getContent(), android.text.Html.FROM_HTML_MODE_LEGACY).toString();
        } else {
            decodedContent = android.text.Html.fromHtml(msg.getContent()).toString();
        }

        String displayMsg = senderName + ": " + decodedContent;
        com.vn.jet.mosco.widget.MoscoNotification.showSuccess(this, displayMsg);
        
        View decor = getWindow().getDecorView();
        if (decor != null) {
            decor.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        }

        // TẠI SAO: Cập nhật đếm Mailbox chưa đọc ngay lập tức trên menu
        fetchExtraNotificationCounts();
    }

    private void onReceiveStreakUpdate(CoupleStreakDto data) {
        if (data == null) return;

        // TẠI SAO: Kiểm tra cấu hình thông báo streak có được bật không
        if (!sessionManager.isStreakNotificationEnabled()) {
            return;
        }

        // TẠI SAO: Thông báo cập nhật streak bùng cháy đa ngôn ngữ dùng XML resource string
        String displayMsg = getString(R.string.settings_noti_streak_update, data.getStreakCount());
        com.vn.jet.mosco.widget.MoscoNotification.showSuccess(this, displayMsg);
        
        View decor = getWindow().getDecorView();
        if (decor != null) {
            decor.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        }

        // TẠI SAO: Làm mới dữ liệu user để cập nhật UI streak ở Header
        loadUserData();
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
        if (btnHeaderMore != null) btnHeaderMore.setVisibility(mode == TOP_BAR_MODE_HOME ? View.VISIBLE : View.GONE);
        if (llShopCurrencies != null) llShopCurrencies.setVisibility(mode == TOP_BAR_MODE_SHOP ? View.VISIBLE : View.GONE);
        if (llInternalCurrencies != null) llInternalCurrencies.setVisibility(mode == TOP_BAR_MODE_HOME ? View.VISIBLE : View.GONE);
        
        // Ẩn/hiện cl_header (Avatar, Name, Level) tùy chế độ
        View clHeader = findViewById(R.id.cl_header);
        if (clHeader != null) {
            clHeader.setVisibility(mode == TOP_BAR_MODE_SHOP ? View.GONE : View.VISIBLE);
        }
    }

    public void setBottomNavVisible(boolean visible) {
        View customNav = findViewById(R.id.cl_custom_bottom_navigation);
        if (customNav != null) {
            customNav.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void syncUiWithFragment() {
        boolean hasBackStack = getSupportFragmentManager().getBackStackEntryCount() > 0;
        if (hasBackStack) {
            setBottomNavVisible(false);
            setTopBarVisible(false);
        } else {
            setBottomNavVisible(true);
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.frame_layout);
            if (currentFragment != null) {
                setTopBarVisible(currentFragment instanceof HomeFragment, TOP_BAR_MODE_HOME);
            } else {
                setTopBarVisible(false);
            }
        }
    }

    private void syncUiForTab(int itemId) {
        setBottomNavVisible(true);
        setTopBarVisible(itemId == R.id.nav_home, TOP_BAR_MODE_HOME);
    }

    private void setupHeader() {
        shimmerHeader = findViewById(R.id.shimmer_header);
        tvCoins    = findViewById(R.id.tv_home_coins);
        tvDiamonds = findViewById(R.id.tv_home_diamonds);
        tvUsername = findViewById(R.id.tv_home_username);
        if (tvUsername != null) {
            // Kích hoạt setSelected(true) để Android TextView khởi chạy hoạt ảnh chữ chạy ngang (marquee/lineshow) khi tên quá dài.
            tvUsername.setSelected(true);
        }
        tvLevel = findViewById(R.id.tv_home_level);
        pbHomeXp = findViewById(R.id.pb_home_xp);
        ivHomeAvatar = findViewById(R.id.iv_home_avatar);
        tvStreakAvatarVal = findViewById(R.id.tv_streak_avatar_val);
        lottieStreakAvatar = findViewById(R.id.lottie_streak_avatar);
        flStreakAvatar = findViewById(R.id.fl_streak_avatar);
        btnHeaderBack = findViewById(R.id.btn_header_back);
        btnHeaderMore = findViewById(R.id.btn_header_more);
        llShopCurrencies = findViewById(R.id.ll_shop_currencies);
        llInternalCurrencies = findViewById(R.id.ll_internal_currencies);
        tvShopCoins = findViewById(R.id.tv_shop_coins);
        tvShopDiamonds = findViewById(R.id.tv_shop_diamonds);

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
        
        // Overflow button: PopupWindow glass + badges + không làm dừng marquee
        if (btnHeaderMore != null) {
            btnHeaderMore.setOnClickListener(v -> {
                if (tvUsername != null) tvUsername.setSelected(false);

                View popupView = getLayoutInflater().inflate(R.layout.popup_header_overflow, null);
                PopupWindow popup = new PopupWindow(popupView,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        true);
                popup.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
                popup.setElevation(12f);
                popup.setOutsideTouchable(true);

                applyPopupBadges(popupView);

                popupView.findViewById(R.id.popup_item_friends).setOnClickListener(btn -> {
                    popup.dismiss();
                    startActivity(new android.content.Intent(this, com.vn.jet.mosco.FriendActivity.class));
                });
                popupView.findViewById(R.id.popup_item_mailbox).setOnClickListener(btn -> {
                    popup.dismiss();
                    com.vn.jet.mosco.utils.NavigationUtils.openMailbox(this);
                });
                popupView.findViewById(R.id.popup_item_shop).setOnClickListener(btn -> {
                    popup.dismiss();
                    openShop();
                });

                popup.setOnDismissListener(() -> {
                    if (tvUsername != null) tvUsername.setSelected(true);
                });

                // Tính toán offset động: Đẩy popup dịch sang trái 160dp để mép phải của popup căn thẳng hàng với mép phải của nút overflow.
                int xoff = -(int) (v.getResources().getDisplayMetrics().density * 160);
                int yoff = (int) (v.getResources().getDisplayMetrics().density * 8);
                popup.showAsDropDown(v, xoff, yoff);
            });
        }
        
        if (ivHomeAvatar != null) {
            ivHomeAvatar.setOnClickListener(v -> {
                com.vn.jet.mosco.utils.NavigationUtils.openProfile(this, null);
            });
        }
    }

    private void showStreakDetail(int currentStreak, int bestStreak, int restores) {
        com.vn.jet.mosco.utils.MoscoDialogHelper.showStreakDetailBottomSheet(
            this,
            currentStreak,
            bestStreak,
            restores,
            gameApiService,
            this::loadUserData
        );
    }

    private UserStats cachedStats;
    public UserStats getCachedStats() {
        return cachedStats;
    }
    public void loadUserData() {
        Long userId = sessionManager.getUserId();
        if (userId == null) return;
        
        // TẠI SAO: Đồng bộ và kiểm tra huy hiệu mới mở khóa (ví dụ: streak, like) ngay khi tải dữ liệu người dùng
        com.vn.jet.mosco.utils.BadgeSyncHelper.syncAndCheckBadges(this, userId);
        
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

                    fetchExtraNotificationCounts();
                    fetchFriendRequestsCount();
                }
            }
            @Override
            public void onFailure(Call<UserStats> call, Throwable t) {
                Log.e("MainActivity", "Failed to fetch stats", t);
            }
        });
    }

    private void applyPopupBadges(View popupView) {
        TextView badgeFriends = popupView.findViewById(R.id.popup_badge_friends);
        TextView badgeMailbox = popupView.findViewById(R.id.popup_badge_mailbox);
        TextView badgeShop = popupView.findViewById(R.id.popup_badge_shop);
        if (badgeFriends != null) {
            if (badgeFriendCount > 0) {
                badgeFriends.setText(badgeFriendCount > 99 ? "99+" : String.valueOf(badgeFriendCount));
                badgeFriends.setVisibility(View.VISIBLE);
            } else {
                badgeFriends.setVisibility(View.GONE);
            }
        }
        if (badgeMailbox != null) {
            if (badgeMailCount > 0) {
                badgeMailbox.setText(badgeMailCount > 99 ? "99+" : String.valueOf(badgeMailCount));
                badgeMailbox.setVisibility(View.VISIBLE);
            } else {
                badgeMailbox.setVisibility(View.GONE);
            }
        }
        if (badgeShop != null) {
            if (badgeShopCount > 0) {
                badgeShop.setText("N");
                badgeShop.setVisibility(View.VISIBLE);
            } else {
                badgeShop.setVisibility(View.GONE);
            }
        }
    }

    private void fetchExtraNotificationCounts() {
        gameApiService.getUserMails(sessionManager.getUserId()).enqueue(new Callback<List<com.vn.jet.mosco.model.UserMail>>() {
            @Override
            public void onResponse(Call<List<com.vn.jet.mosco.model.UserMail>> call, Response<List<com.vn.jet.mosco.model.UserMail>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    int unread = 0;
                    for (com.vn.jet.mosco.model.UserMail mail : response.body()) {
                        if (!mail.isReceived()) unread++;
                    }
                    badgeMailCount = unread;
                }
            }
            @Override public void onFailure(Call<List<com.vn.jet.mosco.model.UserMail>> call, Throwable t) {}
        });
    }

    private void fetchFriendRequestsCount() {
        if (gameApiService == null) return;
        gameApiService.getFriendRequests().enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        org.json.JSONObject json = new org.json.JSONObject(response.body().string());
                        org.json.JSONArray data = json.optJSONArray("data");
                        badgeFriendCount = (data != null) ? data.length() : 0;
                    }
                } catch (Exception ignored) {}
            }
            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {}
        });
    }

    public void openShop() {
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

    private void setupCustomBottomNavigation(BottomNavigationView bottomNav) {
        View btnHome = findViewById(R.id.btn_custom_nav_home);
        View btnUpgrade = findViewById(R.id.btn_custom_nav_upgrade);
        View btnSpin = findViewById(R.id.btn_custom_nav_spin);
        View btnCollect = findViewById(R.id.btn_custom_nav_collect);
        View btnProfile = findViewById(R.id.btn_custom_nav_profile);

        if (btnHome == null) return;

        // Binds custom layout click events directly to BottomNavigationView items
        btnHome.setOnClickListener(v -> bottomNav.setSelectedItemId(R.id.nav_home));
        btnUpgrade.setOnClickListener(v -> bottomNav.setSelectedItemId(R.id.nav_upgrade));
        btnSpin.setOnClickListener(v -> bottomNav.setSelectedItemId(R.id.nav_spin));
        btnCollect.setOnClickListener(v -> bottomNav.setSelectedItemId(R.id.nav_collect));
        btnProfile.setOnClickListener(v -> bottomNav.setSelectedItemId(R.id.nav_profile));

        // Setup the shared onItemSelectedListener to synchronize state & handle transitions
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            
            // Debounce check
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastNavClickTime < 500 && itemId != bottomNav.getSelectedItemId()) {
                return false;
            }

            // Click same tab -> bounce to provide tactile feedback
            if (itemId == bottomNav.getSelectedItemId()) {
                View activeBtn = findViewById(itemId == R.id.nav_home ? R.id.btn_custom_nav_home :
                                             itemId == R.id.nav_upgrade ? R.id.btn_custom_nav_upgrade :
                                             itemId == R.id.nav_spin ? R.id.btn_custom_nav_spin :
                                             itemId == R.id.nav_collect ? R.id.btn_custom_nav_collect :
                                             R.id.btn_custom_nav_profile);
                animateTabClick(activeBtn);
                return false;
            }

            Fragment selectedFragment = null;
            if (itemId == R.id.nav_home) {
                selectedFragment = new HomeFragment();
            } else if (itemId == R.id.nav_upgrade) {
                selectedFragment = new com.vn.jet.mosco.fragment.UpgradeFragment();
            } else if (itemId == R.id.nav_collect) {
                selectedFragment = new CollectionFragment();
            } else if (itemId == R.id.nav_spin) {
                selectedFragment = new SpinFragment();
            } else if (itemId == R.id.nav_profile) {
                selectedFragment = new ProfileFragment();
            }

            if (selectedFragment != null) {
                lastNavClickTime = currentTime;

                // Clear backstack khi chuyển tab để tránh backstack stale
                getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                com.vn.jet.mosco.utils.NavigationUtils.clearProfileStack();

                getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                        .replace(R.id.frame_layout, selectedFragment)
                        .commit();

                // Update visual custom tabs state
                updateCustomTabsVisualState(itemId);

                // Đồng bộ Header & Bottom Nav
                syncUiForTab(itemId);
                return true;
            }
            return false;
        });

        // Initialize visual selection (Home by default)
        updateCustomTabsVisualState(R.id.nav_home);
    }

    private void updateCustomTabsVisualState(int selectedItemId) {
        int[] itemIds = {R.id.nav_home, R.id.nav_upgrade, R.id.nav_spin, R.id.nav_collect, R.id.nav_profile};
        int[] customBtnIds = {R.id.btn_custom_nav_home, R.id.btn_custom_nav_upgrade, R.id.btn_custom_nav_spin, R.id.btn_custom_nav_collect, R.id.btn_custom_nav_profile};
        int[] ivIds = {R.id.iv_custom_nav_home, R.id.iv_custom_nav_upgrade, R.id.iv_custom_nav_spin, R.id.iv_custom_nav_collect, R.id.iv_custom_nav_profile};
        int[] tvIds = {R.id.tv_custom_nav_home, R.id.tv_custom_nav_upgrade, 0, R.id.tv_custom_nav_collect, R.id.tv_custom_nav_profile};

        for (int i = 0; i < itemIds.length; i++) {
            boolean isActive = (itemIds[i] == selectedItemId);
            ImageView iv = findViewById(ivIds[i]);
            TextView tv = (tvIds[i] != 0) ? findViewById(tvIds[i]) : null;
            View btn = findViewById(customBtnIds[i]);

            if (isActive && btn != null) {
                animateTabClick(btn);
            }

            if (iv != null) {
                if (itemIds[i] == R.id.nav_spin) {
                    // Spin tab pill button has custom colors, keep icon white
                    iv.setColorFilter(ContextCompat.getColor(this, R.color.white));
                } else {
                    iv.setColorFilter(ContextCompat.getColor(this, isActive ? R.color.brand_primary : R.color.semantic_text_secondary));
                }
            }

            if (tv != null) {
                tv.setTextColor(ContextCompat.getColor(this, isActive ? R.color.brand_primary : R.color.semantic_text_secondary));
                tv.setTypeface(null, isActive ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            }
        }
    }

    private void animateTabClick(View view) {
        if (view == null) return;
        view.setScaleX(0.9f);
        view.setScaleY(0.9f);
        view.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(250)
            .setInterpolator(new android.view.animation.OvershootInterpolator(1.8f))
            .start();
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

    /**
     * Bật hoặc tắt hiệu ứng skeleton shimmer cho thanh Header dùng chung.
     * TẠI SAO: Đảm bảo khi màn hình đang tải dữ liệu thì Header cũng hiển thị dạng khối xám
     * chạy trượt sáng, không để lộ dữ liệu nháp cũ (Commander, 0 tiền) gây mất thẩm mỹ.
     */
    public void showHeaderShimmer(boolean show) {
        View headerRow = findViewById(R.id.cl_header_row);
        if (shimmerHeader != null && headerRow != null) {
            if (show) {
                com.vn.jet.mosco.utils.SkeletonHelper.skeletonize(headerRow);
                shimmerHeader.showShimmer(true);
                shimmerHeader.startShimmer();
            } else {
                com.vn.jet.mosco.utils.SkeletonHelper.restore(headerRow);
                shimmerHeader.stopShimmer();
                shimmerHeader.hideShimmer();
            }
        }
    }

    public void updateAiFabAvatar() {
        com.vn.jet.mosco.widget.DraggableFab fabAi = findViewById(R.id.fab_ai_assistant);
        if (fabAi != null) {
            String biasId = sessionManager.getAiBiasId();
            if (biasId != null && !biasId.isEmpty()) {
                com.vn.jet.mosco.utils.AppExecutors.getInstance().diskIO().execute(() -> {
                    String avatarUrl = com.vn.jet.mosco.database.AppDatabase.getInstance(this)
                            .masterObjetDao().getLatestPremierImageByMember(biasId);
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        if (avatarUrl != null && !avatarUrl.isEmpty()) {
                            com.bumptech.glide.Glide.with(this)
                                    .load(avatarUrl)
                                    .apply(com.bumptech.glide.request.RequestOptions.bitmapTransform(new com.vn.jet.mosco.utils.SmartFaceCropTransformation(avatarUrl)))
                                    .placeholder(R.drawable.ic_star_twinkle)
                                    .into(fabAi);
                        } else {
                            fabAi.setImageResource(R.drawable.ic_star_twinkle);
                        }
                    });
                });
            } else {
                fabAi.setImageResource(R.drawable.ic_star_twinkle);
            }
        }
    }
}