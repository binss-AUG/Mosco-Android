package com.vn.jet.mosco.spinserver.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.vn.jet.mosco.spinserver.dto.CollectionBookResponse;
import com.vn.jet.mosco.spinserver.dto.CollectionEntry;
import com.vn.jet.mosco.spinserver.model.UserCard;
import com.vn.jet.mosco.spinserver.repository.UserCardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service xử lý logic Bộ Sưu Tập (Collection Book).
 * Cross-reference toàn bộ thẻ trong database.json với thẻ user đã sở hữu,
 * trả về danh sách hoàn chỉnh kèm trạng thái owned/unowned.
 */
@Service
public class CollectionBookService {

    private static final Logger logger = LoggerFactory.getLogger(CollectionBookService.class);

    private final CardDataService cardDataService;
    private final UserCardRepository userCardRepository;
    private final com.vn.jet.mosco.spinserver.repository.UserRepository userRepository;

    public CollectionBookService(CardDataService cardDataService,
                                 UserCardRepository userCardRepository,
                                 com.vn.jet.mosco.spinserver.repository.UserRepository userRepository) {
        this.cardDataService = cardDataService;
        this.userCardRepository = userCardRepository;
        this.userRepository = userRepository;
    }

    /**
     * Lấy toàn bộ Bộ Sưu Tập cho user.
     * So sánh danh sách thẻ trong database.json với bảng user_cards và unlockedCollections.
     * Đánh dấu thẻ nào đã từng sở hữu (Ever Owned).
     *
     * @param userId ID của user cần tra cứu
     * @return CollectionBookResponse chứa tiến trình + danh sách entries
     */
    @Transactional
    public CollectionBookResponse getCollectionBook(Long userId) {
        // 1. Lấy toàn bộ card metadata từ cache
        Map<String, JsonNode> allCards = cardDataService.getAllCardMetadata();
        if (allCards == null || allCards.isEmpty()) {
            logger.warn("CollectionBook: cardMetadataCache trống, trả về response rỗng");
            return new CollectionBookResponse(0, 0, new ArrayList<>());
        }

        com.vn.jet.mosco.spinserver.model.User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return new CollectionBookResponse(0, 0, new ArrayList<>());
        }

        // 2. Lấy danh sách thẻ user ĐANG CÓ để hiển thị level/ovr thực tế
        List<UserCard> userCards = userCardRepository.findByUserId(userId);
        Map<String, UserCard> currentOwnedMap = new HashMap<>();
        Set<String> unlocked = user.getUnlockedCollections();
        boolean needsSaveUser = false;

        for (UserCard uc : userCards) {
            String cId = uc.getCollectionId();
            
            // Tự động migration: Cập nhật unlockedCollections nếu user có thẻ nhưng chưa track
            if (!unlocked.contains(cId)) {
                unlocked.add(cId);
                needsSaveUser = true;
            }

            if (!currentOwnedMap.containsKey(cId)) {
                currentOwnedMap.put(cId, uc);
            } else {
                UserCard existing = currentOwnedMap.get(cId);
                if (uc.getUpgradeLevel() > existing.getUpgradeLevel()) {
                    currentOwnedMap.put(cId, uc);
                }
            }
        }

        if (needsSaveUser) {
            userRepository.save(user);
        }

        // 3. Tạo danh sách entries
        List<CollectionEntry> entries = new ArrayList<>();
        int ownedCount = 0;

        for (Map.Entry<String, JsonNode> entry : allCards.entrySet()) {
            JsonNode meta = entry.getValue();
            String collectionId = entry.getKey();

            CollectionEntry ce = new CollectionEntry();
            ce.setCollectionId(collectionId);
            ce.setMember(meta.has("member") ? meta.get("member").asText() : "");
            ce.setSeason(meta.has("season") ? meta.get("season").asText() : "");
            ce.setCardClass(meta.has("class") ? meta.get("class").asText() : "");
            ce.setCollectionNo(meta.has("collectionNo") ? meta.get("collectionNo").asText() : "");
            ce.setFrontImage(meta.has("frontImage") ? meta.get("frontImage").asText() : "");
            ce.setBackImage(meta.has("backImage") ? meta.get("backImage").asText() : "");
            ce.setBackgroundColor(meta.has("backgroundColor") ? meta.get("backgroundColor").asText() : "#FFFFFF");

            if (unlocked.contains(collectionId)) {
                ce.setOwned(true);
                ownedCount++;
                
                if (currentOwnedMap.containsKey(collectionId)) {
                    UserCard uc = currentOwnedMap.get(collectionId);
                    ce.setUserCardId(uc.getId());
                    ce.setOvr(cardDataService.getOvr(collectionId, uc.getUpgradeLevel()));
                    ce.setUpgradeLevel(uc.getUpgradeLevel());
                    ce.setLevel(uc.getLevel());
                } else {
                    ce.setUserCardId(-1L);
                    ce.setOvr(cardDataService.getOvr(collectionId, 1));
                    ce.setUpgradeLevel(1);
                    ce.setLevel(1);
                }
            } else {
                ce.setOwned(false);
            }
            if (meta.has("createdAt")) {
                ce.setCreatedAt(meta.get("createdAt").asText());
            }

            entries.add(ce);
        }

        // 4. Sắp xếp: Thẻ đã sở hữu lên trước, sau đó theo season DESC
        entries.sort((a, b) -> {
            if (a.isOwned() != b.isOwned()) return a.isOwned() ? -1 : 1;
            return b.getSeason().compareTo(a.getSeason());
        });

        logger.info("CollectionBook: userId={}, everOwned={}/{}", userId, ownedCount, entries.size());
        return new CollectionBookResponse(entries.size(), ownedCount, entries);
    }
}
