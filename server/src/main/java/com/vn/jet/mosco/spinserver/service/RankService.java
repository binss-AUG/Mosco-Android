package com.vn.jet.mosco.spinserver.service;

import com.vn.jet.mosco.spinserver.model.User;
import com.vn.jet.mosco.spinserver.model.UserCard;
import com.vn.jet.mosco.spinserver.repository.UserCardRepository;
import com.vn.jet.mosco.spinserver.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service xử lý logic Bảng xếp hạng (Leaderboard).
 * 
 * 3 loại ranking:
 *   1. LEVEL: Top 10 user có level cao nhất
 *   2. OVR: Top 10 user có OVR cao nhất (OVR = OVR tổ của Objet to nhất đang có)
 *   3. COLLECTION: Top 10 user có nhiều thẻ không trùng nhất (count distinct collectionId)
 *
 * OVR được tính bằng CardDataService.getOvr() — Server là nguồn sự thật duy nhất.
 */
@Service
public class RankService {

    private static final Logger logger = LoggerFactory.getLogger(RankService.class);

    private final UserRepository userRepository;
    private final UserCardRepository userCardRepository;
    private final CardDataService cardDataService;

    public RankService(UserRepository userRepository, UserCardRepository userCardRepository, CardDataService cardDataService) {
        this.userRepository = userRepository;
        this.userCardRepository = userCardRepository;
        this.cardDataService = cardDataService;
    }

    /**
     * Top 10 user theo Level (cao nhất trước).
     */
    public List<Map<String, Object>> getTopByLevel() {
        List<User> allUsers = userRepository.findAll();

        // Sắp xếp theo exp giảm dần (Exp đại diện cho Level thực tế), lấy top 10
        return allUsers.stream()
                .sorted(Comparator.comparingLong(User::getExp).reversed())
                .limit(10)
                .map(user -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("userId", user.getId());
                    entry.put("ingameName", user.getIngameName() != null ? user.getIngameName() : user.getUsername());
                    entry.put("avatarId", user.getAvatarId());
                    
                    // Lấy level chuẩn từ Entity (đã được bọc logic dynamic)
                    entry.put("value", user.getLevel());
                    
                    return entry;
                })
                .collect(Collectors.toList());
    }

    /**
     * Top 10 user theo OVR (cao nhất trước).
     * OVR = OVR tổ của Objet to nhất đang có (cardDataService.getOvr).
     */
    public List<Map<String, Object>> getTopByOvr() {
        List<User> allUsers = userRepository.findAll();
        List<Map<String, Object>> rankings = new ArrayList<>();

        for (User user : allUsers) {
            List<UserCard> cards = userCardRepository.findByUserId(user.getId());
            int maxOvr = 0;

            // Tìm thẻ có OVR cao nhất trong kho của user
            for (UserCard card : cards) {
                int ovr = cardDataService.getOvr(card.getCollectionId(), card.getUpgradeLevel());
                if (ovr > maxOvr) {
                    maxOvr = ovr;
                }
            }

            // Chỉ xếp hạng user có ít nhất 1 thẻ
            if (maxOvr > 0) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("userId", user.getId());
                entry.put("ingameName", user.getIngameName() != null ? user.getIngameName() : user.getUsername());
                entry.put("avatarId", user.getAvatarId());
                entry.put("value", maxOvr);
                rankings.add(entry);
            }
        }

        // Sắp xếp giảm dần, lấy top 10
        rankings.sort((a, b) -> Integer.compare((int) b.get("value"), (int) a.get("value")));
        return rankings.size() > 10 ? rankings.subList(0, 10) : rankings;
    }

    /**
     * Top 10 user theo số thẻ không trùng (Collection = COUNT DISTINCT collectionId).
     */
    public List<Map<String, Object>> getTopByCollection() {
        List<User> allUsers = userRepository.findAll();
        List<Map<String, Object>> rankings = new ArrayList<>();

        for (User user : allUsers) {
            List<UserCard> cards = userCardRepository.findByUserId(user.getId());

            // Đếm số collectionId không trùng
            long distinctCount = cards.stream()
                    .map(UserCard::getCollectionId)
                    .distinct()
                    .count();

            if (distinctCount > 0) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("userId", user.getId());
                entry.put("ingameName", user.getIngameName() != null ? user.getIngameName() : user.getUsername());
                entry.put("avatarId", user.getAvatarId());
                entry.put("value", (int) distinctCount);
                rankings.add(entry);
            }
        }

        rankings.sort((a, b) -> Integer.compare((int) b.get("value"), (int) a.get("value")));
        return rankings.size() > 10 ? rankings.subList(0, 10) : rankings;
    }
}
