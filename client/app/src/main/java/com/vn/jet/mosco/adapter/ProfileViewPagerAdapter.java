package com.vn.jet.mosco.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.vn.jet.mosco.fragment.ProfileGeneralFragment;
import com.vn.jet.mosco.fragment.ProfileTrophyFragment;

/**
 * Adapter quản lý 2 Fragment (General, Trophy) của Profile.
 * Hỗ trợ Lazy Loading mặc định qua ViewPager2.
 */
public class ProfileViewPagerAdapter extends FragmentStateAdapter {

    public ProfileViewPagerAdapter(@NonNull Fragment fragment) {
        super(fragment);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return new ProfileGeneralFragment();
            case 1:
                return new ProfileTrophyFragment();
            default:
                return new ProfileGeneralFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 2;
    }
}
