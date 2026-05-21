package com.vn.jet.mosco.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.CompositePageTransformer;
import androidx.viewpager2.widget.MarginPageTransformer;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import android.widget.EditText;
import com.vn.jet.mosco.ForgotPasswordActivity;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.MainActivity;
import com.vn.jet.mosco.SignInActivity;
import com.vn.jet.mosco.model.Objet;
import com.vn.jet.mosco.model.UserStats;
import com.vn.jet.mosco.network.ApiClient;
import com.vn.jet.mosco.network.GameApiService;
import com.vn.jet.mosco.utils.CardEffectHelper;
import com.vn.jet.mosco.utils.DatabaseLoader;
import com.vn.jet.mosco.utils.LevelBadgeEffectHelper;
import com.vn.jet.mosco.utils.NumberUtils;
import com.vn.jet.mosco.utils.SessionManager;
import com.vn.jet.mosco.ProfileViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.Transformations;
import android.view.ViewStub;

import org.json.JSONObject;
import com.vn.jet.mosco.utils.SmartFaceCropTransformation;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.vn.jet.mosco.utils.BackupManager;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * ProfileFragment — Quản lý hồ sơ, tài sản, và hành động tài khoản.
 * V6.3: Refactored with compact layout & right-aligned Exhibit Carousel.
 */
public class ProfileFragment extends Fragment implements AvatarSelectorBottomSheet.OnAvatarSelectedListener {

    private static final String TAG = "ProfileFragment";
    private static final long MENU_DEBOUNCE_MS = 500;
    public static final String ARG_TARGET_USER_ID = "target_user_id";

    private TextView tvUsername, tvLevel, tvCurrentTitle;
    private ImageView ivAvatar;
    private View avatarCard, btnMenu, btnBack;
    private View btnEditMode, viewAvatarDim;
    private ImageView ivAvatarEditIcon;
    private TextView tvPreviewHeaderLabel;
    private boolean isEditMode = false;

    public boolean isEditMode() {
        return isEditMode;
    }

    private View previewHeader, blockingOverlay;
    private View btnPreviewCancel, btnPreviewConfirm;
    private View layoutProfileContent;
    private ViewStub stubShimmer;
    private View inflatedShimmer;
    private TextView tvStatLikes, tvStatFriends;
    private View tabSlidingThumb;
    private com.google.android.material.tabs.TabLayout tabLayout;
    private androidx.viewpager2.widget.ViewPager2 viewPager;
    private SessionManager sessionManager;
    private GameApiService gameApiService;
    private ProfileViewModel viewModel;

    // Exhibit Showcase Area (Cột phải Header)
    private ViewPager2 vpShowcase;
    private ShowcasePagerAdapter showcaseAdapter;
    private List<String> currentShowcaseIds = new ArrayList<>();
    private static final int SHOWCASE_COUNT = 8;
    private boolean isExhibitEditMode = false;
    private android.os.Handler carouselHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable carouselRunnable;
    private long lastShowcaseClickTime = 0;

    private Long targetUserId;
    private boolean isOwner;
    private String lastImageUrl;
    private Uri lastCroppedUri;
    private long lastMenuClickTime = 0;
    // Lưu trạng thái avatar gốc để rollback khi Discard
    private String savedAvatarIdBeforeEdit;

    // Phase 2: Backup & Export Launchers
    private ActivityResultLauncher<Intent> exportLauncher;
    private ActivityResultLauncher<Intent> importLauncher;

    public ProfileFragment() {
        // Constructor mặc định cho Fragment
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize Export Launcher
        exportLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            boolean success = BackupManager.exportDatabase(requireContext(), uri);
                            if (success) {
                                Toast.makeText(requireContext(), "✅ Backup Created Successfully!", Toast.LENGTH_LONG)
                                        .show();
                            } else {
                                Toast.makeText(requireContext(), "❌ Backup Failed. Please try again.",
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    }
                });

