package com.vn.jet.mosco.fragment;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.scwang.smart.refresh.layout.SmartRefreshLayout;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.utils.SessionManager;
import com.vn.jet.mosco.utils.SmartFaceCropTransformation;

import org.json.JSONObject;

/**
 * RankFragment - Phiên bản Fragment của Bảng xếp hạng.
 * Hỗ trợ kiến trúc Single-Activity để tối ưu RAM.
 */
public class RankFragment extends Fragment {

    private static final String TAG = "RankFragment";
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private View cardMyRank;
    private SessionManager session;
    private SmartRefreshLayout refreshLayout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_rank, container, false);
        
        session = new SessionManager(requireContext());
        tabLayout = view.findViewById(R.id.tab_layout_rank);
        viewPager = view.findViewById(R.id.view_pager_rank);
        cardMyRank = view.findViewById(R.id.card_my_rank);
        refreshLayout = view.findViewById(R.id.swipe_refresh_rank);

        // [QUIET LUXURY] Tìm nút quay lại và đặt tiêu đề cho Header dùng chung
        View headerView = view.findViewById(R.id.layout_header_rank);
        if (headerView != null) {
            TextView tvTitle = headerView.findViewById(R.id.tv_header_title);
            if (tvTitle != null) {
                tvTitle.setText(R.string.rank_header_title);
            }
            View btnBack = headerView.findViewById(R.id.btn_back_common);
            if (btnBack != null) {
                btnBack.setOnClickListener(v -> {
                    if (getActivity() != null) getActivity().getSupportFragmentManager().popBackStack();
                });
            }
        }

        if (refreshLayout != null) {
            refreshLayout.setOnRefreshListener(layout -> refreshCurrentFragment());
        }
        
        View innerItem = view.findViewById(R.id.layout_my_rank_item);
        if (innerItem != null) {
            innerItem.setBackgroundResource(android.R.color.transparent);
        }

        RankPagerAdapter adapter = new RankPagerAdapter(this);
        viewPager.setAdapter(adapter);
        viewPager.setUserInputEnabled(false);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0: tab.setText(getString(R.string.rank_tab_level)); break;
                case 1: tab.setText(getString(R.string.rank_tab_album)); break;
                case 2: tab.setText(getString(R.string.rank_tab_streak)); break;
                case 3: tab.setText(getString(R.string.rank_tab_fame)); break;
                case 4: tab.setText(getString(R.string.rank_tab_social)); break;
                case 5: tab.setText(getString(R.string.rank_tab_duo_streak)); break;
            }
        }).attach();

        return view;
    }

    public void stopRefresh() {
        if (refreshLayout != null) refreshLayout.finishRefresh();
    }

    private void refreshCurrentFragment() {
        if (viewPager == null) return;
        Fragment fragment = getChildFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
        if (fragment instanceof RankListFragment) {
            ((RankListFragment) fragment).refreshData();
        } else {
            stopRefresh();
        }
    }

    public void updateMyRank(JSONObject userRankData, String rankType) {
        if (cardMyRank == null || !isAdded()) return;
        
        cardMyRank.setAlpha(1f);
        cardMyRank.setVisibility(View.VISIBLE);
        
        TextView tvPos = cardMyRank.findViewById(R.id.tv_rank_position);
        TextView tvName = cardMyRank.findViewById(R.id.tv_rank_name);
        TextView tvValue = cardMyRank.findViewById(R.id.tv_rank_value);
        ImageView ivAvatar = cardMyRank.findViewById(R.id.iv_rank_avatar);
        com.airbnb.lottie.LottieAnimationView ivType = cardMyRank.findViewById(R.id.iv_rank_type_icon);

        try {
            if (userRankData == null) {
                tvPos.setText(getString(R.string.placeholder_empty));
                tvName.setText(session.getIngameName() != null ? session.getIngameName() : getString(R.string.profile_preview_default_name));
                tvValue.setText(getString(R.string.placeholder_empty));
                
                // Luồng tải ưu tiên: Avatar của mình ở footer dùng bản thumbnail
                com.vn.jet.mosco.utils.AvatarUtils.loadAvatar(requireContext(), ivAvatar, session.getUserId(), session.getAvatarId(), true);
                return;
            }

            int rank = userRankData.optInt("rank", 0);
            tvPos.setText(String.valueOf(rank));
            tvName.setText(userRankData.optString("ingameName", "Unknown"));
            
            int value = userRankData.optInt("value", 0);
            if (ivType != null) ivType.cancelAnimation();
            
            switch (rankType) {
                case "level": 
                    tvValue.setText(String.format("Lv. %d", value)); 
                    if (ivType != null) ivType.setVisibility(View.GONE);
                    break;
                case "social": 
                    tvValue.setText(String.format("%d Friends", value));
                    if (ivType != null) ivType.setVisibility(View.GONE);
                    break;
                case "collection": 
                    tvValue.setText(String.format("%d Objets", value)); 
                    if (ivType != null) {
                        ivType.setImageResource(R.drawable.ic_objets);
                        ivType.setVisibility(View.VISIBLE);
                    }
                    break;
                case "streak":
                case "duo-streak":
                    tvValue.setText(String.format("%d Days", value));
                    if (ivType != null && rankType.equals("streak")) {
                        ivType.setAnimation(R.raw.streak_animation);
                        ivType.setMinAndMaxFrame(0, 24);
                        if (!ivType.isAnimating()) ivType.playAnimation();
                        com.vn.jet.mosco.utils.StreakColorHelper.applyStreakColor(ivType, value);
                        ivType.setVisibility(View.VISIBLE);
                    } else if (ivType != null) {
                        ivType.setVisibility(View.GONE);
                    }
                    break;
                case "fame":
                    tvValue.setText(String.format("%d Likes", value));
                    if (ivType != null) ivType.setVisibility(View.GONE);
                    break;
            }

            String avatarId = userRankData.optString("avatarId", "1");
            // Luồng tải ưu tiên: Avatar của mình ở footer dùng bản thumbnail
            com.vn.jet.mosco.utils.AvatarUtils.loadAvatar(requireContext(), ivAvatar, userRankData.optLong("userId"), avatarId, true);

        } catch (Exception e) {
            Log.e(TAG, "Error updating my rank footer", e);
        }
    }

    private static class RankPagerAdapter extends FragmentStateAdapter {
        public RankPagerAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            String rankType;
            switch (position) {
                case 0: rankType = "level"; break;
                case 1: rankType = "collection"; break;
                case 2: rankType = "streak"; break;
                case 3: rankType = "fame"; break;
                case 4: rankType = "social"; break;
                case 5: rankType = "duo-streak"; break;
                default: rankType = "level"; break;
            }
            return RankListFragment.newInstance(rankType);
        }

        @Override
        public int getItemCount() { return 6; }
    }
}
