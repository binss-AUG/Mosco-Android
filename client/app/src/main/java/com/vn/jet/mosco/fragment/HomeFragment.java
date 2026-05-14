package com.vn.jet.mosco.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.vn.jet.mosco.MainActivity;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.model.UserStats;
import com.vn.jet.mosco.network.ApiClient;
import com.vn.jet.mosco.network.GameApiService;
import com.vn.jet.mosco.utils.ClickDebounce;
import com.vn.jet.mosco.utils.DatabaseLoader;
import com.vn.jet.mosco.utils.NavigationUtils;
import com.vn.jet.mosco.utils.SessionManager;
import com.vn.jet.mosco.utils.SmartFaceCropTransformation;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import com.vn.jet.mosco.adapter.WorldChatAdapter;
import com.vn.jet.mosco.model.WorldChatMessage;


/**
 * HomeFragment — Galactic Command Center V3.4.
 * Updated: SwipeRefreshLayout integration for data synchronization.
 */
public class HomeFragment extends Fragment implements DatabaseLoader.OnInventoryChangeListener {

    private static final String TAG = "HomeFragment";

    // ── Constants ──
    private static final int BANNER_AUTO_SCROLL_DELAY = 4000;
    private static final int RANK_AUTO_SCROLL_DELAY = 6000;
    private static final int MIN_SKELETON_DURATION = 1500; // Thời gian tối thiểu hiện Skeleton (Luxury feel)

    // ── UI References ──
    private TextView tvUsername, tvCoins, tvDiamonds, tvUserId, tvNotification, tvLevel, tvXpVal;
    private ProgressBar pbHomeXp;
    private ImageView ivHomeAvatar, ivChatAvatar, btnHomeSend;
    private EditText etHomeChat;
    private ViewPager2 vpBanners, vpMiniRanking;
    private LinearLayout llBannerDots;
    private View flAvatarGroup;
    private com.scwang.smart.refresh.layout.SmartRefreshLayout swipeRefreshLayout;
    private com.facebook.shimmer.ShimmerFrameLayout shimmerHome;
    private View clRealContent;
    
    private boolean isUserStatsLoaded = false;
    private boolean isRankLoaded = false;
    private boolean isDataLoaded = false;
    
    // Dashboard Modules
    private View cvModuleStreak, btnFullRank;
    private TextView tvModuleStreakVal;
    private com.airbnb.lottie.LottieAnimationView lottieModuleStreak, lottieModuleStreakGlow;
    private View layoutWorldChatExpanded;
    private RecyclerView rvWorldChatExpanded;
    private TextView tvChatTicker;
    private final Handler tickerHandler = new Handler(Looper.getMainLooper());
    private Runnable tickerRunnable;
    private int currentTickerIndex = 0;

    // ── Quick Tool References ──
    private View btnQuickRank, btnQuickDaily, btnQuickEvent, btnQuickUpgrade, btnQuickShop, btnQuickFriends, btnQuickFormation, btnQuickGift;
    private View vBubbleDaily, vBubbleEvent, vBubbleUpgrade, vBubbleRank, vBubbleShop, vBubbleFriends, vBubbleFormation, vBubbleGift;
    private android.widget.ImageView ivQuickDaily, ivQuickEvent, ivQuickUpgrade, ivQuickRank, ivQuickShop, ivQuickFriends, ivQuickFormation, ivQuickGift;
    private android.widget.HorizontalScrollView hsvQuickTools;
    private LinearLayout llQuickToolsContainer;

    // ── State ──
    private int bannerCount = 0;
    private int bestStreakValue = 0;
    private int restoresThisMonth = 0;
    private int lastProgress = 0;
    private final java.util.List<android.animation.Animator> activeAnimators = new java.util.ArrayList<>();
    
    private RecyclerView rvWorldChat;
    private WorldChatAdapter worldChatAdapter;

    
    private final Handler bannerHandler = new Handler(Looper.getMainLooper());
    private Runnable bannerRunnable;
    
    private final Handler rankHandler = new Handler(Looper.getMainLooper());
    private Runnable rankRunnable;
    private MiniRankPagerAdapter miniRankAdapter;
    private final Map<String, List<JSONObject>> rankDataCache = new HashMap<>();
    private long skeletonStartTime = 0;
    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable rankingTimeoutRunnable;
    private boolean isRankLoading = false;
    private static boolean sIsFirstLoad = true;
    private static UserStats sCachedStats = null; // AAA: Cache để hiển thị tức thì khi chuyển tab

    // ── Services ──
    private SessionManager sessionManager;
    private GameApiService gameApiService;
    private android.animation.ValueAnimator rgbAnimator;
    
    // --- WebSocket World Chat ---
    private com.vn.jet.mosco.network.WebSocketManager wsManager;
    private io.reactivex.disposables.Disposable chatDisposable;

    public HomeFragment() {
        // Required empty public constructor
    }

    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // AAA Strategy: Nếu đã load rồi thì bỏ qua thời gian chờ skeleton
        skeletonStartTime = sIsFirstLoad ? System.currentTimeMillis() : (System.currentTimeMillis() - MIN_SKELETON_DURATION - 100);
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        initViews(view);
        initServices();
        
        setupLongClickCopy(tvUsername, getString(R.string.home_label_username));
        setupLongClickCopy(tvUserId, getString(R.string.home_label_user_id));
        setupBannerCarousel();
        setupQuickToolActions();
        setupQuickToolDimensions();
        setupNotificationTicker();
        setupChatBar();
        setupDashboard();
        setupRefreshLogic();
        loadUserData();
        loadMiniRankData();
        startRankingTimeout();
        startRankAutoScroll();
        startQuickToolAnimations(view);
        
