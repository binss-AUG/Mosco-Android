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
            viewModel.getUserStats().observe(getViewLifecycleOwner(), this::renderData);
        }
    }

    private void renderData(UserStats stats) {
        if (stats == null) return;
        
        tvTotalRolls.setText(String.format("%,d", stats.getTotalRolls()));
        tvCollectionProgress.setText(stats.getCollectionProgress() + "%");

        if (stats.getBadges() == null || stats.getBadges().isEmpty()) {
            rvBadges.setVisibility(View.GONE);
            tvNoBadges.setVisibility(View.VISIBLE);
        } else {
            rvBadges.setVisibility(View.VISIBLE);
            tvNoBadges.setVisibility(View.GONE);
            // TODO: Setup BadgeAdapter here in the future
        }
    }
}