        // Initialize Import Launcher
        importLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            boolean success = BackupManager.restoreDatabase(requireContext(), uri);
                            if (success) {
                                Toast.makeText(requireContext(), "✅ Restore Successful!", Toast.LENGTH_SHORT).show();
                                com.vn.jet.mosco.utils.MoscoDialogHelper.showInfoDialog(
                                        getActivity(),
                                        "Restore Complete",
                                        "Data has been restored. The application will now restart.",
                                        "Restart App",
                                        () -> System.exit(0));
                            } else {
                                Toast.makeText(requireContext(), "❌ Restore Failed. File might be corrupted.",
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    }
                });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        sessionManager = new SessionManager(requireContext());
        handleArguments();

        initViews(view);
        setupViewModel();
        setupProfileRouting(view);

        // Khởi tạo GameApiService dùng chung cho cả Owner và Guest để tránh
        // NullPointerException khi bấm Like/Friend
        gameApiService = ApiClient.getClient(requireContext()).create(GameApiService.class);

        if (isOwner) {
            setupSession();
        }

        setupListeners();

        return view;
    }

    private io.reactivex.disposables.Disposable notificationSubscription;

    private void handleArguments() {
        Bundle args = getArguments();
        Long currentUserId = sessionManager != null ? sessionManager.getUserId() : null;

        if (args != null) {
            if (args.containsKey(ARG_TARGET_USER_ID)) {
                long rawId = args.getLong(ARG_TARGET_USER_ID, -1L);
                if (rawId != -1L) {
                    targetUserId = rawId;
                }
            }
        }

        // Nếu targetUserId vẫn null, xem như là chính mình
        if (targetUserId == null) {
            targetUserId = currentUserId;
        }

        if (targetUserId == null) {
            Log.e(TAG, "Root Cause Error: currentUserId and targetUserId are both null!");
        }

        // Nếu không truyền ID hoặc ID khớp với User hiện tại -> Là Owner
        isOwner = (targetUserId != null && targetUserId.equals(currentUserId));
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        viewModel.setUserId(targetUserId);

        // Hiển thị Shimmer mặc định nếu chưa có dữ liệu trong cache
        showShimmer(true);

        // Quan sát dữ liệu Profile với cơ chế Local-First
        Transformations.distinctUntilChanged(viewModel.getUserStats()).observe(getViewLifecycleOwner(), stats -> {
            if (stats != null) {
                showShimmer(false);
                renderProfileData(stats);
                renderShowcaseData(stats);
            }
        });
    }

    private void showShimmer(boolean show) {
        if (show) {
            if (layoutProfileContent != null)
                layoutProfileContent.setVisibility(View.GONE);
            if (btnMenu != null)
                btnMenu.setVisibility(View.GONE);
            if (btnBack != null)
                btnBack.setVisibility(View.GONE);
            if (btnEditMode != null)
                btnEditMode.setVisibility(View.GONE);

            if (inflatedShimmer == null && stubShimmer != null) {
                if (!isOwner) {
                    stubShimmer.setLayoutResource(R.layout.layout_profile_guest_shimmer);
                }
                inflatedShimmer = stubShimmer.inflate();
            }
            if (inflatedShimmer != null)
                inflatedShimmer.setVisibility(View.VISIBLE);
        } else {
            if (inflatedShimmer != null)
                inflatedShimmer.setVisibility(View.GONE);
            if (layoutProfileContent != null)
                layoutProfileContent.setVisibility(View.VISIBLE);

            // Hiện lại nút điều hướng
            if (isOwner && btnMenu != null)
                btnMenu.setVisibility(View.VISIBLE);
            if (btnBack != null)
                btnBack.setVisibility(View.VISIBLE);
            if (isOwner && btnEditMode != null)
                btnEditMode.setVisibility(View.VISIBLE);
        }
    }

    private void setupProfileRouting(View view) {
        if (isOwner) {
            if (btnMenu != null)
                btnMenu.setVisibility(View.VISIBLE);
            if (btnEditMode != null)
                btnEditMode.setVisibility(View.VISIBLE);
        } else {
            ViewStub stub = view.findViewById(R.id.stub_guest_actions);
            if (stub != null) {
                View inflated = stub.inflate();
                setupGuestListeners(inflated);
            }
            // Guest không được mở menu hệ thống hoặc chỉnh sửa
            if (btnMenu != null)
                btnMenu.setVisibility(View.GONE);
            if (btnEditMode != null)
                btnEditMode.setVisibility(View.GONE);
        }
    }

    private void renderProfileData(com.vn.jet.mosco.model.UserStats stats) {
        tvUsername.setText(stats.getIngameName() != null ? stats.getIngameName() : stats.getUsername());
        tvLevel.setText(getString(R.string.format_level_short, stats.getLevel()));

        if (tvCurrentTitle != null) {
            tvCurrentTitle
                    .setText(stats.getCurrentTitle() != null && !stats.getCurrentTitle().isEmpty() ? stats.getCurrentTitle()
                            : getString(R.string.profile_title_default));
        }

        // [PHASE 7] Update Profile Stats
        if (isAdded() && tvStatLikes != null && getContext() != null) {
            String likesStr = com.vn.jet.mosco.utils.NumberUtils.format(getContext(), stats.getLikesCount()) + " "
                    + getString(R.string.profile_label_likes);
            tvStatLikes.setText(likesStr);
        }
        if (isAdded() && tvStatFriends != null && getContext() != null) {
            String friendsStr = com.vn.jet.mosco.utils.NumberUtils.format(getContext(), stats.getFriendsCount()) + " "
                    + getString(R.string.profile_label_friends);
            tvStatFriends.setText(friendsStr);
        }

        // Load avatar từ URL trong stats nếu có
        if (stats.getAvatarId() != null) {
            loadAvatar(stats.getAvatarId(), stats.getAvatarCropParams());
        }
    }

    private void loadAvatarById(String avatarId) {
        com.vn.jet.mosco.utils.AvatarUtils.loadAvatar(getContext(), ivAvatar, targetUserId, avatarId);
    }

    private void initViews(View v) {
        tvUsername = v.findViewById(R.id.tv_username);
        tvLevel = v.findViewById(R.id.tv_level);
        btnMenu = v.findViewById(R.id.btn_menu);
        ivAvatar = v.findViewById(R.id.iv_avatar);
        avatarCard = v.findViewById(R.id.avatar_card);
        btnBack = v.findViewById(R.id.btn_back);

        // Edit Mode Views
        btnEditMode = v.findViewById(R.id.btn_edit_mode);
        viewAvatarDim = v.findViewById(R.id.view_avatar_dim);
        ivAvatarEditIcon = v.findViewById(R.id.iv_avatar_edit_icon);

        // [PHASE 5] Preview / Edit Mode Header
        previewHeader = v.findViewById(R.id.layout_preview_header);
        blockingOverlay = v.findViewById(R.id.view_blocking_overlay);
        btnPreviewCancel = v.findViewById(R.id.btn_preview_cancel);
        btnPreviewConfirm = v.findViewById(R.id.btn_preview_confirm);
        tvPreviewHeaderLabel = v.findViewById(R.id.tv_preview_header_label);
        layoutProfileContent = v.findViewById(R.id.layout_profile_content);
        stubShimmer = v.findViewById(R.id.stub_profile_shimmer);
        tvCurrentTitle = null;
        tvStatLikes = v.findViewById(R.id.tv_stat_likes);
        tvStatFriends = v.findViewById(R.id.tv_stat_friends);
        tabSlidingThumb = v.findViewById(R.id.tab_sliding_thumb);
        tabLayout = v.findViewById(R.id.tab_layout);
        viewPager = v.findViewById(R.id.view_pager);

        setupViewPager();
        setupExhibitShowcase(v);
    }

    private void setupViewPager() {
        if (viewPager == null || tabLayout == null)
            return;

        // Lazy Loading mặc định (chỉ giữ 1 tab bên cạnh)
        viewPager.setOffscreenPageLimit(androidx.viewpager2.widget.ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT);

        com.vn.jet.mosco.adapter.ProfileViewPagerAdapter adapter = new com.vn.jet.mosco.adapter.ProfileViewPagerAdapter(
                this);
        viewPager.setAdapter(adapter);

        // [UX] Cho phép vuốt ngang ở ViewPager2 chính theo yêu cầu người dùng
        viewPager.setUserInputEnabled(true);

        new com.google.android.material.tabs.TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> {
                    switch (position) {
                        case 0:
                            tab.setText(R.string.profile_tab_general);
                            break;
                        case 1:
                            tab.setText(R.string.profile_tab_trophy);
                            break;
                    }
                }).attach();

        // Thiết lập sliding thumb cho Tab (giống Duration chip ở Stage)
        setupTabThumb();
    }

    /**
     * Đồng bộ indicator dạng pill cho TabLayout, di chuyển mượt mà giữa các tab.
     * Sử dụng View độc lập trong XML để tránh phá vỡ hierarchy của TabLayout.
     */
    private void setupTabThumb() {
        if (tabLayout == null || tabSlidingThumb == null)
            return;

        // [TIPS] Đợi layout sẵn sàng để lấy kích thước tab chính xác (tránh giá trị 0)
        tabLayout.post(() -> {
            updateTabThumb(0, false);
        });

        tabLayout.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                updateTabThumb(tab.getPosition(), true);
            }

            @Override
            public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {
            }
        });
    }

    private void updateTabThumb(int position, boolean animate) {
        if (tabLayout == null || tabLayout.getTabCount() == 0 || tabSlidingThumb == null)
            return;
        com.google.android.material.tabs.TabLayout.Tab tab = tabLayout.getTabAt(position);
        if (tab == null || tab.view == null)
            return;

        int tabLeft = tab.view.getLeft();
        int tabWidth = tab.view.getWidth();

        // Cập nhật chiều rộng thumb khớp với tab hiện tại
        android.view.ViewGroup.LayoutParams lp = tabSlidingThumb.getLayoutParams();
        if (lp != null) {
            lp.width = tabWidth;
            tabSlidingThumb.setLayoutParams(lp);
        }

        if (animate) {
            tabSlidingThumb.animate()
                    .translationX(tabLeft)
                    .setDuration(250) // 250ms thời gian vàng
                    .setInterpolator(new androidx.interpolator.view.animation.FastOutSlowInInterpolator())
                    .start();
        } else {
            tabSlidingThumb.setTranslationX(tabLeft);
        }
    }

    private void setupSession() {
        // Không tái khởi tạo sessionManager — đã init ở onCreateView
        if (getContext() == null)
            return;

        // Ưu tiên hiển thị Display Name, fallback về username
        String displayName = sessionManager.getIngameName();
        if (displayName == null || displayName.isEmpty()) {
            displayName = sessionManager.getUsername();
        }
        tvUsername.setText(displayName);

        loadAvatar();
    }

    private void loadAvatar() {
        if (sessionManager == null)
            return;
        loadAvatar(sessionManager.getAvatarId(), sessionManager.getAvatarCropParams());
    }

    private void loadAvatar(String avatarId, String cropParams) {
        // Null-safe: tránh NPE khi Fragment chưa attach hoặc đã bị detach
        if (!isAdded() || getContext() == null || ivAvatar == null)
            return;

        if (avatarId == null) {
            avatarId = getString(R.string.default_avatar_id);
        }
        com.vn.jet.mosco.utils.AvatarUtils.loadAvatar(getContext(), ivAvatar, targetUserId, avatarId, cropParams);
    }

    private void setupListeners() {
        if (btnMenu != null) {
            btnMenu.setOnClickListener(v -> openProfileMenu());
        }
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> handleBackAction());
        }
        avatarCard.setOnClickListener(v -> {
            // Khi đang ở Edit Mode, nhấn avatar sẽ mở chọn avatar mới
            if (isEditMode) {
                openAvatarPicker();
            } else {
                showAvatarZoomDialog();
            }
        });

        // Nút cây viết: kích hoạt Edit Mode
        if (btnEditMode != null) {
            btnEditMode.setOnClickListener(v -> enterEditMode());
        }

        // Nút X: nếu đang Edit Mode thì hiện dialog Discard
        if (btnPreviewCancel != null) {
            btnPreviewCancel.setOnClickListener(v -> {
                if (isEditMode) {
                    showDiscardDialog();
                }
            });
        }
        // Nút Tick: lưu thay đổi
        if (btnPreviewConfirm != null) {
            btnPreviewConfirm.setOnClickListener(v -> {
                if (isEditMode) {
                    saveEditChanges();
                }
            });
        }
    }

    private void handleBackAction() {
        if (getParentFragmentManager().getBackStackEntryCount() > 0) {
            if (getActivity() != null) {
                getActivity().getOnBackPressedDispatcher().onBackPressed();
            } else {
                getParentFragmentManager().popBackStack();
                com.vn.jet.mosco.utils.NavigationUtils.handleBackPress();
            }
        } else {
            // Nếu không có backstack (ví dụ mở từ tab), quay về Home
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).selectTab(R.id.nav_home);
            }
        }
    }

    private void setupGuestListeners(View v) {
        com.vn.jet.mosco.widget.MoscoButton btnLike = v.findViewById(R.id.btn_like);
        com.vn.jet.mosco.widget.MoscoButton btnFriend = v.findViewById(R.id.btn_add_friend);
        com.vn.jet.mosco.widget.MoscoButton btnMsg = v.findViewById(R.id.btn_direct_message);
        com.vn.jet.mosco.widget.MoscoButton btnDecline = v.findViewById(R.id.btn_decline_request);

        // Quan sát dữ liệu để cập nhật trạng thái nút
        viewModel.getUserStats().observe(getViewLifecycleOwner(), stats -> {
            if (stats == null)
                return;

            // Update Like Button (Cân bằng viền mờ sang trọng với nút Message)
            if (isAdded() && getContext() != null && btnLike != null) {
                if (stats.isLiked()) {
                    btnLike.setText(R.string.profile_btn_liked);
                    btnLike.setMoscoStyle(com.vn.jet.mosco.widget.MoscoButton.STYLE_GHOST);
                } else {
                    btnLike.setText(R.string.profile_btn_like);
                    btnLike.setMoscoStyle(com.vn.jet.mosco.widget.MoscoButton.STYLE_PRIMARY);
                }
            }

            // Update Friend Button & Decline Button
            if (isAdded() && getContext() != null && btnFriend != null) {
                if (btnDecline != null) {
                    btnDecline.setVisibility(stats.getFriendshipStatus() == 3 ? View.VISIBLE : View.GONE);
                }

                switch (stats.getFriendshipStatus()) {
                    case 3: // Nhận được lời mời -> Hiển thị nút Chấp nhận
                        btnFriend.setText(R.string.social_action_accept);
                        btnFriend.setMoscoStyle(com.vn.jet.mosco.widget.MoscoButton.STYLE_PRIMARY);
                        break;
                    case 1: // Pending (Đã gửi lời mời)
                        btnFriend.setText(R.string.profile_btn_pending);
                        btnFriend.setMoscoStyle(com.vn.jet.mosco.widget.MoscoButton.STYLE_WARNING);
                        break;
                    case 2: // Friends
                        btnFriend.setText(R.string.profile_btn_friends);
                        btnFriend.setMoscoStyle(com.vn.jet.mosco.widget.MoscoButton.STYLE_GHOST);
                        break;
                    default: // None
                        btnFriend.setText(R.string.profile_btn_add_friend);
                        btnFriend.setMoscoStyle(com.vn.jet.mosco.widget.MoscoButton.STYLE_PRIMARY);
                        break;
                }
            }
        });

        if (btnLike != null) {
            btnLike.setOnClickListener(new com.vn.jet.mosco.utils.ClickDebounce(500, view -> {
                UserStats stats = viewModel.getUserStats().getValue();
                if (stats != null && getContext() != null && targetUserId != null) {
                    // Tại sao: Áp dụng Optimistic UI để giao diện phản hồi lập tức, lưu trạng thái
                    // gốc để tự động Rollback nếu mạng lỗi
                    final boolean originalLiked = stats.isLiked();
                    final int originalLikesCount = stats.getLikesCount();

                    stats.setLiked(!originalLiked);
                    stats.setLikesCount(stats.isLiked() ? originalLikesCount + 1 : originalLikesCount - 1);

                    final android.content.Context appContext = getContext().getApplicationContext();
                    com.vn.jet.mosco.utils.AppExecutors.getInstance().diskIO().execute(() -> {
                        com.vn.jet.mosco.database.AppDatabase.getInstance(appContext)
                                .userStatsDao().insertUserStats(stats);
                    });

                    if (gameApiService != null) {
                        gameApiService.likeProfile(targetUserId)
                                .enqueue(
                                        new Callback<com.vn.jet.mosco.model.ApiResponse<java.util.Map<String, Object>>>() {
                                            @Override
                                            public void onResponse(
                                                    Call<com.vn.jet.mosco.model.ApiResponse<java.util.Map<String, Object>>> call,
                                                    Response<com.vn.jet.mosco.model.ApiResponse<java.util.Map<String, Object>>> response) {
                                                if (response.isSuccessful() && response.body() != null) {
                                                    java.util.Map<String, Object> data = response.body().getData();
                                                    if (data != null && data.containsKey("likesCount")) {
                                                        try {
                                                            int updatedLikes = ((Double) data.get("likesCount"))
                                                                    .intValue();
                                                            stats.setLikesCount(updatedLikes);
                                                            com.vn.jet.mosco.utils.AppExecutors.getInstance().diskIO()
                                                                    .execute(() -> {
                                                                        com.vn.jet.mosco.database.AppDatabase
                                                                                .getInstance(appContext)
                                                                                .userStatsDao().insertUserStats(stats);
                                                                    });
                                                        } catch (Exception ignored) {
                                                        }
                                                    }
                                                } else {
                                                    rollbackLikeAction(stats, originalLiked, originalLikesCount,
                                                            appContext);
                                                }
                                            }

                                            @Override
                                            public void onFailure(
                                                    Call<com.vn.jet.mosco.model.ApiResponse<java.util.Map<String, Object>>> call,
                                                    Throwable t) {
                                                rollbackLikeAction(stats, originalLiked, originalLikesCount,
                                                        appContext);
                                            }
                                        });
                    }
                }
            }));
        }

        if (btnFriend != null) {
            btnFriend.setOnClickListener(new com.vn.jet.mosco.utils.ClickDebounce(500, view -> {
                UserStats stats = viewModel.getUserStats().getValue();
                if (stats == null || getContext() == null || targetUserId == null)
                    return;

                if (stats.getFriendshipStatus() == 3) {
                    // Nhận được lời mời -> Bấm vào là Chấp nhận ngay
                    final int originalStatus = stats.getFriendshipStatus();
                    stats.setFriendshipStatus(2);
                    stats.setFriendsCount(stats.getFriendsCount() + 1);
                    final android.content.Context appContext = getContext().getApplicationContext();
                    com.vn.jet.mosco.utils.AppExecutors.getInstance().diskIO().execute(() -> {
                        com.vn.jet.mosco.database.AppDatabase.getInstance(appContext)
                                .userStatsDao().insertUserStats(stats);
                    });
                    Toast.makeText(getContext(), getString(R.string.common_msg_success), Toast.LENGTH_SHORT).show();

                    if (gameApiService != null) {
                        gameApiService.acceptFriendByUser(targetUserId)
                                .enqueue(new Callback<com.vn.jet.mosco.model.ApiResponse<Void>>() {
                                    @Override
                                    public void onResponse(Call<com.vn.jet.mosco.model.ApiResponse<Void>> call,
                                            Response<com.vn.jet.mosco.model.ApiResponse<Void>> response) {
                                        if (!response.isSuccessful()) {
                                            rollbackFriendStatus(stats, originalStatus, appContext);
                                        }
                                    }

                                    @Override
                                    public void onFailure(Call<com.vn.jet.mosco.model.ApiResponse<Void>> call,
                                            Throwable t) {
                                        rollbackFriendStatus(stats, originalStatus, appContext);
                                    }
                                });
                    }
                } else if (stats.getFriendshipStatus() == 2) {
                    showUnfriendDialog();
                } else if (stats.getFriendshipStatus() == 1) {
                    showCancelRequestDialog();
                } else {
                    // Tại sao: Cập nhật giao diện PENDING ngay lập tức để người dùng biết đã gửi
                    // yêu cầu
                    final int originalStatus = stats.getFriendshipStatus();
                    stats.setFriendshipStatus(1);
                    final android.content.Context appContext = getContext().getApplicationContext();
                    com.vn.jet.mosco.utils.AppExecutors.getInstance().diskIO().execute(() -> {
                        com.vn.jet.mosco.database.AppDatabase.getInstance(appContext)
                                .userStatsDao().insertUserStats(stats);
                    });
                    Toast.makeText(getContext(), getString(R.string.profile_msg_friend_request_sent),
                            Toast.LENGTH_SHORT).show();

                    if (gameApiService != null) {
                        java.util.Map<String, Long> body = new java.util.HashMap<>();
                        body.put("addresseeId", targetUserId);
                        gameApiService.addFriend(body).enqueue(new Callback<ResponseBody>() {
                            @Override
                            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                                if (!response.isSuccessful()) {
                                    rollbackFriendStatus(stats, originalStatus, appContext);
                                }
                            }

                            @Override
                            public void onFailure(Call<ResponseBody> call, Throwable t) {
                                rollbackFriendStatus(stats, originalStatus, appContext);
                            }
                        });
                    }
                }
            }));
        }

        btnMsg.setOnClickListener(new com.vn.jet.mosco.utils.ClickDebounce(500, view -> {
            com.vn.jet.mosco.model.UserStats targetStats = viewModel.getUserStats().getValue();
            String targetName = (targetStats != null && targetStats.getIngameName() != null)
                    ? targetStats.getIngameName()
                    : "Unknown";
            String targetAvatar = targetStats != null ? targetStats.getAvatarId() : "1";
            com.vn.jet.mosco.utils.NavigationUtils.openPrivateChat(requireActivity(), targetUserId, targetName, targetAvatar);
        }));


        if (btnDecline != null) {
            btnDecline.setOnClickListener(new com.vn.jet.mosco.utils.ClickDebounce(500, view -> {
                UserStats stats = viewModel.getUserStats().getValue();
                if (stats != null && getContext() != null && targetUserId != null) {
                    final int originalStatus = stats.getFriendshipStatus();
                    stats.setFriendshipStatus(0);
                    final android.content.Context appContext = getContext().getApplicationContext();
                    com.vn.jet.mosco.utils.AppExecutors.getInstance().diskIO().execute(() -> {
                        com.vn.jet.mosco.database.AppDatabase.getInstance(appContext)
                                .userStatsDao().insertUserStats(stats);
                    });

                    if (gameApiService != null) {
                        gameApiService.removeFriendByUser(targetUserId)
                                .enqueue(new Callback<com.vn.jet.mosco.model.ApiResponse<Void>>() {
                                    @Override
                                    public void onResponse(Call<com.vn.jet.mosco.model.ApiResponse<Void>> call,
                                            Response<com.vn.jet.mosco.model.ApiResponse<Void>> response) {
                                        if (!response.isSuccessful()) {
                                            rollbackFriendStatus(stats, originalStatus, appContext);
                                        }
                                    }

                                    @Override
                                    public void onFailure(Call<com.vn.jet.mosco.model.ApiResponse<Void>> call,
                                            Throwable t) {
                                        rollbackFriendStatus(stats, originalStatus, appContext);
                                    }
                                });
                    }
                }
            }));
        }

    }

    private void rollbackLikeAction(UserStats stats, boolean originalLiked, int originalLikesCount,
            android.content.Context appContext) {
        stats.setLiked(originalLiked);
        stats.setLikesCount(originalLikesCount);
        com.vn.jet.mosco.utils.AppExecutors.getInstance().diskIO().execute(() -> {
            com.vn.jet.mosco.database.AppDatabase.getInstance(appContext)
                    .userStatsDao().insertUserStats(stats);
        });
        if (isAdded() && getContext() != null) {
            Toast.makeText(getContext(), getString(R.string.common_error_network), Toast.LENGTH_SHORT).show();
        }
    }

    private void rollbackFriendStatus(UserStats stats, int originalStatus, android.content.Context appContext) {
        stats.setFriendshipStatus(originalStatus);
        com.vn.jet.mosco.utils.AppExecutors.getInstance().diskIO().execute(() -> {
            com.vn.jet.mosco.database.AppDatabase.getInstance(appContext)
                    .userStatsDao().insertUserStats(stats);
        });
        if (isAdded() && getContext() != null) {
            Toast.makeText(getContext(), getString(R.string.common_error_network), Toast.LENGTH_SHORT).show();
        }
    }

    private void rollbackFriendRemoval(UserStats stats, int originalStatus, int originalCount,
            android.content.Context appContext) {
        stats.setFriendshipStatus(originalStatus);
        stats.setFriendsCount(originalCount);
        com.vn.jet.mosco.utils.AppExecutors.getInstance().diskIO().execute(() -> {
            com.vn.jet.mosco.database.AppDatabase.getInstance(appContext)
                    .userStatsDao().insertUserStats(stats);
        });
        if (isAdded() && getContext() != null) {
            Toast.makeText(getContext(), getString(R.string.common_error_network), Toast.LENGTH_SHORT).show();
        }
    }

    private void showUnfriendDialog() {
        showFriendActionDialog("Unfriend?", "Are you sure you want to remove this person from your friends list? You will lose your Couple Streak with this person if you unfriend.",
                () -> {
                    UserStats stats = viewModel.getUserStats().getValue();
                    if (stats != null && getContext() != null && targetUserId != null) {
                        final int originalStatus = stats.getFriendshipStatus();
                        final int originalCount = stats.getFriendsCount();
                        stats.setFriendshipStatus(0);
                        stats.setFriendsCount(Math.max(0, originalCount - 1));

                        final android.content.Context appContext = getContext().getApplicationContext();
                        com.vn.jet.mosco.utils.AppExecutors.getInstance().diskIO().execute(() -> {
                            com.vn.jet.mosco.database.AppDatabase.getInstance(appContext)
                                    .userStatsDao().insertUserStats(stats);
                        });

                        if (gameApiService != null) {
                            gameApiService.removeFriendByUser(targetUserId)
                                    .enqueue(new Callback<com.vn.jet.mosco.model.ApiResponse<Void>>() {
                                        @Override
                                        public void onResponse(Call<com.vn.jet.mosco.model.ApiResponse<Void>> call,
                                                Response<com.vn.jet.mosco.model.ApiResponse<Void>> response) {
                                            if (!response.isSuccessful()) {
                                                rollbackFriendRemoval(stats, originalStatus, originalCount, appContext);
                                            }
                                        }

                                        @Override
                                        public void onFailure(Call<com.vn.jet.mosco.model.ApiResponse<Void>> call,
                                                Throwable t) {
                                            rollbackFriendRemoval(stats, originalStatus, originalCount, appContext);
                                        }
                                    });
                        }
                    }
                });
    }

    private void showCancelRequestDialog() {
        showFriendActionDialog("Cancel Request?", "Do you want to cancel your friend request?", () -> {
            UserStats stats = viewModel.getUserStats().getValue();
            if (stats != null && getContext() != null && targetUserId != null) {
                final int originalStatus = stats.getFriendshipStatus();
                stats.setFriendshipStatus(0);

                final android.content.Context appContext = getContext().getApplicationContext();
                com.vn.jet.mosco.utils.AppExecutors.getInstance().diskIO().execute(() -> {
                    com.vn.jet.mosco.database.AppDatabase.getInstance(appContext)
                            .userStatsDao().insertUserStats(stats);
                });

                if (gameApiService != null) {
                    gameApiService.removeFriendByUser(targetUserId)
                            .enqueue(new Callback<com.vn.jet.mosco.model.ApiResponse<Void>>() {
                                @Override
                                public void onResponse(Call<com.vn.jet.mosco.model.ApiResponse<Void>> call,
                                        Response<com.vn.jet.mosco.model.ApiResponse<Void>> response) {
                                    if (!response.isSuccessful()) {
                                        rollbackFriendStatus(stats, originalStatus, appContext);
                                    }
                                }

                                @Override
                                public void onFailure(Call<com.vn.jet.mosco.model.ApiResponse<Void>> call,
                                        Throwable t) {
                                    rollbackFriendStatus(stats, originalStatus, appContext);
                                }
                            });
                }
            }
        });
    }

    private void showFriendActionDialog(String title, String msg, Runnable onConfirm) {
        com.vn.jet.mosco.utils.MoscoDialogHelper.showConfirmDialog(getActivity(), title, msg, "Confirm", "Cancel",
                new com.vn.jet.mosco.utils.MoscoDialogHelper.DialogCallback() {
                    @Override
                    public void onPositive() {
                        onConfirm.run();
                    }
                });
    }

    private void openProfileMenu() {
        // Chống spam click nút Menu để tránh mở nhiều instance gây lỗi UI
        if (android.os.SystemClock.elapsedRealtime() - lastMenuClickTime < MENU_DEBOUNCE_MS) {
            return;
        }
        lastMenuClickTime = android.os.SystemClock.elapsedRealtime();

        ProfileMenuFragment menuFragment = new ProfileMenuFragment();
        menuFragment.setOnMenuActionListener(new ProfileMenuFragment.OnMenuActionListener() {
            @Override
            public void onSwitchAccount() {
                showLogoutConfirmationDialog();
            }

            @Override
            public void onBackupData() {
                // Perform backup to Internal Storage with UID support
                long currentUid = sessionManager.getUserId();
                String backupPath = BackupManager.performInternalBackup(requireContext(), currentUid);
                if (backupPath != null) {
                    Toast.makeText(requireContext(), "✅ Sao lưu thành công", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), "❌ Internal Backup Failed", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onRestoreData() {
                String[] options = { "Restore from Local File", "Restore from Cloud" };
                com.vn.jet.mosco.utils.MoscoDialogHelper.showSingleChoiceDialog(
                        getActivity(),
                        "Restore Data",
                        options,
                        which -> {
                            if (which == 0) {
                                // Local
                                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                                intent.addCategory(Intent.CATEGORY_OPENABLE);
                                intent.setType("application/octet-stream");
                                importLauncher.launch(intent);
                            } else {
                                // Cloud
                                showCloudBackupPicker();
                            }
                        });
            }

            @Override
            public void onCloudSync() {
                long currentUid = sessionManager.getUserId();
                Toast.makeText(requireContext(), "☁️ Syncing to Cloud...", Toast.LENGTH_SHORT).show();

                BackupManager.syncToCloud(requireContext(), currentUid, new BackupManager.SyncCallback() {
                    @Override
                    public void onSuccess(String message) {
                        Toast.makeText(requireContext(), "✅ Cloud Sync Successful!", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(String error) {
                        Toast.makeText(requireContext(), "❌ Sync Failed: " + error, Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onLogout() {
                showLogoutConfirmationDialog();
            }
        });

        getParentFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.anim_slide_in_right, R.anim.anim_slide_out_left, R.anim.anim_slide_in_left,
                        R.anim.anim_slide_out_right)
                .add(R.id.frame_layout, menuFragment)
                .addToBackStack("ProfileMenu")
                .commit();
    }

    // ════════════════════════════════════════════════════════════════
    // EDIT PROFILE DIALOG
    // ════════════════════════════════════════════════════════════════

    private void showLogoutConfirmationDialog() {
        com.vn.jet.mosco.utils.MoscoDialogHelper.showLogoutDialog(getActivity(),
                new com.vn.jet.mosco.utils.MoscoDialogHelper.DialogCallback() {
                    @Override
                    public void onPositive() {
                        sessionManager.clearSession();
                        android.content.Intent intent = new android.content.Intent(getActivity(),
                                com.vn.jet.mosco.SignInActivity.class);
                        intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        if (getActivity() != null)
                            getActivity().finish();
                    }
                });
    }

    private void showDiscardDialog() {
        com.vn.jet.mosco.utils.MoscoDialogHelper.showConfirmDialog(getActivity(),
                "Discard Changes?",
                "Are you sure you want to discard your profile changes?",
                "Discard",
                "Cancel",
                new com.vn.jet.mosco.utils.MoscoDialogHelper.DialogCallback() {
                    @Override
                    public void onPositive() {
                        discardEditMode();
                    }
                });
    }

    private void fetchUserStats() {
        // Logic này đã được chuyển vào ProfileViewModel
    }

    /**
     * Mở danh sách Objet để chọn avatar mới.
     * Tự động chuyển đổi sang URL variant "original" để đảm bảo chất lượng crop đạt
     * chuẩn (không dùng thumbnail).
     */
    private void openAvatarPicker() {
        InventoryBottomSheet inventorySheet = new InventoryBottomSheet();
        inventorySheet.setOnCardSelectedListener(card -> {
            if (card != null && card.getFrontImage() != null) {
                // Đảm bảo dùng ảnh gốc để crop đạt chuẩn chất lượng cao
                String originalUrl = convertToOriginalUrl(card.getFrontImage());
                startManualCrop(originalUrl, card.getCollectionId());
            }
        });
        inventorySheet.show(getChildFragmentManager(), "InventoryBottomSheet");
    }

    private String convertToOriginalUrl(String url) {
        if (url == null)
            return null;
        if (url.contains("/thumbnail")) {
            return url.replace("/thumbnail", "/original");
        }
        if (url.contains("/1x") || url.contains("/2x")) {
            // Regex hoặc replace đơn giản cho các variant khác
            return url.replaceAll("/(1x|2x)$", "/original");
        }
        return url;
    }

    /**
     * Khởi tạo trình cắt ảnh uCrop với cấu hình đồng bộ Galactic Style
     */
    private void startManualCrop(String imageUrl, String collectionId) {
        // Lưu tạm collectionId để sau khi crop xong thì update lên server
        sessionManager.setAvatarId(collectionId);
        this.lastImageUrl = imageUrl; // Lưu lại để quay lại bước này nếu người dùng hủy Preview

        Uri sourceUri = Uri.parse(imageUrl);
        File destinationFile = new File(requireContext().getFilesDir(), getString(R.string.avatar_crop_cache_name));
        Uri destinationUri = Uri.fromFile(destinationFile);

        com.yalantis.ucrop.UCrop.Options options = new com.yalantis.ucrop.UCrop.Options();
        options.setCompressionQuality(90);
        options.setToolbarColor(getResources().getColor(R.color.lg_glass_surface_elevated));
        options.setStatusBarColor(getResources().getColor(R.color.mosco_screen_bg));
        options.setToolbarWidgetColor(android.graphics.Color.WHITE);
        options.setActiveControlsWidgetColor(getResources().getColor(R.color.lg_accent_primary));

        // [PHASE 6] Làm header nổi bật và sử dụng lớp phủ tối mờ kiểu không gian
        options.setDimmedLayerColor(getResources().getColor(R.color.mosco_black_80));
        options.setToolbarCancelDrawable(R.drawable.ic_close);
        options.setToolbarCropDrawable(R.drawable.ic_check);
        options.setToolbarTitle(getString(R.string.profile_crop_title));

        // [PHASE 3] Cắt khung tròn để phù hợp với UI profile dạng Avatar Circle
        options.setCircleDimmedLayer(true);
        options.setShowCropFrame(false);
        options.setShowCropGrid(false);

        com.yalantis.ucrop.UCrop.of(sourceUri, destinationUri)
                .withAspectRatio(1, 1)
                .withMaxResultSize(800, 800)
                .withOptions(options)
                .start(requireContext(), this);
    }

    @Override
    public void onAvatarSelected(String collectionId) {
        // Hàm này từ interface cũ, có thể giữ lại hoặc xóa nếu đã chuyển hẳn sang flow
        // mới
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == com.yalantis.ucrop.UCrop.REQUEST_CROP) {
            if (resultCode == android.app.Activity.RESULT_OK && data != null) {
                Uri resultUri = com.yalantis.ucrop.UCrop.getOutput(data);
                if (resultUri != null) {
                    // Áp dụng avatar mới trực tiếp lên preview
                    this.lastCroppedUri = resultUri;
                    Glide.with(this)
                            .load(resultUri)
                            .skipMemoryCache(true)
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                            .circleCrop() // [FIX] Đảm bảo hiển thị dạng tròn ngay khi crop xong
                            .into(ivAvatar);
                }
            } else if (resultCode == android.app.Activity.RESULT_CANCELED) {
                // Nếu hủy crop, quay lại màn hình chọn Objet
                openAvatarPicker();
            } else if (resultCode == com.yalantis.ucrop.UCrop.RESULT_ERROR && data != null) {
                Throwable cropError = com.yalantis.ucrop.UCrop.getError(data);
                Log.e("ProfileFragment", "Lỗi Crop ảnh", cropError);
            }
        }
    }

    /**
     * Bật chế độ chỉnh sửa: hiện header X/Tick, phủ mờ avatar + icon cây viết
     */
    private void enterEditMode() {
        isEditMode = true;
        // Lưu trạng thái avatar gốc để rollback
        savedAvatarIdBeforeEdit = sessionManager.getAvatarId();
        lastCroppedUri = null;

        // Hiện header và đổi label sang EDIT PROFILE
        if (tvPreviewHeaderLabel != null)
            tvPreviewHeaderLabel.setText(getString(R.string.profile_edit_mode_label));
        if (previewHeader != null)
            previewHeader.setVisibility(View.VISIBLE);

        // Phủ mờ avatar và hiện icon chỉnh sửa
        if (viewAvatarDim != null)
            viewAvatarDim.setVisibility(View.VISIBLE);
        if (ivAvatarEditIcon != null)
            ivAvatarEditIcon.setVisibility(View.VISIBLE);

        // Ẩn nút edit mode để tránh bấm trùng
        if (btnEditMode != null)
            btnEditMode.setVisibility(View.GONE);

        // Thông báo các tab hiện các trường edit
        notifyTabsEditMode(true);
    }

    /**
     * Thoát chế độ chỉnh sửa và hủy mọi thay đổi
     */
    private void discardEditMode() {
        isEditMode = false;
        if (previewHeader != null)
            previewHeader.setVisibility(View.GONE);

        // Khôi phục avatar về trạng thái gốc
        if (viewAvatarDim != null)
            viewAvatarDim.setVisibility(View.GONE);
        if (ivAvatarEditIcon != null)
            ivAvatarEditIcon.setVisibility(View.GONE);
        lastCroppedUri = null;

        // Rollback avatar về ảnh cũ
        if (savedAvatarIdBeforeEdit != null && sessionManager != null) {
            sessionManager.setAvatarId(savedAvatarIdBeforeEdit);
        }
        // Xóa file crop để loadAvatar lấy lại ảnh gốc từ server/local card
        if (isAdded() && getContext() != null) {
            File croppedFile = new File(getContext().getFilesDir(), getString(R.string.avatar_crop_cache_name));
            if (croppedFile.exists())
                croppedFile.delete();
        }
        loadAvatar();

        // Hiện lại nút edit
        if (btnEditMode != null && isOwner)
            btnEditMode.setVisibility(View.VISIBLE);

        // Ẩn các trường edit trong các tab
        notifyTabsEditMode(false);
    }

    /**
     * Hiện dialog xác nhận hủy thay đổi (đồng bộ style với dialog_logout_confirm)
     */

    /**
     * Lưu các thay đổi từ Edit Mode: avatar + username + display name
     */
    private void saveEditChanges() {
        // Thu thập dữ liệu từ tab General
        ProfileGeneralFragment generalFrag = getGeneralFragment();
        String newDisplayName = generalFrag != null ? generalFrag.getEditedDisplayName() : null;
        String newUsername = generalFrag != null ? generalFrag.getEditedUsername() : null;
        String newBio = generalFrag != null ? generalFrag.getEditedBio() : null;

        // Validate cơ bản
        if (newDisplayName != null && (newDisplayName.length() < 2 || newDisplayName.length() > 16)) {
            Toast.makeText(requireContext(), getString(R.string.setup_error_display_name_length), Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        // Gọi API update profile nếu có thay đổi
        com.vn.jet.mosco.network.UpdateProfileRequest request = new com.vn.jet.mosco.network.UpdateProfileRequest();
        if (newUsername != null && !newUsername.isEmpty())
            request.setUsername(newUsername);
        if (newDisplayName != null && !newDisplayName.isEmpty())
            request.setIngameName(newDisplayName);
        if (newBio != null)
            request.setBio(newBio);

        // Đồng bộ avatarId + cropParams nếu đã thay đổi
        String currentAvatarId = sessionManager.getAvatarId();
        if (currentAvatarId != null)
            request.setAvatarId(currentAvatarId);

        String cropParams = sessionManager.getAvatarCropParams();
        if (cropParams != null)
            request.setAvatarCropParams(cropParams);

        if (gameApiService != null && getContext() != null) {
            // Capture ApplicationContext trước khi gửi vào callback bất đồng bộ — tránh NPE
            // khi Fragment bị detach
            final android.content.Context appCtx = getContext().getApplicationContext();
            gameApiService.updateProfile(request)
                    .enqueue(new Callback<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.UserStats>>() {
                        @Override
                        public void onResponse(
                                Call<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.UserStats>> call,
                                Response<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.UserStats>> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                // Cập nhật Session local
                                if (newDisplayName != null && !newDisplayName.isEmpty()) {
                                    sessionManager.setIngameName(newDisplayName);
                                    if (isAdded() && tvUsername != null)
                                        tvUsername.setText(newDisplayName);
                                }
                                if (newUsername != null && !newUsername.isEmpty()) {
                                    sessionManager.setUsername(newUsername);
                                }

                                // [NEW] Lưu metadata crop từ server trả về
                                if (response.body().getData() != null) {
                                    sessionManager.setAvatarCropParams(response.body().getData().getAvatarCropParams());
                                }
                                if (viewModel != null && targetUserId != null) {
                                    if (response.body().getData() != null) {
                                        com.vn.jet.mosco.utils.AppExecutors.getInstance().diskIO().execute(() -> {
                                            com.vn.jet.mosco.database.AppDatabase.getInstance(appCtx)
                                                    .userStatsDao().insertUserStats(response.body().getData());
                                        });
                                    }
                                    viewModel.refreshUserStats(targetUserId);
                                }

                                if (isAdded() && getContext() != null) {
                                    Toast.makeText(getContext(), getString(R.string.common_msg_success),
                                            Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                if (isAdded() && getContext() != null) {
                                    Toast.makeText(getContext(), getString(R.string.common_error_network),
                                            Toast.LENGTH_SHORT).show();
                                }
                            }
                        }

                        @Override
                        public void onFailure(
                                Call<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.UserStats>> call,
                                Throwable t) {
                            if (isAdded() && getContext() != null) {
                                Toast.makeText(getContext(), getString(R.string.common_error_network),
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        }

        // Thoát Edit Mode (giữ lại thay đổi)
        isEditMode = false;
        if (previewHeader != null)
            previewHeader.setVisibility(View.GONE);
        if (viewAvatarDim != null)
            viewAvatarDim.setVisibility(View.GONE);
        if (ivAvatarEditIcon != null)
            ivAvatarEditIcon.setVisibility(View.GONE);
        if (btnEditMode != null && isOwner)
            btnEditMode.setVisibility(View.VISIBLE);
        notifyTabsEditMode(false);
    }

    /**
     * Thông báo các tab về trạng thái Edit Mode
     */
    private void notifyTabsEditMode(boolean editMode) {
        ProfileGeneralFragment general = getGeneralFragment();
        if (general != null)
            general.setEditMode(editMode);

        this.isExhibitEditMode = editMode;
        if (showcaseAdapter != null) {
            showcaseAdapter.notifyItemRangeChanged(0, SHOWCASE_COUNT, "PAYLOAD_EDIT_MODE");
        }
        if (editMode) {
            stopCarousel();
        } else {
            startCarousel();
        }
    }

    /**
     * Lấy instance của ProfileGeneralFragment từ ViewPager
     */
    @Nullable
    private ProfileGeneralFragment getGeneralFragment() {
        if (viewPager == null || viewPager.getAdapter() == null)
            return null;
        // Tab General là position 0. Trong ViewPager2, tag của fragment là "f" +
        // position
        Fragment frag = getChildFragmentManager().findFragmentByTag("f0");
        if (frag instanceof ProfileGeneralFragment) {
            return (ProfileGeneralFragment) frag;
        }
        return null;
    }

    private void enterConfirmationMode(Uri croppedUri) {
        // Không còn dùng nữa - avatar được áp dụng trực tiếp trong onActivityResult
    }

    private void exitConfirmationMode() {
        if (previewHeader != null)
            previewHeader.setVisibility(View.GONE);
        if (blockingOverlay != null)
            blockingOverlay.setVisibility(View.GONE);

        if (btnMenu != null)
            btnMenu.setEnabled(true);
        if (avatarCard != null)
            avatarCard.setEnabled(true);
    }

    private void cancelAvatarPreview() {
        exitConfirmationMode();

        // Reset về ảnh cũ
        loadAvatar();

        // Quay lại bước Crop
        if (lastImageUrl != null) {
            startManualCrop(lastImageUrl, sessionManager.getAvatarId());
        }
    }

    private void confirmAvatarPreview() {
        exitConfirmationMode();

        // Đồng bộ lên Server
        String avatarId = sessionManager.getAvatarId();
        syncAvatarToServer(avatarId);

        Toast.makeText(requireContext(), getString(R.string.common_msg_success), Toast.LENGTH_SHORT).show();
    }

    // [DEPRECATED PHASE 3 Dialog]
    private void showAvatarConfirmationDialog(Uri croppedUri) {
        // Không dùng nữa, chuyển sang inline preview
    }

    private void syncAvatarToServer(String avatarId) {
        com.vn.jet.mosco.network.UpdateProfileRequest request = new com.vn.jet.mosco.network.UpdateProfileRequest();
        request.setAvatarId(avatarId);

        gameApiService.updateProfile(request)
                .enqueue(new Callback<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.UserStats>>() {
                    @Override
                    public void onResponse(
                            Call<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.UserStats>> call,
                            Response<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.UserStats>> response) {
                        if (response.isSuccessful()) {
                            Log.d("ProfileFragment", "Avatar ID synced to server");
                        }
                    }

                    @Override
                    public void onFailure(
                            Call<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.UserStats>> call,
                            Throwable t) {
                        Log.e("ProfileFragment", "Failed to sync avatar ID", t);
                    }
                });
    }

    /**
     * Hiển thị Dialog phóng to Avatar với nền mờ Glassmorphism.
     */
    private void showAvatarZoomDialog() {
        if (getContext() == null)
            return;
        UserStats stats = viewModel.getUserStats().getValue();
        if (stats == null)
            return;

        String avatarId = stats.getAvatarId();
        org.json.JSONObject card = com.vn.jet.mosco.utils.DatabaseLoader.findByCollectionId(getContext(), avatarId);
        if (card == null)
            return;

        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_avatar_zoom, null);
        ImageView ivZoom = dialogView.findViewById(R.id.iv_avatar_zoom);

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0.85f);
        }

        String finalUrl = card.optString("frontImage");
        Glide.with(this)
                .load(finalUrl)
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                .transform(new com.vn.jet.mosco.utils.SmartFaceCropTransformation(finalUrl))
                .into(ivZoom);

        dialogView.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    /**
     * Đồng bộ các chỉ số tương tác (Like/Friends) lên Server.
     */
    private void syncStatsToServer(Integer likes, Integer friends) {
        com.vn.jet.mosco.network.UpdateProfileRequest request = new com.vn.jet.mosco.network.UpdateProfileRequest();
        if (likes != null)
            request.setLikesCount(likes);
        if (friends != null)
            request.setFriendsCount(friends);

        gameApiService.updateProfile(request).enqueue(new Callback<com.vn.jet.mosco.model.ApiResponse<UserStats>>() {
            @Override
            public void onResponse(Call<com.vn.jet.mosco.model.ApiResponse<UserStats>> call,
                    Response<com.vn.jet.mosco.model.ApiResponse<UserStats>> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "Stats synced to server successfully");
                }
            }

            @Override
            public void onFailure(Call<com.vn.jet.mosco.model.ApiResponse<UserStats>> call, Throwable t) {
                Log.e(TAG, "Failed to sync stats to server", t);
            }
        });
    }

    /**
     * Phân tích và trích xuất thông báo lỗi từ Server để hiển thị thân thiện với
     * người dùng
     */
    private String parseServerError(Response<ResponseBody> response) {
        try {
            if (response.errorBody() != null) {
                String body = response.errorBody().string();
                JSONObject json = new JSONObject(body);
                String msg = json.optString("message", "");
                if (!msg.isEmpty())
                    return msg;
            }
        } catch (Exception ignored) {
        }
        return getString(R.string.profile_error_unknown);
    }
    private void showCloudBackupPicker() {
        Toast.makeText(requireContext(), "Fetching backup list...", Toast.LENGTH_SHORT).show();
        BackupManager.fetchCloudBackups(requireContext(),
                new retrofit2.Callback<com.vn.jet.mosco.model.ApiResponse<List<String>>>() {
                    @Override
                    public void onResponse(retrofit2.Call<com.vn.jet.mosco.model.ApiResponse<List<String>>> call,
                            retrofit2.Response<com.vn.jet.mosco.model.ApiResponse<List<String>>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            List<String> files = response.body().getData();
                            if (files == null || files.isEmpty()) {
                                Toast.makeText(requireContext(), "No cloud backups found", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            String[] items = files.toArray(new String[0]);
                            com.vn.jet.mosco.utils.MoscoDialogHelper.showSingleChoiceDialog(
                                    getActivity(),
                                    "Select Cloud Backup",
                                    items,
                                    which -> {
                                        String selectedFile = items[which];
                                        downloadAndRestoreCloud(selectedFile);
                                    });
                        } else {
                            Toast.makeText(requireContext(), "Failed to fetch list: " + response.code(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(retrofit2.Call<com.vn.jet.mosco.model.ApiResponse<List<String>>> call,
                            Throwable t) {
                        Toast.makeText(requireContext(), "Network Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void downloadAndRestoreCloud(String filename) {
        Toast.makeText(requireContext(), "Downloading and Restoring...", Toast.LENGTH_LONG).show();
        BackupManager.downloadAndRestoreCloudBackup(requireContext(), filename, new BackupManager.SyncCallback() {
            @Override
            public void onSuccess(String message) {
                Toast.makeText(requireContext(), "✅ Restore Successful!", Toast.LENGTH_SHORT).show();
                com.vn.jet.mosco.utils.MoscoDialogHelper.showInfoDialog(
                        getActivity(),
                        "Restore Complete",
                        "Data has been restored from the cloud. The application will now restart.",
                        "Restart App",
                        new com.vn.jet.mosco.utils.MoscoDialogHelper.DialogCallback() {
                            @Override
                            public void onPositive() {
                                System.exit(0);
                            }
                        });
            }

            @Override
            public void onFailure(String error) {
                Toast.makeText(requireContext(), "❌ Error: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setTopBarVisible(false);
        }
        startCarousel();
        if (showcaseAdapter != null) showcaseAdapter.playAllPlayers();
        if (sessionManager != null) {
            notificationSubscription = com.vn.jet.mosco.network.WebSocketManager.getInstance().subscribeToPrivateChat(
                String.valueOf(sessionManager.getUserId()),
                message -> {
                    if ("SYSTEM_FRIEND".equals(message.getSenderId())) {
                        if (targetUserId != null && viewModel != null) {
                            requireActivity().runOnUiThread(() -> {
                                viewModel.refreshUserStats(targetUserId);
                            });
                        }
                    }
                }
            );
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stopCarousel();
        if (showcaseAdapter != null) showcaseAdapter.pauseAllPlayers();
        if (notificationSubscription != null && !notificationSubscription.isDisposed()) {
            notificationSubscription.dispose();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (showcaseAdapter != null) {
            showcaseAdapter.releaseAllPlayers();
        }
    }

    // --- Exhibit Showcase Helper Methods & Classes ---

    private void setupExhibitShowcase(View v) {
        vpShowcase = v.findViewById(R.id.vp_showcase);
        if (vpShowcase == null) return;

        showcaseAdapter = new ShowcasePagerAdapter(new ArrayList<>());
        vpShowcase.setAdapter(showcaseAdapter);
        vpShowcase.setOffscreenPageLimit(3);
        vpShowcase.setUserInputEnabled(true); // Cho phép vuốt ngang mượt mà

        // [GLOW FIX] Đảm bảo RecyclerView bên trong ViewPager2 không cắt viền Glow
        View innerRecyclerView = vpShowcase.getChildAt(0);
        if (innerRecyclerView instanceof ViewGroup) {
            ((ViewGroup) innerRecyclerView).setClipChildren(false);
            ((ViewGroup) innerRecyclerView).setClipToPadding(false);
        }

        CompositePageTransformer transformer = new CompositePageTransformer();
        transformer.addTransformer(new MarginPageTransformer(getResources().getDimensionPixelSize(R.dimen.spacing_xs)));
        transformer.addTransformer((page, position) -> {
            float r = 1 - Math.abs(position);
            // Áp dụng scale và alpha Galactic premium cho mượt mà
            float scale = 0.85f + r * 0.15f; 
            page.setScaleY(scale);
            page.setScaleX(scale);
            page.setAlpha(0.6f + r * 0.4f);
        });
        vpShowcase.setPageTransformer(transformer);

        // Đảm bảo Master Data được nạp để lấy metadata (Name, Class) chính xác
        DatabaseLoader.initMasterData(requireContext());
    }

    private void renderShowcaseData(UserStats stats) {
        if (stats == null || vpShowcase == null) return;
        List<String> validIds = new ArrayList<>();
        boolean needsUpdate = false;
        
        List<String> ids = stats.getShowcaseCardIds() != null ? stats.getShowcaseCardIds() : new ArrayList<>();
        
        // Nếu là Owner đang xem profile chính mình, kiểm tra xem thẻ còn trong Inventory không
        if (stats.getId() != null && stats.getId().equals(DatabaseLoader.cachedInventoryUserId) && DatabaseLoader.cachedCollectionMap != null) {
            for (String id : ids) {
                if (id == null || id.trim().isEmpty() || id.equals("null")) {
                    validIds.add("");
                } else {
                    String realId = id;
                    if (id.contains(":")) {
                        String[] parts = id.split(":");
                        if (parts.length > 0) {
                            realId = parts[0];
                        }
                    }
                    if (DatabaseLoader.cachedCollectionMap.containsKey(realId)) {
                        validIds.add(id);
                    } else {
                        // Thẻ này thực sự không còn trong kho -> tự động tháo
                        validIds.add("");
                        needsUpdate = true;
                    }
                }
            }
        } else {
            validIds.addAll(ids);
        }
        
        this.currentShowcaseIds = validIds;
        if (showcaseAdapter != null) {
            showcaseAdapter.updateIds(currentShowcaseIds);
        }
        
        // Nếu phát hiện thẻ ma, báo ViewModel tự động dọn dẹp trên Server
        if (needsUpdate && viewModel != null) {
            viewModel.updateShowcase(validIds);
        }

        // Tự động start carousel khi có dữ liệu
        startCarousel();
    }

    private void startCarousel() {
        stopCarousel();
        if (isEditMode || showcaseAdapter == null || showcaseAdapter.getItemCount() == 0) return;
        carouselRunnable = new Runnable() {
            @Override
            public void run() {
                if (vpShowcase != null && showcaseAdapter != null && showcaseAdapter.getItemCount() > 0) {
                    int nextItem = (vpShowcase.getCurrentItem() + 1) % showcaseAdapter.getItemCount();
                    vpShowcase.setCurrentItem(nextItem, true);
                }
                carouselHandler.postDelayed(this, 5000);
            }
        };
        carouselHandler.postDelayed(carouselRunnable, 5000);
    }

    private void stopCarousel() {
        if (carouselRunnable != null) {
            carouselHandler.removeCallbacks(carouselRunnable);
            carouselRunnable = null;
        }
    }

    private void openInventoryPicker(int index) {
        if (!isEditMode) return;
        
        if (System.currentTimeMillis() - lastShowcaseClickTime < 500) return;
        lastShowcaseClickTime = System.currentTimeMillis();

        if (getChildFragmentManager().findFragmentByTag("InventoryPicker") != null) return;

        InventoryBottomSheet sheet = new InventoryBottomSheet();
        sheet.setShowcaseMode(true); 
        sheet.setOnCardSelectedListener(card -> {
            if (card != null) {
                // Lưu ghép collectionId:upgradeLevel để truyền tải chính xác cấp thẻ của exhibit
                updateShowcase(index, card.getCollectionId() + ":" + card.getUpgradeLevel());
            }
        });
        sheet.show(getChildFragmentManager(), "InventoryPicker");
    }

    private void updateShowcase(int index, String collectionId) {
        if (collectionId != null && !collectionId.isEmpty()) {
            for (String id : currentShowcaseIds) {
                if (collectionId.equals(id)) {
                    Toast.makeText(getContext(), R.string.showcase_msg_duplicate, Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }
        List<String> newIds = new ArrayList<>(currentShowcaseIds);
        while (newIds.size() < SHOWCASE_COUNT) {
            newIds.add("");
        }
        
        if (index >= 0 && index < SHOWCASE_COUNT) {
            String finalId = collectionId != null ? collectionId : "";
            newIds.set(index, finalId);
            
            this.currentShowcaseIds = newIds;
            if (viewModel != null) {
                viewModel.updateShowcase(newIds);
            }
            
            if (showcaseAdapter != null) {
                showcaseAdapter.updateIdAt(index, finalId);
            }
        }
    }

    private void unequipObjet(int index) {
        updateShowcase(index, "");
    }

    private void bindCardView(View cardView, String collectionId, int position) {
        com.google.android.material.card.MaterialCardView cvContainer = cardView.findViewById(R.id.cv_card_container);
        ImageView ivImage = cardView.findViewById(R.id.card_iv_image);
        ImageView ivBack = cardView.findViewById(R.id.card_iv_back);
        View shimmer = cardView.findViewById(R.id.view_card_shimmer);
        TextView tvName = cardView.findViewById(R.id.tv_card_name);
        TextView tvOvr = cardView.findViewById(R.id.card_tv_ovr);
        ImageView ivLevel = cardView.findViewById(R.id.card_iv_level);
        View layoutCore = cardView.findViewById(R.id.layout_core);
        View layoutEmpty = cardView.findViewById(R.id.layout_empty_placeholder);
        View layoutAddPlus = cardView.findViewById(R.id.layout_add_objet_plus);

        if (cvContainer == null) return;

        if (tvOvr != null) tvOvr.setVisibility(View.GONE);

        if (tvName != null) {
            tvName.setVisibility(View.GONE);
        }

        if (collectionId == null || collectionId.isEmpty() || collectionId.equals("null")) {
            if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
            if (layoutCore != null) layoutCore.setVisibility(View.GONE);
            if (ivBack != null) ivBack.setVisibility(View.GONE);
            
            if (layoutAddPlus != null) {
                layoutAddPlus.setVisibility(isEditMode ? View.VISIBLE : View.GONE);
            }

            View btnUnequip = cardView.findViewById(R.id.btn_unequip);
            if (btnUnequip != null) btnUnequip.setVisibility(View.GONE);

            CardEffectHelper.applyEmptyStateGlow(cvContainer, false);
        } else {
            if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
            if (layoutCore != null) layoutCore.setVisibility(View.VISIBLE);
            if (layoutAddPlus != null) layoutAddPlus.setVisibility(View.GONE);
            
            View btnUnequip = cardView.findViewById(R.id.btn_unequip);
            if (btnUnequip != null) {
                btnUnequip.setVisibility(View.GONE); 
                btnUnequip.setOnClickListener(v -> unequipObjet(position));
            }

            if (layoutAddPlus != null) layoutAddPlus.setVisibility(View.GONE);
            View coreAddPlus = cardView.findViewById(R.id.layout_add_objet_plus);
            if (coreAddPlus != null) coreAddPlus.setVisibility(View.GONE);
            
            org.json.JSONObject cardData = DatabaseLoader.findByCollectionId(getContext(), collectionId);
            if (cardData == null) {
                DatabaseLoader.initMasterDataSync(getContext());
                cardData = DatabaseLoader.findByCollectionId(getContext(), collectionId);
            }

            if (cardData == null) {
                return;
            }
            
            if (ivImage != null) {
                ivImage.setAlpha(1.0f);
                ivImage.setVisibility(View.VISIBLE);
                String frontImage = cardData.optString("frontImage");
                com.vn.jet.mosco.utils.GlideBindingAdapter.loadImage(ivImage, frontImage, false);
            }

            if (ivBack != null) {
                ivBack.setVisibility(View.GONE);
                ivBack.setImageResource(R.drawable.objet_back_spin);
            }

            // Video MP4 playback cho Motion Cards
            android.view.TextureView vvVideo = cardView.findViewById(R.id.card_vv_video);
            if (vvVideo != null) {
                String cardClass = cardData.optString("class", "");
                String videoUrl = cardData.optString("frontVideoUrl", "");
                if ("Motion".equalsIgnoreCase(cardClass) && !videoUrl.isEmpty()) {
                    androidx.media3.exoplayer.ExoPlayer player = com.vn.jet.mosco.utils.MotionVideoHelper.playMotionVideo(getContext(), vvVideo, videoUrl, ivImage);
                    if (player != null && showcaseAdapter != null) {
                        showcaseAdapter.putPlayer(position, player);
                    }
                } else {
                    vvVideo.setVisibility(View.GONE);
                }
            }

            String frontImageStr = cardData.optString("frontImage");
            Objet mockObj = new Objet(0, collectionId, frontImageStr, 1, 0, cardData.optInt("upgradeLevel", 0));
            
            CardEffectHelper.apply(cvContainer, shimmer, mockObj, false);

            if (ivLevel != null) {
                int level = cardData.optInt("upgradeLevel", 0);
                if (level > 0) {
                    ivLevel.setVisibility(View.VISIBLE);
                    Glide.with(this).load(getString(R.string.asset_grade_path) + level + ".png").into(ivLevel);
                    LevelBadgeEffectHelper.apply(ivLevel, level);
                } else {
                    ivLevel.setVisibility(View.GONE);
                }
            }
        }
    }

    private void toggleFlip(View itemView) {
        View layoutCore = itemView.findViewById(R.id.layout_core);
        View ivBack = itemView.findViewById(R.id.card_iv_back);
        View cvContainer = itemView.findViewById(R.id.cv_card_container);
        if (layoutCore == null || ivBack == null || cvContainer == null) return;

        boolean isBackVisible = ivBack.getVisibility() == View.VISIBLE;
        
        float distance = 12000;
        float scale = getResources().getDisplayMetrics().density;
        cvContainer.setCameraDistance(distance * scale);

        ObjectAnimator flip1 = ObjectAnimator.ofFloat(cvContainer, "rotationY", 0f, 90f);
        flip1.setDuration(300);
        flip1.setInterpolator(new AccelerateInterpolator());
        
        flip1.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (isBackVisible) {
                    ivBack.setVisibility(View.GONE);
                    layoutCore.setVisibility(View.VISIBLE);
                } else {
                    ivBack.setVisibility(View.VISIBLE);
                    layoutCore.setVisibility(View.GONE);
                }
                ObjectAnimator flip2 = ObjectAnimator.ofFloat(cvContainer, "rotationY", -90f, 0f);
                flip2.setDuration(300);
                flip2.setInterpolator(new DecelerateInterpolator());
                flip2.start();
            }
        });
        flip1.start();
    }

    // --- Showcase View Holder ---
    private static class ShowcaseViewHolder extends RecyclerView.ViewHolder {
        public ShowcaseViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    // --- Showcase Pager Adapter ---
    private class ShowcasePagerAdapter extends RecyclerView.Adapter<ShowcaseViewHolder> {
        private final List<String> ids = new ArrayList<>();
        private final java.util.Map<Integer, androidx.media3.exoplayer.ExoPlayer> playerMap = new java.util.HashMap<>();

        public void putPlayer(int position, androidx.media3.exoplayer.ExoPlayer player) {
            playerMap.put(position, player);
        }

        public void pauseAllPlayers() {
            for (androidx.media3.exoplayer.ExoPlayer player : playerMap.values()) {
                if (player != null) player.pause();
            }
        }

        public void playAllPlayers() {
            for (androidx.media3.exoplayer.ExoPlayer player : playerMap.values()) {
                if (player != null) player.play();
            }
        }

        public void releaseAllPlayers() {
            for (androidx.media3.exoplayer.ExoPlayer player : playerMap.values()) {
                if (player != null) player.release();
            }
            playerMap.clear();
        }

        @Override
        public void onViewRecycled(@NonNull ShowcaseViewHolder holder) {
            super.onViewRecycled(holder);
            int position = holder.getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION && playerMap.containsKey(position)) {
                androidx.media3.exoplayer.ExoPlayer player = playerMap.remove(position);
                if (player != null) player.release();
            }
        }

        public ShowcasePagerAdapter(List<String> initialIds) { 
            if (initialIds != null) this.ids.addAll(initialIds); 
        }

        public void updateIds(List<String> newIds) {
            List<String> paddedIds = new ArrayList<>();
            if (isOwner) {
                // Owner: Giữ đủ 8 slot (đắp thêm "" nếu thiếu)
                paddedIds.addAll(newIds);
                while (paddedIds.size() < SHOWCASE_COUNT) paddedIds.add("");
            } else {
                // Guest: Chỉ lấy những card có ID thực sự hợp lệ, không show empty slot
                for (String id : newIds) {
                    if (id != null && !id.trim().isEmpty() && !id.equals("null")) {
                        paddedIds.add(id);
                    }
                }
            }
            
            if (this.ids.equals(paddedIds)) return;

            this.ids.clear();
            this.ids.addAll(paddedIds);
            notifyDataSetChanged();
        }

        public void updateIdAt(int index, String cardId) {
            if (index >= 0 && index < ids.size()) {
                ids.set(index, cardId);
                notifyItemChanged(index);
            }
        }

        @NonNull
        @Override
        public ShowcaseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_showcase_pager, parent, false);
            return new ShowcaseViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ShowcaseViewHolder holder, int position) {
            String cardId = (position < ids.size()) ? ids.get(position) : "";
            
            // Giải phóng player cũ nếu có trước khi tái sử dụng view
            if (playerMap.containsKey(position)) {
                androidx.media3.exoplayer.ExoPlayer oldPlayer = playerMap.remove(position);
                if (oldPlayer != null) oldPlayer.release();
            }

            bindCardView(holder.itemView, cardId, position);
            
            holder.itemView.setOnClickListener(v -> {
                if (isEditMode) {
                    openInventoryPicker(position);
                } else {
                    if (cardId != null && !cardId.isEmpty() && !cardId.equals("null")) {
                        org.json.JSONObject cardData = DatabaseLoader.findByCollectionId(getContext(), cardId);
                        if (cardData == null) {
                            DatabaseLoader.initMasterDataSync(getContext());
                            cardData = DatabaseLoader.findByCollectionId(getContext(), cardId);
                        }
                        if (cardData != null) {
                            com.vn.jet.mosco.model.CollectionEntry entry = new com.vn.jet.mosco.model.CollectionEntry();
                            entry.setCollectionId(cardData.optString("collectionId", ""));
                            entry.setMember(cardData.optString("member", ""));
                            entry.setSeason(cardData.optString("season", ""));
                            entry.setCardClass(cardData.optString("class", ""));
                            entry.setCollectionNo(cardData.optString("collectionNo", ""));
                            entry.setFrontImage(cardData.optString("frontImage", ""));
                            entry.setBackgroundColor(cardData.optString("backgroundColor", ""));
                            entry.setBackImage(cardData.optString("backImage", ""));
                            entry.setOvr(cardData.optInt("ovr", 0));
                            entry.setUpgradeLevel(cardData.optInt("upgradeLevel", 0));
                            entry.setLevel(cardData.optInt("level", 1));
                            entry.setFrontVideoUrl(cardData.optString("frontVideoUrl", ""));
                            entry.setOwned(true); // Đảm bảo hiệu ứng glow hoạt động và ẩn màn đen

                            // Gọi CollectionDetailBinder với isAlbumMode = true và isFromExhibit = true để ẩn sạch các nút chức năng và nút X, nhưng vẫn giữ badge level & hiệu ứng thẻ!
                            com.vn.jet.mosco.utils.CollectionDetailBinder.showDetail(getContext(), entry, true, true, null);
                        }
                    }
                }
            });

            holder.itemView.setOnLongClickListener(v -> {
                if (isEditMode) {
                    if (cardId != null && !cardId.isEmpty() && !cardId.equals("null")) {
                        View btnUnequip = holder.itemView.findViewById(R.id.btn_unequip);
                        if (btnUnequip != null) {
                            btnUnequip.setVisibility(View.VISIBLE);
                            return true;
                        }
                    }
                }
                return false;
            });
        }

        @Override
        public void onBindViewHolder(@NonNull ShowcaseViewHolder holder, int position, @NonNull List<Object> payloads) {
            if (payloads.contains("PAYLOAD_EDIT_MODE")) {
                String cardId = (position < ids.size()) ? ids.get(position) : "";
                boolean isEmpty = cardId == null || cardId.isEmpty() || cardId.equals("null");
                View layoutAddPlus = holder.itemView.findViewById(R.id.layout_add_objet_plus);
                if (layoutAddPlus != null && isEmpty) {
                    layoutAddPlus.setVisibility(isEditMode ? View.VISIBLE : View.GONE);
                }
            } else {
                super.onBindViewHolder(holder, position, payloads);
            }
        }

        @Override
        public int getItemCount() { return ids.size(); }
    }
}
