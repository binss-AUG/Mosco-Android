package com.vn.jet.mosco.fragment;

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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.vn.jet.mosco.ForgotPasswordActivity;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.MainActivity;
import com.vn.jet.mosco.SignInActivity;
import com.vn.jet.mosco.model.UserStats;
import com.vn.jet.mosco.network.ApiClient;
import com.vn.jet.mosco.network.GameApiService;
import com.vn.jet.mosco.utils.NumberUtils;
import com.vn.jet.mosco.utils.SessionManager;
import com.vn.jet.mosco.ProfileViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.Transformations;
import android.view.ViewStub;

import org.json.JSONObject;
import com.vn.jet.mosco.utils.SmartFaceCropTransformation;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * ProfileFragment — Quản lý hồ sơ, tài sản, và hành động tài khoản.
 * V6.2: Objet based Avatar (Preset Selection) with LeftOffsetCrop.
 */
public class ProfileFragment extends Fragment implements AvatarSelectorBottomSheet.OnAvatarSelectedListener {

    private static final String TAG = "ProfileFragment";
    private static final long MENU_DEBOUNCE_MS = 500;
    public static final String ARG_TARGET_USER_ID = "target_user_id";

    private TextView tvUsername, tvLevel;
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
    private TextView tvCurrentTitle, tvStatLikes, tvStatFriends;
    private View tabSlidingThumb;
    private com.google.android.material.tabs.TabLayout tabLayout;
    private androidx.viewpager2.widget.ViewPager2 viewPager;
    private SessionManager sessionManager;
    private GameApiService gameApiService;
    private ProfileViewModel viewModel;

    private Long targetUserId;
    private boolean isOwner;
    private String lastImageUrl;
    private Uri lastCroppedUri;
    private long lastMenuClickTime = 0;
    // Lưu trạng thái avatar gốc để rollback khi Discard
    private String savedAvatarIdBeforeEdit;

    public ProfileFragment() {
        // Constructor mặc định cho Fragment
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

        if (isOwner) {
            setupSession();
        }

        setupListeners();

        return view;
    }

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

        tvCurrentTitle
                .setText(stats.getCurrentTitle() != null && !stats.getCurrentTitle().isEmpty() ? stats.getCurrentTitle()
                        : getString(R.string.profile_title_default));

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
        tvCurrentTitle = v.findViewById(R.id.tv_current_title);
        tvStatLikes = v.findViewById(R.id.tv_stat_likes);
        tvStatFriends = v.findViewById(R.id.tv_stat_friends);
        tabSlidingThumb = v.findViewById(R.id.tab_sliding_thumb);
        tabLayout = v.findViewById(R.id.tab_layout);
        viewPager = v.findViewById(R.id.view_pager);

