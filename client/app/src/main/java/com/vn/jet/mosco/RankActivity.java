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

/**
 * Bảng xếp hạng — Cấu trúc Tab GIỐNG HỆT CollectionFragment.
 * 3 Tab: LEVEL, OVR, COLLECTION.
 * Sử dụng TabLayout + ViewPager2 + FragmentStateAdapter + TabLayoutMediator.
 */
public class RankActivity extends AppCompatActivity {

    private static final String TAG = "RankActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rank);

        // Nút back
        findViewById(R.id.btn_back_rank).setOnClickListener(v -> finish());

        TabLayout tabLayout = findViewById(R.id.tab_layout_rank);
        ViewPager2 viewPager = findViewById(R.id.view_pager_rank);

        // Setup adapter — Copy pattern từ CollectionFragment
        RankPagerAdapter adapter = new RankPagerAdapter(this);
        viewPager.setAdapter(adapter);

        // QUAN TRỌNG: Tắt swipe để chỉ cho chuyển tab bằng click — giống Collection
        viewPager.setUserInputEnabled(false);

        // Gắn TabLayout + ViewPager2 — Copy pattern từ CollectionFragment
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0: tab.setText("LEVEL"); break;
                case 1: tab.setText("OVR"); break;
                case 2: tab.setText("OBJET"); break;
            }
        }).attach();
    }

    /**
     * PagerAdapter — Copy pattern từ CollectionPagerAdapter.
     * Mỗi tab trả về 1 RankListFragment với param loại rank khác nhau.
     */
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
                case 1: rankType = "ovr"; break;
                case 2: rankType = "collection"; break;
                default: rankType = "level"; break;
            }
            return RankListFragment.newInstance(rankType);
        }

        @Override
        public int getItemCount() { return 3; }
    }
}
