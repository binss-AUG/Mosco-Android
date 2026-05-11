package com.vn.jet.mosco.fragment;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.vn.jet.mosco.ProfileViewModel;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.model.UserStats;
import com.vn.jet.mosco.utils.SessionManager;

/**
 * Tab General trong Profile V2.
 * Hiển thị thông tin cơ bản: Bio, Joined Date, UID, Email.
 * Khi Edit Mode bật: hiện thêm Username, Display Name có thể sửa.
 */
public class ProfileGeneralFragment extends Fragment {

    private TextView tvUid, tvJoinedDate, tvEmail;
    private EditText edtBio, edtDisplayName, edtUsername;
    private View layoutDisplayName, layoutUsername;
    private ImageButton btnCopyUid, btnCopyEmail;
    private ProfileViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile_general, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        tvUid = view.findViewById(R.id.tv_uid);
        edtBio = view.findViewById(R.id.edt_bio);
        tvJoinedDate = view.findViewById(R.id.tv_joined_date);
        tvEmail = view.findViewById(R.id.tv_email);
        btnCopyUid = view.findViewById(R.id.btn_copy_uid);
        btnCopyEmail = view.findViewById(R.id.btn_copy_email);
        edtDisplayName = view.findViewById(R.id.edt_display_name);
        edtUsername = view.findViewById(R.id.edt_username);
        layoutDisplayName = view.findViewById(R.id.layout_display_name);
        layoutUsername = view.findViewById(R.id.layout_username);

        // Lấy ProfileViewModel từ parent (ProfileFragment) để share chung dữ liệu
        if (getParentFragment() != null) {
            viewModel = new ViewModelProvider(getParentFragment()).get(ProfileViewModel.class);
            viewModel.getUserStats().observe(getViewLifecycleOwner(), this::renderData);
        }

        // Sao chép UID vào clipboard
        btnCopyUid.setOnClickListener(v -> {
            String uidStr = tvUid.getText().toString();
            if (!uidStr.isEmpty()) {
                copyToClipboard("UID", uidStr, getString(R.string.profile_msg_copied_uid));
            }
        });

        // Sao chép Email vào clipboard
        btnCopyEmail.setOnClickListener(v -> {
            String emailStr = tvEmail.getText().toString();
            if (!emailStr.isEmpty()) {
                copyToClipboard("Email", emailStr, getString(R.string.profile_msg_copied_email));
            }
        });
    }

    private void renderData(UserStats stats) {
        if (stats == null) return;
        
        // Công thức ID đồng bộ với HomeFragment: 10000000L + userId
        long idDisplay = 10000000L + stats.getId();
        tvUid.setText(String.valueOf(idDisplay));
        
        // Fallback cho Bio và Joined Date
        edtBio.setText(stats.getBio() != null && !stats.getBio().isEmpty() ? stats.getBio() : getString(R.string.profile_fallback_bio));
        tvJoinedDate.setText(stats.getJoinedDate() != null && !stats.getJoinedDate().isEmpty() ? stats.getJoinedDate() : getString(R.string.profile_fallback_joined_date));

        // Email: Owner hiện thật, Guest hiện placeholder
        boolean isOwner = isOwnerProfile();
        tvEmail.setText(isOwner ? stats.getEmail() : getString(R.string.profile_email_placeholder));
        if (btnCopyEmail != null) {
            btnCopyEmail.setVisibility(isOwner ? View.VISIBLE : View.GONE);
        }

        // Visibility handling: Only show Display Name and Username to the Owner
        if (layoutDisplayName != null) layoutDisplayName.setVisibility(isOwner ? View.VISIBLE : View.GONE);
        if (layoutUsername != null) layoutUsername.setVisibility(isOwner ? View.VISIBLE : View.GONE);

        // Pre-fill edit fields
        if (edtDisplayName != null) {
            String ingame = stats.getIngameName();
            edtDisplayName.setText(ingame != null ? ingame : "");
        }
        if (isOwner && edtUsername != null) {
            edtUsername.setText(stats.getUsername() != null ? stats.getUsername() : "");
        }
    }

    /**
     * Bật chế độ chỉnh sửa: cho phép sửa Bio, Username và Display Name
     */
    public void setEditMode(boolean editMode) {
        boolean isOwner = isOwnerProfile();
        if (edtBio != null) edtBio.setEnabled(editMode && isOwner);
        if (edtDisplayName != null) edtDisplayName.setEnabled(editMode && isOwner);
        if (edtUsername != null) edtUsername.setEnabled(editMode && isOwner);
        
        // Nếu là edit mode, focus vào bio
        if (editMode && isOwner && edtBio != null) {
            edtBio.requestFocus();
        }
    }

    /**
     * Lấy Bio người dùng đã nhập
     */
    @Nullable
    public String getEditedBio() {
        if (edtBio != null && edtBio.getText() != null) {
            return edtBio.getText().toString().trim();
        }
        return null;
    }

    /**
     * Lấy Display Name người dùng đã nhập
     */
    @Nullable
    public String getEditedDisplayName() {
        if (edtDisplayName != null && edtDisplayName.getText() != null) {
            return edtDisplayName.getText().toString().trim();
        }
        return null;
    }

    /**
     * Lấy Username người dùng đã nhập
     */
    @Nullable
    public String getEditedUsername() {
        if (edtUsername != null && edtUsername.getText() != null) {
            return edtUsername.getText().toString().trim();
        }
        return null;
    }

    /**
     * Kiểm tra xem Profile hiện tại có phải Owner hay không
     */
    private boolean isOwnerProfile() {
        SessionManager sm = new SessionManager(requireContext());
        Long currentId = sm.getUserId();
        if (viewModel != null && viewModel.getUserStats().getValue() != null) {
            return currentId != null && currentId == viewModel.getUserStats().getValue().getId();
        }
        return true;
    }

    /**
     * Hàm tiện ích sao chép text vào clipboard, tránh lặp logic (DRY)
     */
    private void copyToClipboard(String label, String text, String toastMsg) {
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(requireContext(), toastMsg, Toast.LENGTH_SHORT).show();
        }
    }
}
