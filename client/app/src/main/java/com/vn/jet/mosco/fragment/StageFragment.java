package com.vn.jet.mosco.fragment;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.CompositePageTransformer;
import androidx.viewpager2.widget.MarginPageTransformer;
import androidx.viewpager2.widget.ViewPager2;

import com.vn.jet.mosco.R;
import com.vn.jet.mosco.dto.StartStageRequest;
import com.vn.jet.mosco.model.CardDisplayItem;
import com.vn.jet.mosco.model.UserCard;
import com.vn.jet.mosco.network.ApiClient;
import com.vn.jet.mosco.network.GameApiService;
import com.vn.jet.mosco.utils.AppConfig;
import com.vn.jet.mosco.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;

import android.os.Handler;
import android.os.Looper;

public class StageFragment extends Fragment {

    private ViewPager2 viewPager;
    private View viewBgOverlay;
    private View[] dots;
    private int currentMapId = 1;
    private int userLevel = 1;
    private final List<MapData> maps = new ArrayList<>();

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            updateVisibleTimers();
            timerHandler.postDelayed(this, 1000);
        }
    };

    private static class MapData {
        int id;
        int nameRes;
        int subRes;
        int requiredLevel;
        int accentColor;
        int bgColor;
        int rewardsRes;
        int durationHours;
        int illustrationRes;
        com.vn.jet.mosco.dto.StageSessionResponse activeSession;

        MapData(int id, int nameRes, int subRes, int requiredLevel, int accentColor, int bgColor, int rewardsRes, int durationHours, int illustrationRes) {
            this.id = id;
            this.nameRes = nameRes;
            this.subRes = subRes;
            this.requiredLevel = requiredLevel;
            this.accentColor = accentColor;
            this.bgColor = bgColor;
            this.rewardsRes = rewardsRes;
            this.durationHours = durationHours;
            this.illustrationRes = illustrationRes;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initMaps();
    }

    private void initMaps() {
        maps.add(new MapData(1, R.string.stage_map_1_name, R.string.stage_map_1_sub, 1, R.color.stage_map_1_accent, R.color.stage_map_1_bg, R.string.stage_map_reward_coins, 1, R.drawable.bg_stage_map_1));
        maps.add(new MapData(2, R.string.stage_map_2_name, R.string.stage_map_2_sub, 5, R.color.stage_map_2_accent, R.color.stage_map_2_bg, R.string.stage_map_reward_rare, 4, R.drawable.bg_stage_map_2));
        maps.add(new MapData(3, R.string.stage_map_3_name, R.string.stage_map_3_sub, 15, R.color.stage_map_3_accent, R.color.stage_map_3_bg, R.string.stage_map_reward_premium, 8, R.drawable.bg_stage_map_3));
        maps.add(new MapData(4, R.string.stage_map_4_name, R.string.stage_map_4_sub, 36, R.color.stage_map_4_accent, R.color.stage_map_4_bg, R.string.stage_map_reward_epic, 12, R.drawable.bg_stage_map_4));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stage, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewPager = view.findViewById(R.id.vp_stage_maps);
        viewBgOverlay = view.findViewById(R.id.view_bg_overlay);
        
        dots = new View[]{
                view.findViewById(R.id.dot_0),
                view.findViewById(R.id.dot_1),
                view.findViewById(R.id.dot_2),
                view.findViewById(R.id.dot_3)
        };

        setupViewPager();
        loadUserLevel();
        loadActiveSessions();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadActiveSessions();
        timerHandler.post(timerRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();
        timerHandler.removeCallbacks(timerRunnable);
    }

    private void updateVisibleTimers() {
        View adapterView = viewPager.getChildAt(0);
        if (adapterView instanceof RecyclerView) {
            RecyclerView recyclerView = (RecyclerView) adapterView;
            for (int i = 0; i < recyclerView.getChildCount(); i++) {
                View child = recyclerView.getChildAt(i);
                RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(child);
                if (holder instanceof MapViewHolder) {
                    int pos = recyclerView.getChildAdapterPosition(child);
                    if (pos != RecyclerView.NO_POSITION && pos < maps.size()) {
                        MapViewHolder mapHolder = (MapViewHolder) holder;
                        MapData map = maps.get(pos);
                        if (map.activeSession != null) {
                            updateTimerUI(mapHolder, map.activeSession);
                        }
                    }
                }
            }
        }
    }

    private void updateTimerUI(MapViewHolder holder, com.vn.jet.mosco.dto.StageSessionResponse session) {
        long msPerHour = getResources().getInteger(R.integer.ms_per_hour);
        long totalMs = session.getDurationHours() * msPerHour;
        
        // Hạn chế lỗi chênh lệch thời gian hệ thống thiết bị (local) với Server dẫn đến elapsedMs bị âm
        long now = System.currentTimeMillis();
        long elapsedMs = Math.max(0, now - session.getStartTimeMillis());
        long remainingMs = session.getEndTimeMillis() - now;

        if (remainingMs > 0) {
            // Đảm bảo không xảy ra ArithmeticException nếu totalMs = 0 và giới hạn progress luôn trong khoảng [0, 100] để tránh lỗi hiển thị lệch của LinearProgressIndicator
            int progress = totalMs > 0 ? (int) ((elapsedMs * 100) / totalMs) : 0;
            holder.pbTime.setProgress(Math.max(0, Math.min(100, progress)));
            
            long seconds = (remainingMs / 1000) % 60;
            long minutes = (remainingMs / (1000 * 60)) % 60;
            long hours = (remainingMs / (1000 * 60 * 60));
            holder.tvTimer.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));

            holder.layoutRunningActions.setVisibility(View.VISIBLE);
            holder.btnClaim.setVisibility(View.GONE);
            holder.layoutDurationPicker.setVisibility(View.GONE);
        } else {
            holder.pbTime.setProgress(100);
            holder.tvTimer.setText(R.string.action_done);

            holder.layoutRunningActions.setVisibility(View.GONE);
            holder.btnClaim.setVisibility(View.VISIBLE);
            holder.layoutDurationPicker.setVisibility(View.GONE);
            holder.btnClaim.setOnClickListener(v -> claimMissionReward(session.getId()));
        }
    }

    private void loadUserLevel() {
        // Mocking user level for now, in real app get from Session/User model
        userLevel = 50; // Set high for testing
    }

    private void loadActiveSessions() {
        Long userId = new SessionManager(requireContext()).getUserId();
        if (userId == null) return;

        GameApiService apiService = ApiClient.getClient(requireContext()).create(GameApiService.class);
        apiService.getMyStageSessions(userId).enqueue(new retrofit2.Callback<List<com.vn.jet.mosco.dto.StageSessionResponse>>() {
            @Override
            public void onResponse(retrofit2.Call<List<com.vn.jet.mosco.dto.StageSessionResponse>> call, retrofit2.Response<List<com.vn.jet.mosco.dto.StageSessionResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<com.vn.jet.mosco.dto.StageSessionResponse> sessions = response.body();
                    for (MapData map : maps) {
                        map.activeSession = null;
                        for (com.vn.jet.mosco.dto.StageSessionResponse session : sessions) {
                            String status = session.getStatus();
                            if (session.getMapId() == map.id && ("RUNNING".equals(status) || "COMPLETED".equals(status))) {
                                map.activeSession = session;
                                break;
                            }
                        }
                    }
                    if (viewPager.getAdapter() != null) {
                        viewPager.getAdapter().notifyDataSetChanged();
                    }
                }
            }

            @Override
            public void onFailure(retrofit2.Call<List<com.vn.jet.mosco.dto.StageSessionResponse>> call, Throwable t) {
                // Ignore error for silent load
            }
        });
    }

    private void setupViewPager() {
        MapAdapter adapter = new MapAdapter();
        adapter.setHasStableIds(true);
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(3);
        
        CompositePageTransformer transformer = new CompositePageTransformer();
        transformer.addTransformer(new MarginPageTransformer(40));
        transformer.addTransformer((page, position) -> {
            float r = 1 - Math.abs(position);
            page.setScaleY(0.85f + r * 0.15f);
            page.setAlpha(0.5f + r * 0.5f);
        });
        viewPager.setPageTransformer(transformer);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateUIForPage(position);
            }
        });
        
        updateUIForPage(0);
    }

    private void updateUIForPage(int position) {
        currentMapId = maps.get(position).id;
        
        // Update Dots
        int duration = getResources().getInteger(R.integer.daily_indicator_scale_duration);
        float activeScale = getResources().getInteger(R.integer.daily_indicator_scale_active_percent) / 100f;
        float inactiveScale = getResources().getInteger(R.integer.daily_indicator_scale_inactive_percent) / 100f;

        for (int i = 0; i < dots.length; i++) {
            if (dots[i] == null) continue;
            dots[i].setBackgroundResource(i == position ? R.drawable.bg_indicator_active : R.drawable.bg_indicator_inactive);
            // Áp dụng animation scaleX co giãn dẹt cho indicators của bản đồ khi chuyển map
            dots[i].animate().scaleX(i == position ? activeScale : inactiveScale)
                    .setDuration(duration).start();
        }

        // Animate Background Overlay Color
        int newColor = ContextCompat.getColor(requireContext(), maps.get(position).bgColor);
        animateBackgroundColor(newColor);
    }

    private void animateBackgroundColor(int targetColor) {
        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), Color.TRANSPARENT, targetColor);
        colorAnimation.setDuration(600);
        colorAnimation.addUpdateListener(animator -> {
            viewBgOverlay.setBackgroundColor((int) animator.getAnimatedValue());
            viewBgOverlay.setAlpha(0.8f);
        });
        colorAnimation.start();
    }

    private class MapAdapter extends RecyclerView.Adapter<MapViewHolder> {
        @NonNull
        @Override
        public MapViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_stage_map_card, parent, false);
            return new MapViewHolder(v);
        }

        @Override
        public long getItemId(int position) {
            return maps.get(position).id;
        }

        @Override
        public void onBindViewHolder(@NonNull MapViewHolder holder, int position) {
            MapData map = maps.get(position);
            
            // RESET HOLDER STATE (Crucial for Recycling)
            holder.itemView.animate().cancel();
            holder.viewThumb.animate().cancel();
            holder.viewThumb.setTranslationX(0);
            holder.layoutRunning.animate().cancel();
            holder.layoutRunning.setAlpha(1.0f);
            
            
            holder.tvName.setText(map.nameRes);
            holder.tvDimension.setText(map.subRes);
            holder.tvReward.setText(map.rewardsRes);
            holder.ivIllustration.setImageResource(map.illustrationRes);
            holder.ivIllustration.setAlpha(0.6f);
            
            boolean isLocked = userLevel < map.requiredLevel;
            boolean isRunning = map.activeSession != null;

            holder.layoutLock.setVisibility(isLocked ? View.VISIBLE : View.GONE);
            holder.layoutRunning.setVisibility(isRunning ? View.VISIBLE : View.GONE);
            holder.btnDispatch.setVisibility(isRunning || isLocked ? View.GONE : View.VISIBLE);
            holder.layoutDurationPicker.setVisibility(isRunning || isLocked ? View.GONE : View.VISIBLE);
            holder.viewAccentLine.setVisibility(isRunning || isLocked ? View.GONE : View.VISIBLE);

            if (isRunning) {
                updateTimerUI(holder, map.activeSession);
                startPulsingAnimation(holder.tvTimer);
                startPulsingAnimation(holder.pbTime);

                holder.btnAbort.setOnClickListener(v -> showAbortConfirmation(map.activeSession));
                holder.btnSpeedUp.setOnClickListener(v -> showSpeedUpConfirmation(map.activeSession));
            }

            holder.tvLockLevel.setText(getString(R.string.stage_lock_level_format, map.requiredLevel));

            int accent = ContextCompat.getColor(requireContext(), map.accentColor);
            
            // Fix clashing: Keep text white but tint the background tag with more opacity
            holder.tvDimension.setTextColor(Color.WHITE);
            int tagBgColor = (accent & 0x00FFFFFF) | 0x88000000; // 50% alpha of accent
            holder.tvDimension.setBackgroundTintList(android.content.res.ColorStateList.valueOf(tagBgColor));
            
            holder.btnDispatch.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent));
            
            // Contrast check: if accent is light (Map 1, 2, 3), use dark text
            if (map.accentColor == R.color.daily_morning_accent || 
                map.accentColor == R.color.daily_noon_accent ||
                map.accentColor == R.color.white) {
                holder.btnDispatch.setTextColor(ContextCompat.getColor(requireContext(), R.color.mosco_bg_dark));
            } else {
                holder.btnDispatch.setTextColor(Color.WHITE);
            }

            holder.btnDispatch.setText(R.string.stage_btn_dispatch);
            
            // Duration Selection Logic
            setupDurationSelection(holder, map, accent, position);
            
            

            holder.btnDispatch.setOnClickListener(v -> openSquadSelection(map.id));
        }

        private void setupDurationSelection(MapViewHolder holder, MapData map, int accent, int position) {
            TextView[] chips = {holder.tv1h, holder.tv4h, holder.tv8h, holder.tv12h};
            int[] actualDurations = {1, 4, 8, 12};
            
            // Default selection: find index matching map.durationHours
            int selectedIndex = 2; // Default 8H
            for (int i = 0; i < actualDurations.length; i++) {
                if (actualDurations[i] == map.durationHours) {
                    selectedIndex = i;
                    break;
                }
            }

            for (int i = 0; i < chips.length; i++) {
                final int index = i;
                chips[i].setOnClickListener(v -> {
                    map.durationHours = actualDurations[index];
                    updateDurationUI(holder, index, true);
                });
            }
            
            final int finalSelectedIndex = selectedIndex;
            final int pos = position;
            // Post to ensure layout is ready for measurement
            holder.itemView.post(() -> {
                if (holder.getBindingAdapterPosition() == pos) {
                    updateDurationUI(holder, finalSelectedIndex, false);
                }
            });
        }

        private void updateDurationUI(MapViewHolder holder, int selectedIndex, boolean animate) {
            TextView[] chips = {holder.tv1h, holder.tv4h, holder.tv8h, holder.tv12h};
            
            for (int i = 0; i < chips.length; i++) {
                chips[i].setAlpha(i == selectedIndex ? 1.0f : 0.4f);
            }

            // Animate thumb position
            View thumb = holder.viewThumb;
            View container = holder.layoutDuration;
            
            int totalWidth = container.getWidth() - container.getPaddingLeft() - container.getPaddingRight();
            int itemWidth = totalWidth / 4;
            
            if (thumb.getLayoutParams().width == 0) {
                ViewGroup.LayoutParams lp = thumb.getLayoutParams();
                lp.width = itemWidth;
                thumb.setLayoutParams(lp);
            }

            float targetX = container.getPaddingLeft() + (selectedIndex * itemWidth);
            if (animate) {
                thumb.animate()
                    .translationX(targetX)
                    .setDuration(250)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
            } else {
                thumb.setTranslationX(targetX);
            }
        }

        @Override
        public int getItemCount() {
            return maps.size();
        }
    }

    static class MapViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvDimension, tvReward, tvLockLevel;
        TextView tv1h, tv4h, tv8h, tv12h;
        ImageView ivIllustration;
        View viewThumb, layoutLock, layoutDuration, layoutRunning, viewAccentLine;
        androidx.appcompat.widget.AppCompatButton btnDispatch;
        com.google.android.material.progressindicator.LinearProgressIndicator pbTime;
        TextView tvTimer;
        View btnAbort, layoutRunningActions, layoutDurationPicker;
        androidx.appcompat.widget.AppCompatButton btnSpeedUp, btnClaim;
        
        public MapViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_map_name);
            tvDimension = itemView.findViewById(R.id.tv_map_dimension);
            tvReward = itemView.findViewById(R.id.tv_map_reward_desc);
            ivIllustration = itemView.findViewById(R.id.iv_map_illustration);
            viewThumb = itemView.findViewById(R.id.segmented_thumb);
            layoutLock = itemView.findViewById(R.id.layout_lock_overlay);
            layoutDuration = itemView.findViewById(R.id.layout_duration_selection);
            layoutRunning = itemView.findViewById(R.id.layout_running);
            btnDispatch = itemView.findViewById(R.id.btn_dispatch);
            tv1h = itemView.findViewById(R.id.tv_duration_1h);
            tv4h = itemView.findViewById(R.id.tv_duration_4h);
            tv8h = itemView.findViewById(R.id.tv_duration_8h);
            tv12h = itemView.findViewById(R.id.tv_duration_12h);
            tvLockLevel = itemView.findViewById(R.id.tv_lock_level);
            viewAccentLine = itemView.findViewById(R.id.view_accent_line);

            pbTime = itemView.findViewById(R.id.pb_stage_time);
            tvTimer = itemView.findViewById(R.id.tv_timer);
            btnAbort = itemView.findViewById(R.id.btn_abort_mission);
            btnSpeedUp = itemView.findViewById(R.id.btn_speed_up);
            layoutRunningActions = itemView.findViewById(R.id.layout_running_actions);
            btnClaim = itemView.findViewById(R.id.btn_claim_reward);
            layoutDurationPicker = itemView.findViewById(R.id.layout_duration_picker);
        }
    }

    private void startPulsingAnimation(View view) {
        ObjectAnimator pulse = ObjectAnimator.ofFloat(view, "alpha", 0.9f, 1.0f);
        pulse.setDuration(2000);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.start();
    }

    private void openSquadSelection(int mapId) {
        InventoryBottomSheet bottomSheet = new InventoryBottomSheet();
        bottomSheet.setSquadSelectMode(6, materials -> {
            List<Long> ids = new ArrayList<>();
            for (com.vn.jet.mosco.model.CardDisplayItem mc : materials) {
                ids.add(mc.getId());
            }
            startStage(mapId, ids);
        });
        bottomSheet.show(getChildFragmentManager(), "SquadSelection");
    }

    private void showAbortConfirmation(com.vn.jet.mosco.dto.StageSessionResponse session) {
        android.app.Dialog dialog = new android.app.Dialog(requireContext(), R.style.GalacticDialogTheme);
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_stage_confirm, null);
        dialog.setContentView(dialogView);
        dialog.setCancelable(true);

        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        TextView tvMsg = dialogView.findViewById(R.id.tv_dialog_msg);
        tvTitle.setText(R.string.stage_dialog_abort_title);
        tvMsg.setText(R.string.stage_dialog_abort_msg);

        dialogView.findViewById(R.id.btn_dialog_cancel).setOnClickListener(v -> dialog.dismiss());
        
        View btnConfirm = dialogView.findViewById(R.id.btn_dialog_confirm);
        TextView tvBtnConfirm = dialogView.findViewById(R.id.tv_btn_confirm_text);
        tvBtnConfirm.setText(R.string.action_confirm);
        btnConfirm.setOnClickListener(v -> {
            abortMission(session.getId());
            dialog.dismiss();
        });

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.85);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }

    private void showSpeedUpConfirmation(com.vn.jet.mosco.dto.StageSessionResponse session) {
        long remainingMs = session.getEndTimeMillis() - System.currentTimeMillis();
        long msPerHour = getResources().getInteger(R.integer.ms_per_hour);
        int speedUpCost = getResources().getInteger(R.integer.stage_speed_up_cost_per_hour);
        
        long hoursLeft = (remainingMs / msPerHour) + 1;
        int cost = (int) (hoursLeft * speedUpCost);

        android.app.Dialog dialog = new android.app.Dialog(requireContext(), R.style.GalacticDialogTheme);
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_stage_confirm, null);
        dialog.setContentView(dialogView);
        dialog.setCancelable(true);

        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        TextView tvMsg = dialogView.findViewById(R.id.tv_dialog_msg);
        tvTitle.setText(R.string.stage_dialog_speedup_title);
        tvMsg.setText(getString(R.string.stage_dialog_speedup_msg, cost));

        dialogView.findViewById(R.id.btn_dialog_cancel).setOnClickListener(v -> dialog.dismiss());
        
        View btnConfirm = dialogView.findViewById(R.id.btn_dialog_confirm);
        TextView tvBtnConfirm = dialogView.findViewById(R.id.tv_btn_confirm_text);
        tvBtnConfirm.setText(R.string.stage_btn_speed_up);
        btnConfirm.setOnClickListener(v -> {
            speedUpMission(session.getId());
            dialog.dismiss();
        });

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.85);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }

    private void abortMission(Long sessionId) {
        Long userId = new SessionManager(requireContext()).getUserId();
        ApiClient.getClient(requireContext()).create(GameApiService.class)
            .abortStage(userId, sessionId).enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
                @Override
                public void onResponse(retrofit2.Call<okhttp3.ResponseBody> call, retrofit2.Response<okhttp3.ResponseBody> response) {
                    if (response.isSuccessful()) {
                        Toast.makeText(requireContext(), R.string.stage_msg_abort_success, Toast.LENGTH_SHORT).show();
                        loadActiveSessions();
                    }
                }
                @Override
                public void onFailure(retrofit2.Call<okhttp3.ResponseBody> call, Throwable t) {}
            });
    }

    private void speedUpMission(Long sessionId) {
        Long userId = new SessionManager(requireContext()).getUserId();
        ApiClient.getClient(requireContext()).create(GameApiService.class)
            .speedUpStage(userId, sessionId).enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
                @Override
                public void onResponse(retrofit2.Call<okhttp3.ResponseBody> call, retrofit2.Response<okhttp3.ResponseBody> response) {
                    if (response.isSuccessful()) {
                        Toast.makeText(requireContext(), R.string.stage_msg_speedup_success, Toast.LENGTH_SHORT).show();
                        loadActiveSessions();
                    } else {
                        try {
                            String error = response.errorBody() != null ? response.errorBody().string() : getString(R.string.common_error_unknown);
                            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                        } catch (Exception e) {}
                    }
                }
                @Override
                public void onFailure(retrofit2.Call<okhttp3.ResponseBody> call, Throwable t) {}
            });
    }

    private void claimMissionReward(Long sessionId) {
        Long userId = new SessionManager(requireContext()).getUserId();
        ApiClient.getClient(requireContext()).create(GameApiService.class)
            .claimStageReward(userId, sessionId).enqueue(new retrofit2.Callback<com.vn.jet.mosco.dto.StageRewardResponse>() {
                @Override
                public void onResponse(retrofit2.Call<com.vn.jet.mosco.dto.StageRewardResponse> call, retrofit2.Response<com.vn.jet.mosco.dto.StageRewardResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        showRewardDialog(response.body());
                        loadActiveSessions(); // Reload to clear the claimed session
                    }
                }
                @Override
                public void onFailure(retrofit2.Call<com.vn.jet.mosco.dto.StageRewardResponse> call, Throwable t) {
                    Toast.makeText(requireContext(), R.string.common_error_network, Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void showRewardDialog(com.vn.jet.mosco.dto.StageRewardResponse reward) {
        android.app.Dialog dialog = new android.app.Dialog(requireContext(), R.style.RewardOverlayTheme);
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_reward_overlay, null);
        dialog.setContentView(dialogView);
        dialog.setCancelable(false);

        TextView tvCoins = dialogView.findViewById(R.id.tv_reward_coins);
        TextView tvDiamonds = dialogView.findViewById(R.id.tv_reward_diamonds);
        tvCoins.setText(String.format("%,d", reward.getCoins()));
        tvDiamonds.setText(String.format("%,d", reward.getDiamonds()));

        // Dim if reward is 0
        if (reward.getCoins() <= 0) {
            dialogView.findViewById(R.id.layout_reward_coins).setAlpha(0.4f);
        }
        if (reward.getDiamonds() <= 0) {
            dialogView.findViewById(R.id.layout_reward_diamonds).setAlpha(0.4f);
        }

        View rootLayout = dialogView.findViewById(R.id.root_reward_layout);
        rootLayout.setOnClickListener(v -> dialog.dismiss());
        dialogView.setOnClickListener(v -> dialog.dismiss());

        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        
        dialog.show();
    }

    private void startStage(int mapId, List<Long> cardIds) {
        // Find the map to get its current durationHours
        int selectedDuration = 8;
        for (MapData map : maps) {
            if (map.id == mapId) {
                selectedDuration = map.durationHours;
                break;
            }
        }

        Long userId = new SessionManager(requireContext()).getUserId();
        StartStageRequest request = new StartStageRequest();
        request.setMapId(mapId);
        request.setCardIds(cardIds);
        request.setDurationHours(selectedDuration);

        GameApiService apiService = ApiClient.getClient(requireContext()).create(GameApiService.class);
        apiService.startStage(userId, request).enqueue(new retrofit2.Callback<com.vn.jet.mosco.dto.StageSessionResponse>() {
            @Override
            public void onResponse(retrofit2.Call<com.vn.jet.mosco.dto.StageSessionResponse> call, retrofit2.Response<com.vn.jet.mosco.dto.StageSessionResponse> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(requireContext(), R.string.stage_msg_dispatch_success, Toast.LENGTH_LONG).show();
                    loadActiveSessions();
                } else {
                    String errorMsg = getString(R.string.common_error_unknown) + " (" + response.code() + ")";
                    try {
                        if (response.errorBody() != null) {
                            errorMsg = response.errorBody().string();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(retrofit2.Call<com.vn.jet.mosco.dto.StageSessionResponse> call, Throwable t) {
                Toast.makeText(requireContext(), R.string.common_error_network, Toast.LENGTH_SHORT).show();
            }
        });
    }
}