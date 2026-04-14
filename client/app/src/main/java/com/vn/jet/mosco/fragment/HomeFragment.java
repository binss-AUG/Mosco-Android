package com.vn.jet.mosco.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GestureDetectorCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.vn.jet.mosco.MainActivity;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.model.Objet;
import com.vn.jet.mosco.model.UserStats;
import com.vn.jet.mosco.network.ApiClient;
import com.vn.jet.mosco.network.GameApiService;
import com.vn.jet.mosco.utils.CardAssetManager;
import com.vn.jet.mosco.utils.ClickDebounce;
import com.vn.jet.mosco.utils.DatabaseLoader;
import com.vn.jet.mosco.utils.LevelBadgeEffectHelper;
import com.vn.jet.mosco.utils.ObjetDetailBinder;
import com.vn.jet.mosco.utils.SessionManager;

import org.json.JSONObject;

import java.io.File;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * HomeFragment — Galactic Home Dashboard (Immersive Showcase + Floating HUD).
 *
 * Architecture Overview:
 *   1. Header Section: User profile + formatted currency display
 *   2. Event Banner: Auto-scrolling ViewPager2 with dot indicators
 *   3. Hero Card Showcase: Displays the user's highest-OVR card with
 *      hologram uplight, 3D flip gesture, and double-tap detail dialog
 *   4. Floating HUD: Expandable glass FAB for quick utility access
 *
 * Gesture System uses GestureDetectorCompat to decouple single-tap (flip)
 * from double-tap (detail dialog), avoiding the 300ms TouchSlop ambiguity
 * that plagues standard OnClickListener + onDoubleTap combos.
 */
public class HomeFragment extends Fragment implements DatabaseLoader.OnInventoryChangeListener {

    private static final String TAG = "HomeFragment";

    // ── Duration constants for animations (ms) ──
    private static final int FLIP_HALF_DURATION = 250;
    private static final int HUD_EXPAND_DURATION = 200;
    private static final int BANNER_AUTO_SCROLL_DELAY = 4000;
    private static final int HOLOGRAM_PULSE_DURATION = 2500;
    private static final int SHOWCASE_FLOAT_DURATION = 3000;

    // ── UI References ──
    private TextView tvUsername, tvLevel, tvOvr, tvCoins, tvDiamonds, tvUserId;
    private View layoutUserId;
    private ImageView ivShowcaseFront, ivShowcaseBack, ivLevelBadge;
    private com.vn.jet.mosco.utils.StrokedTextView tvShowcaseOvr;
    private MaterialCardView cvShowcaseCard;
    private View flShowcaseContainer, viewCardShimmer;
    private LinearLayout llEmptyState, llBannerDots;
    private View layoutShowcaseLoading;
    private TextView tvShowcaseLoading, tvCardCount;
    private ViewPager2 vpBanners;
    private View viewProjectorBeam, viewAvatarGlow, flAvatarGroup;

    // ── Quick Tool References (New HUD V3) ──
    private View btnQuickRank, btnQuickDaily, btnQuickEvent, btnQuickUpgrade, btnQuickShop, btnQuickFriends, btnQuickFormation, btnQuickGift;
    private android.widget.HorizontalScrollView hsvQuickTools;
    private LinearLayout llQuickToolsContainer;

    // ── State ──
    private boolean isCardFlipped = false;
    private boolean isFlipAnimating = false;
    private float initialTouchX = 0f;
    private float startCardRotation = 0f;
    private ObjectAnimator snapAnimator;
    private String heroBackImageUrl = null;

    // ── Services ──
    private SessionManager sessionManager;
    private GameApiService gameApiService;

    // ── Gesture Detection ──
    private GestureDetectorCompat gestureDetector;
    private static final int SWIPE_THRESHOLD = 100;
    private static final int SWIPE_VELOCITY_THRESHOLD = 100;

    // ── Banner Auto-scroll ──
    private final Handler bannerHandler = new Handler(Looper.getMainLooper());
    private Runnable bannerRunnable;
    private int bannerCount = 0;
    private final Handler showcaseLoadingHandler = new Handler(Looper.getMainLooper());
    private Runnable showcaseLoadingTimeoutRunnable;

    // ── Cached best card data ──
    private Objet heroObjet;
    private JSONObject heroCardJson;
    private int pendingShowcaseAssetLoads = 0;
    private ImageView ivHomeAvatar;

