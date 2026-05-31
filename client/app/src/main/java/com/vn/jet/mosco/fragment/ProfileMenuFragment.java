package com.vn.jet.mosco.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.utils.SessionManager;

import java.io.File;

public class ProfileMenuFragment extends Fragment {

    public interface OnMenuActionListener {
        void onSwitchAccount();
        void onBackupData();
        void onRestoreData();
        void onCloudSync();
        void onLogout();
    }

    private OnMenuActionListener listener;
    private SessionManager sessionManager;

    private SwitchMaterial switchDarkMode, switchMusic, switchSfx, switchAutoBackup, switchNotiChat, switchNotiStreak;
    private TextView tvCacheSize, tvBackupInterval;
    private View btnClearCache, layoutBackupInterval;
    private com.vn.jet.mosco.widget.MoscoButton btnChangeLanguage;
    private View menuReportBug, btnPrivacyPolicy, btnTermsOfService, btnOpenSource, btnDeleteAccount;

    public void setOnMenuActionListener(OnMenuActionListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile_menu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        sessionManager = new SessionManager(requireContext());

        // Back button
        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (getParentFragmentManager() != null) {
                getParentFragmentManager().popBackStack();
            }
        });

        // Mapping Views
        switchDarkMode = view.findViewById(R.id.switch_dark_mode);
        switchMusic = view.findViewById(R.id.switch_music);
        switchSfx = view.findViewById(R.id.switch_sfx);
        switchAutoBackup = view.findViewById(R.id.switch_auto_backup);
        tvCacheSize = view.findViewById(R.id.tv_cache_size);
        tvBackupInterval = view.findViewById(R.id.tv_backup_interval);
        layoutBackupInterval = view.findViewById(R.id.layout_backup_interval);
        btnClearCache = view.findViewById(R.id.btn_clear_cache);
        btnChangeLanguage = view.findViewById(R.id.btn_change_language);
        
        switchNotiChat = view.findViewById(R.id.switch_noti_chat);
        switchNotiStreak = view.findViewById(R.id.switch_noti_streak);
        menuReportBug = view.findViewById(R.id.menu_report_bug);
        btnPrivacyPolicy = view.findViewById(R.id.btn_privacy_policy);
        btnTermsOfService = view.findViewById(R.id.btn_terms_of_service);
        btnOpenSource = view.findViewById(R.id.btn_open_source);
        btnDeleteAccount = view.findViewById(R.id.btn_delete_account);

        setupInitialState();
        setupListeners(view);
        calculateCacheSize();
    }

    private void setupInitialState() {
        switchDarkMode.setChecked(sessionManager.isDarkMode());
        switchMusic.setChecked(sessionManager.isMusicEnabled());
        switchSfx.setChecked(sessionManager.isSfxEnabled());
        switchAutoBackup.setChecked(sessionManager.isAutoBackupEnabled());
        
        if (switchNotiChat != null) {
            switchNotiChat.setChecked(sessionManager.isPrivateChatNotificationEnabled());
        }
        if (switchNotiStreak != null) {
            switchNotiStreak.setChecked(sessionManager.isStreakNotificationEnabled());
        }
        
        updateBackupIntervalUI();
        
        // TẠI SAO: Đặt văn bản hiển thị cho nút ngôn ngữ theo Locale đang hoạt động
        String currentLang = sessionManager.getLanguage();
        if (btnChangeLanguage != null) {
            btnChangeLanguage.setText(currentLang.equals("vi") ? R.string.language_vi : R.string.language_en);
        }
    }

    private void updateBackupIntervalUI() {
        boolean enabled = sessionManager.isAutoBackupEnabled();
        layoutBackupInterval.setVisibility(enabled ? View.VISIBLE : View.GONE);
        
        int interval = sessionManager.getBackupInterval();
        String label;
        if (interval == 6) label = getString(R.string.interval_6h);
        else if (interval == 12) label = getString(R.string.interval_12h);
        else if (interval == 24) label = getString(R.string.interval_24h);
        else if (interval == 72) label = getString(R.string.interval_3d);
        else if (interval == 168) label = getString(R.string.interval_7d);
        else if (interval == 360) label = getString(R.string.interval_15d);
        else if (interval == 720) label = getString(R.string.interval_30d);
        else label = getString(R.string.interval_24h); // Fallback
        
        tvBackupInterval.setText(label);
    }

    private void setupListeners(View view) {
        // --- THEME SWITCH ---
        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sessionManager.setDarkMode(isChecked);
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });

        // --- AUDIO SWITCH ---
        switchMusic.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sessionManager.setMusicEnabled(isChecked);
        });

        switchSfx.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sessionManager.setSfxEnabled(isChecked);
        });

        // --- AUTO BACKUP ---
        switchAutoBackup.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sessionManager.setAutoBackupEnabled(isChecked);
            updateBackupIntervalUI();
            com.vn.jet.mosco.utils.WorkScheduler.scheduleAutoBackup(requireContext());
        });

        tvBackupInterval.setOnClickListener(v -> showIntervalPicker());

        // --- CLEAR CACHE ---
        btnClearCache.setOnClickListener(v -> {
            clearAppCache();
        });

        // --- NOTIFICATIONS ---
        if (switchNotiChat != null) {
            switchNotiChat.setOnCheckedChangeListener((buttonView, isChecked) -> {
                sessionManager.setPrivateChatNotificationEnabled(isChecked);
            });
        }
        if (switchNotiStreak != null) {
            switchNotiStreak.setOnCheckedChangeListener((buttonView, isChecked) -> {
                sessionManager.setStreakNotificationEnabled(isChecked);
            });
        }

        // --- GHOST MENUS WITH LOCALIZATION ---
        setupMenuItem(view.findViewById(R.id.menu_backup_data), 
            getString(R.string.settings_action_backup_data), 
            getString(R.string.settings_desc_backup_data), 
            v -> { if(listener != null) listener.onBackupData(); });

        setupMenuItem(view.findViewById(R.id.menu_restore_data), 
            getString(R.string.settings_action_restore_data), 
            getString(R.string.settings_desc_restore_data), 
            v -> { if(listener != null) listener.onRestoreData(); });

        setupMenuItem(view.findViewById(R.id.menu_cloud_sync), 
            getString(R.string.settings_action_cloud_sync), 
            getString(R.string.settings_desc_cloud_sync), 
            v -> { if(listener != null) listener.onCloudSync(); });

        setupMenuItem(view.findViewById(R.id.menu_switch_account), 
            getString(R.string.settings_action_switch_account), 
            getString(R.string.settings_desc_switch_account), 
            v -> { if(listener != null) listener.onSwitchAccount(); });

        setupMenuItem(menuReportBug, 
            getString(R.string.settings_action_bug_report), 
            getString(R.string.settings_desc_report_bug), 
            v -> showBugReportDialog());

        // --- ACCOUNT ACTIONS ---
        view.findViewById(R.id.btn_change_password).setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), com.vn.jet.mosco.ForgotPasswordActivity.class);
            intent.putExtra("from_settings", true);
            startActivity(intent);
        });

        view.findViewById(R.id.btn_logout).setOnClickListener(v -> {
            if (listener != null) listener.onLogout();
        });

        if (btnDeleteAccount != null) {
            btnDeleteAccount.setOnClickListener(v -> confirmDeleteAccount());
        }

        // --- LEGAL POLICIES ---
        if (btnPrivacyPolicy != null) {
            btnPrivacyPolicy.setOnClickListener(v -> showPrivacyPolicy());
        }
        if (btnTermsOfService != null) {
            btnTermsOfService.setOnClickListener(v -> showTermsOfService());
        }
        if (btnOpenSource != null) {
            btnOpenSource.setOnClickListener(v -> showOpenSourceLicenses());
        }

        // --- LANGUAGE SWITCH ---
        if (btnChangeLanguage != null) {
            btnChangeLanguage.setOnClickListener(v -> {
                showLanguagePicker();
            });
        }
    }

    private void setupMenuItem(View container, String title, String desc, View.OnClickListener clickListener) {
        if (container == null) return;
        TextView tvTitle = container.findViewById(R.id.tv_ghost_title);
        TextView tvDesc = container.findViewById(R.id.tv_ghost_desc);
        
        if (tvTitle != null) tvTitle.setText(title);
        if (tvDesc != null) tvDesc.setText(desc);
        
        container.setOnClickListener(clickListener);
    }

    private void showIntervalPicker() {
        String[] options = {
            getString(R.string.interval_6h),
            getString(R.string.interval_12h),
            getString(R.string.interval_24h),
            getString(R.string.interval_3d),
            getString(R.string.interval_7d),
            getString(R.string.interval_15d),
            getString(R.string.interval_30d)
        };
        int[] hours = {6, 12, 24, 72, 168, 360, 720};
        
        int currentInterval = sessionManager.getBackupInterval();
        int checkedItem = 2; // Default 24h
        for (int i = 0; i < hours.length; i++) {
            if (hours[i] == currentInterval) {
                checkedItem = i;
                break;
            }
        }

        com.vn.jet.mosco.utils.MoscoDialogHelper.showSingleChoiceDialog(
            getActivity(),
            getString(R.string.settings_label_backup_interval),
            options,
            index -> {
                sessionManager.setBackupInterval(hours[index]);
                updateBackupIntervalUI();
                com.vn.jet.mosco.utils.WorkScheduler.scheduleAutoBackup(requireContext());
            }
        );
    }

    /**
     * Hiển thị Dialog chọn ngôn ngữ English/Tiếng Việt chuẩn hệ thống.
     * TẠI SAO: Người dùng có thể chọn đổi ngôn ngữ tức thì, cấu hình sẽ lưu vào SessionManager
     * và gọi recreate() để nạp lại tài nguyên XML theo Locale mới trên toàn app.
     */
    private void showLanguagePicker() {
        String[] options = {
            getString(R.string.language_en),
            getString(R.string.language_vi)
        };
        String currentLang = sessionManager.getLanguage();
        
        com.vn.jet.mosco.utils.MoscoDialogHelper.showSingleChoiceDialog(
            getActivity(),
            getString(R.string.settings_dialog_language_title),
            options,
            index -> {
                String selectedLang = (index == 1) ? "vi" : "en";
                if (!selectedLang.equals(currentLang)) {
                    sessionManager.setLanguage(selectedLang);
                    if (getActivity() != null) {
                        getActivity().recreate();
                    }
                }
            }
        );
    }

    private void calculateCacheSize() {
        long size = getDirSize(requireContext().getCacheDir());
        String sizeStr = android.text.format.Formatter.formatFileSize(requireContext(), size);
        tvCacheSize.setText(getString(R.string.settings_label_cache_usage, sizeStr));
    }

    private long getDirSize(File dir) {
        if (dir == null || !dir.exists()) return 0;
        long size = 0;
        for (File file : dir.listFiles()) {
            if (file != null && file.isDirectory()) {
                size += getDirSize(file);
            } else if (file != null && file.isFile()) {
                size += file.length();
            }
        }
        return size;
    }

    private void clearAppCache() {
        new Thread(() -> {
            try {
                // Clear Glide memory on main thread
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        com.bumptech.glide.Glide.get(requireContext()).clearMemory();
                        Toast.makeText(getContext(), getString(R.string.settings_msg_clearing_cache), Toast.LENGTH_SHORT).show();
                    });
                }

                // Clear files from cache dir
                deleteDir(requireContext().getCacheDir());
                
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        calculateCacheSize();
                        Toast.makeText(getContext(), getString(R.string.common_msg_success), Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) return false;
            }
            return dir.delete();
        } else if (dir != null && dir.isFile()) {
            return dir.delete();
        } else {
            return false;
        }
    }

    private void showBugReportDialog() {
        if (getActivity() == null || getActivity().isFinishing() || getActivity().isDestroyed()) return;

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.layout_mosco_dialog_base, null);
        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        android.widget.FrameLayout flContent = dialogView.findViewById(R.id.fl_dialog_content);
        com.vn.jet.mosco.widget.MoscoButton btnPositive = dialogView.findViewById(R.id.btn_positive);
        com.vn.jet.mosco.widget.MoscoButton btnNegative = dialogView.findViewById(R.id.btn_negative);

        tvTitle.setText(R.string.settings_bug_dialog_title);
        flContent.removeAllViews();

        android.widget.EditText etBugDescription = new android.widget.EditText(requireContext());
        etBugDescription.setHint(R.string.settings_bug_dialog_hint);
        etBugDescription.setHintTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.mosco_white_40));
        etBugDescription.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.white));
        etBugDescription.setBackgroundResource(R.drawable.lg_input_bg);
        etBugDescription.setMinLines(4);
        etBugDescription.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        etBugDescription.setPadding(padding, padding, padding, padding);

        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        );
        etBugDescription.setLayoutParams(lp);
        flContent.addView(etBugDescription);

        btnPositive.setText(getString(R.string.settings_action_submit));
        btnNegative.setText(getString(R.string.action_cancel));

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            dialog.getWindow().setWindowAnimations(android.R.style.Animation_Dialog);
        }

        btnPositive.setOnClickListener(v -> {
            String bugText = etBugDescription.getText().toString().trim();
            if (bugText.isEmpty()) {
                android.widget.Toast.makeText(requireContext(), "Please describe the bug", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            
            // TẠI SAO: Hiển thị thông báo gửi thành công và thực hiện rung nhẹ
            com.vn.jet.mosco.widget.MoscoNotification.showSuccess(getActivity(), getString(R.string.settings_bug_dialog_success));
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        });

        btnNegative.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showPrivacyPolicy() {
        if (getActivity() == null) return;
        com.vn.jet.mosco.utils.MoscoDialogHelper.showInfoDialog(
            getActivity(),
            getString(R.string.settings_action_privacy),
            getString(R.string.settings_privacy_content),
            getString(R.string.action_confirm),
            null
        );
    }

    private void showTermsOfService() {
        if (getActivity() == null) return;
        com.vn.jet.mosco.utils.MoscoDialogHelper.showInfoDialog(
            getActivity(),
            getString(R.string.settings_action_terms),
            getString(R.string.settings_terms_content),
            getString(R.string.action_confirm),
            null
        );
    }

    private void showOpenSourceLicenses() {
        if (getActivity() == null) return;
        com.vn.jet.mosco.utils.MoscoDialogHelper.showInfoDialog(
            getActivity(),
            getString(R.string.settings_action_licenses),
            getString(R.string.settings_licenses_content),
            getString(R.string.action_confirm),
            null
        );
    }

    private void confirmDeleteAccount() {
        if (getActivity() == null) return;
        com.vn.jet.mosco.utils.MoscoDialogHelper.showConfirmDialog(
            getActivity(),
            getString(R.string.settings_dialog_delete_title),
            getString(R.string.settings_dialog_delete_msg),
            getString(R.string.action_confirm),
            getString(R.string.action_cancel),
            new com.vn.jet.mosco.utils.MoscoDialogHelper.DialogCallback() {
                @Override
                public void onPositive() {
                    // TẠI SAO: Xóa sạch session, dọn cache và quay về màn hình đăng nhập
                    sessionManager.clearSession();
                    if (getActivity() != null) {
                        Intent intent = new Intent(getActivity(), com.vn.jet.mosco.SignInActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        getActivity().finish();
                    }
                }
            }
        );
    }
}
