package com.vn.jet.mosco.spinserver.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.vn.jet.mosco.spinserver.dto.StartStageRequest;
import com.vn.jet.mosco.spinserver.dto.StageSessionResponse;
import com.vn.jet.mosco.spinserver.dto.StageRewardResponse;
import com.vn.jet.mosco.spinserver.model.*;
import com.vn.jet.mosco.spinserver.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.vn.jet.mosco.spinserver.utils.MessageConstants;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StageService {

    private static final Logger logger = LoggerFactory.getLogger(StageService.class);
    private static final int SPEED_UP_COST_PER_HOUR = 10;

    private final StageSessionRepository sessionRepository;
    private final StageSessionMemberRepository memberRepository;
    private final UserCardRepository cardRepository;
    private final UserRepository userRepository;
    private final CardDataService cardDataService;

    public StageService(StageSessionRepository sessionRepository,
                        StageSessionMemberRepository memberRepository,
                        UserCardRepository cardRepository,
                        UserRepository userRepository,
                        CardDataService cardDataService) {
        this.sessionRepository = sessionRepository;
        this.memberRepository = memberRepository;
        this.cardRepository = cardRepository;
        this.userRepository = userRepository;
        this.cardDataService = cardDataService;
    }

    /**
     * Bắt đầu một phiên AFK Stage.
     * Kiểm tra điều kiện Level, trạng thái thẻ và tính toán tổng Score của đội hình.
     */
    @Transactional
    public StageSessionResponse startStage(Long userId, StartStageRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 1. Kiểm tra điều kiện mở khóa Map theo Level tài khoản
        validateMapUnlock(user.getLevel(), request.getMapId());

        // 2. Kiểm tra danh sách thẻ cử đi (1-6 thẻ)
        if (request.getCardIds() == null || request.getCardIds().isEmpty() || request.getCardIds().size() > 6) {
            throw new RuntimeException(MessageConstants.STAGE_ERR_TEAM_SIZE);
        }

        List<UserCard> cards = cardRepository.findAllById(request.getCardIds());
        if (cards.size() != request.getCardIds().size()) {
            throw new RuntimeException(MessageConstants.STAGE_ERR_CARDS_NOT_EXIST);
        }

        int totalScore = 0;
        for (UserCard card : cards) {
            // Kiểm tra quyền sở hữu và trạng thái thẻ
            if (!card.getUser().getId().equals(userId)) {
                throw new RuntimeException(MessageConstants.STAGE_ERR_CARD_NOT_OWNED);
            }
            String status = card.getStatus();
            if (status != null && !"AVAILABLE".equals(status)) {
                throw new RuntimeException(MessageConstants.STAGE_ERR_CARD_BUSY);
            }

            // Tính điểm Score dựa trên hiếm (Class, Season, Badge) thay vì OVR
            totalScore += calculateCardScore(card);
            
            // Khóa thẻ với Map ID để Client hiển thị vị trí
            card.setStatus("BUSY_AFK_" + request.getMapId());
        }
        cardRepository.saveAll(cards);

        // 3. Khởi tạo phiên AFK (Sử dụng UTC để đồng bộ tuyệt đối)
        java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
        java.time.LocalDateTime endTime = now.plusHours(request.getDurationHours());
        
        String memberIdsStr = request.getCardIds().stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));

        StageSession session = new StageSession(user, request.getMapId(), request.getDurationHours(), now, endTime, totalScore, memberIdsStr);
        session = sessionRepository.save(session);

        // 4. Lưu danh sách thành viên tham gia phiên
        for (UserCard card : cards) {
            memberRepository.save(new StageSessionMember(session, card));
        }

        logger.info("User {} started AFK Stage Map {} for {} hours with Score {}", userId, request.getMapId(), request.getDurationHours(), totalScore);
        return new StageSessionResponse(session);
    }

    /**
     * Nhận phần thưởng sau khi hoàn thành.
     * Áp dụng Lazy Evaluation: Tính toán thưởng dựa trên thời gian thực tế và Score.
     */
    @Transactional
    public StageRewardResponse claimReward(Long userId, Long sessionId) {
        StageSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException(MessageConstants.STAGE_ERR_SESSION_NOT_FOUND));

        if (!session.getUser().getId().equals(userId)) {
            throw new RuntimeException(MessageConstants.STAGE_ERR_SESSION_ACCESS_DENIED);
        }

        // Cho phép nhận thưởng nếu đang RUNNING (và hết thời gian) HOẶC đã COMPLETED (do Speed-up)
        boolean isRunningFinished = "RUNNING".equals(session.getStatus()) && !LocalDateTime.now().isBefore(session.getEndTime());
        boolean isSpeedUpFinished = "COMPLETED".equals(session.getStatus());

        if (!isRunningFinished && !isSpeedUpFinished) {
            throw new RuntimeException(MessageConstants.STAGE_ERR_SESSION_NOT_FINISHED);
        }

        // 1. Tính toán phần thưởng
        Reward reward = calculateReward(session);
        
        // 2. Cộng thưởng cho User
        User user = session.getUser();
        user.setCoins(user.getCoins() + reward.coins);
        user.setDiamonds(user.getDiamonds() + reward.diamonds);
        userRepository.save(user);

        // 3. Giải phóng thẻ
        unlockCards(session);

        // 4. Cập nhật trạng thái phiên thành CLAIMED để tránh nhận 2 lần
        session.setStatus("CLAIMED");
        sessionRepository.save(session);
        
        logger.info("User {} claimed reward from AFK Stage {}: {} coins, {} diamonds", userId, sessionId, reward.coins, reward.diamonds);
        
        return new StageRewardResponse(reward.coins, reward.diamonds, "Rewards collected successfully!");
    }

    /**
     * Hủy phiên AFK giữa chừng. Thẻ được trả về nhưng không có thưởng.
     */
    @Transactional
    public void abortStage(Long userId, Long sessionId) {
        StageSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException(MessageConstants.STAGE_ERR_SESSION_NOT_FOUND));

        if (!session.getUser().getId().equals(userId)) {
            throw new RuntimeException(MessageConstants.STAGE_ERR_SESSION_ACCESS_DENIED);
        }

        if (!"RUNNING".equals(session.getStatus())) {
            throw new RuntimeException(MessageConstants.STAGE_ERR_SESSION_INVALID);
        }

        // Giải phóng thẻ và hủy phiên
        unlockCards(session);
        session.setStatus("CANCELED");
        sessionRepository.save(session);
        
        logger.info("User {} aborted AFK Stage {}", userId, sessionId);
    }

    /**
     * Dùng Kim Cương để hoàn thành ngay lập tức (Speed-up).
     */
    @Transactional
    public void speedUpStage(Long userId, Long sessionId) {
        StageSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException(MessageConstants.STAGE_ERR_SESSION_NOT_FOUND));

        User user = session.getUser();
        if (!user.getId().equals(userId)) {
            throw new RuntimeException(MessageConstants.STAGE_ERR_SESSION_ACCESS_DENIED);
        }

        if (!"RUNNING".equals(session.getStatus())) {
            throw new RuntimeException(MessageConstants.STAGE_ERR_SESSION_ALREADY_FINISHED);
        }

        // Tính phí Speed-up (Sử dụng UTC để tính)
        java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
        long hoursLeft = java.time.Duration.between(now, session.getEndTime()).toHours() + 1;
        long cost = hoursLeft * SPEED_UP_COST_PER_HOUR;

        if (user.getDiamonds() < cost) {
            throw new RuntimeException(MessageConstants.STAGE_ERR_SPEEDUP_NOT_ENOUGH_DIAMONDS);
        }

        user.setDiamonds(user.getDiamonds() - cost);
        userRepository.save(user);

        // Chuyển sang trạng thái COMPLETED để User tự bấm "CLAIM" trên Client
        session.setEndTime(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
        session.setStatus("COMPLETED");
        sessionRepository.save(session);

        logger.info("User {} speeded up AFK Stage session {} for {} diamonds", userId, sessionId, cost);
    }

    public List<StageSessionResponse> getMySessions(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        // Lấy cả phiên đang chạy và phiên đã hoàn thành nhưng chưa Claim
        List<StageSession> sessions = sessionRepository.findByUserAndStatusIn(user, java.util.List.of("RUNNING", "COMPLETED"));
        return sessions.stream().map(StageSessionResponse::new).collect(java.util.stream.Collectors.toList());
    }

    private void validateMapUnlock(int userLevel, int mapId) {
        int requiredLv = switch (mapId) {
            case 1 -> 1;
            case 2 -> 1; // Temporarily 1 for testing
            case 3 -> 1; // Temporarily 1 for testing
            case 4 -> 1; // Temporarily 1 for testing
            default -> throw new RuntimeException(MessageConstants.STAGE_ERR_MAP_NOT_FOUND);
        };
        if (userLevel < requiredLv) {
            throw new RuntimeException(MessageConstants.STAGE_ERR_LEVEL_LOCKED);
        }
    }

    private int calculateCardScore(UserCard card) {
        String collectionId = card.getCollectionId();
        JsonNode meta = cardDataService.getCardMetadata(collectionId);
        if (meta == null) return 1;

        // 1. Class Rank (từ game_config qua CardDataService logic)
        String typeKey = cardDataService.getTypeKey(collectionId);
        int classRank = 1;
        // Giả định game_config có rank trong classes
        // Thẻ Premier có rank 4, Unit 3, Double 2, First 1...
        // Tạm thời hardcode dựa trên game_config.json đã đọc
        classRank = switch(typeKey) {
            case "Premier" -> 4;
            case "Unit", "Special" -> 3;
            case "Double" -> 2;
            default -> 1;
        };

        // 2. Season Bonus
        String seasonId = meta.has("season") ? meta.get("season").asText() : "";
        int seasonBonus = 0;
        // Map season ID to bonus (theo game_config.json)
        if (seasonId.startsWith("Binary02")) seasonBonus = 6;
        else if (seasonId.startsWith("Atom02")) seasonBonus = 5;
        else if (seasonId.startsWith("Ever01")) seasonBonus = 4;
        else if (seasonId.startsWith("Divine01")) seasonBonus = 3;
        else if (seasonId.startsWith("Cream01")) seasonBonus = 2;
        else if (seasonId.startsWith("Binary01")) seasonBonus = 1;

        // 3. Badge Value (từ upgradeLevel)
        // Dựa trên badge_progression: mốc 10 là 25 điểm.
        int badgeValue = switch(card.getUpgradeLevel()) {
            case 10 -> 25;
            case 9 -> 20;
            case 8 -> 16;
            case 7 -> 12;
            case 6 -> 9;
            case 5 -> 7;
            case 4 -> 5;
            case 3 -> 3;
            case 2 -> 2;
            default -> 1;
        };

        return classRank + seasonBonus + badgeValue;
    }

    private Reward calculateReward(StageSession session) {
        // Base rewards per hour per Map
        long baseCoinsPerHour = switch (session.getMapId()) {
            case 1 -> 100;
            case 2 -> 250;
            case 3 -> 600;
            case 4 -> 1500;
            default -> 0;
        };
        long baseDiamondsPerHour = switch (session.getMapId()) {
            case 3 -> 1;
            case 4 -> 5;
            default -> 0;
        };

        // Công thức: Reward = Base * Duration * (1 + Score/200)
        double multiplier = 1.0 + (session.getTeamScore() / 200.0);
        long totalCoins = (long) (baseCoinsPerHour * session.getDurationHours() * multiplier);
        long totalDiamonds = (long) (baseDiamondsPerHour * session.getDurationHours() * multiplier);

        return new Reward(totalCoins, totalDiamonds);
    }

    private void unlockCards(StageSession session) {
        List<StageSessionMember> members = memberRepository.findByStageSession(session);
        List<UserCard> cards = members.stream()
                .map(StageSessionMember::getUserCard)
                .collect(Collectors.toList());
        for (UserCard card : cards) {
            card.setStatus("AVAILABLE");
        }
        cardRepository.saveAll(cards);
    }

    private static class Reward {
        long coins;
        long diamonds;
        Reward(long coins, long diamonds) {
            this.coins = coins;
            this.diamonds = diamonds;
        }
    }
}