        // Bắt đầu Shimmer ngay khi mở fragment (chỉ hiện nếu chưa có data)
        if (shimmerHome != null && sIsFirstLoad) {
            shimmerHome.startShimmer();
        } else if (shimmerHome != null) {
            shimmerHome.setVisibility(View.GONE);
            if (clRealContent != null) clRealContent.setVisibility(View.VISIBLE);
        }
        
        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopBannerAutoScroll();
        stopRankAutoScroll();
        stopChatTicker();
        if (rgbAnimator != null) rgbAnimator.cancel();
        
        // --- 🌐 WORLD CHAT: Cleanup WebSocket ---
        if (chatDisposable != null && !chatDisposable.isDisposed()) {
            chatDisposable.dispose();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        startBannerAutoScroll();
        startRankAutoScroll();
        startAvatarPulse();
        loadUserData();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopBannerAutoScroll();
        stopRankAutoScroll();
    }

    private void startQuickToolAnimations(View root) {
        if (root == null) return;
        
        View[] tools = {
            btnQuickDaily, btnQuickUpgrade, btnQuickRank,
            btnQuickShop, btnQuickFriends, btnQuickGift
        };
        View[] bubbles = {
            vBubbleDaily, vBubbleUpgrade, vBubbleRank,
            vBubbleShop, vBubbleFriends, vBubbleGift
        };
        View[] icons = {
            ivQuickDaily, ivQuickUpgrade, ivQuickRank,
            ivQuickShop, ivQuickFriends, ivQuickGift
        };

        // Lấy density an toàn từ root view
        float density = root.getContext().getResources().getDisplayMetrics().density;
        float iconBobDistance = 4f * density; // Nhấp nhô nhẹ 4dp cho icon
        long baseDuration = 3000;

        for (int i = 0; i < tools.length; i++) {
            final View bubbleIridescent = bubbles[i];
            final View icon = icons[i];

            // 1. Hiệu ứng xoay vệt sáng nội bộ (Base đứng yên)
            if (bubbleIridescent != null) {
                bubbleIridescent.setLayerType(View.LAYER_TYPE_HARDWARE, null); // Ép dùng GPU cho mượt
                long rotateDuration = 5000 + (i * 400);
                android.animation.ObjectAnimator rotating = android.animation.ObjectAnimator.ofFloat(
                    bubbleIridescent, "rotation", 0f, 360f
                );
                rotating.setDuration(rotateDuration);
                rotating.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
                rotating.setInterpolator(new android.view.animation.LinearInterpolator());
                rotating.start();
                activeAnimators.add(rotating);

                // 2. Gộp hiệu ứng "Nhịp thở" vào 1 Animator duy nhất để tiết kiệm tài nguyên
                android.animation.PropertyValuesHolder pvhX = android.animation.PropertyValuesHolder.ofFloat("scaleX", 0.96f, 1.04f, 0.96f);
                android.animation.PropertyValuesHolder pvhY = android.animation.PropertyValuesHolder.ofFloat("scaleY", 0.96f, 1.04f, 0.96f);
                android.animation.ObjectAnimator pulse = android.animation.ObjectAnimator.ofPropertyValuesHolder(bubbleIridescent, pvhX, pvhY);
                pulse.setDuration(rotateDuration);
                pulse.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
                pulse.start();
                activeAnimators.add(pulse);
            }

            // 3. HIỆU ỨNG NHẤP NHÔ NHẸ CHO ICON BÊN TRONG
            if (icon != null) {
                icon.setLayerType(View.LAYER_TYPE_HARDWARE, null); // Tăng tốc phần cứng cho icon
                long bobDuration = baseDuration + (i * 500);
                android.animation.ObjectAnimator bobbing = android.animation.ObjectAnimator.ofFloat(
                    icon, "translationY", 0f, -iconBobDistance, 0f
                );
                bobbing.setDuration(bobDuration);
                bobbing.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
                bobbing.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
                bobbing.start();
                activeAnimators.add(bobbing);
            }
        }
    }

    @Override
    public void onDestroyView() {
        for (android.animation.Animator animator : activeAnimators) {
            if (animator != null) animator.cancel();
        }
        activeAnimators.clear();
        DatabaseLoader.unregisterInventoryChangeListener(this);
        stopBannerAutoScroll();
        stopRankAutoScroll();
        stopRankingTimeout();
        super.onDestroyView();
    }

    @Override
    public void onInventoryChanged() { }

