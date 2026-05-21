package com.vn.jet.mosco.spinserver.utils;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Trình theo dõi trạng thái hoạt động trực tuyến (Online/Offline) của người chơi.
 * Tại sao (WHY): Lưu vết hoạt động cuối cùng của người chơi trên bộ nhớ đệm Thread-safe ConcurrentHashMap
 * thay vì ghi xuống Disk liên tục giúp bảo toàn tối đa I/O đĩa và phản hồi trạng thái online cực nhanh.
 */
public class UserSessionTracker {
    private static final ConcurrentHashMap<Long, Long> activeUsers = new ConcurrentHashMap<>();

    /**
     * Cập nhật thời điểm hoạt động cuối cùng của người chơi.
     */
    public static void updateActivity(Long userId) {
        if (userId != null) {
            activeUsers.put(userId, System.currentTimeMillis());
        }
    }

    /**
     * Kiểm tra người chơi có trực tuyến hay không.
     * Trực tuyến = Có hoạt động trong vòng 5 phút trở lại đây.
     */
    public static boolean isOnline(Long userId) {
        if (userId == null) return false;
        Long lastActive = activeUsers.get(userId);
        if (lastActive == null) return false;
        // Trực tuyến nếu hoạt động trong 5 phút gần nhất (300.000 ms)
        return (System.currentTimeMillis() - lastActive) < 300000;
    }
}
