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

    private TextView tvUsername, tvEmail, tvCoins, tvDiamonds, tvLevel;
    private ImageView ivAvatar;
    private View btnLogout, btnChangePassword, btnInventory, btnEditProfile, avatarCard, btnEditAvatar, btnSettings;
    private SessionManager sessionManager;
    private GameApiService gameApiService;

    public ProfileFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        initViews(view);
        setupSession();
        setupListeners();
        fetchUserStats();
        return view;
    }

    private void initViews(View v) {
        tvUsername = v.findViewById(R.id.tv_username);
        tvEmail = v.findViewById(R.id.tv_email);
        tvCoins = v.findViewById(R.id.tv_coins);
        tvDiamonds = v.findViewById(R.id.tv_diamonds);
        tvLevel = v.findViewById(R.id.tv_level);
        btnLogout = v.findViewById(R.id.btn_logout);
        btnChangePassword = v.findViewById(R.id.btn_change_password);
        btnInventory = v.findViewById(R.id.btn_inventory);
        btnEditProfile = v.findViewById(R.id.btn_edit_profile);
        ivAvatar = v.findViewById(R.id.iv_avatar);
        avatarCard = v.findViewById(R.id.avatar_card);
        btnEditAvatar = v.findViewById(R.id.btn_edit_avatar);
        btnSettings = v.findViewById(R.id.btn_settings);
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
        String avatarId = sessionManager.getAvatarId();
        if (avatarId == null) avatarId = "1"; // Fallback default

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
        btnLogout.setOnClickListener(v -> showLogoutConfirmationDialog());
        
        // --- 🔍 ZOOM LOGIC ---
        // Ấn vào khung Avatar để phóng to xem ảnh
        avatarCard.setOnClickListener(v -> showAvatarZoomDialog());

        // --- ✏️ EDIT LOGIC ---
        // Ấn vào nút "Cây viết" để đổi ảnh
        if (btnEditAvatar != null) {
            btnEditAvatar.setOnClickListener(v -> openAvatarPicker());
        }

        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> {
                SettingsBottomSheet settingsBottomSheet = new SettingsBottomSheet();
                settingsBottomSheet.show(getChildFragmentManager(), "SettingsBottomSheet");
            });
        }

        btnChangePassword.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), ForgotPasswordActivity.class);
            startActivity(intent);
        });

        btnInventory.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                BottomNavigationView nav = getActivity().findViewById(R.id.bottom_navigation);
                if (nav != null) {
                    nav.setSelectedItemId(R.id.nav_collect);
                }
            }
        });

        // Nút Edit Profile mới
        if (btnEditProfile != null) {
            btnEditProfile.setOnClickListener(v -> showEditProfileDialog());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  EDIT PROFILE DIALOG
    // ════════════════════════════════════════════════════════════════

    private void showEditProfileDialog() {
        if (requireContext() == null) return;

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
                        Toast.makeText(requireContext(), getString(R.string.common_msg_success), Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(requireContext(), getString(R.string.common_error_network), Toast.LENGTH_SHORT).show();
                }
            });
        });

        dialog.show();
    }

    private void fetchUserStats() {
        Long userId = sessionManager.getUserId();
        if (userId == null) return;

        gameApiService.getUserStats(userId).enqueue(new Callback<UserStats>() {
            @Override
            public void onResponse(Call<UserStats> call, Response<UserStats> response) {
                if (response.isSuccessful() && response.body() != null) {
                    UserStats stats = response.body();
                    tvCoins.setText(NumberUtils.format(requireContext(), stats.getCoins() != null ? stats.getCoins() : 0));
                    tvDiamonds.setText(NumberUtils.format(requireContext(), stats.getDiamonds() != null ? stats.getDiamonds() : 0));
                    if (tvLevel != null) {
                        tvLevel.setText(getString(R.string.format_level_short, stats.getLevel()));
                    }
                }
            }

            @Override
            public void onFailure(Call<UserStats> call, Throwable t) {
                Log.e("ProfileFragment", "Error fetching stats", t);
            }
        });
    }

    private void showLogoutConfirmationDialog() {
        if (requireContext() == null) return;
        
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

    private void openAvatarPicker() {
        AvatarSelectorBottomSheet sheet = new AvatarSelectorBottomSheet(sessionManager.getAvatarId(), this);
        sheet.show(getChildFragmentManager(), "AvatarSelectorBottomSheet");
    }

    @Override
    public void onAvatarSelected(String collectionId) {
        if (collectionId == null) return;

        // Gọi API update-profile để lưu avatarId lên Server
        Map<String, String> body = new HashMap<>();
        body.put("avatarId", collectionId);

        gameApiService.updateProfile(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    sessionManager.setAvatarId(collectionId);
                    loadAvatar(); // Refresh UI ngay lập tức
                    Toast.makeText(requireContext(), getString(R.string.common_msg_success), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), getString(R.string.common_msg_system_error), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(requireContext(), getString(R.string.common_error_network), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Phế truất các hàm cũ không còn dùng (vì không chọn từ Gallery nữa)
    private void startCrop(Uri sourceUri) {}

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != android.app.Activity.RESULT_OK) return;

        if (requestCode == 1001 && data != null) {
            Uri selectedImage = data.getData();
            if (selectedImage != null) {
                startCrop(selectedImage);
            }
        } else if (requestCode == com.yalantis.ucrop.UCrop.REQUEST_CROP) {
            Uri resultUri = com.yalantis.ucrop.UCrop.getOutput(data);
            if (resultUri != null) {
                // Đã lưu vào đường dẫn cố định, chỉ cần load lại UI
                loadAvatar();
            }
        } else if (resultCode == com.yalantis.ucrop.UCrop.RESULT_ERROR) {
            Throwable cropError = com.yalantis.ucrop.UCrop.getError(data);
            Log.e("ProfileFragment", "Lỗi Crop ảnh", cropError);
        }
    }

    /**
     * Hiển thị Dialog phóng to Avatar với nền mờ Glassmorphism.
     */
    private void showAvatarZoomDialog() {
        String avatarId = sessionManager.getAvatarId();
        org.json.JSONObject card = com.vn.jet.mosco.utils.DatabaseLoader.findByCollectionId(requireContext(), avatarId);
        if (card == null) return;

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
     * Parse message lỗi từ Server response body.
     */
    private String parseServerError(Response<ResponseBody> response) {
        try {
            if (response.errorBody() != null) {
                String body = response.errorBody().string();
                JSONObject json = new JSONObject(body);
                String msg = json.optString("message", "");
                if (!msg.isEmpty()) return msg;
            }
        } catch (Exception ignored) {}
        return "An error occurred. Please try again.";
    }
}