        setupViewPager();
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
                        case 2:
                            tab.setText(R.string.profile_tab_exhibit);
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
        gameApiService = ApiClient.getClient(requireContext()).create(GameApiService.class);

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
            getParentFragmentManager().popBackStack();
            com.vn.jet.mosco.utils.NavigationUtils.handleBackPress();
        } else {
            // Nếu không có backstack (ví dụ mở từ tab), quay về Home
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).selectTab(R.id.nav_home);
            }
        }
    }

    private void setupGuestListeners(View v) {
        com.google.android.material.button.MaterialButton btnLike = v.findViewById(R.id.btn_like);
        com.google.android.material.button.MaterialButton btnFriend = v.findViewById(R.id.btn_add_friend);
        com.google.android.material.button.MaterialButton btnMsg = v.findViewById(R.id.btn_direct_message);

        // Quan sát dữ liệu để cập nhật trạng thái nút
        viewModel.getUserStats().observe(getViewLifecycleOwner(), stats -> {
            if (stats == null)
                return;

            // Update Like Button
            if (isAdded() && getContext() != null) {
                if (stats.isLiked()) {
                    btnLike.setText(R.string.profile_btn_liked);
                    btnLike.setBackgroundTintList(android.content.res.ColorStateList
                            .valueOf(getResources().getColor(R.color.lg_accent_primary_dim)));
                } else {
                    btnLike.setText(R.string.profile_btn_like);
                    btnLike.setBackgroundTintList(
                            android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.lg_accent_primary)));
                }
            }

            // Update Friend Button
            if (isAdded() && getContext() != null) {
                switch (stats.getFriendshipStatus()) {
                    case 1: // Pending
                        btnFriend.setText(R.string.profile_btn_pending);
                        btnFriend.setStrokeColor(android.content.res.ColorStateList
                                .valueOf(getResources().getColor(R.color.palette_gold)));
                        break;
                    case 2: // Friends
                        btnFriend.setText(R.string.profile_btn_friends);
                        btnFriend.setStrokeColor(android.content.res.ColorStateList
                                .valueOf(getResources().getColor(R.color.mosco_success)));
                        break;
                    default: // None
                        btnFriend.setText(R.string.profile_btn_add_friend);
                        btnFriend.setStrokeColor(android.content.res.ColorStateList
                                .valueOf(getResources().getColor(R.color.lg_outline)));
                        break;
                }
            }
        });

        btnLike.setOnClickListener(new com.vn.jet.mosco.utils.ClickDebounce(500, view -> {
            UserStats stats = viewModel.getUserStats().getValue();
            if (stats != null && getContext() != null) {
                // Toggle Like locally for Optimistic UI
                stats.setLiked(!stats.isLiked());
                stats.setLikesCount(stats.isLiked() ? stats.getLikesCount() + 1 : stats.getLikesCount() - 1);

                // Update Local DB to trigger observer
                android.content.Context appContext = getContext().getApplicationContext();
                com.vn.jet.mosco.utils.AppExecutors.getInstance().diskIO().execute(() -> {
                    com.vn.jet.mosco.database.AppDatabase.getInstance(appContext)
                            .userStatsDao().insertUserStats(stats);
                });

                // [SYNC] Gửi lên Server để lưu trữ vĩnh viễn
                syncStatsToServer(stats.getLikesCount(), stats.getFriendsCount());
                // TODO: Gọi API Like chuyên sâu nếu cần phân biệt ai like ai
            }
        }));

        btnFriend.setOnClickListener(new com.vn.jet.mosco.utils.ClickDebounce(500, view -> {
            UserStats stats = viewModel.getUserStats().getValue();
            if (stats == null || getContext() == null)
                return;

            if (stats.getFriendshipStatus() == 2) {
                showUnfriendDialog();
            } else if (stats.getFriendshipStatus() == 1) {
                showCancelRequestDialog();
            } else {
                // Send Request
                stats.setFriendshipStatus(1);
                android.content.Context appContext = getContext().getApplicationContext();
                com.vn.jet.mosco.utils.AppExecutors.getInstance().diskIO().execute(() -> {
                    com.vn.jet.mosco.database.AppDatabase.getInstance(appContext)
                            .userStatsDao().insertUserStats(stats);
                });
                Toast.makeText(getContext(), getString(R.string.profile_msg_friend_request_sent), Toast.LENGTH_SHORT)
                        .show();

                // [SYNC] Gửi yêu cầu kết bạn lên Server
                // TODO: Gọi API /api/friends/add
                syncStatsToServer(null, stats.getFriendsCount());
            }
        }));

        btnMsg.setOnClickListener(new com.vn.jet.mosco.utils.ClickDebounce(500, view -> {
            Toast.makeText(requireContext(), getString(R.string.profile_msg_chat_coming_soon_toast), Toast.LENGTH_SHORT)
                    .show();
        }));
    }

    private void showUnfriendDialog() {
        showFriendActionDialog("Unfriend?", "Are you sure you want to remove this person from your friends list?",
                () -> {
                    UserStats stats = viewModel.getUserStats().getValue();
                    if (stats != null && getContext() != null) {
                        stats.setFriendshipStatus(0);
                        stats.setFriendsCount(Math.max(0, stats.getFriendsCount() - 1));

                        android.content.Context appContext = getContext().getApplicationContext();
                        com.vn.jet.mosco.utils.AppExecutors.getInstance().diskIO().execute(() -> {
                            com.vn.jet.mosco.database.AppDatabase.getInstance(appContext)
                                    .userStatsDao().insertUserStats(stats);
                        });

                        // [SYNC] Cập nhật số bạn bè lên Server
                        syncStatsToServer(null, stats.getFriendsCount());
                        // TODO: Gọi API /api/friends/remove
                    }
                });
    }

    private void showCancelRequestDialog() {
        showFriendActionDialog("Cancel Request?", "Do you want to cancel your friend request?", () -> {
            UserStats stats = viewModel.getUserStats().getValue();
            if (stats != null && getContext() != null) {
                stats.setFriendshipStatus(0);

                android.content.Context appContext = getContext().getApplicationContext();
                com.vn.jet.mosco.utils.AppExecutors.getInstance().diskIO().execute(() -> {
                    com.vn.jet.mosco.database.AppDatabase.getInstance(appContext)
                            .userStatsDao().insertUserStats(stats);
                });

                // [SYNC] Đồng bộ trạng thái hủy yêu cầu
                syncStatsToServer(null, stats.getFriendsCount());
                // TODO: Gọi API hủy kết bạn chuyên sâu
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
            public void onForgotPassword() {
                startActivity(new Intent(getActivity(), com.vn.jet.mosco.ForgotPasswordActivity.class));
            }

            @Override
            public void onSwitchAccount() {
                showLogoutConfirmationDialog();
            }

            @Override
            public void onSettings() {
                new SettingsBottomSheet().show(getChildFragmentManager(), "SettingsBottomSheet");
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
                        exitConfirmationMode();
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

        ProfileExhibitFragment exhibit = getExhibitFragment();
        if (exhibit != null)
            exhibit.setEditMode(editMode);
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

    @Nullable
    private ProfileExhibitFragment getExhibitFragment() {
        if (viewPager == null || viewPager.getAdapter() == null)
            return null;
        // Tab Exhibit là position 2
        Fragment frag = getChildFragmentManager().findFragmentByTag("f2");
        if (frag instanceof ProfileExhibitFragment) {
            return (ProfileExhibitFragment) frag;
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
}
