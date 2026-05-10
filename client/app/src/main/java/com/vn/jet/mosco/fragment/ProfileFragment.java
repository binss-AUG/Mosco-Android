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
    public boolean isEditMode() { return isEditMode; }
    private View previewHeader, blockingOverlay;
    private View btnPreviewCancel, btnPreviewConfirm;
    private View layoutProfileContent;
    private ViewStub stubShimmer;
    private View inflatedShimmer;
    private TextView tvCurrentTitle;
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
            if (layoutProfileContent != null) layoutProfileContent.setVisibility(View.GONE);
            if (inflatedShimmer == null && stubShimmer != null) {
                inflatedShimmer = stubShimmer.inflate();
            }
            if (inflatedShimmer != null) inflatedShimmer.setVisibility(View.VISIBLE);
        } else {
            if (inflatedShimmer != null) inflatedShimmer.setVisibility(View.GONE);
            if (layoutProfileContent != null) layoutProfileContent.setVisibility(View.VISIBLE);
        }
    }

    private void setupProfileRouting(View view) {
        if (isOwner) {
            if (btnMenu != null) btnMenu.setVisibility(View.VISIBLE);
            if (btnEditMode != null) btnEditMode.setVisibility(View.VISIBLE);
        } else {
            ViewStub stub = view.findViewById(R.id.stub_guest_actions);
            if (stub != null) {
                View inflated = stub.inflate();
                setupGuestListeners(inflated);
            }
            // Guest không được mở menu hệ thống hoặc chỉnh sửa
            if (btnMenu != null) btnMenu.setVisibility(View.GONE);
            if (btnEditMode != null) btnEditMode.setVisibility(View.GONE);
        }
    }

    private void renderProfileData(com.vn.jet.mosco.model.UserStats stats) {
        tvUsername.setText(stats.getIngameName() != null ? stats.getIngameName() : stats.getUsername());
        tvLevel.setText(getString(R.string.format_level_short, stats.getLevel()));
        
        tvCurrentTitle.setText(stats.getCurrentTitle() != null && !stats.getCurrentTitle().isEmpty() ? stats.getCurrentTitle() : getString(R.string.profile_title_default));

        // Load avatar từ URL trong stats nếu có
        if (stats.getAvatarId() != null) {
             loadAvatarById(stats.getAvatarId());
        }
    }

    private void loadAvatarById(String avatarId) {
        if (getContext() == null || avatarId == null || avatarId.isEmpty()) {
            ivAvatar.setImageResource(R.drawable.ic_user);
            return;
        }

        JSONObject card = com.vn.jet.mosco.utils.DatabaseLoader.findByCollectionId(getContext(), avatarId);
        if (card != null) {
            String imgUrl = card.optString("frontImage", "");
            if (imgUrl.contains("/original") || imgUrl.contains("/thumbnail")) {
                // Đã là URL đầy đủ
            } else {
                // Cần convert (nếu card chỉ chứa ID)
                imgUrl = com.vn.jet.mosco.utils.GlideBindingAdapter.convertImageIdToUrl(imgUrl, false);
            }

            Glide.with(this)
                    .load(imgUrl)
                    .transform(new SmartFaceCropTransformation())
                    .placeholder(R.drawable.ic_user)
                    .error(R.drawable.ic_user)
                    .into(ivAvatar);
        } else {
            ivAvatar.setImageResource(R.drawable.ic_user);
        }
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
        tabSlidingThumb = v.findViewById(R.id.tab_sliding_thumb);
        tabLayout = v.findViewById(R.id.tab_layout);
        viewPager = v.findViewById(R.id.view_pager);

        setupViewPager();
    }

    private void setupViewPager() {
        if (viewPager == null || tabLayout == null) return;
        
        // Lazy Loading mặc định (chỉ giữ 1 tab bên cạnh)
        viewPager.setOffscreenPageLimit(androidx.viewpager2.widget.ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT);
        
        com.vn.jet.mosco.adapter.ProfileViewPagerAdapter adapter = new com.vn.jet.mosco.adapter.ProfileViewPagerAdapter(this);
        viewPager.setAdapter(adapter);
        
        // [FIX] Tắt vuốt ngang ở ViewPager2 gốc để tránh xung đột thao tác vuốt với ViewPager2 con (Exhibit Pager)
        viewPager.setUserInputEnabled(false);

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
                }
        ).attach();

        // Thiết lập sliding thumb cho Tab (giống Duration chip ở Stage)
        setupTabThumb();
    }

    /**
     * Đồng bộ indicator dạng pill cho TabLayout, di chuyển mượt mà giữa các tab.
     * Sử dụng View độc lập trong XML để tránh phá vỡ hierarchy của TabLayout.
     */
    private void setupTabThumb() {
        if (tabLayout == null || tabSlidingThumb == null) return;

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
            public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
            @Override
            public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
        });
    }

    private void updateTabThumb(int position, boolean animate) {
        if (tabLayout == null || tabLayout.getTabCount() == 0 || tabSlidingThumb == null) return;
        com.google.android.material.tabs.TabLayout.Tab tab = tabLayout.getTabAt(position);
        if (tab == null || tab.view == null) return;

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
        sessionManager = new SessionManager(requireContext());
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
        if (isOwner) {
            // [PHASE 2] Chỉ Owner mới dùng ảnh đã crop thủ công từ cache
            File croppedFile = new File(requireContext().getCacheDir(), com.vn.jet.mosco.utils.AppConfig.AVATAR_CROP_CACHE_NAME);
            if (croppedFile.exists()) {
                Glide.with(this)
                        .load(croppedFile)
                        .skipMemoryCache(true)
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                        .circleCrop()
                        .into(ivAvatar);
                return;
            }
        }

        String avatarId = sessionManager.getAvatarId();
        if (avatarId == null)
            avatarId = com.vn.jet.mosco.utils.AppConfig.DEFAULT_AVATAR_ID; // Mặc định từ AppConfig
            
        loadAvatarById(avatarId);
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
        // Áp dụng Click Debounce 500ms theo yêu cầu Tech Lead
        v.findViewById(R.id.btn_follow).setOnClickListener(new com.vn.jet.mosco.utils.ClickDebounce(500, view -> {
            Toast.makeText(requireContext(), getString(R.string.profile_msg_followed), Toast.LENGTH_SHORT).show();
            // Optimistic UI: Đổi nút sang "Following" (Ví dụ)
        }));
        
        v.findViewById(R.id.btn_add_friend).setOnClickListener(new com.vn.jet.mosco.utils.ClickDebounce(500, view -> {
            Toast.makeText(requireContext(), getString(R.string.profile_msg_friend_request_sent), Toast.LENGTH_SHORT).show();
        }));

        v.findViewById(R.id.btn_direct_message).setOnClickListener(new com.vn.jet.mosco.utils.ClickDebounce(500, view -> {
             Toast.makeText(requireContext(), getString(R.string.profile_msg_chat_coming_soon_toast), Toast.LENGTH_SHORT).show();
        }));
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

    // [DEPRECATED PHASE 3 Dialog]
    private void showEditProfileDialog() {
        // Redundant - functionality merged into inline Edit Mode (saveEditChanges)
    }

    private void fetchUserStats() {
        // Logic này đã được chuyển vào ProfileViewModel
    }

    private void showLogoutConfirmationDialog() {
        if (requireContext() == null)
            return;

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_logout_confirm, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_logout).setOnClickListener(v -> {
            dialog.dismiss();
            sessionManager.clearSession();
            Intent intent = new Intent(getActivity(), SignInActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            if (getActivity() != null) {
                getActivity().finish();
                getActivity().overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        });

        dialog.show();
    }

    /**
     * Mở danh sách Objet để chọn avatar mới.
     * Tự động chuyển đổi sang URL variant "original" để đảm bảo chất lượng crop đạt chuẩn (không dùng thumbnail).
     */
    private void openAvatarPicker() {
        InventoryBottomSheet inventorySheet = new InventoryBottomSheet();
        inventorySheet.setOnObjetSelectedListener(objet -> {
            if (objet != null && objet.getImageUrl() != null) {
                // Đảm bảo dùng ảnh gốc để crop đạt chuẩn chất lượng cao
                String originalUrl = convertToOriginalUrl(objet.getImageUrl());
                startManualCrop(originalUrl, objet.getCollectionId());
            }
        });
        inventorySheet.show(getChildFragmentManager(), "InventoryBottomSheet");
    }

    private String convertToOriginalUrl(String url) {
        if (url == null) return null;
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
        File destinationFile = new File(requireContext().getCacheDir(), com.vn.jet.mosco.utils.AppConfig.AVATAR_CROP_CACHE_NAME);
        Uri destinationUri = Uri.fromFile(destinationFile);

        com.yalantis.ucrop.UCrop.Options options = new com.yalantis.ucrop.UCrop.Options();
        options.setCompressionQuality(90);
        options.setToolbarColor(getResources().getColor(R.color.mosco_surface_container_high));
        options.setStatusBarColor(getResources().getColor(R.color.mosco_screen_bg));
        options.setToolbarWidgetColor(android.graphics.Color.WHITE);
        options.setActiveControlsWidgetColor(getResources().getColor(R.color.mosco_primary));
        
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
                    // Áp dụng avatar mới trực tiếp lên preview, không cần header xác nhận
                    this.lastCroppedUri = resultUri;
                    Glide.with(this)
                            .load(resultUri)
                            .skipMemoryCache(true)
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                            .circleCrop()
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
        if (tvPreviewHeaderLabel != null) tvPreviewHeaderLabel.setText(getString(R.string.profile_edit_mode_label));
        if (previewHeader != null) previewHeader.setVisibility(View.VISIBLE);

        // Phủ mờ avatar và hiện icon chỉnh sửa
        if (viewAvatarDim != null) viewAvatarDim.setVisibility(View.VISIBLE);
        if (ivAvatarEditIcon != null) ivAvatarEditIcon.setVisibility(View.VISIBLE);

        // Ẩn nút edit mode để tránh bấm trùng
        if (btnEditMode != null) btnEditMode.setVisibility(View.GONE);

        // Thông báo các tab hiện các trường edit
        notifyTabsEditMode(true);
    }

    /**
     * Thoát chế độ chỉnh sửa và hủy mọi thay đổi
     */
    private void discardEditMode() {
        isEditMode = false;
        if (previewHeader != null) previewHeader.setVisibility(View.GONE);

        // Khôi phục avatar về trạng thái gốc
        if (viewAvatarDim != null) viewAvatarDim.setVisibility(View.GONE);
        if (ivAvatarEditIcon != null) ivAvatarEditIcon.setVisibility(View.GONE);
        lastCroppedUri = null;

        // Rollback avatar về ảnh cũ
        if (savedAvatarIdBeforeEdit != null) {
            sessionManager.setAvatarId(savedAvatarIdBeforeEdit);
        }
        // Xóa file crop tạm để loadAvatar lấy lại ảnh gốc
        File croppedFile = new File(requireContext().getCacheDir(), com.vn.jet.mosco.utils.AppConfig.AVATAR_CROP_CACHE_NAME);
        if (croppedFile.exists()) croppedFile.delete();
        loadAvatar();

        // Hiện lại nút edit
        if (btnEditMode != null && isOwner) btnEditMode.setVisibility(View.VISIBLE);

        // Ẩn các trường edit trong các tab
        notifyTabsEditMode(false);
    }

    /**
     * Hiện dialog xác nhận hủy thay đổi (đồng bộ style với dialog_logout_confirm)
     */
    private void showDiscardDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_discard_changes, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_discard).setOnClickListener(v -> {
            dialog.dismiss();
            discardEditMode();
        });

        dialog.show();
    }

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
            Toast.makeText(requireContext(), getString(R.string.setup_error_display_name_length), Toast.LENGTH_SHORT).show();
            return;
        }

        // Gọi API update profile nếu có thay đổi
        com.vn.jet.mosco.network.UpdateProfileRequest request = new com.vn.jet.mosco.network.UpdateProfileRequest();
        if (newUsername != null && !newUsername.isEmpty()) request.setUsername(newUsername);
        if (newDisplayName != null && !newDisplayName.isEmpty()) request.setIngameName(newDisplayName);
        if (newBio != null) request.setBio(newBio);

        // Đồng bộ avatarId nếu đã thay đổi
        String currentAvatarId = sessionManager.getAvatarId();
        if (currentAvatarId != null) request.setAvatarId(currentAvatarId);

        if (gameApiService != null) {
            gameApiService.updateProfile(request).enqueue(new Callback<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.UserStats>>() {
                @Override
                public void onResponse(Call<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.UserStats>> call, Response<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.UserStats>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        // Cập nhật Session local
                        if (newDisplayName != null && !newDisplayName.isEmpty()) {
                            sessionManager.setIngameName(newDisplayName);
                            tvUsername.setText(newDisplayName);
                        }
                        if (newUsername != null && !newUsername.isEmpty()) {
                            sessionManager.setUsername(newUsername);
                        }
                        
                        // [CRITICAL] Đồng bộ ViewModel và Local DB
                        if (viewModel != null && targetUserId != null) {
                            if (response.body().getData() != null) {
                                com.vn.jet.mosco.utils.AppExecutors.getInstance().diskIO().execute(() -> {
                                    com.vn.jet.mosco.database.AppDatabase.getInstance(requireContext())
                                            .userStatsDao().insertUserStats(response.body().getData());
                                });
                            }
                            viewModel.refreshUserStats(targetUserId);
                        }
                        
                        Toast.makeText(requireContext(), getString(R.string.common_msg_success), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), getString(R.string.common_error_network), Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.UserStats>> call, Throwable t) {
                    Toast.makeText(requireContext(), getString(R.string.common_error_network), Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Thoát Edit Mode (giữ lại thay đổi)
        isEditMode = false;
        if (previewHeader != null) previewHeader.setVisibility(View.GONE);
        if (viewAvatarDim != null) viewAvatarDim.setVisibility(View.GONE);
        if (ivAvatarEditIcon != null) ivAvatarEditIcon.setVisibility(View.GONE);
        if (btnEditMode != null && isOwner) btnEditMode.setVisibility(View.VISIBLE);
        notifyTabsEditMode(false);
    }

    /**
     * Thông báo các tab về trạng thái Edit Mode
     */
    private void notifyTabsEditMode(boolean editMode) {
        ProfileGeneralFragment general = getGeneralFragment();
        if (general != null) general.setEditMode(editMode);
        
        ProfileExhibitFragment exhibit = getExhibitFragment();
        if (exhibit != null) exhibit.setEditMode(editMode);
    }

    /**
     * Lấy instance của ProfileGeneralFragment từ ViewPager
     */
    @Nullable
    private ProfileGeneralFragment getGeneralFragment() {
        if (viewPager == null || viewPager.getAdapter() == null) return null;
        // Tab General là position 0. Trong ViewPager2, tag của fragment là "f" + position
        Fragment frag = getChildFragmentManager().findFragmentByTag("f0");
        if (frag instanceof ProfileGeneralFragment) {
            return (ProfileGeneralFragment) frag;
        }
        return null;
    }

    @Nullable
    private ProfileExhibitFragment getExhibitFragment() {
        if (viewPager == null || viewPager.getAdapter() == null) return null;
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
        if (previewHeader != null) previewHeader.setVisibility(View.GONE);
        if (blockingOverlay != null) blockingOverlay.setVisibility(View.GONE);
        
        if (btnMenu != null) btnMenu.setEnabled(true);
        if (avatarCard != null) avatarCard.setEnabled(true);
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

        gameApiService.updateProfile(request).enqueue(new Callback<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.UserStats>>() {
            @Override
            public void onResponse(Call<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.UserStats>> call, Response<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.UserStats>> response) {
                if (response.isSuccessful()) {
                    Log.d("ProfileFragment", "Avatar ID synced to server");
                }
            }

            @Override
            public void onFailure(Call<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.UserStats>> call, Throwable t) {
                Log.e("ProfileFragment", "Failed to sync avatar ID", t);
            }
        });
    }

    /**
     * Hiển thị Dialog phóng to Avatar với nền mờ Glassmorphism.
     */
    private void showAvatarZoomDialog() {
        String avatarId = sessionManager.getAvatarId();
        org.json.JSONObject card = com.vn.jet.mosco.utils.DatabaseLoader.findByCollectionId(requireContext(), avatarId);
        if (card == null)
            return;

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_avatar_zoom, null);
        ImageView ivZoom = dialogView.findViewById(R.id.iv_avatar_zoom);

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0.85f);
        }

        Glide.with(this)
                .load(card.optString("frontImage"))
                .transform(new com.vn.jet.mosco.utils.SmartFaceCropTransformation())
                .into(ivZoom);

        dialogView.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    /**
     * Phân tích và trích xuất thông báo lỗi từ Server để hiển thị thân thiện với người dùng
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