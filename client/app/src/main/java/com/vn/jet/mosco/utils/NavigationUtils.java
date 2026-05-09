package com.vn.jet.mosco.utils;

import android.os.Bundle;
import android.util.Log;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.vn.jet.mosco.R;
import com.vn.jet.mosco.fragment.ProfileFragment;
import com.vn.jet.mosco.fragment.RankFragment;

import java.util.LinkedList;

/**
 * NavigationUtils - Quản lý điều hướng tập trung cho dự án Mosco.
 * Triển khai cơ chế "Eviction Stack" (Tối đa 5 Profile) để bảo vệ bộ nhớ.
 */
public class NavigationUtils {
    private static final String TAG = "NavigationUtils";
    private static final int MAX_PROFILE_STACK = 5;
    
    // Danh sách lưu trữ các tag của Profile Fragment đang có trong Backstack
    private static final LinkedList<String> profileStackTags = new LinkedList<>();

    /**
     * Mở màn hình Profile với logic giới hạn Stack.
     * @param activity Context của activity chứa fragment.
     * @param targetUserId ID người dùng cần xem (null nếu là chính mình).
     */
    public static void openProfile(FragmentActivity activity, Long targetUserId) {
        if (activity == null) return;

        SessionManager sessionManager = new SessionManager(activity);
        Long currentUserId = sessionManager.getUserId();

        // Nếu là chính mình (targetUserId null hoặc khớp ID hiện tại), chuyển sang Tab Profile (Tab thứ 5)
        if (activity instanceof com.vn.jet.mosco.MainActivity && (targetUserId == null || (currentUserId != null && currentUserId.equals(targetUserId)))) {
            ((com.vn.jet.mosco.MainActivity) activity).selectTab(R.id.nav_profile);
            return;
        }

        FragmentManager fragmentManager = activity.getSupportFragmentManager();
        String tag = "Profile_" + (targetUserId != null ? targetUserId : "Owner") + "_" + System.currentTimeMillis();

        // Kiểm tra nếu đạt giới hạn stack
        if (profileStackTags.size() >= MAX_PROFILE_STACK) {
            String oldestTag = profileStackTags.removeFirst();
            Fragment oldestFragment = fragmentManager.findFragmentByTag(oldestTag);
            if (oldestFragment != null) {
                Log.d(TAG, "Evicting oldest profile fragment: " + oldestTag);
                // Loại bỏ fragment cũ nhất để giải phóng bộ nhớ
                fragmentManager.beginTransaction().remove(oldestFragment).commitAllowingStateLoss();
                // Lưu ý: Việc remove khỏi backstack thực sự của FragmentManager phức tạp hơn,
                // nhưng việc remove fragment instance là bước quan trọng nhất để cứu RAM.
            }
        }

        ProfileFragment fragment = new ProfileFragment();
        if (targetUserId != null) {
            Bundle args = new Bundle();
            args.putLong(ProfileFragment.ARG_TARGET_USER_ID, targetUserId);
            fragment.setArguments(args);
        }

        fragmentManager.beginTransaction()
                .setCustomAnimations(
                        R.anim.anim_slide_in_right,
                        R.anim.anim_slide_out_left,
                        R.anim.anim_slide_in_left,
                        R.anim.anim_slide_out_right
                )
                .add(R.id.frame_layout, fragment, tag)
                .addToBackStack(tag)
                .commit();

        profileStackTags.addLast(tag);
        Log.d(TAG, "Profile opened. Current stack size: " + profileStackTags.size());
    }

    public static void openRank(FragmentActivity activity) {
        if (activity == null) return;
        
        // Khi mở Rank, dọn dẹp các Profile cũ để tránh rối
        profileStackTags.clear();
        
        activity.getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.anim_slide_in_right, R.anim.anim_slide_out_left, R.anim.anim_slide_in_left, R.anim.anim_slide_out_right)
                .replace(R.id.frame_layout, new RankFragment())
                .addToBackStack("Rank")
                .commit();
    }

    /**
     * Cần gọi hàm này khi người dùng nhấn Back để đồng bộ lại stack nội bộ.
     */
    public static void handleBackPress() {
        if (!profileStackTags.isEmpty()) {
            profileStackTags.removeLast();
            Log.d(TAG, "Profile popped. Remaining stack: " + profileStackTags.size());
        }
    }
    
    /**
     * Chuyển đổi sang một Fragment chính (Home, Rank, etc.) và dọn dẹp Stack Profile.
     */
    public static void navigateToMainFragment(FragmentActivity activity, Fragment fragment) {
        if (activity == null) return;
        
        profileStackTags.clear();
        activity.getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        
        activity.getSupportFragmentManager().beginTransaction()
                .replace(R.id.frame_layout, fragment)
                .commit();
    }
}
