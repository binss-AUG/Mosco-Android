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

    private static final long MENU_DEBOUNCE_MS = 500;
    public static final String ARG_TARGET_USER_ID = "target_user_id";

    private TextView tvUsername, tvEmail, tvLevel;
    private ImageView ivAvatar;
    private View avatarCard, btnEditAvatar, btnMenu, btnBack;
    private View previewHeader, blockingOverlay;
    private View btnPreviewCancel, btnPreviewConfirm;
    private View layoutProfileContent;
    private ViewStub stubShimmer;
    private View inflatedShimmer;
    private SessionManager sessionManager;
    private GameApiService gameApiService;
    private ProfileViewModel viewModel;

    private Long targetUserId;
    private boolean isOwner;
    private String lastImageUrl; // URL ảnh gốc trước khi crop để quay lại
    private Uri lastCroppedUri; // URI ảnh sau khi crop để confirm
    private long lastMenuClickTime = 0;

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
        if (getArguments() != null) {
            targetUserId = getArguments().getLong(ARG_TARGET_USER_ID, -1L);
            if (targetUserId == -1L) targetUserId = null;
        }

        Long currentUserId = sessionManager.getUserId();
        
        // Nếu không truyền ID hoặc ID khớp với User hiện tại -> Là Owner
        isOwner = (targetUserId == null || targetUserId.equals(currentUserId));
        
        if (targetUserId == null) {
            targetUserId = currentUserId;
        }

        // Kiểm tra Null Safety nghiêm ngặt theo đặc tả
        if (targetUserId == null) {
            Log.e("ProfileFragment", "TARGET_USER_ID is null and no session found");
            // Hiển thị thông báo hoặc đóng fragment nếu cần
            Toast.makeText(requireContext(), "Không tìm thấy người dùng", Toast.LENGTH_SHORT).show();
        }
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
            ViewStub stub = view.findViewById(R.id.stub_owner_actions);
            if (stub != null) {
                View inflated = stub.inflate();
                setupOwnerListeners(inflated);
            }
            if (btnMenu != null) btnMenu.setVisibility(View.VISIBLE);
            if (btnEditAvatar != null) btnEditAvatar.setVisibility(View.VISIBLE);
        } else {
            ViewStub stub = view.findViewById(R.id.stub_guest_actions);
            if (stub != null) {
                View inflated = stub.inflate();
                setupGuestListeners(inflated);
            }
            // Guest không được sửa avatar hay mở menu hệ thống
            if (btnMenu != null) btnMenu.setVisibility(View.GONE);
            if (btnEditAvatar != null) btnEditAvatar.setVisibility(View.GONE);
        }
    }

    private void renderProfileData(com.vn.jet.mosco.model.UserStats stats) {
        tvUsername.setText(stats.getIngameName() != null ? stats.getIngameName() : stats.getUsername());
        tvEmail.setText(isOwner ? stats.getEmail() : getString(R.string.profile_email_placeholder)); // Ẩn email nếu là Guest
        tvLevel.setText(getString(R.string.format_level_short, stats.getLevel()));
        
        // Load avatar từ URL trong stats nếu có
        if (stats.getAvatarId() != null) {
             loadAvatarById(stats.getAvatarId());
        }
    }

    private void loadAvatarById(String avatarId) {
        org.json.JSONObject card = com.vn.jet.mosco.utils.DatabaseLoader.findByCollectionId(requireContext(), avatarId);
        if (card != null) {
            String imageUrl = card.optString("frontImage");
            Glide.with(this)
                    .load(imageUrl)
                    .transform(new com.vn.jet.mosco.utils.SmartFaceCropTransformation())
                    .placeholder(R.drawable.ic_user)
                    .error(R.drawable.ic_user)
                    .into(ivAvatar);
        }
    }

    private void initViews(View v) {
        tvUsername = v.findViewById(R.id.tv_username);
        tvEmail = v.findViewById(R.id.tv_email);
        tvLevel = v.findViewById(R.id.tv_level);
        btnMenu = v.findViewById(R.id.btn_menu);
        ivAvatar = v.findViewById(R.id.iv_avatar);
        avatarCard = v.findViewById(R.id.avatar_card);
        btnEditAvatar = v.findViewById(R.id.btn_edit_avatar);
        btnBack = v.findViewById(R.id.btn_back);

        // [PHASE 5] Preview Views
        previewHeader = v.findViewById(R.id.layout_preview_header);
        blockingOverlay = v.findViewById(R.id.view_blocking_overlay);
        btnPreviewCancel = v.findViewById(R.id.btn_preview_cancel);
        btnPreviewConfirm = v.findViewById(R.id.btn_preview_confirm);
        layoutProfileContent = v.findViewById(R.id.layout_profile_content);
        stubShimmer = v.findViewById(R.id.stub_profile_shimmer);
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
        tvEmail.setText(sessionManager.getEmail());

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

        org.json.JSONObject card = com.vn.jet.mosco.utils.DatabaseLoader.findByCollectionId(requireContext(), avatarId);
        if (card != null) {
            String imageUrl = card.optString("frontImage");
            Glide.with(this)
                    .load(imageUrl)
                    .transform(new com.vn.jet.mosco.utils.SmartFaceCropTransformation())
                    .placeholder(R.drawable.ic_user)
                    .error(R.drawable.ic_user)
                    .into(ivAvatar);
        } else {
            // Fallback nếu không tìm thấy Objet trong JSON
            ivAvatar.setImageResource(R.drawable.ic_user);
        }
    }

    private void setupListeners() {
        if (btnMenu != null) {
            btnMenu.setOnClickListener(v -> openProfileMenu());
        }
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> handleBackAction());
        }
        avatarCard.setOnClickListener(v -> showAvatarZoomDialog());
        if (btnEditAvatar != null) {
            btnEditAvatar.setOnClickListener(v -> openAvatarPicker());
        }
        
        // [PHASE 5] Inline Preview Listeners
        if (btnPreviewCancel != null) {
            btnPreviewCancel.setOnClickListener(v -> cancelAvatarPreview());
        }
        if (btnPreviewConfirm != null) {
            btnPreviewConfirm.setOnClickListener(v -> confirmAvatarPreview());
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

    private void setupOwnerListeners(View v) {
        v.findViewById(R.id.btn_edit_profile).setOnClickListener(view -> showEditProfileDialog());
        v.findViewById(R.id.btn_settings).setOnClickListener(view -> {
             new SettingsBottomSheet().show(getChildFragmentManager(), "SettingsBottomSheet");
        });
        v.findViewById(R.id.btn_resource_management).setOnClickListener(view -> {
            // Chuyển sang màn hình quản lý kho đồ
             Toast.makeText(requireContext(), "Coming Soon: Resource Management", Toast.LENGTH_SHORT).show();
        });
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
            public void onEditProfile() {
                showEditProfileDialog();
            }

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

    private void showEditProfileDialog() {
        if (requireContext() == null)
            return;

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_profile, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Bind fields
        TextInputEditText edtUsername = dialogView.findViewById(R.id.edt_edit_username);
        TextInputEditText edtEmail = dialogView.findViewById(R.id.edt_edit_email);
        TextInputEditText edtDisplayName = dialogView.findViewById(R.id.edt_edit_display_name);
        TextInputLayout tilUsername = dialogView.findViewById(R.id.til_edit_username);
        TextInputLayout tilDisplayName = dialogView.findViewById(R.id.til_edit_display_name);

        // Pre-fill dữ liệu hiện tại
        edtUsername.setText(sessionManager.getUsername());
        edtEmail.setText(sessionManager.getEmail());
        String currentIngame = sessionManager.getIngameName();
        if (currentIngame != null) {
            edtDisplayName.setText(currentIngame);
        }

        // Cancel
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());

        // Save — gọi API update-profile
        dialogView.findViewById(R.id.btn_save).setOnClickListener(v -> {
            String newUsername = edtUsername.getText() != null ? edtUsername.getText().toString().trim() : "";
            String newDisplayName = edtDisplayName.getText() != null ? edtDisplayName.getText().toString().trim() : "";

            // Validate client-side (Server vẫn validate lại)
            tilUsername.setError(null);
            tilDisplayName.setError(null);

            if (newUsername.isEmpty()) {
                tilUsername.setError(getString(R.string.auth_error_empty_field));
                return;
            }
            if (newDisplayName.isEmpty()) {
                tilDisplayName.setError(getString(R.string.auth_error_empty_field));
                return;
            }
            if (newDisplayName.length() < 2 || newDisplayName.length() > 16) {
                tilDisplayName.setError(getString(R.string.setup_error_display_name_length));
                return;
            }

            // Gọi API
            Map<String, String> body = new HashMap<>();
            body.put("username", newUsername);
            body.put("ingameName", newDisplayName);

            gameApiService.updateProfile(body).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    if (response.isSuccessful()) {
                        // Cập nhật Session local
                        sessionManager.setIngameName(newDisplayName);
                        // Cập nhật UI
                        tvUsername.setText(newDisplayName);
                        Toast.makeText(requireContext(), getString(R.string.common_msg_success), Toast.LENGTH_SHORT)
                                .show();
                        dialog.dismiss();
                    } else {
                        // Parse lỗi từ Server
                        String errorMsg = parseServerError(response);
                        // Hiển thị lỗi vào field phù hợp
                        if (errorMsg.toLowerCase().contains("username")) {
                            tilUsername.setError(errorMsg);
                        } else {
                            tilDisplayName.setError(errorMsg);
                        }
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    Toast.makeText(requireContext(), getString(R.string.common_error_network), Toast.LENGTH_SHORT)
                            .show();
                }
            });
        });

        dialog.show();
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
     * Mở màn hình chọn Objet từ kho đồ để làm phôi cho Avatar
     */
    private void openAvatarPicker() {
        InventoryBottomSheet inventorySheet = new InventoryBottomSheet();
        inventorySheet.setOnObjetSelectedListener(objet -> {
            if (objet != null && objet.getImageUrl() != null) {
                startManualCrop(objet.getImageUrl(), objet.getCollectionId());
            }
        });
        inventorySheet.show(getChildFragmentManager(), "InventoryBottomSheet");
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
                    // [PHASE 5] Chuyển sang chế độ xác nhận Inline thay vì Dialog
                    enterConfirmationMode(resultUri);
                }
            } else if (resultCode == android.app.Activity.RESULT_CANCELED) {
                // [PHASE 3] Nếu hủy, quay lại màn hình chọn Objet (Bottom Sheet)
                openAvatarPicker();
            } else if (resultCode == com.yalantis.ucrop.UCrop.RESULT_ERROR && data != null) {
                Throwable cropError = com.yalantis.ucrop.UCrop.getError(data);
                Log.e("ProfileFragment", "Lỗi Crop ảnh", cropError);
            }
        }
    }

    /**
     * Kích hoạt chế độ duyệt thử (Inline Preview) sau khi crop ảnh thành công
     */
    private void enterConfirmationMode(Uri croppedUri) {
        this.lastCroppedUri = croppedUri;
        
        // Hiện Header xác nhận và chặn tương tác phía dưới để người dùng tập trung duyệt ảnh
        if (previewHeader != null) previewHeader.setVisibility(View.VISIBLE);
        if (blockingOverlay != null) blockingOverlay.setVisibility(View.VISIBLE);
        
        // [FIX] Vô hiệu hóa các nút điều hướng chính để tránh hiện tượng spam/chạm xuyên thấu
        if (btnMenu != null) btnMenu.setEnabled(false);
        if (btnEditAvatar != null) btnEditAvatar.setEnabled(false);
        if (avatarCard != null) avatarCard.setEnabled(false);
        
        // Cập nhật Avatar preview ngay trên Profile để người dùng thấy diện mạo tổng thể
        Glide.with(this)
                .load(croppedUri)
                .skipMemoryCache(true)
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                .circleCrop()
                .into(ivAvatar);
        
        // Thông báo cho người dùng về trạng thái hiện tại
        Toast.makeText(requireContext(), getString(R.string.profile_preview_confirm_msg), Toast.LENGTH_SHORT).show();
    }

    private void exitConfirmationMode() {
        // Ẩn UI xác nhận
        if (previewHeader != null) previewHeader.setVisibility(View.GONE);
        if (blockingOverlay != null) blockingOverlay.setVisibility(View.GONE);
        
        // Khôi phục tương tác
        if (btnMenu != null) btnMenu.setEnabled(true);
        if (btnEditAvatar != null) btnEditAvatar.setEnabled(true);
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
        Map<String, String> body = new HashMap<>();
        body.put("avatarId", avatarId);

        gameApiService.updateProfile(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Log.d("ProfileFragment", "Avatar ID synced to server");
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
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