    private void initViews(View v) {
        tvUsername = v.findViewById(R.id.tv_home_username);
        tvCoins = v.findViewById(R.id.tv_home_coins);
        tvDiamonds = v.findViewById(R.id.tv_home_diamonds);
        tvNotification = v.findViewById(R.id.tv_home_notification);
        tvLevel = v.findViewById(R.id.tv_home_level);
        tvXpVal = v.findViewById(R.id.tv_home_xp_val);
        pbHomeXp = v.findViewById(R.id.pb_home_xp);
        
        ivHomeAvatar = v.findViewById(R.id.iv_home_avatar);
        ivChatAvatar = v.findViewById(R.id.iv_chat_avatar);
        etHomeChat = v.findViewById(R.id.et_home_chat);
        btnHomeSend = v.findViewById(R.id.btn_home_send);
        tvChatTicker = v.findViewById(R.id.tv_chat_ticker);
        layoutWorldChatExpanded = v.findViewById(R.id.layout_world_chat_expanded);
        rvWorldChatExpanded = v.findViewById(R.id.rv_world_chat_expanded);

        llBannerDots = v.findViewById(R.id.ll_banner_dots);
        vpBanners = v.findViewById(R.id.vp_banners);
        swipeRefreshLayout = v.findViewById(R.id.swipe_refresh_home);
        pbHomeXp = v.findViewById(R.id.pb_home_xp);
        
        btnQuickRank = v.findViewById(R.id.btn_quick_rank);
        btnQuickDaily = v.findViewById(R.id.btn_quick_daily);
        btnQuickEvent = v.findViewById(R.id.btn_quick_event);
        btnQuickUpgrade = v.findViewById(R.id.btn_quick_upgrade);
        btnQuickShop = v.findViewById(R.id.btn_quick_shop);
        btnQuickFriends = v.findViewById(R.id.btn_quick_friends);
        btnQuickFormation = v.findViewById(R.id.btn_quick_formation);
        btnQuickGift = v.findViewById(R.id.btn_quick_gift);
        
        vBubbleDaily = v.findViewById(R.id.v_bubble_daily);
        vBubbleEvent = v.findViewById(R.id.v_bubble_event);
        vBubbleUpgrade = v.findViewById(R.id.v_bubble_upgrade);
        vBubbleRank = v.findViewById(R.id.v_bubble_rank);
        vBubbleShop = v.findViewById(R.id.v_bubble_shop);
        vBubbleFriends = v.findViewById(R.id.v_bubble_friends);
        vBubbleFormation = v.findViewById(R.id.v_bubble_formation);
        vBubbleGift = v.findViewById(R.id.v_bubble_gift);

        ivQuickDaily = v.findViewById(R.id.iv_quick_daily);
        ivQuickEvent = v.findViewById(R.id.iv_quick_event);
        ivQuickUpgrade = v.findViewById(R.id.iv_quick_upgrade);
        ivQuickRank = v.findViewById(R.id.iv_quick_rank);
        ivQuickShop = v.findViewById(R.id.iv_quick_shop);
        ivQuickFriends = v.findViewById(R.id.iv_quick_friends);
        ivQuickFormation = v.findViewById(R.id.iv_quick_formation);
        ivQuickGift = v.findViewById(R.id.iv_quick_gift);

        hsvQuickTools = v.findViewById(R.id.hsv_quick_tools);
        llQuickToolsContainer = v.findViewById(R.id.ll_quick_tools_container);
        
        // Fix conflict: Không cho SwipeRefreshLayout bắt sự kiện khi đang vuốt ngang Quick Tools
        if (hsvQuickTools != null) {
            hsvQuickTools.setOnTouchListener((view, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN || event.getAction() == android.view.MotionEvent.ACTION_MOVE) {
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setEnableRefresh(false);
                } else {
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setEnableRefresh(true);
                }
                return false;
            });
        }
        
        flAvatarGroup = v.findViewById(R.id.fl_avatar_group);
        tvUserId = v.findViewById(R.id.tv_home_user_id);
        
        // Dashboard
        cvModuleStreak = v.findViewById(R.id.cv_module_streak);
        tvModuleStreakVal = v.findViewById(R.id.tv_module_streak_val);
        lottieModuleStreak = v.findViewById(R.id.lottie_module_streak);
        lottieModuleStreakGlow = v.findViewById(R.id.lottie_module_streak_glow);
        vpMiniRanking = v.findViewById(R.id.vp_mini_ranking);
        btnFullRank = v.findViewById(R.id.btn_home_full_rank);
        
