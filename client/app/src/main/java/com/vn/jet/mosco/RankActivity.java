package com.vn.jet.mosco;

import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.vn.jet.mosco.fragment.RankListFragment;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.vn.jet.mosco.utils.SessionManager;
import org.json.JSONObject;
import com.bumptech.glide.Glide;
import com.vn.jet.mosco.utils.SmartFaceCropTransformation;
import com.scwang.smart.refresh.layout.SmartRefreshLayout;
import com.scwang.smart.refresh.layout.api.RefreshLayout;

/**
 * Bảng xếp hạng — Cấu trúc Tab GIỐNG HỆT CollectionFragment.
 * 3 Tab: LEVEL, OVR, COLLECTION.
 * Sử dụng TabLayout + ViewPager2 + FragmentStateAdapter + TabLayoutMediator.
 */
public class RankActivity extends MoscoBaseActivity {

    private static final String TAG = "RankActivity";
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private View cardMyRank;
    private SessionManager session;
    private SmartRefreshLayout refreshLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rank);

        session = new SessionManager(this);
        tabLayout = findViewById(R.id.tab_layout_rank);
        viewPager = findViewById(R.id.view_pager_rank);
        cardMyRank = findViewById(R.id.card_my_rank);
        refreshLayout = findViewById(R.id.swipe_refresh_rank);

        // Pull Refresh Listener
        if (refreshLayout != null) {
            refreshLayout.setOnRefreshListener(layout -> refreshCurrentFragment());
        }
        
        // Loại bỏ nền của item bên trong để tránh bị "bí bách" (Double Border)
        View innerItem = findViewById(R.id.layout_my_rank_item);
        if (innerItem != null) {
            innerItem.setBackgroundResource(android.R.color.transparent);
        }

        // Nút back
        findViewById(R.id.btn_back_rank).setOnClickListener(v -> finish());

        // Setup adapter — Copy pattern từ CollectionFragment
        RankPagerAdapter adapter = new RankPagerAdapter(this);
        viewPager.setAdapter(adapter);

        // QUAN TRỌNG: Tắt swipe để chỉ cho chuyển tab bằng click — giống Collection
        viewPager.setUserInputEnabled(false);

        // Gắn TabLayout + ViewPager2 — Bỏ OVR, Thêm Wealth
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0: tab.setText(getString(R.string.rank_tab_level)); break;
                case 1: tab.setText(getString(R.string.rank_tab_album)); break;
                case 2: tab.setText(getString(R.string.rank_tab_wealth)); break;
                case 3: tab.setText(getString(R.string.rank_tab_streak)); break;
            }
        }).attach();
    }

    /**
     * PagerAdapter — Quản lý các tab Ranking.
     */
    public Long getCurrentUserId() {
        return session.getUserId();
    }

    public void hideMyRank() {
        if (cardMyRank != null) {
            cardMyRank.setVisibility(View.GONE);
            cardMyRank.animate().cancel();
        }
    }

    /**
     * Dừng hiệu ứng Refresh. Gọi bởi Fragment sau khi load xong.
     */
    public void stopRefresh() {
        if (refreshLayout != null) {
            refreshLayout.finishRefresh();
        }
    }

    private void refreshCurrentFragment() {
        if (viewPager == null) return;
        
        // ViewPager2 đặt tag cho fragment theo định dạng "f" + position
        Fragment fragment = getSupportFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
        if (fragment instanceof RankListFragment) {
            ((RankListFragment) fragment).refreshData();
        } else {
            // Fallback nếu tag không đúng
            stopRefresh();
        }
    }

    /**
     * Cập nhật dữ liệu vào thanh Footer nổi.
     * Fragment sẽ gọi hàm này sau khi tìm thấy User trong danh sách API.
     */
    public void updateMyRank(JSONObject userRankData, String rankType) {
        // Hiện Footer lập tức
        cardMyRank.setAlpha(1f);
        cardMyRank.setVisibility(View.VISIBLE);
        
        TextView tvPos = cardMyRank.findViewById(R.id.tv_rank_position);
        TextView tvName = cardMyRank.findViewById(R.id.tv_rank_name);
        TextView tvValue = cardMyRank.findViewById(R.id.tv_rank_value);
        ImageView ivAvatar = cardMyRank.findViewById(R.id.iv_rank_avatar);
        com.airbnb.lottie.LottieAnimationView ivType = cardMyRank.findViewById(R.id.iv_rank_type_icon);

        try {
            if (userRankData == null) {
                // Trường hợp Sếp chưa lên Top hoặc chưa load được data cá nhân
                tvPos.setText(getString(R.string.placeholder_empty));
                tvName.setText(session.getIngameName() != null ? session.getIngameName() : getString(R.string.profile_preview_default_name));
                tvValue.setText(getString(R.string.placeholder_empty));
                
                // Load avatar từ session để sếp vẫn thấy mặt mình
                String avatarId = session.getAvatarId();
                String avatarUrl = session.getAvatar();
                if (avatarUrl == null || avatarUrl.isEmpty()) {
                    JSONObject card = com.vn.jet.mosco.utils.DatabaseLoader.findByCollectionId(this, avatarId);
                    if (card != null) avatarUrl = card.optString("frontImage", null);
                }
                
                Glide.with(this)
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_user)
                    .transform(new SmartFaceCropTransformation())
                    .into(ivAvatar);

                tvPos.setTextColor(android.graphics.Color.WHITE);
                return;
            }

            int rank = userRankData.optInt("rank", 0);
            tvPos.setText(String.valueOf(rank));
            tvName.setText(userRankData.optString("ingameName", "Unknown"));
            
            int value = userRankData.optInt("value", 0);
            if (ivType != null) ivType.cancelAnimation();
            switch (rankType) {
                case "level": 
                    tvValue.setText(getString(R.string.rank_format_level, value)); 
                    if (ivType != null) ivType.setVisibility(View.GONE);
                    break;
                case "wealth": 
                    tvValue.setText(com.vn.jet.mosco.utils.NumberUtils.format(this, (long)value)); 
                    if (ivType != null) {
                        ivType.setImageResource(R.drawable.ic_item_diamond);
                        ivType.setVisibility(View.VISIBLE);
                    }
                    break;
                case "collection": 
                    tvValue.setText(getString(R.string.rank_format_album, value)); 
                    if (ivType != null) {
                        ivType.setImageResource(R.drawable.ic_objets);
                        ivType.setVisibility(View.VISIBLE);
                    }
                    break;
                case "streak":
                    tvValue.setText(getString(R.string.rank_format_streak, value));
                    if (ivType != null) {
                        ivType.setAnimation(R.raw.streak_animation);
                        ivType.setMinAndMaxFrame(0, 24);
                        if (!ivType.isAnimating()) {
                            ivType.playAnimation();
                        }
                        com.vn.jet.mosco.utils.StreakColorHelper.applyStreakColor(ivType, value);
                        ivType.setVisibility(View.VISIBLE);
                    }
                    break;
            }

            // Avatar
            String avatarUrl = userRankData.optString("avatarUrl", null);
            String avatarId = userRankData.optString("avatarId", "1");
            
            if (avatarUrl == null || avatarUrl.isEmpty() || "null".equals(avatarUrl)) {
                JSONObject card = com.vn.jet.mosco.utils.DatabaseLoader.findByCollectionId(this, avatarId);
                if (card != null) {
                    avatarUrl = card.optString("frontImage", null);
                }
            }

            Glide.with(this)
                .load(avatarUrl)
                .placeholder(R.drawable.ic_user)
                .transform(new SmartFaceCropTransformation())
                .into(ivAvatar);

            // Sử dụng màu trắng cơ bản (Unified)
            tvPos.setTextColor(android.graphics.Color.WHITE);

        } catch (Exception e) {
            // Không ẩn footer nếu lỗi, chỉ để mặc định
            tvPos.setText(getString(R.string.placeholder_empty));
            tvValue.setText(getString(R.string.placeholder_empty));
        }
    }

    private static class RankPagerAdapter extends FragmentStateAdapter {
        public RankPagerAdapter(@NonNull AppCompatActivity activity) {
            super(activity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            String rankType;
            switch (position) {
                case 0: rankType = "level"; break;
                case 1: rankType = "collection"; break;
                case 2: rankType = "wealth"; break;
                case 3: rankType = "streak"; break;
                default: rankType = "level"; break;
            }
            return RankListFragment.newInstance(rankType);
        }

        @Override
        public int getItemCount() { return 4; }
    }
}
