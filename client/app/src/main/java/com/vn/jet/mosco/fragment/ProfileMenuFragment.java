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

    private SwitchMaterial switchDarkMode, switchMusic, switchSfx, switchAutoBackup;
    private TextView tvCacheSize, tvBackupInterval;
    private View btnClearCache, layoutBackupInterval;
    private com.vn.jet.mosco.widget.MoscoButton btnChangeLanguage;

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

        setupInitialState();
        setupListeners(view);
        calculateCacheSize();
    }

    private void setupInitialState() {
        switchDarkMode.setChecked(sessionManager.isDarkMode());
        switchMusic.setChecked(sessionManager.isMusicEnabled());
        switchSfx.setChecked(sessionManager.isSfxEnabled());
        switchAutoBackup.setChecked(sessionManager.isAutoBackupEnabled());
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

        // --- GHOST MENUS ---
        setupMenuItem(view.findViewById(R.id.menu_backup_data), 
            "Backup Local Data", 
            "Create a snapshot of your local history", 
            v -> { if(listener != null) listener.onBackupData(); });

        setupMenuItem(view.findViewById(R.id.menu_restore_data), 
            "Restore Local Data", 
            "Load data from a previous backup file", 
            v -> { if(listener != null) listener.onRestoreData(); });

        setupMenuItem(view.findViewById(R.id.menu_cloud_sync), 
            "Cloud Sync", 
            "Upload latest backup to Mosco Cloud", 
            v -> { if(listener != null) listener.onCloudSync(); });

        setupMenuItem(view.findViewById(R.id.menu_switch_account), 
            "Switch Account", 
            "Login with a different identity", 
            v -> { if(listener != null) listener.onSwitchAccount(); });

        // --- ACCOUNT ACTIONS ---
        view.findViewById(R.id.btn_change_password).setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), com.vn.jet.mosco.ForgotPasswordActivity.class);
            intent.putExtra("from_settings", true);
            startActivity(intent);
        });

        view.findViewById(R.id.btn_logout).setOnClickListener(v -> {
            if (listener != null) listener.onLogout();
        });

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
}
