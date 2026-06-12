package com.vn.jet.mosco.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.vn.jet.mosco.ProfileViewModel;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.model.UserStats;
import com.google.android.material.bottomsheet.BottomSheetDialog;

/**
 * Tab Trophy trong Profile V2.
 * Hiển thị Gacha Stats và Danh sách Huy hiệu.
 */
public class ProfileTrophyFragment extends Fragment {

    private TextView tvTotalRolls, tvCollectionProgress, tvNoBadges;
    private RecyclerView rvBadges;
    private ProfileViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile_trophy, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        tvTotalRolls = view.findViewById(R.id.tv_total_rolls_value);
        tvCollectionProgress = view.findViewById(R.id.tv_collection_progress_value);
        tvNoBadges = view.findViewById(R.id.tv_no_badges);
        rvBadges = view.findViewById(R.id.rv_badges);

        if (getParentFragment() != null) {
            viewModel = new ViewModelProvider(getParentFragment()).get(ProfileViewModel.class);
            
            // TẠI SAO: Quan sát trạng thái shimmer của parent để tự động chạy skeletonize/restore cho chính mình.
            // Điều này giải quyết lỗi chữ thật bị mờ nhòe thay vì hiển thị khối xám shimmer.
            viewModel.getIsShimmering().observe(getViewLifecycleOwner(), isShimmering -> {
                View root = getView();
                if (root == null) return;
                if (Boolean.TRUE.equals(isShimmering)) {
                    com.vn.jet.mosco.utils.SkeletonHelper.skeletonize(root);
                } else {
                    com.vn.jet.mosco.utils.SkeletonHelper.restore(root);
                    renderData(viewModel.getUserStats().getValue());
                }
            });

            viewModel.getUserStats().observe(getViewLifecycleOwner(), stats -> {
                // TẠI SAO: Chỉ kết xuất dữ liệu thật (renderData) khi hiệu ứng shimmer đã tắt hoàn toàn.
                Boolean isShimmering = viewModel.getIsShimmering().getValue();
                if (!Boolean.TRUE.equals(isShimmering)) {
                    renderData(stats);
                }
            });
        }
    }

    private void renderData(UserStats stats) {
        if (stats == null) return;
        
        tvTotalRolls.setText(String.format(java.util.Locale.US, "%,d", stats.getTotalRolls()));
        tvCollectionProgress.setText(stats.getCollectionProgress() + "%");

        if (stats.getBadges() == null || stats.getBadges().isEmpty()) {
            rvBadges.setVisibility(View.GONE);
            tvNoBadges.setVisibility(View.VISIBLE);
        } else {
            rvBadges.setVisibility(View.VISIBLE);
            tvNoBadges.setVisibility(View.GONE);
            rvBadges.setAdapter(new BadgeAdapter(stats.getBadges(), stats));
        }
    }

    private static class BadgeAdapter extends RecyclerView.Adapter<BadgeAdapter.ViewHolder> {
        private final java.util.List<String> badges;
        private final UserStats stats;

        public BadgeAdapter(java.util.List<String> badges, UserStats stats) {
            this.badges = badges != null ? new java.util.ArrayList<>(badges) : new java.util.ArrayList<>();
            this.stats = stats;

            // TẠI SAO: Tự động sắp xếp huy hiệu theo thứ tự cấp bậc từ cao xuống thấp
            java.util.Collections.sort(this.badges, (b1, b2) -> {
                int score1 = getTierScore(b1);
                int score2 = getTierScore(b2);
                return Integer.compare(score2, score1);
            });
        }

        private int getTierScore(String badgeName) {
            if (badgeName == null) return 0;
            if (badgeName.startsWith("EX ")) return 6;
            if (badgeName.startsWith("Diamond ")) return 5;
            if (badgeName.startsWith("Gold ")) return 4;
            if (badgeName.startsWith("Silver ")) return 3;
            if (badgeName.startsWith("Bronze ")) return 2;
            if (badgeName.startsWith("Iron ")) return 1;
            return 0;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_honor_badge, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String badgeName = badges.get(position);
            holder.tvBadgeName.setText(badgeName);

            // Phân tích Cấp bậc (Tier) và Loại huy hiệu (Type) từ chuỗi
            String tier = "";
            String type = badgeName;
            String[] parts = badgeName.split(" ", 2);
            if (parts.length >= 2) {
                tier = parts[0];
                type = parts[1];
            }

            // Gán icon vector tối giản đã vẽ mới tương ứng với loại huy hiệu
            int iconResId = R.drawable.ic_star;
            if ("Spin Master".equals(type)) {
                iconResId = R.drawable.ic_badge_spin;
            } else if ("Pack Master".equals(type)) {
                iconResId = R.drawable.ic_badge_pack;
            } else if ("Collection Master".equals(type)) {
                iconResId = R.drawable.ic_badge_collection;
            } else if ("Immortal".equals(type)) {
                iconResId = R.drawable.ic_badge_immortal;
            } else if ("Duo Flame".equals(type)) {
                iconResId = R.drawable.ic_badge_duo;
            } else if ("Celebrity".equals(type)) {
                iconResId = R.drawable.ic_badge_celebrity;
            } else if ("Golden Hammer".equals(type)) {
                iconResId = R.drawable.ic_badge_hammer;
            }
            holder.ivBadgeIcon.setImageResource(iconResId);

            // Gán Hexagon Frame theo Cấp bậc (Tier)
            int frameResId = R.drawable.ic_hex_iron;
            int tintColorRes = R.color.badge_tier_iron;
            boolean isEx = false;

            if ("Iron".equals(tier)) {
                frameResId = R.drawable.ic_hex_iron;
                tintColorRes = R.color.badge_tier_iron;
            } else if ("Bronze".equals(tier)) {
                frameResId = R.drawable.ic_hex_bronze;
                tintColorRes = R.color.badge_tier_bronze;
            } else if ("Silver".equals(tier)) {
                frameResId = R.drawable.ic_hex_silver;
                tintColorRes = R.color.badge_tier_silver;
            } else if ("Gold".equals(tier)) {
                frameResId = R.drawable.ic_hex_gold;
                tintColorRes = R.color.badge_tier_gold;
            } else if ("Diamond".equals(tier)) {
                frameResId = R.drawable.ic_hex_diamond;
                tintColorRes = R.color.badge_tier_diamond;
            } else if ("EX".equals(tier)) {
                frameResId = R.drawable.ic_hex_ex;
                tintColorRes = R.color.badge_tier_ex_red;
                isEx = true;
            }

            holder.ivBadgeFrame.setImageResource(frameResId);
            holder.itemView.setBackground(null);
            holder.ivBadgeIcon.setImageTintList(androidx.core.content.ContextCompat.getColorStateList(holder.itemView.getContext(), tintColorRes));

            // TẠI SAO: Đổi màu chữ đỏ nổi bật cho mốc EX để tạo điểm nhấn thị giác cao cấp
            if (isEx) {
                holder.tvBadgeName.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.badge_tier_ex_red));
            } else {
                holder.tvBadgeName.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.white));
            }

            // TẠI SAO: Click mở BottomSheet hiển thị tiến trình chi tiết
            final String finalTier = tier;
            final String finalType = type;
            holder.itemView.setOnClickListener(v -> showBadgeProgressDialog(v.getContext(), finalTier, finalType, stats));
        }

        private void showBadgeProgressDialog(android.content.Context context, String tier, String type, UserStats stats) {
            if (stats == null) return;

            BottomSheetDialog dialog = new BottomSheetDialog(context);
            
            // Thiết kế LinearLayout động tương thích tối đa với Dark Mode
            android.widget.LinearLayout layout = new android.widget.LinearLayout(context);
            layout.setOrientation(android.widget.LinearLayout.VERTICAL);
            int padding = (int) (24 * context.getResources().getDisplayMetrics().density);
            layout.setPadding(padding, padding, padding, padding);
            layout.setBackgroundColor(androidx.core.content.ContextCompat.getColor(context, R.color.semantic_background));
            
            // Biểu tượng Huy hiệu (Khung Lục Giác + Lõi) trên BottomSheet
            android.widget.FrameLayout iconContainer = new android.widget.FrameLayout(context);
            android.widget.LinearLayout.LayoutParams containerParams = new android.widget.LinearLayout.LayoutParams(
                (int) (80 * context.getResources().getDisplayMetrics().density),
                (int) (80 * context.getResources().getDisplayMetrics().density)
            );
            containerParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            containerParams.bottomMargin = (int) (16 * context.getResources().getDisplayMetrics().density);

            android.widget.ImageView ivFrame = new android.widget.ImageView(context);
            android.widget.ImageView ivIcon = new android.widget.ImageView(context);
            
            int iconResId = R.drawable.ic_star;
            if ("Spin Master".equals(type)) iconResId = R.drawable.ic_badge_spin;
            else if ("Pack Master".equals(type)) iconResId = R.drawable.ic_badge_pack;
            else if ("Collection Master".equals(type)) iconResId = R.drawable.ic_badge_collection;
            else if ("Immortal".equals(type)) iconResId = R.drawable.ic_badge_immortal;
            else if ("Duo Flame".equals(type)) iconResId = R.drawable.ic_badge_duo;
            else if ("Celebrity".equals(type)) iconResId = R.drawable.ic_badge_celebrity;
            else if ("Golden Hammer".equals(type)) iconResId = R.drawable.ic_badge_hammer;
            ivIcon.setImageResource(iconResId);
            
            int frameResId = R.drawable.ic_hex_iron;
            int tintColorRes = R.color.badge_tier_iron;
            if ("Iron".equals(tier)) { frameResId = R.drawable.ic_hex_iron; tintColorRes = R.color.badge_tier_iron; }
            else if ("Bronze".equals(tier)) { frameResId = R.drawable.ic_hex_bronze; tintColorRes = R.color.badge_tier_bronze; }
            else if ("Silver".equals(tier)) { frameResId = R.drawable.ic_hex_silver; tintColorRes = R.color.badge_tier_silver; }
            else if ("Gold".equals(tier)) { frameResId = R.drawable.ic_hex_gold; tintColorRes = R.color.badge_tier_gold; }
            else if ("Diamond".equals(tier)) { frameResId = R.drawable.ic_hex_diamond; tintColorRes = R.color.badge_tier_diamond; }
            else if ("EX".equals(tier)) { frameResId = R.drawable.ic_hex_ex; tintColorRes = R.color.badge_tier_ex_red; }
            
            ivFrame.setImageResource(frameResId);
            ivIcon.setImageTintList(androidx.core.content.ContextCompat.getColorStateList(context, tintColorRes));
            
            android.widget.FrameLayout.LayoutParams frameParams = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT);
            iconContainer.addView(ivFrame, frameParams);
            
            android.widget.FrameLayout.LayoutParams iconParams = new android.widget.FrameLayout.LayoutParams(
                (int) (40 * context.getResources().getDisplayMetrics().density),
                (int) (40 * context.getResources().getDisplayMetrics().density)
            );
            iconParams.gravity = android.view.Gravity.CENTER;
            iconContainer.addView(ivIcon, iconParams);
            
            layout.addView(iconContainer, containerParams);

            // Tiêu đề Huy hiệu kèm cấp bậc
            android.widget.TextView tvTitle = new android.widget.TextView(context);
            tvTitle.setText(tier + " " + type);
            tvTitle.setTextSize(20);
            tvTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            tvTitle.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            if ("EX".equals(tier)) {
                tvTitle.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.badge_tier_ex_red));
            } else {
                tvTitle.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.white));
            }
            android.widget.LinearLayout.LayoutParams titleParams = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            );
            titleParams.bottomMargin = (int) (12 * context.getResources().getDisplayMetrics().density);
            layout.addView(tvTitle, titleParams);

            // Lập chỉ số và mô tả mốc tiếp theo
            int currentVal = 0;
            int targetVal = 0;
            String desc = "";

            if ("Spin Master".equals(type)) {
                currentVal = stats.getSpinsCount();
                int[] targets = {36, 100, 500, 1000, 6700};
                targetVal = getNextTarget(currentVal, targets);
                desc = "Quay thẻ bài trong Gacha Spin để mở khóa cấp bậc tiếp theo.";
            } else if ("Pack Master".equals(type)) {
                currentVal = stats.getPacksCount();
                int[] targets = {36, 100, 500, 1000, 6700};
                targetVal = getNextTarget(currentVal, targets);
                desc = "Mở các gói vật phẩm (Pack) trong Shop để nâng cấp huy hiệu.";
            } else if ("Collection Master".equals(type)) {
                currentVal = stats.getCollectionProgress();
                int[] targets = {5, 15, 35, 60, 80, 95};
                targetVal = getNextTarget(currentVal, targets);
                desc = "Sở hữu thêm nhiều thẻ bài mới để nâng tỉ lệ hoàn thiện bộ sưu tập.";
            } else if ("Immortal".equals(type)) {
                currentVal = stats.getStreak();
                int[] targets = {3, 10, 30, 100, 200, 365};
                targetVal = getNextTarget(currentVal, targets);
                desc = "Duy trì đăng nhập điểm danh liên tiếp hằng ngày để tăng chuỗi.";
            } else if ("Duo Flame".equals(type)) {
                currentVal = stats.getCoupleStreakCount();
                int[] targets = {3, 10, 30, 100, 200, 365};
                targetVal = getNextTarget(currentVal, targets);
                desc = "Trò chuyện và giữ ngọn lửa tương tác liên tục với bạn cặp.";
            } else if ("Celebrity".equals(type)) {
                currentVal = stats.getLikesCount();
                int[] targets = {5, 15, 50, 150, 300, 600};
                targetVal = getNextTarget(currentVal, targets);
                desc = "Nhận thêm lượt Thích (Like) trang cá nhân từ những người chơi khác.";
            } else if ("Golden Hammer".equals(type)) {
                currentVal = 0;
                if ("EX".equals(tier)) currentVal = 10;
                else if ("Diamond".equals(tier)) currentVal = 8;
                else if ("Gold".equals(tier)) currentVal = 5;
                else if ("Silver".equals(tier)) currentVal = 10;
                else if ("Bronze".equals(tier)) currentVal = 8;
                else if ("Iron".equals(tier)) currentVal = 5;
                
                targetVal = 10;
                desc = "Nâng cấp (Upgrade) thẻ bài của bạn đạt các mốc cộng cao hơn.";
            }

            android.widget.TextView tvDesc = new android.widget.TextView(context);
            tvDesc.setText(desc);
            tvDesc.setTextSize(14);
            tvDesc.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.lg_text_secondary));
            tvDesc.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            android.widget.LinearLayout.LayoutParams descParams = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            );
            descParams.bottomMargin = (int) (18 * context.getResources().getDisplayMetrics().density);
            layout.addView(tvDesc, descParams);

            // Thông số tiến trình (Ví dụ: 12 / 36 Lượt quay)
            if (targetVal > 0) {
                android.widget.TextView tvProgressLabel = new android.widget.TextView(context);
                String unit = "";
                if ("Spin Master".equals(type)) unit = "Lượt quay";
                else if ("Pack Master".equals(type)) unit = "Gói mở";
                else if ("Collection Master".equals(type)) unit = "% bộ sưu tập";
                else if ("Immortal".equals(type) || "Duo Flame".equals(type)) unit = "Ngày Streak";
                else if ("Celebrity".equals(type)) unit = "Lượt thích";
                else if ("Golden Hammer".equals(type)) unit = "Cấp cộng";

                tvProgressLabel.setText(String.format(java.util.Locale.US, "Tiến trình: %d / %d %s", currentVal, targetVal, unit));
                tvProgressLabel.setTextSize(13);
                tvProgressLabel.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.white));
                tvProgressLabel.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
                android.widget.LinearLayout.LayoutParams progTextParams = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                );
                progTextParams.bottomMargin = (int) (8 * context.getResources().getDisplayMetrics().density);
                layout.addView(tvProgressLabel, progTextParams);

                // ProgressBar hiển thị tiến trình ngang dạng Liquid
                android.widget.ProgressBar progressBar = new android.widget.ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
                progressBar.setMax(targetVal);
                progressBar.setProgress(Math.min(currentVal, targetVal));
                progressBar.setProgressTintList(androidx.core.content.ContextCompat.getColorStateList(context, tintColorRes));
                
                android.widget.LinearLayout.LayoutParams progressParams = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    (int) (10 * context.getResources().getDisplayMetrics().density)
                );
                progressParams.bottomMargin = (int) (16 * context.getResources().getDisplayMetrics().density);
                layout.addView(progressBar, progressParams);
            }

            dialog.setContentView(layout);
            dialog.show();
        }

        private int getNextTarget(int current, int[] targets) {
            for (int target : targets) {
                if (current < target) return target;
            }
            return targets[targets.length - 1];
        }

        @Override
        public int getItemCount() {
            return badges.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvBadgeName;
            ImageView ivBadgeIcon;
            ImageView ivBadgeFrame;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvBadgeName = itemView.findViewById(R.id.tv_badge_name);
                ivBadgeIcon = itemView.findViewById(R.id.iv_badge_icon);
                ivBadgeFrame = itemView.findViewById(R.id.iv_badge_frame);
            }
        }
    }
}