    public HomeFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        initViews(view);
        initServices();
        initGestureDetector();
        setupBannerCarousel();
        setupQuickToolActions();
        setupQuickToolDimensions();
        loadUserData();
        loadHeroShowcase();
        
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        startBannerAutoScroll();
        startAvatarPulse();
        loadUserData();
        loadHeroShowcase();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopBannerAutoScroll();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        DatabaseLoader.unregisterInventoryChangeListener(this);
        stopBannerAutoScroll();
        if (showcaseLoadingTimeoutRunnable != null) {
            showcaseLoadingHandler.removeCallbacks(showcaseLoadingTimeoutRunnable);
        }
    }

    @Override
    public void onInventoryChanged() {
        if (getActivity() != null) {
            getActivity().runOnUiThread(this::loadHeroShowcase);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  INITIALIZATION
    // ════════════════════════════════════════════════════════════════

    private void initViews(View v) {
        try {
            tvUsername = v.findViewById(R.id.tv_home_username);
            tvLevel = v.findViewById(R.id.tv_home_level);
            tvOvr = v.findViewById(R.id.tv_home_ovr);
            tvCoins = v.findViewById(R.id.tv_home_coins);
            tvDiamonds = v.findViewById(R.id.tv_home_diamonds);
            ivHomeAvatar = v.findViewById(R.id.iv_home_avatar);
            ivShowcaseFront = v.findViewById(R.id.card_iv_image);
            ivShowcaseBack = v.findViewById(R.id.iv_showcase_back);
            ivLevelBadge = v.findViewById(R.id.card_iv_level);
            tvShowcaseOvr = v.findViewById(R.id.card_tv_ovr);
            cvShowcaseCard = v.findViewById(R.id.cv_showcase_card);
            viewCardShimmer = v.findViewById(R.id.view_card_shimmer);
            llEmptyState = v.findViewById(R.id.ll_empty_state);
            llBannerDots = v.findViewById(R.id.ll_banner_dots);
            
            btnQuickRank = v.findViewById(R.id.btn_quick_rank);
            btnQuickDaily = v.findViewById(R.id.btn_quick_daily);
            btnQuickEvent = v.findViewById(R.id.btn_quick_event);
            btnQuickUpgrade = v.findViewById(R.id.btn_quick_upgrade);
            btnQuickShop = v.findViewById(R.id.btn_quick_shop);
            btnQuickFriends = v.findViewById(R.id.btn_quick_friends);
            btnQuickFormation = v.findViewById(R.id.btn_quick_formation);
            btnQuickGift = v.findViewById(R.id.btn_quick_gift);
            
            hsvQuickTools = v.findViewById(R.id.hsv_quick_tools);
            layoutShowcaseLoading = v.findViewById(R.id.layout_showcase_loading);
            tvShowcaseLoading = v.findViewById(R.id.tv_showcase_loading);
            
            flShowcaseContainer = v.findViewById(R.id.fl_showcase_container);
            vpBanners = v.findViewById(R.id.vp_banners);
            viewProjectorBeam = v.findViewById(R.id.view_projector_beam);
            viewAvatarGlow = v.findViewById(R.id.view_avatar_glow);
            flAvatarGroup = v.findViewById(R.id.fl_avatar_group);

            tvUserId = v.findViewById(R.id.tv_home_user_id);
            layoutUserId = v.findViewById(R.id.layout_user_id);
            llQuickToolsContainer = v.findViewById(R.id.ll_quick_tools_container);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views", e);
        }
    }

    private void initServices() {
        try {
            sessionManager = new SessionManager(requireContext());
            gameApiService = ApiClient.getClient(requireContext()).create(GameApiService.class);
            DatabaseLoader.registerInventoryChangeListener(this);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing services", e);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  GESTURE DETECTOR — Optimized for 2026 UX
    // ════════════════════════════════════════════════════════════════

    private void initGestureDetector() {
        GestureDetector.SimpleOnGestureListener gestureListener =
                new GestureDetector.SimpleOnGestureListener() {

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                // Single tap -> Open selector to change card
                openCardSelector();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                // Double tap -> Open detail dialog
                if (heroObjet != null) {
                    openDetailDialog();
                }
                return true;
            }

            // onFling removed because custom OnTouchListener implements real-time dragging

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }
        };

        gestureDetector = new GestureDetectorCompat(requireContext(), gestureListener);

        if (cvShowcaseCard != null) {
            float scale = getResources().getDisplayMetrics().density;
            cvShowcaseCard.setCameraDistance(8000 * scale);
            cvShowcaseCard.setOnTouchListener((v, event) -> {
                boolean handled = gestureDetector.onTouchEvent(event);
                if (heroObjet == null) return handled;

                View pseudoGlow = (View) cvShowcaseCard.getTag(R.id.view_progress_fill);
                if (pseudoGlow != null) pseudoGlow.setCameraDistance(8000 * scale);

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        if (snapAnimator != null && snapAnimator.isRunning()) {
                            snapAnimator.cancel();
                        }
                        initialTouchX = event.getRawX();
                        startCardRotation = cvShowcaseCard.getRotationY();
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float diffX = event.getRawX() - initialTouchX;
                        // Đọc cấu hình độ nhạy xoay từ resource (không hardcode)
                        int sensitivity = getResources().getInteger(R.integer.card_flip_sensitivity);
                        float newRotation = startCardRotation + (diffX / sensitivity);
                        cvShowcaseCard.setRotationY(newRotation);
                        
                        if (pseudoGlow != null) pseudoGlow.setRotationY(newRotation);
                        
                        checkFaceSwap(newRotation);
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                        snapCardPosition();
                        return true;
                }
                return handled;
            });
        }
    }

    /**
     * Opens the standard object selector (same as Upgrade/Spin)
     */
    private void openCardSelector() {
        if (getContext() == null) return;
        
        InventoryBottomSheet bottomSheet = new InventoryBottomSheet();

        bottomSheet.setOnObjetSelectedListener(card -> {
            // Cập nhật thẻ bài Showcase mới
            if (card != null) {
                heroObjet = card;
                
                // QUAN TRỌNG: Lưu ID thẻ vừa chọn vào Session để không bị Reset khi chuyển Tab
                if (sessionManager != null) {
                    sessionManager.setSelectedShowcaseId(card.getId());
                }

                // Tìm kiếm trong kho đồ để lấy đầy đủ thông tin (Back image, OVR...)
                if (DatabaseLoader.cachedUserInventory != null) {
                    for (DatabaseLoader.UserInventoryItem item : DatabaseLoader.cachedUserInventory) {
                        if (item.id != null && item.id.intValue() == card.getId()) {
                            if (getContext() != null) {
                                heroCardJson = DatabaseLoader.findById(getContext(), item.collectionId);
                                heroBackImageUrl = heroCardJson != null ? heroCardJson.optString("backImage", "") : "";
                            }
                            bindHeroCard(item);
                            bindHeaderBadges(item);
                            break;
                        }
                    }
                }
            }
        });

        bottomSheet.show(getParentFragmentManager(), "hero_card_selector");
    }

    /**
     * Mở hộp thoại chi tiết (Full Stats) cho thẻ Showcase.
     * Sử dụng ObjetDetailBinder để đảm bảo tính nhất quán trong giao diện thẻ.
     */
    private void openDetailDialog() {
        if (getContext() == null || heroObjet == null) return;
        try {
            ObjetDetailBinder.showObjetDetail(getContext(), heroObjet);
        } catch (Exception e) {
            Log.e(TAG, "Error opening detail dialog", e);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  HIỆU ỨNG LẬT THẺ 3D — Xoay 180° (Đã tối ưu cho vuốt)
    // ════════════════════════════════════════════════════════════════

    private void checkFaceSwap(float currentRotation) {
        float normalized = currentRotation % 360;
        if (normalized < 0) normalized += 360;
        // Face swap threshold boundary (90 to 270 is Back Face)
        boolean shouldBeFlipped = (normalized > 90 && normalized < 270);
        setCardFace(shouldBeFlipped);
    }

    private void snapCardPosition() {
        if (cvShowcaseCard == null) return;
        
        float currentRotation = cvShowcaseCard.getRotationY();
        float normalized = currentRotation % 360;
        if (normalized < 0) normalized += 360;

        float nearestAngle;
        if (normalized <= 90 || normalized >= 270) {
            // Nearest is Front (0, 360, etc)
            nearestAngle = Math.round(currentRotation / 360f) * 360f;
        } else {
            // Nearest is Back (180, 540, etc)
            nearestAngle = Math.round((currentRotation - 180f) / 360f) * 360f + 180f;
        }

        snapAnimator = ObjectAnimator.ofFloat(cvShowcaseCard, "rotationY", currentRotation, nearestAngle);
        // Snappy spring effect
        snapAnimator.setDuration(250); 
        snapAnimator.setInterpolator(new OvershootInterpolator(1.2f));
        
        View pseudoGlow = (View) cvShowcaseCard.getTag(R.id.view_progress_fill);
        if (pseudoGlow != null) {
            snapAnimator.addUpdateListener(animation -> pseudoGlow.setRotationY((float) animation.getAnimatedValue()));
        }
        
        snapAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                checkFaceSwap(cvShowcaseCard.getRotationY());
            }
        });
        snapAnimator.start();
    }

    private void setCardFace(boolean showBack) {
        if (isCardFlipped == showBack) return;
        if (ivShowcaseFront == null || ivShowcaseBack == null) return;

        isCardFlipped = showBack;

        if (!showBack) {
            // Reveal Front
            ivShowcaseFront.setVisibility(View.VISIBLE);
            ivShowcaseBack.setVisibility(View.GONE);
            if (tvShowcaseOvr != null) tvShowcaseOvr.setVisibility(View.GONE);
            if (ivLevelBadge != null && heroObjet != null && heroObjet.getUpgradeLevel() > 0) {
                ivLevelBadge.setVisibility(View.VISIBLE);
            }
            if (viewCardShimmer != null) viewCardShimmer.setVisibility(View.VISIBLE);
        } else {
            // Reveal Back
            ivShowcaseFront.setVisibility(View.GONE);
            ivShowcaseBack.setVisibility(View.VISIBLE);
            ivShowcaseBack.setScaleX(-1f); // Mirror fix
            ivShowcaseBack.setAlpha(1f);
            
            if (tvShowcaseOvr != null) tvShowcaseOvr.setVisibility(View.GONE);
            if (ivLevelBadge != null) ivLevelBadge.setVisibility(View.GONE);
            if (viewCardShimmer != null) viewCardShimmer.setVisibility(View.GONE);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  QUICK TOOLS — Horizontal Menu logic
    // ════════════════════════════════════════════════════════════════

    private void setupQuickToolActions() {
        if (btnQuickDaily != null) btnQuickDaily.setOnClickListener(v -> {
            startActivity(new android.content.Intent(getContext(), com.vn.jet.mosco.DailyCheckinActivity.class));
        });
        if (btnQuickEvent != null) btnQuickEvent.setOnClickListener(v -> {
            startActivity(new android.content.Intent(getContext(), com.vn.jet.mosco.MissionActivity.class));
        });
        if (btnQuickUpgrade != null) btnQuickUpgrade.setOnClickListener(v -> navigateToTab(R.id.nav_stage));
        if (btnQuickRank != null) btnQuickRank.setOnClickListener(v -> {
            startActivity(new android.content.Intent(getContext(), com.vn.jet.mosco.RankActivity.class));
        });
        if (btnQuickFriends != null) btnQuickFriends.setOnClickListener(v -> {
            startActivity(new android.content.Intent(getContext(), com.vn.jet.mosco.FriendActivity.class));
        });
        if (btnQuickFormation != null) {
            btnQuickFormation.setOnClickListener(v -> {
                startActivity(new android.content.Intent(getContext(), com.vn.jet.mosco.FormationActivity.class));
            });
        }
        if (btnQuickGift != null) {
            btnQuickGift.setOnClickListener(v -> {
                startActivity(new android.content.Intent(getContext(), com.vn.jet.mosco.GiftActivity.class));
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

    /**
     * Tự động tính toán chiều rộng cho các Quick Tool item để hiển thị đúng 5 item trên màn hình.
     * Đảm bảo trải nghiệm cuộn ngang mượt mà và cân đối.
     */
    private void setupQuickToolDimensions() {
        if (hsvQuickTools == null || llQuickToolsContainer == null) return;

        hsvQuickTools.post(() -> {
            if (!isAdded() || getContext() == null) return;

            int hsvWidth = hsvQuickTools.getMeasuredWidth();
            // Trừ đi padding của HorizontalScrollView (Glass container)
            int horizontalPadding = hsvQuickTools.getPaddingLeft() + hsvQuickTools.getPaddingRight();
            int availableWidth = hsvWidth - horizontalPadding;

            // Chia cho 5 để lúc nào cũng thấy đúng 5 nút
            int itemWidth = availableWidth / 5;

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

    // ════════════════════════════════════════════════════════════════
    //  BANNER CAROUSEL — ViewPager2 with auto-scroll
    // ════════════════════════════════════════════════════════════════

    private void setupBannerCarousel() {
        if (vpBanners == null || getContext() == null) return;

        try {
            // Use the existing banner images from drawable (ads1, ads2, ads3)
            int[] bannerResIds = {R.drawable.ads1, R.drawable.ads2, R.drawable.ads3};
            bannerCount = bannerResIds.length;

            vpBanners.setAdapter(new BannerPagerAdapter(bannerResIds));
            vpBanners.setOffscreenPageLimit(bannerCount);

            // Build dot indicators
            buildDotIndicators(bannerCount);

            // Khởi tạo ở vị trí giữa dải Integer.MAX_VALUE để sếp có thể vuốt trái/phải vô tận ngay từ đầu
            int middlePos = (Integer.MAX_VALUE / 2) - ((Integer.MAX_VALUE / 2) % bannerCount);
            vpBanners.setCurrentItem(middlePos, false);

            // Listen for page changes to update dots
            vpBanners.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    updateDotIndicators(position % bannerCount);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up banner carousel", e);
        }
    }

    private void buildDotIndicators(int count) {
        if (llBannerDots == null || getContext() == null) return;
        llBannerDots.removeAllViews();

        int dotSize = getResources().getDimensionPixelSize(R.dimen.home_dot_size);
        int dotSpacing = getResources().getDimensionPixelSize(R.dimen.home_dot_spacing);

        for (int i = 0; i < count; i++) {
            View dot = new View(getContext());
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
            if (dot != null) {
                dot.setBackgroundResource(
                        i == activePosition ? R.drawable.bg_dot_active : R.drawable.bg_dot_inactive);
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
                    int nextItem = vpBanners.getCurrentItem() + 1;
                    vpBanners.setCurrentItem(nextItem, true);
                    bannerHandler.postDelayed(this, BANNER_AUTO_SCROLL_DELAY);
                }
            }
        };
        bannerHandler.postDelayed(bannerRunnable, BANNER_AUTO_SCROLL_DELAY);
    }

    private void stopBannerAutoScroll() {
        if (bannerRunnable != null) {
            bannerHandler.removeCallbacks(bannerRunnable);
        }
    }

    /**
     * Heartbeat pulse animation for the avatar glow ring.
     */
    private void startAvatarPulse() {
        if (viewAvatarGlow == null) return;
        
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(viewAvatarGlow, "scaleX", 1f, 1.2f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(viewAvatarGlow, "scaleY", 1f, 1.2f, 1f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(viewAvatarGlow, "alpha", 0.1f, 0.4f, 0.1f);
        
        AnimatorSet pulse = new AnimatorSet();
        pulse.playTogether(scaleX, scaleY, alpha);
        pulse.setDuration(2000);
        pulse.setStartDelay(500);
        pulse.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (isAdded()) pulse.start();
            }
        });
        pulse.start();
    }

    /**
     * Minimal RecyclerView.Adapter for the ViewPager2 banner carousel.
     * Each page is a single ImageView displaying a drawable banner resource.
     */
    private static class BannerPagerAdapter
            extends androidx.recyclerview.widget.RecyclerView.Adapter<BannerPagerAdapter.VH> {

        private final int[] resIds;

        BannerPagerAdapter(int[] resIds) {
            this.resIds = resIds;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView iv = new ImageView(parent.getContext());
            iv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            return new VH(iv);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            int actualPos = position % resIds.length;
            ((ImageView) holder.itemView).setImageResource(resIds[actualPos]);
        }

        @Override
        public int getItemCount() {
            return Integer.MAX_VALUE; // Circular Infinite Loop
        }

        static class VH extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            VH(@NonNull View itemView) {
                super(itemView);
            }
        }
    }

    /**
     * Navigates to a specific bottom navigation tab by its menu item ID.
     */
    private void navigateToTab(int navItemId) {
        if (getActivity() instanceof MainActivity) {
            BottomNavigationView nav = getActivity().findViewById(R.id.bottom_navigation);
            if (nav != null) {
                nav.setSelectedItemId(navItemId);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  DATA LOADING — User stats + Hero card
    // ════════════════════════════════════════════════════════════════

    private void loadUserData() {
        if (sessionManager == null || gameApiService == null) return;

        try {
            // Ưu tiên hiển thị Display Name, fallback về username
            String displayName = sessionManager.getIngameName();
            if (displayName == null || displayName.isEmpty()) {
                displayName = sessionManager.getUsername();
            }
            if (tvUsername != null) {
                tvUsername.setText(displayName != null ? displayName
                        : getString(R.string.home_default_username));
                tvUsername.setSelected(true); // Kích hoạt hiệu ứng Chữ chạy (Marquee)
            }

            // --- 🎭 SYNC AVATAR LOGIC ---
            if (ivHomeAvatar != null) {
                String avatarId = sessionManager.getAvatarId();
                if (avatarId == null) avatarId = "1";
                
                org.json.JSONObject card = DatabaseLoader.findByCollectionId(requireContext(), avatarId);
                if (card != null) {
                    String imgUrl = card.optString("frontImage");
                    Glide.with(this)
                            .load(imgUrl)
                            .transform(new com.vn.jet.mosco.utils.SmartFaceCropTransformation())
                            .placeholder(R.drawable.ic_user)
                            .error(R.drawable.ic_user)
                            .into(ivHomeAvatar);
                } else {
                    ivHomeAvatar.setImageResource(R.drawable.ic_user);
                }
            }

            // Fetch live stats from server for currency and level
            Long userId = sessionManager.getUserId();
            if (userId == null) return;

            // Set Display User ID (10,000,000 + DB ID)
            long displayId = 10000000L + userId;
            if (tvUserId != null) tvUserId.setText("ID: " + displayId);
            if (layoutUserId != null) {
                layoutUserId.setOnClickListener(v_id -> {
                    copyToClipboard(String.valueOf(displayId));
                });
            }

            gameApiService.getUserStats(userId).enqueue(new Callback<UserStats>() {
                @Override
                public void onResponse(Call<UserStats> call, Response<UserStats> response) {
                    if (!isAdded() || getContext() == null) return;
                    if (response.isSuccessful() && response.body() != null) {
                        UserStats stats = response.body();
                        bindCurrency(stats.getCoins(), stats.getDiamonds());
                        if (tvLevel != null) {
                            tvLevel.setText("LV. " + stats.getLevel());
                        }
                    }
                }

                @Override
                public void onFailure(Call<UserStats> call, Throwable t) {
                    Log.e(TAG, "Failed to fetch user stats", t);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error loading user data", e);
        }
    }

    /**
     * Định dạng và hiển thị tài nguyên (Tiền/Kim cương) với logic rút gọn số.
     * Giúp giao diện luôn gọn gàng ngay cả khi người dùng là "đại gia".
     */
    private void bindCurrency(Long coins, Long diamonds) {
        if (tvCoins != null) {
            long c = coins != null ? coins : 0;
            tvCoins.setText(com.vn.jet.mosco.utils.NumberUtils.format(getContext(), c));
        }
        if (tvDiamonds != null) {
            long d = diamonds != null ? diamonds : 0;
            tvDiamonds.setText(com.vn.jet.mosco.utils.NumberUtils.format(getContext(), d));
        }
    }




    /**
     * Finds the user's highest-OVR card from the global inventory cache
     * and displays it as the "Hero Card" in the showcase centerpiece.
     *
     * Fallback: If inventory is empty, shows the empty state overlay.
     */
    private void loadHeroShowcase() {
        if (getContext() == null) return;

        try {
            Long currentUid = sessionManager.getUserId();
            List<DatabaseLoader.UserInventoryItem> inventory = DatabaseLoader.cachedUserInventory;

            // Kiểm tra tính hợp lệ của Cache: Chỉ dùng nếu ID trong cache khớp với ID người dùng hiện tại
            if (inventory != null && DatabaseLoader.cachedInventoryUserId != null && 
                !DatabaseLoader.cachedInventoryUserId.equals(currentUid)) {
                inventory = null; // ID không khớp -> Buộc phải reload từ server
            }

            if (inventory == null) {
                // Dữ liệu chưa từng được tải (lần đầu vào App hoặc sau khi logout/login)
                if (sessionManager != null && gameApiService != null) {
                    Long uid = sessionManager.getUserId();
                    if (uid != null) {
                        setShowcaseLoading(true, "Đang đồng bộ...");
                        DatabaseLoader.reloadInventoryFromServer(requireContext(), uid, gameApiService);
                        return;
                    }
                }
            }

            if (inventory == null || inventory.isEmpty()) {
                // Thực sự không có thẻ bài nào (hoặc nạp cache thất bại)
                setShowcaseLoading(false, null);
                bindHeaderBadges(null);
                showEmptyState(true);
                return;
            }

            // LOGIC SHOWCASE: Ưu tiên nạp thẻ sếp đã chọn (Lưu trong Session)
            DatabaseLoader.UserInventoryItem bestCard = null;
            long selectedId = sessionManager.getSelectedShowcaseId();
            
            if (selectedId != -1L) {
                // Sục sạo tìm đúng thẻ đã chọn trong danh sách inventory của sếp
                for (DatabaseLoader.UserInventoryItem item : inventory) {
                    if (item.id != null && item.id == selectedId) {
                        bestCard = item;
                        break;
                    }
                }
            }
            
            // FALLBACK: Nếu không tìm thấy thẻ chọn cũ (VD: bị recycle), thì mới lấy thẻ mạnh nhất (OVR cao nhất)
            if (bestCard == null) {
                for (DatabaseLoader.UserInventoryItem item : inventory) {
                    if (bestCard == null || item.ovr > bestCard.ovr) {
                        bestCard = item;
                    }
                }
                // Tự động lưu luôn cái thằng mạnh nhất này vào làm mặc định
                if (bestCard != null && bestCard.id != null) {
                    sessionManager.setSelectedShowcaseId(bestCard.id);
                }
            }

            if (bestCard == null) {
                bindHeaderBadges(null);
                showEmptyState(true);
                return;
            }

            showEmptyState(false);

            // Build the Objet model from cached inventory data
            heroObjet = new Objet(
                    bestCard.id != null ? bestCard.id.intValue() : 0,
                    bestCard.collectionId,
                    bestCard.frontImage,
                    bestCard.level,
                    bestCard.exp,
                    bestCard.upgradeLevel
            );
            heroObjet.setOvr(bestCard.ovr);
            heroObjet.setMember(bestCard.member);
            heroObjet.setSeason(bestCard.season);
            heroObjet.setTypeKey(bestCard.cardClass);
            heroObjet.setBackImageUrl(bestCard.backImage);
            heroObjet.setCollectionNo(bestCard.collectionNo);
            heroObjet.setSlug(bestCard.slug);
            heroObjet.setBackgroundColor(bestCard.backgroundColor);
            heroObjet.setTextColor(bestCard.textColor);
            heroObjet.setAvailableTags(bestCard.availableTags);
            heroObjet.setDimension(bestCard.dimension);

            heroBackImageUrl = bestCard.backImage;

            bindHeroCard(bestCard);
            bindHeaderBadges(bestCard);

        } catch (Exception e) {
            Log.e(TAG, "Error loading hero showcase", e);
            showEmptyState(true);
        }
    }

    private void showEmptyState(boolean show) {
        if (llEmptyState != null) {
            llEmptyState.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (cvShowcaseCard != null) {
            if (show) {
                // Xóa bỏ ảnh cũ nếu có
                if (ivShowcaseFront != null) Glide.with(this).clear(ivShowcaseFront);
                if (ivShowcaseBack != null) Glide.with(this).clear(ivShowcaseBack);
                if (tvShowcaseOvr != null) tvShowcaseOvr.setVisibility(View.GONE);
                if (ivLevelBadge != null) ivLevelBadge.setVisibility(View.GONE);
                if (viewCardShimmer != null) viewCardShimmer.setVisibility(View.GONE);
                
                // Hiển thị khung với hiệu ứng Glow tím nhạt Placeholder + Floating
                cvShowcaseCard.setVisibility(View.VISIBLE);
                com.vn.jet.mosco.utils.CardEffectHelper.applyEmptyStateGlow(cvShowcaseCard, true);
                
                // Tắt beam sáng (Projector) trong khi empty
                if (viewProjectorBeam != null) viewProjectorBeam.setAlpha(0f);
            } else {
                cvShowcaseCard.setVisibility(View.VISIBLE);
            }
        }
    }

    /**
     * Loads the hero card's front image into the showcase using the
     * "Local Thumbnail First" strategy: show 2x cached image instantly,
     * then crossfade to 4x network image when it arrives.
     */
    private void bindHeroCard(DatabaseLoader.UserInventoryItem card) {
        if (ivShowcaseFront == null || getContext() == null) return;

        try {
            String frontImageUrl = card.frontImage;
            if (frontImageUrl == null || frontImageUrl.isEmpty()) {
                setShowcaseLoading(false, null);
                return;
            }
            setShowcaseLoading(true, "Đang tải Showcase...");
            if (cvShowcaseCard != null) {
                cvShowcaseCard.setEnabled(false);
            }

            heroBackImageUrl = card.backImage;

            pendingShowcaseAssetLoads = 1;

            if (showcaseLoadingTimeoutRunnable != null) {
                showcaseLoadingHandler.removeCallbacks(showcaseLoadingTimeoutRunnable);
            }
            showcaseLoadingTimeoutRunnable = () -> {
                pendingShowcaseAssetLoads = 0;
                setShowcaseLoading(false, null);
                if (cvShowcaseCard != null) {
                    cvShowcaseCard.setEnabled(true);
                }
            };
            showcaseLoadingHandler.postDelayed(showcaseLoadingTimeoutRunnable, 1200);

            Glide.with(this).clear(ivShowcaseFront);
            if (ivShowcaseBack != null) {
                Glide.with(this).clear(ivShowcaseBack);
            }

            // Local Thumbnail First strategy — identical to ObjetDetailBinder
            final File localThumb = CardAssetManager.getLocalFile(getContext(), frontImageUrl);

            // Try to load from memory cache first (GPU-preloaded from SplashActivity)
            // If found, display instantly without animation for instant showcase
            Glide.with(this)
                    .load(frontImageUrl)
                    .onlyRetrieveFromCache(true)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            // Not in cache, fall back to normal loading with thumbnail on UI thread queue
                            if (ivShowcaseFront != null) {
                                ivShowcaseFront.post(() -> loadShowcaseWithThumbnail(frontImageUrl, localThumb));
                            }
                            return true; // Prevent error callback since we're handling it
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                            // GPU-preloaded image found! Display instantly
                            ivShowcaseFront.setImageDrawable(resource);
                            onShowcaseAssetReady();
                            return true;
                        }
                    })
                    .into(ivShowcaseFront);

            // Reset flip state when loading a new card
            isCardFlipped = false;
            if (ivShowcaseBack != null) {
                ivShowcaseBack.setVisibility(View.GONE);
                ivShowcaseBack.setScaleX(1f); // Reset mirror fix
                ivShowcaseBack.setImageResource(R.drawable.objet_back_spin);
                ivShowcaseBack.setAlpha(1f);
            }
            if (ivShowcaseFront != null) ivShowcaseFront.setVisibility(View.VISIBLE);
            if (cvShowcaseCard != null) cvShowcaseCard.setRotationY(0f);
            
            // Also reset glow rotation
            View pseudoGlow = (View) cvShowcaseCard.getTag(R.id.view_progress_fill);
            if (pseudoGlow != null) pseudoGlow.setRotationY(0f);

            // Load actual back image URL from card metadata
            if (ivShowcaseBack != null) {
                if (heroBackImageUrl != null && !heroBackImageUrl.isEmpty()) {
                    File localBackThumb = CardAssetManager.getLocalFile(getContext(), heroBackImageUrl);
                    com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> backThumb = null;
                    if (localBackThumb != null && localBackThumb.exists()) {
                        backThumb = Glide.with(this).load(localBackThumb);
                    }
                    Glide.with(this)
                            .load(heroBackImageUrl)
                            .thumbnail(backThumb)
                            .placeholder(R.drawable.objet_back_spin)
                            .error(R.drawable.objet_back_spin)
                            .dontAnimate()
                            .listener(new RequestListener<Drawable>() {
                                @Override
                                public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                                    return false;
                                }

                                @Override
                                public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                                    return false;
                                }
                            })
                            .into(ivShowcaseBack);
                }
            }

            // Bind OVR text overlay
            if (tvShowcaseOvr != null) {
                tvShowcaseOvr.setText(String.valueOf(card.ovr));
                tvShowcaseOvr.setVisibility(View.GONE);
            }

            // Bind Level/Grade badge
            if (ivLevelBadge != null) {
                if (card.upgradeLevel > 0) {
                    String assetPath = "file:///android_asset/grade/" + card.upgradeLevel + ".png";
                    Glide.with(this).load(assetPath).into(ivLevelBadge);
                    ivLevelBadge.setVisibility(View.VISIBLE);
                    LevelBadgeEffectHelper.apply(ivLevelBadge, card.upgradeLevel);
                } else {
                    ivLevelBadge.setVisibility(View.GONE);
                    com.vn.jet.mosco.utils.LevelBadgeEffectHelper.remove(ivLevelBadge);
                }
            }

            // Apply high-end sacrifice card style (Shimmer + TriplesBorder Mask + Glow)
            com.vn.jet.mosco.utils.CardEffectHelper.apply(cvShowcaseCard, viewCardShimmer, heroObjet, true);

            // Projector Beam turn on
            if (viewProjectorBeam != null) {
                viewProjectorBeam.setAlpha(0f);
                viewProjectorBeam.animate().alpha(1f).setDuration(1500).setStartDelay(200).start();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error binding hero card", e);
            setShowcaseLoading(false, null);
            if (cvShowcaseCard != null) {
                cvShowcaseCard.setEnabled(true);
            }
        }
    }

    /**
     * Fallback loading method when GPU-preloaded cache miss occurs.
     * Uses thumbnail strategy for faster perceived loading.
     */
    private void loadShowcaseWithThumbnail(String frontImageUrl, File localThumb) {
        com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> thumbRequest = null;
        if (localThumb != null && localThumb.exists()) {
            thumbRequest = Glide.with(this).load(localThumb);
        }

        Glide.with(this)
                .load(frontImageUrl)
                .thumbnail(thumbRequest)
                .placeholder(R.drawable.item_shop_demo)
                .error(R.drawable.item_shop_demo)
                .transition(DrawableTransitionOptions.withCrossFade(200))
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        onShowcaseAssetReady();
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                        onShowcaseAssetReady();
                        return false;
                    }
                })
                .into(ivShowcaseFront);
    }

    private void onShowcaseAssetReady() {
        pendingShowcaseAssetLoads = Math.max(0, pendingShowcaseAssetLoads - 1);
        if (pendingShowcaseAssetLoads == 0) {
            if (showcaseLoadingTimeoutRunnable != null) {
                showcaseLoadingHandler.removeCallbacks(showcaseLoadingTimeoutRunnable);
            }
            setShowcaseLoading(false, null);
            if (cvShowcaseCard != null) {
                cvShowcaseCard.setEnabled(true);
            }
        }
    }

    private void setShowcaseLoading(boolean show, @Nullable String message) {
        if (layoutShowcaseLoading != null) {
            layoutShowcaseLoading.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (tvShowcaseLoading != null && message != null) {
            tvShowcaseLoading.setText(message);
        }
    }

    /**
     * Cập nhật các Badge Level và OVR ở Header dựa trên thẻ bài mạnh nhất.
     * Đã refactor để xóa bỏ các ký tự thừa và lặp nhãn theo yêu cầu UI/UX.
     */
    private void bindHeaderBadges(DatabaseLoader.UserInventoryItem card) {
        try {
            if (card == null) {
                // Tân thủ hoặc rương trống
                if (tvLevel != null) tvLevel.setText(getString(R.string.home_default_level));
                if (tvOvr != null) tvOvr.setText(getString(R.string.home_default_ovr));
                return;
            }
            
            if (tvLevel != null) {
                tvLevel.setText(getString(R.string.home_format_level, card.level));
            }
            if (tvOvr != null) {
                tvOvr.setText(getString(R.string.home_format_ovr, card.ovr));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error binding header badges", e);
        }
    }

    private void copyToClipboard(String text) {
        if (getContext() == null) return;
        try {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                    getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText(getString(R.string.home_msg_copy_clipboard_label), text);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                android.widget.Toast.makeText(getContext(), getString(R.string.home_toast_copy_success, text), android.widget.Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to copy to clipboard", e);
        }
    }
}
