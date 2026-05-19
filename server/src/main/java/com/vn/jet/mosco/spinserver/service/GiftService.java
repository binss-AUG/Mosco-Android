package com.vn.jet.mosco.spinserver.service;

import com.vn.jet.mosco.spinserver.dto.GiftHistoryDTO;
import com.vn.jet.mosco.spinserver.model.GiftHistory;
import com.vn.jet.mosco.spinserver.model.User;
import com.vn.jet.mosco.spinserver.model.UserCard;
import com.vn.jet.mosco.spinserver.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GiftService — Xử lý nghiệp vụ tặng Objet giữa các user.
 *
 * Luồng chính:
 *   1. Validate (ownership, friendship, daily limit, formation lock, tài nguyên)
 *   2. Trừ phí: 36,000 Coin + 36 Diamond
 *   3. Chuyển chủ thẻ: card.user = receiver
 *   4. Ghi log GiftHistory (đóng vai trò inbox cho người nhận)
 *
 * Tại sao dùng @Transactional: Đảm bảo tính nguyên tử —
 * nếu bất kỳ bước nào thất bại, toàn bộ giao dịch rollback.
 */
@Service
public class GiftService {

    private static final Logger logger = LoggerFactory.getLogger(GiftService.class);

    // ═══ CẤU HÌNH — Tập trung tại đây để dễ thay đổi ═══
    private static final int DAILY_GIFT_LIMIT = 5;
    private static final long GIFT_COST_COINS = 0L; // Không tốn Gold
    private static final long GIFT_COST_DIAMONDS = 0L; // Không tốn Diamond

    private final UserRepository userRepository;
    private final UserCardRepository userCardRepository;
    private final FriendshipRepository friendshipRepository;
    private final GiftHistoryRepository giftHistoryRepository;
    private final CardDataService cardDataService;

    public GiftService(UserRepository userRepository,
                       UserCardRepository userCardRepository,
                       FriendshipRepository friendshipRepository,
                       GiftHistoryRepository giftHistoryRepository,
                       CardDataService cardDataService) {
        this.userRepository = userRepository;
        this.userCardRepository = userCardRepository;
        this.friendshipRepository = friendshipRepository;
        this.giftHistoryRepository = giftHistoryRepository;
        this.cardDataService = cardDataService;
    }

    /**
     * Gửi tặng một thẻ bài cho bạn bè.
     * Phí: 36,000 Coin + 36 Diamond. Giới hạn: 5 lần/ngày.
     *
     * @param senderId   ID người gửi (lấy từ JWT)
     * @param cardId     ID thẻ bài cần gửi
     * @param receiverId ID người nhận
     * @return null nếu thành công, String lỗi nếu thất bại
     */
    @Transactional
    public String sendGift(Long senderId, Long cardId, Long receiverId) {
        // 1. Không cho tặng chính mình
        if (senderId.equals(receiverId)) {
            return "Cannot send a gift to yourself!";
        }

        // 2. Kiểm tra người gửi tồn tại
        User sender = userRepository.findById(senderId).orElse(null);
        if (sender == null) {
            return "Sender does not exist!";
        }

        // 3. Kiểm tra người nhận tồn tại
        User receiver = userRepository.findById(receiverId).orElse(null);
        if (receiver == null) {
            return "Receiver does not exist!";
        }

        // 5. Kiểm tra thẻ thuộc về người gửi
        UserCard card = userCardRepository.findByIdAndUserId(cardId, senderId).orElse(null);
        if (card == null) {
            return "Card does not exist or does not belong to you!";
        }

        // 6. Kiểm tra thẻ có đang trong Formation không (Formation Lock)
        if (isCardInFormation(sender, cardId)) {
            return "This card is equipped in your Formation! Please remove it before gifting.";
        }

        // 7. Kiểm tra giới hạn gửi trong ngày của người gửi (5/ngày)
        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        int todaySentCount = giftHistoryRepository.countBySenderIdAndCreatedAtAfter(senderId, startOfDay);
        if (todaySentCount >= DAILY_GIFT_LIMIT) {
            return "You have reached your daily gifting limit (5/5)! Please try again tomorrow.";
        }

        // 7.5. Kiểm tra giới hạn nhận trong ngày của người nhận (5/ngày)
        int todayReceivedCount = giftHistoryRepository.countByReceiverIdAndCreatedAtAfter(receiverId, startOfDay);
        if (todayReceivedCount >= DAILY_GIFT_LIMIT) {
            return "The receiver has reached their daily limit for receiving gifts!";
        }

        // 8. Kiểm tra tài nguyên (36,000 Coin + 36 Diamond)
        if (sender.getCoins() < GIFT_COST_COINS) {
            return "Not enough Coins! Requires " + String.format("%,d", GIFT_COST_COINS) + " Coins.";
        }
        if (sender.getDiamonds() < GIFT_COST_DIAMONDS) {
            return "Not enough Diamonds! Requires " + GIFT_COST_DIAMONDS + " Diamonds.";
        }

        // ═══ TẤT CẢ VALIDATION PASS → THỰC HIỆN GIAO DỊCH ═══

        // 9. Trừ phí gửi tặng
        sender.setCoins(sender.getCoins() - GIFT_COST_COINS);
        sender.setDiamonds(sender.getDiamonds() - GIFT_COST_DIAMONDS);
        userRepository.save(sender);

        // 10. Chuyển chủ thẻ: card giờ thuộc về receiver
        String collectionId = card.getCollectionId();
        card.setUser(receiver);
        userCardRepository.save(card);

        // Cập nhật thẻ vào danh sách đã từng sở hữu (Ever Owned) cho người nhận
        receiver.getUnlockedCollections().add(collectionId);
        userRepository.save(receiver);

        // 11. Ghi log lịch sử giao dịch (đóng vai trò inbox cho người nhận luôn)
        GiftHistory history = new GiftHistory(senderId, receiverId, cardId, collectionId);
        giftHistoryRepository.save(history);

        logger.info("Gift sent: sender={}, receiver={}, cardId={}, collectionId={}, cost={}C+{}D",
                senderId, receiverId, cardId, collectionId, GIFT_COST_COINS, GIFT_COST_DIAMONDS);

        return null; // null = thành công (theo convention của FriendService)
    }

