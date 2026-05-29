package com.vn.jet.mosco.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.vn.jet.mosco.ProfileViewModel;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.model.UserStats;

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
            rvBadges.setAdapter(new BadgeAdapter(stats.getBadges()));
        }
    }

    private static class BadgeAdapter extends RecyclerView.Adapter<BadgeAdapter.ViewHolder> {
        private final java.util.List<String> badges;

        public BadgeAdapter(java.util.List<String> badges) {
            this.badges = badges != null ? badges : new java.util.ArrayList<>();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_honor_badge, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.tvBadgeName.setText(badges.get(position));
        }

        @Override
        public int getItemCount() {
            return badges.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvBadgeName;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvBadgeName = (TextView) itemView;
            }
        }
    }
}
