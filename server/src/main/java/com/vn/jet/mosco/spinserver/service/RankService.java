package com.vn.jet.mosco.spinserver.service;

import com.vn.jet.mosco.spinserver.model.User;
import com.vn.jet.mosco.spinserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service xử lý logic Bảng xếp hạng (Leaderboard) sử dụng Redis ZSET.
 * Đảm bảo hiệu năng realtime cho 20.000+ người chơi.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RankService {

    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String RANK_KEY_LEVEL = "rank:level";
    private static final String RANK_KEY_OVR = "rank:ovr";
    private static final String RANK_KEY_COLLECTION = "rank:collection";
    private static final String RANK_KEY_WEALTH = "rank:wealth";
    private static final String RANK_KEY_STREAK = "rank:streak";

    /**
     * Cập nhật điểm số của User lên Redis ZSET.
     * Cần được gọi mỗi khi User thay đổi chỉ số (Level up, nạp coin, cào thẻ...).
     */
    public void updateUserRank(User user, long maxOvr, long distinctCollection) {
        ZSetOperations<String, String> zSet = redisTemplate.opsForZSet();
        String userIdStr = user.getId().toString();

        zSet.add(RANK_KEY_LEVEL, userIdStr, user.getExp());
        zSet.add(RANK_KEY_OVR, userIdStr, maxOvr);
        zSet.add(RANK_KEY_COLLECTION, userIdStr, distinctCollection);
        zSet.add(RANK_KEY_WEALTH, userIdStr, user.getDiamonds());
        zSet.add(RANK_KEY_STREAK, userIdStr, user.getBestStreak());
    }

    public List<Map<String, Object>> getTopByLevel() {
        List<Map<String, Object>> result = getTopFromRedis(RANK_KEY_LEVEL);
        if (result.isEmpty()) {
            return userRepository.findTop10ByOrderByLevelDesc().stream().map(u -> {
                Map<String, Object> m = new HashMap<>();
                m.put("userId", u.getId());
                m.put("ingameName", u.getIngameName() != null ? u.getIngameName() : u.getUsername());
                m.put("avatarId", u.getAvatarId());
                m.put("value", u.getLevel());
                return m;
            }).collect(Collectors.toList());
        }
        return result;
    }

    public List<Map<String, Object>> getTopByOvr() {
        return getTopFromRedis(RANK_KEY_OVR);
    }

    public List<Map<String, Object>> getTopByCollection() {
        List<Map<String, Object>> result = getTopFromRedis(RANK_KEY_COLLECTION);
        if (result.isEmpty()) {
            return userRepository.findTop10ByCollectionCount().stream().map(u -> {
                Map<String, Object> m = new HashMap<>();
                m.put("userId", u.getId());
                m.put("ingameName", u.getIngameName() != null ? u.getIngameName() : u.getUsername());
                m.put("avatarId", u.getAvatarId());
                m.put("value", u.getUnlockedCollections().size());
                return m;
            }).collect(Collectors.toList());
        }
        return result;
    }

    public List<Map<String, Object>> getTopByWealth() {
        List<Map<String, Object>> result = getTopFromRedis(RANK_KEY_WEALTH);
        if (result.isEmpty()) {
            return userRepository.findTop10ByOrderByDiamondsDesc().stream().map(u -> {
                Map<String, Object> m = new HashMap<>();
                m.put("userId", u.getId());
                m.put("ingameName", u.getIngameName() != null ? u.getIngameName() : u.getUsername());
                m.put("avatarId", u.getAvatarId());
                m.put("value", u.getDiamonds());
                return m;
            }).collect(Collectors.toList());
        }
        return result;
    }

    public List<Map<String, Object>> getTopByStreak() {
        List<Map<String, Object>> result = getTopFromRedis(RANK_KEY_STREAK);
        if (result.isEmpty()) {
            return userRepository.findTop10ByOrderByBestStreakDesc().stream().map(u -> {
                Map<String, Object> m = new HashMap<>();
                m.put("userId", u.getId());
                m.put("ingameName", u.getIngameName() != null ? u.getIngameName() : u.getUsername());
                m.put("avatarId", u.getAvatarId());
                m.put("value", u.getBestStreak());
                return m;
            }).collect(Collectors.toList());
        }
        return result;
    }

    private List<Map<String, Object>> getTopFromRedis(String key) {
        try {
            Set<ZSetOperations.TypedTuple<String>> topEntries = redisTemplate.opsForZSet()
                    .reverseRangeWithScores(key, 0, 9);

            if (topEntries == null || topEntries.isEmpty()) {
                return Collections.emptyList();
            }

            return topEntries.stream().map(tuple -> {
                String value = tuple.getValue();
                if (value == null) return new HashMap<String, Object>();
                
                try {
                    Long userId = Long.valueOf(value);
                    User user = userRepository.findById(userId).orElse(null);
                    Map<String, Object> map = new HashMap<>();
                    if (user != null) {
                        map.put("userId", userId);
                        map.put("ingameName", user.getIngameName() != null ? user.getIngameName() : user.getUsername());
                        map.put("avatarId", user.getAvatarId());
                        
                        // Trả về giá trị điểm số (an toàn với null)
                        if (key.equals(RANK_KEY_LEVEL)) {
                            map.put("value", user.getLevel());
                        } else {
                            Double score = tuple.getScore();
                            map.put("value", score != null ? score.intValue() : 0);
                        }
                    }
                    return map;
                } catch (NumberFormatException nfe) {
                    return new HashMap<String, Object>();
                }
            }).filter(m -> !m.isEmpty()).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("CRITICAL: Redis connection or logic error for key {}: {}", key, e.getMessage());
            return Collections.emptyList();
        }
    }
}
