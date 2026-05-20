package com.vn.jet.mosco.spinserver.security;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Trình lưu trữ bộ đệm token hoạt động (Active Token Cache) trên RAM.
 * Tại sao (WHY): Giảm tải tối đa việc SELECT database bảng User tại JwtAuthFilter cho mỗi request HTTP,
 * giúp hệ thống chịu tải cao và phản hồi xác thực nhanh chóng.
 */
public class TokenCache {
    private static final ConcurrentHashMap<Long, String> activeTokenCache = new ConcurrentHashMap<>();

    public static void put(Long userId, String token) {
        if (userId != null && token != null) {
            activeTokenCache.put(userId, token);
        }
    }

    public static String get(Long userId) {
        if (userId == null) return null;
        return activeTokenCache.get(userId);
    }

    public static void evict(Long userId) {
        if (userId != null) {
            activeTokenCache.remove(userId);
        }
    }

    public static void clear() {
        activeTokenCache.clear();
    }
}
