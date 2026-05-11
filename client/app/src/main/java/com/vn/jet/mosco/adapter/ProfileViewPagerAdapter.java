package com.vn.jet.mosco.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.vn.jet.mosco.fragment.ProfileGeneralFragment;
import com.vn.jet.mosco.fragment.ProfileTrophyFragment;
import com.vn.jet.mosco.fragment.ProfileExhibitFragment;

/**
 * Adapter quản lý 3 Fragment (General, Trophy, Exhibit) của Profile V2.
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
            case 2:
                return new ProfileExhibitFragment();
            default:
                return new ProfileGeneralFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}
