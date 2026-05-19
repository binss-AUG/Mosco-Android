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

        // Bỏ logic ép về Tab Profile để giữ nguyên Backstack khi bấm vào Avatar của chính mình từ các màn hình khác
        // (Giúp nút Back hoạt động đúng, quay về "vị trí đã tương tác" thay vì về Home)

        FragmentManager fragmentManager = activity.getSupportFragmentManager();
        String tag = "Profile_" + (targetUserId != null ? targetUserId : "Owner") + "_" + System.currentTimeMillis();

        // [Quyết định của Tech Lead] Khi số lượng chuẩn bị đạt 6 (tức size >= 5),
        // tiến hành tìm Fragment cũ nhất theo Tag và gọi lệnh .remove() để giải phóng RAM triệt để
        if (profileStackTags.size() >= MAX_PROFILE_STACK) {
            String oldestTag = profileStackTags.removeFirst();
            Fragment oldestFragment = fragmentManager.findFragmentByTag(oldestTag);
            if (oldestFragment != null) {
                Log.d(TAG, "Evicting oldest profile fragment: " + oldestTag);
                fragmentManager.beginTransaction().remove(oldestFragment).commitAllowingStateLoss();
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
    /**
     * Mở màn hình Chat riêng với người dùng khác.
     * @param activity Context của activity chứa fragment.
     * @param partnerId ID người dùng cần chat.
     * @param partnerName Tên hiển thị của người dùng đó.
     * @param partnerAvatar ID avatar của người dùng đó.
     */
    public static void openPrivateChat(FragmentActivity activity, Long partnerId, String partnerName, String partnerAvatar) {
        openPrivateChat(activity, partnerId, partnerName, partnerAvatar, false, false);
    }

    public static void openPrivateChat(FragmentActivity activity, Long partnerId, String partnerName, String partnerAvatar, boolean isOnline, boolean isStranger) {
        if (activity == null || partnerId == null) return;

        FragmentManager fragmentManager = activity.getSupportFragmentManager();
        String tag = "Chat_" + partnerId;

        // Nếu Fragment đã tồn tại trong backstack, quay về đó thay vì tạo mới
        if (fragmentManager.findFragmentByTag(tag) != null) {
            fragmentManager.popBackStack(tag, 0);
            return;
        }

        com.vn.jet.mosco.fragment.ChatPrivateFragment fragment = 
                com.vn.jet.mosco.fragment.ChatPrivateFragment.newInstance(partnerId, partnerName, partnerAvatar, isOnline, isStranger);

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
    }

    public static void openMailbox(FragmentActivity activity) {
        if (activity == null) return;
        
        activity.getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.anim_slide_in_right, R.anim.anim_slide_out_left, R.anim.anim_slide_in_left, R.anim.anim_slide_out_right)
                .add(R.id.frame_layout, new com.vn.jet.mosco.fragment.MailboxFragment())
                .addToBackStack("Mailbox")
                .commit();
    }
}