        shimmerHome = v.findViewById(R.id.shimmer_home);
        clRealContent = v.findViewById(R.id.cl_real_content);
    }

    private void initServices() {
        sessionManager = new SessionManager(requireContext());
        gameApiService = ApiClient.getClient(requireContext()).create(GameApiService.class);
        miniRankAdapter = new MiniRankPagerAdapter();
        worldChatAdapter = new WorldChatAdapter();
        DatabaseLoader.registerInventoryChangeListener(this);

    }

    private void setupRefreshLogic() {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(refreshLayout -> {
                loadUserData();
                loadMiniRankData();
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    refreshLayout.finishRefresh();
                }, 1500);
            });
        }
    }

    private void setupDashboard() {
        if (cvModuleStreak != null) {
            cvModuleStreak.setOnClickListener(v -> {
                int streak = 0;
                try { 
                    String val = tvModuleStreakVal.getText().toString().replace(" DAYS", "").trim();
                    streak = Integer.parseInt(val); 
                } catch (Exception ignored) {}
                showStreakDetail(streak, bestStreakValue, restoresThisMonth);
            });
        }
        
        if (btnFullRank != null) {
            btnFullRank.setOnClickListener(v -> NavigationUtils.openRank(getActivity()));
        }

        if (vpMiniRanking != null) {
            vpMiniRanking.setAdapter(miniRankAdapter);
            
            // Fix conflict: Khóa Pull Refresh khi đang tương tác với Mini Ranking
            vpMiniRanking.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageScrollStateChanged(int state) {
                    if (swipeRefreshLayout != null) {
                        swipeRefreshLayout.setEnableRefresh(state == androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_IDLE);
                    }
                }
            });
        }
    }

    private void startRankingTimeout() {
        stopRankingTimeout();
        rankingTimeoutRunnable = () -> {
            if (!isRankLoaded && isAdded()) {
                isRankLoading = false;
                showRankingError();
            }
        };
        timeoutHandler.postDelayed(rankingTimeoutRunnable, 10000); // Tăng lên 10s cho ổn định
    }

    private void stopRankingTimeout() {
        if (rankingTimeoutRunnable != null) {
            timeoutHandler.removeCallbacks(rankingTimeoutRunnable);
        }
    }

    private void showRankingError() {
        if (vpMiniRanking != null) {
            if (miniRankAdapter != null) {
                miniRankAdapter.setLoadingState(false);
                miniRankAdapter.setErrorState(true);
            }
        }
    }

    private void loadMiniRankData() {
        isRankLoaded = false;
        isRankLoading = true;
        if (miniRankAdapter != null) {
            miniRankAdapter.setLoadingState(true);
        }
        String[] types = {"level", "collection", "wealth", "streak"};
        for (String type : types) {
            fetchRankTop5(type);
        }
    }

    private void fetchRankTop5(String type) {
        if (gameApiService == null) return;
        Call<ResponseBody> call;
        switch (type) {
            case "streak": call = gameApiService.getRankByStreak(); break;
            case "wealth": call = gameApiService.getRankByWealth(); break;
            case "collection": call = gameApiService.getRankByCollection(); break;
            default: call = gameApiService.getRankByLevel(); break;
        }

        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject root = new JSONObject(response.body().string());
                        JSONArray data = root.optJSONArray("data");
                        if (data != null) {
                            List<JSONObject> list = new ArrayList<>();
                            for (int i = 0; i < Math.min(data.length(), 5); i++) {
                                list.add(data.getJSONObject(i));
                            }
                            rankDataCache.put(type, list);
                            isRankLoading = false;
                            if (miniRankAdapter != null) {
                                miniRankAdapter.setLoadingState(false);
                                miniRankAdapter.notifyDataSetChanged();
                            }
                            
                            isRankLoaded = true;
                            stopRankingTimeout();
                            checkAndShowContent();
                        }
                    } else {
                        // Response không thành công -> Hiện lỗi ngay
                        showRankingError();
                        stopRankingTimeout();
                    }
                } catch (Exception e) { 
                    Log.e(TAG, "Mini rank error: " + type, e); 
                    showRankingError();
                    stopRankingTimeout();
                }
            }
            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) { 
                if (isAdded()) {
                    showRankingError();
                    stopRankingTimeout();
                }
            }
        });
    }

    private void startRankAutoScroll() {
        if (vpMiniRanking == null) return;
        stopRankAutoScroll();
        rankRunnable = new Runnable() {
            @Override
            public void run() {
                if (vpMiniRanking != null && isAdded()) {
                    int total = miniRankAdapter.getItemCount();
                    if (total > 0) {
                        int next = (vpMiniRanking.getCurrentItem() + 1) % total;
                        vpMiniRanking.setCurrentItem(next, true);
                    }
                    rankHandler.postDelayed(this, RANK_AUTO_SCROLL_DELAY);
                }
            }
        };
        rankHandler.postDelayed(rankRunnable, RANK_AUTO_SCROLL_DELAY);
    }

    private void stopRankAutoScroll() {
        if (rankRunnable != null) rankHandler.removeCallbacks(rankRunnable);
    }

    private void setupNotificationTicker() {
        if (tvNotification != null) tvNotification.setSelected(true);
    }

    private void setupChatBar() {
        if (worldChatAdapter == null) {
            worldChatAdapter = new WorldChatAdapter();
        }
        // Luôn cập nhật ID mới nhất từ session để tránh bị stale
        if (sessionManager.getUserId() != null) {
            worldChatAdapter.setCurrentUserId(String.valueOf(sessionManager.getUserId()));
        }

        if (rvWorldChatExpanded != null) {
            rvWorldChatExpanded.setLayoutManager(new LinearLayoutManager(requireContext()));
            rvWorldChatExpanded.setAdapter(worldChatAdapter);
            
            // Professional system messages
            worldChatAdapter.addMessage(new WorldChatMessage("0", getString(R.string.chat_msg_system), "0", getString(R.string.chat_msg_welcome)));
            worldChatAdapter.addMessage(new WorldChatMessage("1", "Admin_Zero", "1", "Welcome to the central communication hub."));
        }

        // Ticker Logic
        startChatTicker();

        // Interaction Logic: Click ticker to expand
        if (tvChatTicker != null) {
            tvChatTicker.setOnClickListener(v -> expandChat());
        }

        // Close expanded chat
        View headerView = layoutWorldChatExpanded != null ? layoutWorldChatExpanded.findViewById(R.id.layout_chat_expanded_header) : null;
        if (headerView != null) {
            TextView tvTitle = headerView.findViewById(R.id.tv_header_title);
            if (tvTitle != null) tvTitle.setText(getString(R.string.chat_header_world));
            
            View btnBack = headerView.findViewById(R.id.btn_back_common);
            if (btnBack != null) btnBack.setVisibility(View.GONE);
            
            View btnCloseX = layoutWorldChatExpanded.findViewById(R.id.btn_close_chat_x);
            if (btnCloseX != null) {
                btnCloseX.setOnClickListener(v -> collapseChat());
            }
        }

        // --- 🌐 WORLD CHAT: WebSocket Integration ---
        wsManager = com.vn.jet.mosco.network.WebSocketManager.getInstance();
        wsManager.connect();
        
        chatDisposable = wsManager.subscribeToWorldChat(message -> {
            if (isAdded() && worldChatAdapter != null) {
                worldChatAdapter.addMessage(message);
                if (rvWorldChatExpanded != null) {
                    rvWorldChatExpanded.smoothScrollToPosition(worldChatAdapter.getItemCount() - 1);
                }
            }
        });

        if (btnHomeSend != null) {
            btnHomeSend.setOnClickListener(v -> {
                String msg = etHomeChat.getText().toString().trim();
                if (!msg.isEmpty()) {
                    String myName = sessionManager.getIngameName();
                    String myAvatar = sessionManager.getAvatarId();
                    String currentUserId = sessionManager.getUserId() != null ? String.valueOf(sessionManager.getUserId()) : "guest";
                    
                    // Gửi qua WebSocket (Server sẽ broadcast lại cho mọi người)
                    com.vn.jet.mosco.model.WorldChatMessage chatMsg = 
                        new com.vn.jet.mosco.model.WorldChatMessage(currentUserId, myName, myAvatar, msg);
                    wsManager.sendWorldMessage(chatMsg);
                    
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                    etHomeChat.setText("");
                }
            });
        }
    }

    private void startChatTicker() {
        stopChatTicker();
        tickerRunnable = new Runnable() {
            @Override
            public void run() {
                if (worldChatAdapter != null && worldChatAdapter.getItemCount() > 0 && tvChatTicker != null && etHomeChat.getVisibility() == View.GONE) {
                    WorldChatMessage msg = worldChatAdapter.getMessageAt(currentTickerIndex % worldChatAdapter.getItemCount());
                    tvChatTicker.setText(msg.getSenderName() + ": " + msg.getContent());
                    currentTickerIndex++;
                }
                tickerHandler.postDelayed(this, 3000);
            }
        };
        tickerHandler.postAtFrontOfQueue(tickerRunnable);
    }

    private void stopChatTicker() {
        if (tickerRunnable != null) tickerHandler.removeCallbacks(tickerRunnable);
    }

    private void expandChat() {
        if (layoutWorldChatExpanded != null) {
            layoutWorldChatExpanded.setVisibility(View.VISIBLE);
            etHomeChat.setVisibility(View.VISIBLE);
            tvChatTicker.setVisibility(View.GONE);
            etHomeChat.requestFocus();
            
            // Scroll to latest
            if (rvWorldChatExpanded != null && worldChatAdapter.getItemCount() > 0) {
                rvWorldChatExpanded.scrollToPosition(worldChatAdapter.getItemCount() - 1);
            }
        }
    }

    private void collapseChat() {
        if (layoutWorldChatExpanded != null) {
            layoutWorldChatExpanded.setVisibility(View.GONE);
            etHomeChat.setVisibility(View.GONE);
            tvChatTicker.setVisibility(View.VISIBLE);
            etHomeChat.clearFocus();
        }
    }

    private void setupQuickToolActions() {
        if (btnQuickDaily != null) btnQuickDaily.setOnClickListener(v -> startActivity(new android.content.Intent(requireContext(), com.vn.jet.mosco.DailyCheckinActivity.class)));
        // if (btnQuickEvent != null) btnQuickEvent.setOnClickListener(v -> startActivity(new android.content.Intent(requireContext(), com.vn.jet.mosco.MissionActivity.class)));
        if (btnQuickUpgrade != null) {
            btnQuickUpgrade.setOnClickListener(v -> {
                if (getActivity() != null) {
                    getActivity().getSupportFragmentManager().beginTransaction()
                            .add(R.id.frame_layout, new UpgradeFragment())
                            .addToBackStack(null)
                            .commit();
                }
            });
        }
        if (btnQuickRank != null) btnQuickRank.setOnClickListener(v -> NavigationUtils.openRank(getActivity()));
        if (btnQuickFriends != null) btnQuickFriends.setOnClickListener(v -> startActivity(new android.content.Intent(requireContext(), com.vn.jet.mosco.FriendActivity.class)));
        // if (btnQuickFormation != null) btnQuickFormation.setOnClickListener(v -> startActivity(new android.content.Intent(requireContext(), com.vn.jet.mosco.FormationActivity.class)));
        if (btnQuickGift != null) btnQuickGift.setOnClickListener(v -> startActivity(new android.content.Intent(requireContext(), com.vn.jet.mosco.GiftActivity.class)));
        
        if (flAvatarGroup != null) {
            flAvatarGroup.setOnClickListener(new ClickDebounce() {
                @Override
                public void onDebouncedClick(View v) { 
                    NavigationUtils.openProfile(getActivity(), null); 
                }
            });
        }

        if (btnQuickShop != null) {
            btnQuickShop.setOnClickListener(v -> {
                if (getActivity() != null) {
                    getActivity().getSupportFragmentManager().beginTransaction()
                            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
                            .add(R.id.frame_layout, new ShopFragment())
                            .addToBackStack(null)
                            .commit();
                }
            });
        }
    }

    private void setupQuickToolDimensions() {
        if (hsvQuickTools == null || llQuickToolsContainer == null) return;
        hsvQuickTools.post(() -> {
            if (!isAdded() || requireContext() == null) return;
            int hsvWidth = hsvQuickTools.getMeasuredWidth();
            int horizontalPadding = hsvQuickTools.getPaddingLeft() + hsvQuickTools.getPaddingRight();
            int itemWidth = (hsvWidth - horizontalPadding) / 5;
            for (int i = 0; i < llQuickToolsContainer.getChildCount(); i++) {
                View child = llQuickToolsContainer.getChildAt(i);
                if (child != null) {
                    ViewGroup.LayoutParams params = child.getLayoutParams();
                    params.width = itemWidth;
                    child.setLayoutParams(params);
                }
            }
        });
    }

    private void setupBannerCarousel() {
        if (vpBanners == null || requireContext() == null) return;
        try {
            int[] bannerResIds = {R.drawable.ads1, R.drawable.ads2, R.drawable.ads3};
            bannerCount = bannerResIds.length;
            vpBanners.setAdapter(new BannerPagerAdapter(bannerResIds));
            vpBanners.setOffscreenPageLimit(bannerCount);
            buildDotIndicators(bannerCount);
            int middlePos = (Integer.MAX_VALUE / 2) - ((Integer.MAX_VALUE / 2) % bannerCount);
            vpBanners.setCurrentItem(middlePos, false);
            vpBanners.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) { updateDotIndicators(position % bannerCount); }
            });
        } catch (Exception e) { Log.e(TAG, "Error banner carousel", e); }
    }

    private void buildDotIndicators(int count) {
        if (llBannerDots == null || requireContext() == null) return;
        llBannerDots.removeAllViews();
        int dotSize = getResources().getDimensionPixelSize(R.dimen.home_dot_size);
        int dotSpacing = getResources().getDimensionPixelSize(R.dimen.home_dot_spacing);
        for (int i = 0; i < count; i++) {
            View dot = new View(requireContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dotSize, dotSize);
            params.setMargins(dotSpacing, 0, dotSpacing, 0);
            dot.setLayoutParams(params);
            dot.setBackgroundResource(i == 0 ? R.drawable.bg_dot_active : R.drawable.bg_dot_inactive);
            llBannerDots.addView(dot);
        }
    }

    private void updateDotIndicators(int activePosition) {
        if (llBannerDots == null) return;
        for (int i = 0; i < llBannerDots.getChildCount(); i++) {
            View dot = llBannerDots.getChildAt(i);
            if (dot != null) dot.setBackgroundResource(i == activePosition ? R.drawable.bg_dot_active : R.drawable.bg_dot_inactive);
        }
    }

    private void startBannerAutoScroll() {
        if (vpBanners == null || bannerCount <= 1) return;
        stopBannerAutoScroll();
        bannerRunnable = new Runnable() {
            @Override
            public void run() {
                if (vpBanners != null && isAdded()) {
                    vpBanners.setCurrentItem(vpBanners.getCurrentItem() + 1, true);
                    bannerHandler.postDelayed(this, BANNER_AUTO_SCROLL_DELAY);
                }
            }
        };
        bannerHandler.postDelayed(bannerRunnable, BANNER_AUTO_SCROLL_DELAY);
    }

    private void stopBannerAutoScroll() {
        if (bannerRunnable != null) bannerHandler.removeCallbacks(bannerRunnable);
    }

    private void startAvatarPulse() {
        // Pulse animation temporarily disabled for new head-body design
    }

    private void navigateToTab(int navItemId) {
        if (getActivity() instanceof MainActivity) {
            BottomNavigationView nav = getActivity().findViewById(R.id.bottom_navigation);
            if (nav != null) nav.setSelectedItemId(navItemId);
        }
    }

    private void loadUserData() {
        // Nếu đã có data thì không reset để tránh hiện skeleton lại
        if (sIsFirstLoad) isUserStatsLoaded = false;
        if (sessionManager == null || gameApiService == null) return;
        try {
            String displayName = sessionManager.getIngameName();
            if (displayName == null || displayName.isEmpty()) displayName = sessionManager.getUsername();
            if (tvUsername != null) {
                tvUsername.setText(displayName != null ? displayName : getString(R.string.placeholder_commander));
                tvUsername.setSelected(true);
            }

            Long userId = sessionManager.getUserId();
            String avatarId = sessionManager.getAvatarId();
            if (avatarId == null) avatarId = "1";
            com.vn.jet.mosco.utils.AvatarUtils.loadAvatar(requireContext(), ivHomeAvatar, userId, avatarId);
            com.vn.jet.mosco.utils.AvatarUtils.loadAvatar(requireContext(), ivChatAvatar, userId, avatarId);
            if (userId == null) return;
            if (tvUserId != null) tvUserId.setText(getString(R.string.home_format_user_id, String.valueOf(10000000L + userId)));
            
            // AAA Strategy: Hiển thị data cũ ngay lập tức nếu có
            if (sCachedStats != null) {
                bindCurrency(sCachedStats.getCoins(), sCachedStats.getDiamonds(), sCachedStats.getStreak(), sCachedStats.getBestStreak(), sCachedStats.getStreakRestoresThisMonth(), sCachedStats.getLevel(), sCachedStats.getExp());
                isUserStatsLoaded = true;
                checkAndShowContent();
            }

            gameApiService.getUserStats(userId).enqueue(new Callback<UserStats>() {
                @Override
                public void onResponse(Call<UserStats> call, Response<UserStats> response) {
                    if (!isAdded() || requireContext() == null) return;
                    if (response.isSuccessful() && response.body() != null) {
                        UserStats stats = response.body();
                        sCachedStats = stats; // Cập nhật cache
                        bindCurrency(stats.getCoins(), stats.getDiamonds(), stats.getStreak(), stats.getBestStreak(), stats.getStreakRestoresThisMonth(), stats.getLevel(), stats.getExp());
                        isUserStatsLoaded = true;
                        sIsFirstLoad = false; // Đã xong lần đầu
                        checkAndShowContent();
                    }
                }
                @Override
                public void onFailure(Call<UserStats> call, Throwable t) {
                    isUserStatsLoaded = true; 
                    checkAndShowContent();
                }
            });
        } catch (Exception e) { Log.e(TAG, "Error data", e); }
    }

    private void bindCurrency(Long coins, Long diamonds, int streak, int bestStreak, int restores, int level, long exp) {
        if (tvCoins != null) tvCoins.setText(com.vn.jet.mosco.utils.NumberUtils.format(requireContext(), coins != null ? coins : 0));
        if (tvDiamonds != null) tvDiamonds.setText(com.vn.jet.mosco.utils.NumberUtils.format(requireContext(), diamonds != null ? diamonds : 0));
        
        if (tvModuleStreakVal != null) tvModuleStreakVal.setText(getString(R.string.streak_format_days, streak));
        
        if (lottieModuleStreak != null) {
            lottieModuleStreak.setMinAndMaxFrame(0, 24);
            if (!lottieModuleStreak.isAnimating()) lottieModuleStreak.playAnimation();
            
            if (streak >= 1000) {
                startRGBStreakAnimation(lottieModuleStreak);
            } else {
                stopRGBStreakAnimation();
                com.vn.jet.mosco.utils.StreakColorHelper.applyStreakColor(lottieModuleStreak, streak);
            }
        }
        
        if (lottieModuleStreakGlow != null) {
            lottieModuleStreakGlow.setMinAndMaxFrame(0, 24);
            if (!lottieModuleStreakGlow.isAnimating()) lottieModuleStreakGlow.playAnimation();
            
            if (streak >= 1000) {
                startRGBStreakAnimation(lottieModuleStreakGlow);
            } else {
                com.vn.jet.mosco.utils.StreakColorHelper.applyShadowEffect(lottieModuleStreakGlow);
            }
        }

        // XP Bar Animation
        if (tvLevel != null) tvLevel.setText(getString(R.string.format_level, level));
        
        long nextLevelXp = level * 1000L;
        if (nextLevelXp == 0) nextLevelXp = 1000;
        int progress = (int) ((exp * 100) / nextLevelXp);
        if (progress > 100) progress = 100;
        
        if (pbHomeXp != null) {
            ObjectAnimator anim = ObjectAnimator.ofInt(pbHomeXp, "progress", lastProgress, progress);
            anim.setDuration(1200);
            anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
            anim.start();
            lastProgress = progress;
        }
        
        if (tvXpVal != null) {
            String pct = String.format("%.2f%%", (exp * 100f) / nextLevelXp);
            tvXpVal.setText(pct);
        }

        this.bestStreakValue = bestStreak;
        this.restoresThisMonth = restores;
    }

    private void checkAndShowContent() {
        // Chỉ cần UserStats là cho hiện Home (Non-blocking Ranking)
        if (isUserStatsLoaded) {
            long elapsedTime = System.currentTimeMillis() - skeletonStartTime;
            if (elapsedTime < MIN_SKELETON_DURATION) {
                // Nếu dữ liệu về quá nhanh, đợi thêm cho đủ thời gian "Luxury"
                new Handler(Looper.getMainLooper()).postDelayed(this::checkAndShowContent, MIN_SKELETON_DURATION - elapsedTime);
                return;
            }

            if (shimmerHome != null && shimmerHome.getVisibility() == View.VISIBLE) {
                // Hiệu ứng Fade out skeleton và Fade in content
                clRealContent.setAlpha(0f);
                clRealContent.setVisibility(View.VISIBLE);
                
                clRealContent.animate()
                        .alpha(1f)
                        .setDuration(400)
                        .setListener(null);
                
                shimmerHome.animate()
                        .alpha(0f)
                        .setDuration(300)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                shimmerHome.stopShimmer();
                                shimmerHome.setVisibility(View.GONE);
                            }
                        });
            }
        }
    }

    private void showStreakDetail(int currentStreak, int bestStreak, int restores) {
        if (requireContext() == null) return;
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext(), R.style.CustomBottomSheetDialogTheme);
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_streak_detail, null);
        
        TextView tvCurrent = view.findViewById(R.id.tv_current_streak);
        TextView tvBest = view.findViewById(R.id.tv_best_streak);
        android.widget.Button btnRestore = view.findViewById(R.id.btn_restore_streak);
        com.airbnb.lottie.LottieAnimationView ivIcon = view.findViewById(R.id.iv_streak_icon);
        if (ivIcon != null) {
            ivIcon.setMinAndMaxFrame(0, 24);
            ivIcon.playAnimation();
            
            if (currentStreak >= 1000) {
                android.animation.ValueAnimator dialogRgbAnimator = android.animation.ValueAnimator.ofFloat(0f, 360f);
                dialogRgbAnimator.setDuration(3000);
                dialogRgbAnimator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                dialogRgbAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
                dialogRgbAnimator.addUpdateListener(animation -> {
                    float hue = (float) animation.getAnimatedValue();
                    com.vn.jet.mosco.utils.StreakColorHelper.applyRGBEffect(ivIcon, hue);
                });
                dialogRgbAnimator.start();
                dialog.setOnDismissListener(d -> dialogRgbAnimator.cancel());
            } else {
                com.vn.jet.mosco.utils.StreakColorHelper.applyStreakColor(ivIcon, currentStreak);
            }
        }
        tvCurrent.setText(getString(R.string.rank_format_streak, currentStreak));
        tvBest.setText(getString(R.string.rank_format_streak, bestStreak));
        btnRestore.setText(restores < 3 ? "RESTORE (FREE " + (3 - restores) + "/3)" : "RESTORE (500 DIAMONDS)");

        btnRestore.setOnClickListener(v -> {
            if (currentStreak >= bestStreak && currentStreak > 0) return;
            btnRestore.setEnabled(false);
            gameApiService.restoreStreak().enqueue(new Callback<com.vn.jet.mosco.model.ApiResponse<UserStats>>() {
                @Override
                public void onResponse(Call<com.vn.jet.mosco.model.ApiResponse<UserStats>> call, Response<com.vn.jet.mosco.model.ApiResponse<UserStats>> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        UserStats updated = response.body().getData();
                        bindCurrency(updated.getCoins(), updated.getDiamonds(), updated.getStreak(), updated.getBestStreak(), updated.getStreakRestoresThisMonth(), updated.getLevel(), updated.getExp());
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

    private void startRGBStreakAnimation(com.airbnb.lottie.LottieAnimationView lottie) {
        if (rgbAnimator != null && rgbAnimator.isRunning()) return;
        rgbAnimator = android.animation.ValueAnimator.ofFloat(0f, 360f);
        rgbAnimator.setDuration(3000);
        rgbAnimator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        rgbAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
        rgbAnimator.addUpdateListener(animation -> {
            float hue = (float) animation.getAnimatedValue();
            com.vn.jet.mosco.utils.StreakColorHelper.applyRGBEffect(lottieModuleStreak, hue);
        });
        rgbAnimator.start();
    }

    private void stopRGBStreakAnimation() {
        if (rgbAnimator != null) {
            rgbAnimator.cancel();
            rgbAnimator = null;
        }
    }

    private void setupLongClickCopy(TextView textView, String label) {
        if (textView == null) return;
        textView.setOnLongClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label, textView.getText().toString()));
            android.widget.Toast.makeText(requireContext(), getString(R.string.home_msg_copied_format, label), android.widget.Toast.LENGTH_SHORT).show();
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            return true;
        });
    }

    private class MiniRankPagerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private String[] titles;
        private final String[] types = {"level", "collection", "wealth", "streak"};
        private boolean isError = false;
        private boolean isLoading = false;
        private static final int TYPE_CONTENT = 0;
        private static final int TYPE_ERROR = 1;
        private static final int TYPE_LOADING = 2;

        public MiniRankPagerAdapter() {
            titles = new String[]{
                getString(R.string.home_rank_mini_level),
                getString(R.string.home_rank_mini_album),
                getString(R.string.home_rank_mini_wealth),
                getString(R.string.home_rank_mini_streaks)
            };
        }

        public void setErrorState(boolean error) {
            this.isError = error;
            if (error) this.isLoading = false;
            notifyDataSetChanged();
        }

        public void setLoadingState(boolean loading) {
            this.isLoading = loading;
            if (loading) this.isError = false;
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            if (isLoading) return TYPE_LOADING;
            return isError ? TYPE_ERROR : TYPE_CONTENT;
        }

        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_LOADING) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_home_rank_loading, parent, false);
                return new LoadingVH(v);
            }
            if (viewType == TYPE_ERROR) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_home_rank_error, parent, false);
                return new ErrorVH(v);
            }
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.fragment_mini_rank_page, parent, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof VH) {
                VH vh = (VH) holder;
                vh.tvTitle.setText(titles[position]);
                String type = types[position];
                List<JSONObject> data = rankDataCache.get(type);
                vh.adapter.updateData(data != null ? data : new ArrayList<>(), type);
            } else if (holder instanceof ErrorVH) {
                ErrorVH evh = (ErrorVH) holder;
                evh.btnRetry.setOnClickListener(v -> {
                    loadMiniRankData();
                    startRankingTimeout();
                });
            }
        }

        @Override public int getItemCount() { 
            return (isError || isLoading) ? 1 : types.length; 
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvTitle; RecyclerView rv; MiniRankItemAdapter adapter;
            VH(View v) { 
                super(v); 
                tvTitle = v.findViewById(R.id.tv_mini_rank_title); 
                rv = v.findViewById(R.id.rv_mini_rank); 
                rv.setLayoutManager(new LinearLayoutManager(requireContext()));
                adapter = new MiniRankItemAdapter(new ArrayList<>(), "level");
                rv.setAdapter(adapter);
            }
        }

        class ErrorVH extends RecyclerView.ViewHolder {
            android.widget.Button btnRetry;
            ErrorVH(View v) {
                super(v);
                btnRetry = v.findViewById(R.id.btn_rank_retry);
            }
        }

        class LoadingVH extends RecyclerView.ViewHolder {
            LoadingVH(View v) { super(v); }
        }
    }

    private class MiniRankItemAdapter extends RecyclerView.Adapter<MiniRankItemAdapter.VH> {
        private List<JSONObject> items;
        private String type;
        MiniRankItemAdapter(List<JSONObject> items, String type) { this.items = items; this.type = type; }
        void updateData(List<JSONObject> newItems, String newType) { 
            this.items = newItems; 
            this.type = newType; 
            notifyDataSetChanged(); 
        }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_home_mini_rank, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull VH holder, int position) {
            JSONObject item = items.get(position);
            int pos = position + 1;
            
            holder.tvPos.setText(String.valueOf(pos));
            
            // Highlight Top 3 with specific colors
            if (pos == 1) {
                holder.tvPos.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.mosco_gold));
            } else if (pos == 2) {
                holder.tvPos.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.rank_silver));
            } else if (pos == 3) {
                holder.tvPos.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.rank_bronze));
            } else {
                holder.tvPos.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.white_40));
            }

            String rawName = item.optString("ingameName", "Unknown");
            if (rawName.length() > 14) {
                rawName = rawName.substring(0, 12) + "...";
            }
            holder.tvName.setText(rawName);
            
            String valStr = "";
            int val = item.optInt("value", 0);
            android.content.Context context = requireContext();
            if (context != null) {
                switch (type) {
                    case "streak": 
                        valStr = context.getString(R.string.rank_format_streak, val); 
                        break;
                    case "wealth": 
                        valStr = com.vn.jet.mosco.utils.NumberUtils.format(context, (long)val); 
                        break;
                    case "collection": 
                        valStr = context.getString(R.string.rank_format_album, val); 
                        break;
                    default: 
                        valStr = context.getString(R.string.rank_format_level, val); 
                        break;
                }
            }
            holder.tvVal.setText(valStr);

            long userId = item.optLong("userId", -1L);
            String avatarId = item.optString("avatarId", "1");
            com.vn.jet.mosco.utils.AvatarUtils.loadAvatar(context, holder.ivAvatar, userId, avatarId);

            // Bridge Bridge: Nhấn vào item mở profile
            holder.itemView.setOnClickListener(v -> {
                if (userId != -1L) {
                    com.vn.jet.mosco.utils.NavigationUtils.openProfile(getActivity(), userId);
                }
            });
        }
        @Override public int getItemCount() { return items.size(); }
        class VH extends RecyclerView.ViewHolder {
            TextView tvPos, tvName, tvVal; ImageView ivAvatar;
            VH(View v) { super(v); tvPos = v.findViewById(R.id.tv_mini_rank_pos); tvName = v.findViewById(R.id.tv_mini_rank_name); tvVal = v.findViewById(R.id.tv_mini_rank_val); ivAvatar = v.findViewById(R.id.iv_mini_rank_avatar); }
        }
    }

    private static class BannerPagerAdapter extends RecyclerView.Adapter<BannerPagerAdapter.VH> {
        private final int[] resIds;
        BannerPagerAdapter(int[] resIds) { this.resIds = resIds; }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView iv = new ImageView(parent.getContext());
            iv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            return new VH(iv);
        }
        @Override public void onBindViewHolder(@NonNull VH holder, int position) { ((ImageView) holder.itemView).setImageResource(resIds[position % resIds.length]); }
        @Override public int getItemCount() { return Integer.MAX_VALUE; }
        static class VH extends RecyclerView.ViewHolder { VH(@NonNull View itemView) { super(itemView); } }
    }
}
