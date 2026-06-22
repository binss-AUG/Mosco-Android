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
    private TextView tvNotification; // Vẫn giữ lại cho ticker
    private ImageView ivChatAvatar, btnHomeSend;
    private EditText etHomeChat;
    private ViewPager2 vpBanners, vpMiniRanking;
    private LinearLayout llBannerDots;
    private com.scwang.smart.refresh.layout.SmartRefreshLayout swipeRefreshLayout;
    private com.facebook.shimmer.ShimmerFrameLayout shimmerHome;
    private View clRealContent;
    
    private boolean isUserStatsLoaded = false;
    private boolean isRankLoaded = false;
    private boolean isDataLoaded = false;
    
    // Dashboard Modules
    private View cvModuleStreak, cvModuleDaily, cvModuleStage, btnFullRank;
    private TextView tvModuleStreakVal;
    private com.airbnb.lottie.LottieAnimationView lottieModuleStreak, lottieModuleStreakGlow;
    private View layoutWorldChatExpanded;
    private RecyclerView rvWorldChatExpanded;
    private TextView tvChatTicker;
    private final Handler tickerHandler = new Handler(Looper.getMainLooper());
    private Runnable tickerRunnable;
    private int currentTickerIndex = 0;

    // Header Buttons & Badges
    private View btnFriends, btnMailbox, btnShop;
    
    // Avatar Streak overlay
    private View flStreakAvatar;
    private TextView tvStreakAvatarVal;
    private com.airbnb.lottie.LottieAnimationView lottieStreakAvatar;



    // ── State ──
    private int bannerCount = 0;
    private int currentStreakValue = 0;
    private int bestStreakValue = 0;
    private int restoresThisMonth = 0;
    private int lastProgress = 0;
    private final java.util.List<android.animation.Animator> activeAnimators = new java.util.ArrayList<>();
    
    private RecyclerView rvWorldChat;
    private static WorldChatAdapter sWorldChatAdapter;
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
    private RecyclerView.AdapterDataObserver chatDataObserver;

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
        
        // setupLongClickCopy đã chuyển sang MainActivity
        setupBannerCarousel();
        setupQuickToolActions();
        setupNotificationTicker();
        setupChatBar();
        setupDashboard();
        setupRefreshLogic();
        loadUserData();
        loadMiniRankData();
        startRankingTimeout();
        startRankAutoScroll();
        
        // Bắt đầu Shimmer ngay khi mở fragment (chỉ hiện nếu chưa có data)
        if (shimmerHome != null && sIsFirstLoad) {
            // Skeletonize layout thực tế của Home trước khi bắt đầu shimmer
            if (clRealContent != null) {
                clRealContent.setVisibility(View.VISIBLE);
                com.vn.jet.mosco.utils.SkeletonHelper.skeletonize(clRealContent);
            }
            shimmerHome.showShimmer(true);
            shimmerHome.startShimmer();
            
            // TẠI SAO: Đồng bộ bắt đầu Shimmer cho thanh Header dùng chung ở MainActivity
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).showHeaderShimmer(true);
            }
        } else if (shimmerHome != null) {
            if (clRealContent != null) {
                clRealContent.setVisibility(View.VISIBLE);
                com.vn.jet.mosco.utils.SkeletonHelper.restore(clRealContent);
            }
            shimmerHome.stopShimmer();
            shimmerHome.hideShimmer();
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
        if (chatDisposable != null && !chatDisposable.isDisposed()) {
            chatDisposable.dispose();
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
        if (worldChatAdapter != null && chatDataObserver != null) {
            worldChatAdapter.unregisterAdapterDataObserver(chatDataObserver);
            chatDataObserver = null;
        }
        super.onDestroyView();
    }

    @Override
    public void onInventoryChanged() { }

    private void initViews(View v) {
        // Thanh top bar đã được chuyển sang MainActivity quản lý
        tvNotification = v.findViewById(R.id.tv_home_notification);
        ivChatAvatar = v.findViewById(R.id.iv_chat_avatar);
        etHomeChat = v.findViewById(R.id.et_home_chat);
        btnHomeSend = v.findViewById(R.id.btn_home_send);
        tvChatTicker = v.findViewById(R.id.tv_chat_ticker);
        layoutWorldChatExpanded = v.findViewById(R.id.layout_world_chat_expanded);
        rvWorldChatExpanded = v.findViewById(R.id.rv_world_chat_expanded);

        llBannerDots = v.findViewById(R.id.ll_banner_dots);
        vpBanners = v.findViewById(R.id.vp_banners);
        swipeRefreshLayout = v.findViewById(R.id.swipe_refresh_home);
        
        // Top bar views đã được chuyển sang MainActivity

        // Dashboard
        cvModuleStreak = v.findViewById(R.id.cv_module_streak);
        cvModuleDaily = v.findViewById(R.id.cv_module_daily);
        cvModuleStage = v.findViewById(R.id.cv_module_stage);
        tvModuleStreakVal = v.findViewById(R.id.tv_module_streak_val);
        lottieModuleStreak = v.findViewById(R.id.lottie_module_streak);
        vpMiniRanking = v.findViewById(R.id.vp_mini_ranking);
        btnFullRank = v.findViewById(R.id.btn_home_full_rank);
        
        shimmerHome = v.findViewById(R.id.shimmer_home);
        clRealContent = v.findViewById(R.id.cl_real_content);
    }

    private void initServices() {
        sessionManager = new SessionManager(requireContext());
        gameApiService = ApiClient.getClient(requireContext()).create(GameApiService.class);
        miniRankAdapter = new MiniRankPagerAdapter();
        if (sWorldChatAdapter == null) {
            sWorldChatAdapter = new WorldChatAdapter();
        }
        worldChatAdapter = sWorldChatAdapter;
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
        // Header Buttons Actions
        if (btnFriends != null) {
            btnFriends.setOnClickListener(new ClickDebounce(v -> {
                android.widget.Toast.makeText(getContext(), "Friends system coming soon", android.widget.Toast.LENGTH_SHORT).show();
            }));
        }

        if (btnMailbox != null) {
            btnMailbox.setOnClickListener(new ClickDebounce(v -> {
                NavigationUtils.openMailbox(getActivity());
            }));
        }

        if (btnShop != null) {
            btnShop.setOnClickListener(new ClickDebounce(v -> {
                if (getActivity() != null) {
                    getActivity().getSupportFragmentManager().beginTransaction()
                            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
                            .add(R.id.frame_layout, new ShopFragment())
                            .addToBackStack(null)
                            .commit();
                }
            }));
        }

        if (flStreakAvatar != null) {
            flStreakAvatar.setOnClickListener(new ClickDebounce(v -> {
                // Tại sao (WHY): Truyền đúng streak hiện tại để dialog đồng bộ thông tin chính xác
                showStreakDetail(currentStreakValue, bestStreakValue, restoresThisMonth);
            }));
        }

        if (cvModuleStreak != null) {
            cvModuleStreak.setOnClickListener(new ClickDebounce(v -> {
                // Tại sao (WHY): Truyền đúng streak hiện tại để dialog đồng bộ thông tin chính xác
                showStreakDetail(currentStreakValue, bestStreakValue, restoresThisMonth);
            }));
        }
        
        if (btnFullRank != null) {
            btnFullRank.setOnClickListener(v -> NavigationUtils.openRank(getActivity()));
        }

        if (vpMiniRanking != null) {
            // Thiết lập giới hạn clip nghiêm ngặt để triệt tiêu hoàn toàn hiện tượng lọt trang kề bên (vượt giới địa lý)
            vpMiniRanking.setClipChildren(true);
            vpMiniRanking.setClipToPadding(true);
            vpMiniRanking.setAdapter(miniRankAdapter);
            
            // Cấu hình PageTransformer tạo hiệu ứng chiều sâu 3D mờ dần chuẩn xác trong ranh giới thẻ
            vpMiniRanking.setPageTransformer((page, position) -> {
                float absPos = Math.abs(position);
                if (absPos >= 1.0f) {
                    page.setAlpha(0.0f);
                    page.setScaleX(0.9f);
                    page.setScaleY(0.9f);
                    page.setTranslationX(0.0f);
                } else {
                    // Hiệu ứng mờ dần mượt mà khi trượt
                    page.setAlpha(1.0f - absPos * 0.8f);
                    // Hiệu ứng thu nhỏ nhẹ tạo chiều sâu 3D sang trọng
                    float scale = 0.92f + (1.0f - absPos) * 0.08f;
                    page.setScaleX(scale);
                    page.setScaleY(scale);
                    // Giữ nguyên vị trí trượt ngang mặc định để không gây lọt trang
                    page.setTranslationX(0.0f);
                }
            });
            
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
        
        // Bắt đầu hiệu ứng FOMO chớp nháy cho chữ ở đáy card
        startFomoBlink(getActivity() != null ? getActivity().findViewById(R.id.tv_module_streak_hint) : null);
        startFomoBlink(getActivity() != null ? getActivity().findViewById(R.id.tv_module_daily_hint) : null);
        startFomoBlink(getActivity() != null ? getActivity().findViewById(R.id.tv_module_stage_hint) : null);
    }

    private void startFomoBlink(View view) {
        if (view == null) return;
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, "alpha", 1.0f, 0.3f, 1.0f);
        animator.setDuration(1000);
        animator.setRepeatCount(ObjectAnimator.INFINITE);
        animator.setRepeatMode(ObjectAnimator.REVERSE);
        animator.start();
        activeAnimators.add(animator);
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
        String[] types = {"level", "collection", "social", "streak", "fame", "duo-streak"};
        for (String type : types) {
            fetchRankTop5(type);
        }
    }

    private void fetchRankTop5(String type) {
        if (gameApiService == null) return;
        Call<ResponseBody> call;
        switch (type) {
            case "streak": call = gameApiService.getRankByStreak(); break;
            case "fame": call = gameApiService.getRankByFame(); break;
            case "social": call = gameApiService.getRankBySocial(); break;
            case "duo-streak": call = gameApiService.getRankByDuoStreak(); break;
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
        // Tắt chức năng lướt tự động theo yêu cầu của user
        if (true) return;
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
        // Luôn cập nhật ID mới nhất từ session để tránh bị stale
        if (sessionManager.getUserId() != null) {
            worldChatAdapter.setCurrentUserId(String.valueOf(sessionManager.getUserId()));
        }

        if (rvWorldChatExpanded != null) {
            LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
            layoutManager.setStackFromEnd(true);
            rvWorldChatExpanded.setLayoutManager(layoutManager);
            rvWorldChatExpanded.setAdapter(worldChatAdapter);
            // Tắt DefaultItemAnimator để tránh xung đột với manual float-up animation
            rvWorldChatExpanded.setItemAnimator(null);

            chatDataObserver = new RecyclerView.AdapterDataObserver() {
                @Override
                public void onItemRangeInserted(int positionStart, int itemCount) {
                    super.onItemRangeInserted(positionStart, itemCount);
                    if (rvWorldChatExpanded != null && rvWorldChatExpanded.getLayoutManager() instanceof LinearLayoutManager) {
                        LinearLayoutManager lm = (LinearLayoutManager) rvWorldChatExpanded.getLayoutManager();
                        int lastVisible = lm.findLastCompletelyVisibleItemPosition();
                        boolean isSelf = false;
                        if (worldChatAdapter.getItemCount() > 0) {
                            WorldChatMessage lastMsg = worldChatAdapter.getMessageAt(worldChatAdapter.getItemCount() - 1);
                            if (lastMsg != null && sessionManager.getUserId() != null && lastMsg.getSenderId().equals(String.valueOf(sessionManager.getUserId()))) {
                                isSelf = true;
                            }
                        }
                        if (isSelf || lastVisible >= worldChatAdapter.getItemCount() - 2) {
                            rvWorldChatExpanded.post(() -> rvWorldChatExpanded.scrollToPosition(worldChatAdapter.getItemCount() - 1));
                        }
                    }
                }
            };
            worldChatAdapter.registerAdapterDataObserver(chatDataObserver);

            // Bám đáy khi bàn phím ảo bật lên
            rvWorldChatExpanded.addOnLayoutChangeListener((v1, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (bottom < oldBottom) {
                    rvWorldChatExpanded.postDelayed(() -> {
                        if (worldChatAdapter.getItemCount() > 0) {
                            rvWorldChatExpanded.scrollToPosition(worldChatAdapter.getItemCount() - 1);
                        }
                    }, 60);
                }
            });
            
            // Professional system messages
            if (worldChatAdapter.getItemCount() == 0) {
                worldChatAdapter.addMessage(new WorldChatMessage("0", getString(R.string.chat_msg_system), "0", getString(R.string.chat_msg_welcome)));
                worldChatAdapter.addMessage(new WorldChatMessage("1", "Admin_Zero", "1", "Welcome to the central communication hub."));
            }
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
                // Tại sao (WHY): Cập nhật ticker ngay lập tức khi nhận được tin nhắn mới nhất từ WebSocket
                updateChatTickerWithLatest();
            }
        });

        final long[] lastWorldChatSendTime = {0};

        if (btnHomeSend != null) {
            btnHomeSend.setOnClickListener(v -> {
                String msg = etHomeChat.getText().toString().trim();
                if (!msg.isEmpty()) {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastWorldChatSendTime[0] < 3000) {
                        if (getContext() != null) {
                            android.widget.Toast.makeText(getContext(), "Vui lòng chờ 3s trước khi gửi tin nhắn tiếp theo!", android.widget.Toast.LENGTH_SHORT).show();
                        }
                        return;
                    }
                    lastWorldChatSendTime[0] = currentTime;

                    String myName = sessionManager.getIngameName();
                    String myAvatar = sessionManager.getAvatarId();
                    String currentUserId = sessionManager.getUserId() != null ? String.valueOf(sessionManager.getUserId()) : "guest";
                    
                    // Gửi qua WebSocket (Server sẽ broadcast lại cho mọi người)
                    com.vn.jet.mosco.model.WorldChatMessage chatMsg = 
                        new com.vn.jet.mosco.model.WorldChatMessage(currentUserId, myName, myAvatar, msg);

                    // Tại sao (WHY): Optimistic UI - In ra màn hình ngay lập tức để tránh cảm giác lag
                    if (worldChatAdapter != null) {
                        worldChatAdapter.addMessage(chatMsg);
                        if (rvWorldChatExpanded != null && worldChatAdapter.getItemCount() > 0) {
                            rvWorldChatExpanded.scrollToPosition(worldChatAdapter.getItemCount() - 1);
                        }
                    }

                    wsManager.sendWorldMessage(chatMsg);
                    
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                    etHomeChat.setText("");
                }
            });
        }
    }

    private void startChatTicker() {
        stopChatTicker();
        // Tại sao (WHY): Hiển thị tin nhắn mới nhất ngay khi khởi chạy ticker thay vì chạy vòng lặp xoay các tin nhắn cũ
        updateChatTickerWithLatest();
    }

    private void stopChatTicker() {
        if (tickerRunnable != null) tickerHandler.removeCallbacks(tickerRunnable);
    }

    // Tại sao (WHY): Hàm giải mã HTML Entities và cập nhật Ticker hiển thị tin nhắn mới nhất trong danh sách
    private void updateChatTickerWithLatest() {
        if (worldChatAdapter != null && worldChatAdapter.getItemCount() > 0 && tvChatTicker != null && etHomeChat.getVisibility() == View.GONE) {
            WorldChatMessage msg = worldChatAdapter.getMessageAt(worldChatAdapter.getItemCount() - 1);
            if (msg != null) {
                CharSequence decodedContent;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    decodedContent = android.text.Html.fromHtml(msg.getContent(), android.text.Html.FROM_HTML_MODE_LEGACY);
                } else {
                    decodedContent = android.text.Html.fromHtml(msg.getContent());
                }
                tvChatTicker.setText(msg.getSenderName() + ": " + decodedContent);
            }
        }
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
            // Tại sao (WHY): Cập nhật ticker hiển thị tin nhắn mới nhất khi người dùng đóng khung chat đầy đủ
            updateChatTickerWithLatest();
        }
    }

    private void setupQuickToolActions() {
        if (cvModuleStreak != null) {
            cvModuleStreak.setOnClickListener(new ClickDebounce(v -> {
                // Tại sao (WHY): Khi nhấn vào thẻ Streak, mở trực tiếp Activity điểm danh hàng ngày để tối ưu UX
                startActivity(new android.content.Intent(requireContext(), com.vn.jet.mosco.DailyCheckinActivity.class));
            }));
        }
        if (cvModuleDaily != null) {
            cvModuleDaily.setOnClickListener(new ClickDebounce(v -> {
                startActivity(new android.content.Intent(requireContext(), com.vn.jet.mosco.DailyCheckinActivity.class));
            }));
        }
        if (cvModuleStage != null) {
            cvModuleStage.setOnClickListener(new ClickDebounce(v -> {
                if (getActivity() != null) {
                    getActivity().getSupportFragmentManager().beginTransaction()
                            .add(R.id.frame_layout, new com.vn.jet.mosco.fragment.StageFragment())
                            .addToBackStack(null)
                            .commit();
                }
            }));
        }
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
        int dotWidth = getResources().getDimensionPixelSize(R.dimen.page_indicator_width);
        int dotHeight = getResources().getDimensionPixelSize(R.dimen.page_indicator_height);
        int dotSpacing = getResources().getDimensionPixelSize(R.dimen.page_indicator_spacing);
        float activeScale = getResources().getInteger(R.integer.daily_indicator_scale_active_percent) / 100f;
        float inactiveScale = getResources().getInteger(R.integer.daily_indicator_scale_inactive_percent) / 100f;

        for (int i = 0; i < count; i++) {
            View dot = new View(requireContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dotWidth, dotHeight);
            params.setMargins(dotSpacing, 0, dotSpacing, 0);
            dot.setLayoutParams(params);
            dot.setBackgroundResource(i == 0 ? R.drawable.bg_dot_active : R.drawable.bg_dot_inactive);
            
            // Thiết lập giá trị scale ban đầu cho các dot để tránh bị khựng hình khi mới load banner lần đầu
            dot.setScaleX(i == 0 ? activeScale : inactiveScale);
            
            llBannerDots.addView(dot);
        }
    }

    private void updateDotIndicators(int activePosition) {
        if (llBannerDots == null) return;
        int duration = getResources().getInteger(R.integer.daily_indicator_scale_duration);
        float activeScale = getResources().getInteger(R.integer.daily_indicator_scale_active_percent) / 100f;
        float inactiveScale = getResources().getInteger(R.integer.daily_indicator_scale_inactive_percent) / 100f;

        for (int i = 0; i < llBannerDots.getChildCount(); i++) {
            View dot = llBannerDots.getChildAt(i);
            if (dot != null) {
                dot.setBackgroundResource(i == activePosition ? R.drawable.bg_dot_active : R.drawable.bg_dot_inactive);
                // Áp dụng animation scaleX co giãn dẹt cho indicator khi banner thay đổi (auto-scroll hoặc manual drag)
                dot.animate().scaleX(i == activePosition ? activeScale : inactiveScale)
                        .setDuration(duration).start();
            }
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
        if (sIsFirstLoad) isUserStatsLoaded = false;
        if (sessionManager == null || gameApiService == null) return;
        try {
            Long userId = sessionManager.getUserId();
            String avatarId = sessionManager.getAvatarId();
            if (avatarId == null) avatarId = "1";
            com.vn.jet.mosco.utils.AvatarUtils.loadAvatar(requireContext(), ivChatAvatar, userId, avatarId);
            
            // Gọi MainActivity cập nhật lại dữ liệu trên thanh top bar dùng chung
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).loadUserData();
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
        // Tại sao (WHY): Sử dụng số ngày streak nạp từ đối tượng UserStats phía Server thay vì tính toán local.
        // Điều này đảm bảo tính năng chống cheat/hack tuyệt đối khi người dùng cố tình thay đổi thời gian hệ thống của thiết bị/giả lập.
        if (tvModuleStreakVal != null) tvModuleStreakVal.setText(getString(R.string.streak_format_days, streak));
        
        if (lottieModuleStreak != null) {
            com.vn.jet.mosco.utils.StreakColorHelper.setupStreakLottie(lottieModuleStreak, streak, streak > 0);
            if (streak >= 1000) {
                startRGBStreakAnimation(lottieModuleStreak);
            }
        }
        
        if (lottieModuleStreakGlow != null) {
            com.vn.jet.mosco.utils.StreakColorHelper.setupStreakLottie(lottieModuleStreakGlow, streak, streak > 0);
            if (streak >= 1000) {
                startRGBStreakAnimation(lottieModuleStreakGlow);
            } else {
                com.vn.jet.mosco.utils.StreakColorHelper.applyShadowEffect(lottieModuleStreakGlow);
            }
        }

        this.currentStreakValue = streak;
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

            // Tại sao (WHY): Giữ màn hình ở trạng thái skeleton 3s ở chế độ Debug để dễ dàng quan sát, kiểm thử giao diện shimmer
            if (com.vn.jet.mosco.utils.AppConfig.DEBUG_MODE && com.vn.jet.mosco.utils.AppConfig.DEBUG_SIMULATE_DELAY) {
                new Handler(Looper.getMainLooper()).postDelayed(this::hideHomeShimmerNow, 3000);
            } else {
                hideHomeShimmerNow();
            }
        }
    }

    private void hideHomeShimmerNow() {
        if (shimmerHome != null) {
            if (clRealContent != null) {
                com.vn.jet.mosco.utils.SkeletonHelper.restore(clRealContent);
            }
            shimmerHome.stopShimmer();
            shimmerHome.hideShimmer();

            // TẠI SAO: Đồng bộ tắt Shimmer của Header dùng chung ở MainActivity khi dữ liệu Home đã nạp xong
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).showHeaderShimmer(false);
            }
        }
    }

    private void showStreakDetail(int currentStreak, int bestStreak, int restores) {
        com.vn.jet.mosco.utils.MoscoDialogHelper.showStreakDetailBottomSheet(
            requireContext(),
            currentStreak,
            bestStreak,
            restores,
            gameApiService,
            () -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).loadUserData();
                }
            }
        );
    }

    private void startRGBStreakAnimation(com.airbnb.lottie.LottieAnimationView lottie) {
        if (rgbAnimator != null && rgbAnimator.isRunning()) return;
        rgbAnimator = android.animation.ValueAnimator.ofFloat(0f, 360f);
        rgbAnimator.setDuration(3000);
        rgbAnimator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        rgbAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
        rgbAnimator.addUpdateListener(animation -> {
            float hue = (float) animation.getAnimatedValue();
            com.vn.jet.mosco.utils.StreakColorHelper.applyRGBEffect(lottie, hue);
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


    private void updateStreakDisplay(int streak) {
        if (tvStreakAvatarVal != null) {
            String oldVal = tvStreakAvatarVal.getText().toString();
            int oldStreak = 0;
            try { oldStreak = Integer.parseInt(oldVal); } catch (Exception ignored) {}
            
            tvStreakAvatarVal.setText(String.valueOf(streak));
            
            if (streak > oldStreak) {
                // Animation tăng streak
                tvStreakAvatarVal.animate()
                    .scaleX(1.15f)
                    .scaleY(1.15f)
                    .setDuration(120)
                    .withEndAction(() -> {
                        tvStreakAvatarVal.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                    }).start();
            }
        }
    }

    private class MiniRankPagerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private String[] titles;
        private final String[] types = {"level", "collection", "streak", "fame", "social", "duo-streak"};
        private boolean isError = false;
        private boolean isLoading = false;
        private static final int TYPE_CONTENT = 0;
        private static final int TYPE_ERROR = 1;
        private static final int TYPE_LOADING = 2;

        public MiniRankPagerAdapter() {
            titles = new String[]{
                "TOP " + getString(R.string.rank_tab_level),
                "TOP " + getString(R.string.rank_tab_album),
                "TOP " + getString(R.string.rank_tab_streak),
                "TOP " + getString(R.string.rank_tab_fame),
                "TOP " + getString(R.string.rank_tab_social),
                "TOP " + getString(R.string.rank_tab_duo_streak)
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
                ViewGroup container = v.findViewById(R.id.ll_skeleton_container);
                if (container != null) {
                    com.vn.jet.mosco.utils.SkeletonHelper.populateShimmerContainer(container, R.layout.item_home_mini_rank, 5);
                }
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
                if (data == null || data.isEmpty()) {
                    vh.rv.setVisibility(View.GONE);
                    if (vh.tvEmpty != null) vh.tvEmpty.setVisibility(View.VISIBLE);
                } else {
                    vh.rv.setVisibility(View.VISIBLE);
                    if (vh.tvEmpty != null) vh.tvEmpty.setVisibility(View.GONE);
                    vh.adapter.updateData(data, type);
                }
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
            TextView tvTitle; RecyclerView rv; MiniRankItemAdapter adapter; TextView tvEmpty;
            VH(View v) { 
                super(v); 
                tvTitle = v.findViewById(R.id.tv_mini_rank_title); 
                rv = v.findViewById(R.id.rv_mini_rank); 
                tvEmpty = v.findViewById(R.id.tv_mini_rank_empty);
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
                    case "duo-streak":
                        valStr = context.getString(R.string.rank_format_streak, val); 
                        break;
                    case "fame": 
                        valStr = String.valueOf(val); 
                        break;
                    case "social":
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
