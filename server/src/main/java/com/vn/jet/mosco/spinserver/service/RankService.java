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
        return getTopFromRedis(RANK_KEY_LEVEL);
    }

    public List<Map<String, Object>> getTopByOvr() {
        return getTopFromRedis(RANK_KEY_OVR);
    }

    public List<Map<String, Object>> getTopByCollection() {
        return getTopFromRedis(RANK_KEY_COLLECTION);
    }

    public List<Map<String, Object>> getTopByWealth() {
        return getTopFromRedis(RANK_KEY_WEALTH);
    }

    public List<Map<String, Object>> getTopByStreak() {
        return getTopFromRedis(RANK_KEY_STREAK);
    }

    private List<Map<String, Object>> getTopFromRedis(String key) {
        Set<ZSetOperations.TypedTuple<String>> topEntries = redisTemplate.opsForZSet()
                .reverseRangeWithScores(key, 0, 9);

        if (topEntries == null || topEntries.isEmpty()) {
            return Collections.emptyList();
        }

        return topEntries.stream().map(tuple -> {
            Long userId = Long.valueOf(tuple.getValue());
            User user = userRepository.findById(userId).orElse(null);
            Map<String, Object> map = new HashMap<>();
            if (user != null) {
                map.put("userId", userId);
                map.put("ingameName", user.getIngameName() != null ? user.getIngameName() : user.getUsername());
                map.put("avatarId", user.getAvatarId());
                
                // Trả về giá trị điểm số
                if (key.equals(RANK_KEY_LEVEL)) {
                    map.put("value", user.getLevel());
                } else {
                    map.put("value", tuple.getScore().intValue());
                }
            }
            return map;
        }).filter(m -> !m.isEmpty()).collect(Collectors.toList());
    }
}
