package com.vn.jet.mosco.spinserver.service;

import com.vn.jet.mosco.spinserver.model.User;
import com.vn.jet.mosco.spinserver.model.UserCard;
import com.vn.jet.mosco.spinserver.repository.UserRepository;
import com.vn.jet.mosco.spinserver.repository.UserCardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service xử lý logic Bảng xếp hạng (Leaderboard) sử dụng MySQL trực tiếp.
 * Đảm bảo hiệu năng cho 20.000+ người chơi/thẻ bài.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RankService {

    private final UserRepository userRepository;
    private final UserCardRepository userCardRepository;
    private final CardDataService cardDataService;

    @jakarta.annotation.PostConstruct
    public void repairTotalDiamonds() {
        log.info("Checking and repairing totalDiamonds for existing users...");
        List<User> users = userRepository.findAll();
        for (User user : users) {
            if (user.getTotalDiamonds() == 0 && user.getDiamonds() > 0) {
                user.setTotalDiamonds(user.getDiamonds());
                userRepository.save(user);
            }
        }
        log.info("TotalDiamonds repair completed.");
    }

    /**
     * Không còn sử dụng Redis nên phương thức này được giữ lại làm stub
     * để không ảnh hưởng đến các service khác gọi tới.
     */
    public void updateUserRank(User user, long maxOvr, long distinctCollection) {
        // No-op (Bảng xếp hạng truy vấn trực tiếp từ MySQL)
    }

    public List<Map<String, Object>> getTopByLevel() {
        return userRepository.findTop10ByOrderByLevelDesc().stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("userId", u.getId());
            m.put("ingameName", u.getIngameName() != null ? u.getIngameName() : u.getUsername());
            m.put("avatarId", u.getAvatarId());
            m.put("value", u.getLevel());
            return m;
        }).collect(Collectors.toList());
    }

    /**
     * Tính toán Top 10 OVR bằng cách tải tất cả thẻ bài của người chơi
     * và tính OVR tối đa theo công thức của CardDataService.
     */
    public List<Map<String, Object>> getTopByOvr() {
        try {
            List<UserCard> allCards = userCardRepository.findAllWithUser();
            Map<Long, Integer> userMaxOvr = new HashMap<>();
            for (UserCard uc : allCards) {
                if (uc.getUser() != null) {
                    int ovr = cardDataService.getOvr(uc.getCollectionId(), uc.getUpgradeLevel());
                    userMaxOvr.merge(uc.getUser().getId(), ovr, Math::max);
                }
            }

            return userMaxOvr.entrySet().stream()
                    .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                    .limit(10)
                    .map(entry -> {
                        User u = userRepository.findById(entry.getKey()).orElse(null);
                        Map<String, Object> m = new HashMap<>();
                        if (u != null) {
                            m.put("userId", u.getId());
                            m.put("ingameName", u.getIngameName() != null ? u.getIngameName() : u.getUsername());
                            m.put("avatarId", u.getAvatarId());
                            m.put("value", entry.getValue());
                        }
                        return m;
                    })
                    .filter(m -> !m.isEmpty())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to fetch top OVR from DB: ", e);
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> getTopByCollection() {
        return userRepository.findTop10ByCollectionCount().stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("userId", u.getId());
            m.put("ingameName", u.getIngameName() != null ? u.getIngameName() : u.getUsername());
            m.put("avatarId", u.getAvatarId());
            m.put("value", u.getUnlockedCollections().size());
            return m;
        }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getTopByWealth() {
        return userRepository.findTop10ByOrderByTotalDiamondsDesc().stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("userId", u.getId());
            m.put("ingameName", u.getIngameName() != null ? u.getIngameName() : u.getUsername());
            m.put("avatarId", u.getAvatarId());
            m.put("value", u.getTotalDiamonds());
            return m;
        }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getTopByStreak() {
        return userRepository.findTop10ByOrderByBestStreakDesc().stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("userId", u.getId());
            m.put("ingameName", u.getIngameName() != null ? u.getIngameName() : u.getUsername());
            m.put("avatarId", u.getAvatarId());
            m.put("value", u.getBestStreak());
            return m;
        }).collect(Collectors.toList());
    }
}