    /**
     * Kiểm tra thẻ có đang nằm trong Formation hay không.
     * Formation lưu dạng "cardId1,cardId2,...,cardId6" trong User.activeFormation.
     */
    private boolean isCardInFormation(User user, Long cardId) {
        String formation = user.getActiveFormation();
        if (formation == null || formation.isEmpty()) return false;

        // Parse formation string thành danh sách ID
        return Arrays.stream(formation.split(","))
                .filter(s -> !s.equals("null") && !s.trim().isEmpty())
                .anyMatch(s -> {
                    try {
                        return Long.parseLong(s.trim()) == cardId;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                });
    }

    /**
     * Lấy số lượt tặng còn lại trong ngày.
     */
    public int getDailyRemaining(Long userId) {
        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        int todayCount = giftHistoryRepository.countBySenderIdAndCreatedAtAfter(userId, startOfDay);
        return Math.max(0, DAILY_GIFT_LIMIT - todayCount);
    }

    /**
     * Lấy danh sách quà đã nhận (inbox) — cho tab "Nhận" trong GiftActivity.
     */
    public List<GiftHistoryDTO> getReceivedGifts(Long userId) {
        List<GiftHistory> records = giftHistoryRepository.findReceivedGifts(userId);
        return records.stream().map(r -> toDTO(r, userId)).collect(Collectors.toList());
    }

    /**
     * Lấy danh sách quà đã gửi — cho tab "Gửi" trong GiftActivity.
     */
    public List<GiftHistoryDTO> getSentGifts(Long userId) {
        List<GiftHistory> records = giftHistoryRepository.findSentGifts(userId);
        return records.stream().map(r -> toDTO(r, userId)).collect(Collectors.toList());
    }

    /**
     * Đánh dấu tất cả quà nhận là đã đọc — gọi khi user mở tab "Nhận".
     */
    @Transactional
    public void markReceivedAsRead(Long userId) {
        List<GiftHistory> unread = giftHistoryRepository.findReceivedGifts(userId)
                .stream()
                .filter(g -> !g.isReceiverRead())
                .collect(Collectors.toList());
        for (GiftHistory g : unread) {
            g.setReceiverRead(true);
        }
        giftHistoryRepository.saveAll(unread);
    }

    /**
     * Đếm số quà chưa đọc — để hiển thị badge trên nút Quick Tool.
     */
    public int getUnreadCount(Long userId) {
        return giftHistoryRepository.countByReceiverIdAndReceiverReadFalse(userId);
    }

    /**
     * Lấy lịch sử toàn bộ — cho tab "Tất cả".
     */
    public List<GiftHistoryDTO> getHistory(Long userId) {
        List<GiftHistory> records = giftHistoryRepository.findByUserInvolved(userId);
        return records.stream().map(r -> toDTO(r, userId)).collect(Collectors.toList());
    }

    /**
     * Chuyển đổi GiftHistory Entity → DTO có đầy đủ thông tin hiển thị.
     */
    private GiftHistoryDTO toDTO(GiftHistory record, Long currentUserId) {
        GiftHistoryDTO dto = new GiftHistoryDTO();
        dto.setId(record.getId());
        dto.setSenderId(record.getSenderId());
        dto.setReceiverId(record.getReceiverId());
        dto.setCollectionId(record.getCollectionId());
        dto.setCreatedAt(record.getCreatedAt());

        // Lấy tên user để hiển thị
        userRepository.findById(record.getSenderId())
                .ifPresent(u -> dto.setSenderName(
                        u.getIngameName() != null ? u.getIngameName() : u.getUsername()));
        userRepository.findById(record.getReceiverId())
                .ifPresent(u -> dto.setReceiverName(
                        u.getIngameName() != null ? u.getIngameName() : u.getUsername()));

        // Lấy front image từ CardDataService
        var meta = cardDataService.getCardMetadata(record.getCollectionId());
        if (meta != null && meta.has("frontImage")) {
            dto.setCardFrontImage(meta.get("frontImage").asText());
        }

        return dto;
    }
}
