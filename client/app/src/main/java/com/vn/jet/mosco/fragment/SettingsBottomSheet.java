package com.vn.jet.mosco.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.bumptech.glide.Glide;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.utils.SessionManager;

import java.io.File;

public class SettingsBottomSheet extends BottomSheetDialogFragment {

    private SessionManager sessionManager;
    private SwitchMaterial switchDarkMode, switchMusic, switchSfx;
    private TextView tvCacheSize;
    private View btnClearCache, btnClose;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.layout_settings_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        sessionManager = new SessionManager(requireContext());

        // Mapping Views
        switchDarkMode = view.findViewById(R.id.switch_dark_mode);
        switchMusic = view.findViewById(R.id.switch_music);
        switchSfx = view.findViewById(R.id.switch_sfx);
        tvCacheSize = view.findViewById(R.id.tv_cache_size);
        btnClearCache = view.findViewById(R.id.btn_clear_cache);
        btnClose = view.findViewById(R.id.btn_close_settings);

        setupInitialState();
        setupListeners();
        calculateCacheSize();
    }

    private void setupInitialState() {
        switchDarkMode.setChecked(sessionManager.isDarkMode());
        switchMusic.setChecked(sessionManager.isMusicEnabled());
        switchSfx.setChecked(sessionManager.isSfxEnabled());
    }

    private void setupListeners() {
        btnClose.setOnClickListener(v -> dismiss());

        // --- 🌔 THEME SWITCH ---
        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sessionManager.setDarkMode(isChecked);
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });

        // --- 🎵 AUDIO SWITCH ---
        switchMusic.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sessionManager.setMusicEnabled(isChecked);
            // logic tắt nhạc chuông/bgm sẽ được quản lý bởi AudioManager sau này
        });

        switchSfx.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sessionManager.setSfxEnabled(isChecked);
        });

        // --- 🧹 CLEAR CACHE ---
        btnClearCache.setOnClickListener(v -> {
            clearAppCache();
        });
    }

    private void calculateCacheSize() {
        long size = getDirSize(requireContext().getCacheDir());
        String sizeStr = android.text.format.Formatter.formatFileSize(requireContext(), size);
        tvCacheSize.setText("Current usage: " + sizeStr);
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
                requireActivity().runOnUiThread(() -> {
                    com.bumptech.glide.Glide.get(requireContext()).clearMemory();
                    Toast.makeText(getContext(), "Clearing cache...", Toast.LENGTH_SHORT).show();
                });

                // Clear files from cache dir
                deleteDir(requireContext().getCacheDir());
                
                requireActivity().runOnUiThread(() -> {
                    calculateCacheSize();
                    Toast.makeText(getContext(), "Cache cleared successfully!", Toast.LENGTH_SHORT).show();
                });
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

    @Override
    public int getTheme() {
        return R.style.CustomBottomSheetDialogTheme;
    }
}
