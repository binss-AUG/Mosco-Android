package com.vn.jet.mosco.spinserver.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vn.jet.mosco.spinserver.model.UpgradeRequest;
import com.vn.jet.mosco.spinserver.model.UpgradeResponse;
import com.vn.jet.mosco.spinserver.model.UserCard;
import com.vn.jet.mosco.spinserver.repository.UserCardRepository;
import com.vn.jet.mosco.spinserver.repository.StageSessionMemberRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.vn.jet.mosco.spinserver.utils.ChaosTheoryHelper;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Service xử lý logic nâng cấp thẻ bài (FO4 Style).
 * Đảm bảo tính nhất quán dữ liệu bằng Pessimistic Locking.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UpgradeService {

    private final UserCardRepository userCardRepository;
    private final StageSessionMemberRepository stageSessionMemberRepository;
    private final CardDataService cardDataService;
    private final ObjectMapper objectMapper;

    private Map<Integer, Double> upgradeRates;
    private JsonNode customUpgradeConfig;

    @PostConstruct
    public void init() {
        try {
            // Tải cấu hình hợp nhất rates_config.json
            InputStream isConfig = new ClassPathResource("rates_config.json").getInputStream();
            JsonNode configJson = objectMapper.readTree(isConfig);

            // Load upgrade rates
            JsonNode ratesNode = configJson.get("upgrade_rates");
            upgradeRates = objectMapper.convertValue(ratesNode, new TypeReference<Map<Integer, Double>>() {});

            // Load custom upgrade config (X, M coefficients)
            customUpgradeConfig = configJson.get("custom_upgrade_rates");

            log.info("UpgradeService: Loaded upgrade configurations from rates_config.json.");
        } catch (Exception e) {
            log.error("UpgradeService: Failed to load upgrade configurations", e);
        }
    }

    /**
     * Thực hiện nâng cấp thẻ bài.
     * Sử dụng @Transactional và PESSIMISTIC_WRITE để chống Race Condition.
     */
    @Transactional
    public UpgradeResponse upgrade(UpgradeRequest request) {
        log.info("Starting card upgrade process for user: {}", request.getUserId());

        // 1. Khóa thẻ chính (PESSIMISTIC_WRITE)
        UserCard mainCard = userCardRepository.findWithLockById(request.getBaseCardId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thẻ chính"));

        if (mainCard.getUpgradeLevel() >= 10) {
            throw new RuntimeException("Thẻ đã đạt cấp độ tối đa (+10)");
        }

        // 2. Khóa và kiểm tra danh sách thẻ nguyên liệu
        List<UserCard> materials = request.getMaterialCardIds().stream()
                .map(id -> userCardRepository.findWithLockById(id)
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy thẻ nguyên liệu: " + id)))
                .toList();

        if (materials.isEmpty() || materials.size() > 5) {
            throw new RuntimeException("Số lượng thẻ nguyên liệu không hợp lệ (1-5)");
        }

        // 3. Tính toán tỷ lệ thành công (RNG)
        int nextLevel = mainCard.getUpgradeLevel() + 1;
        double maxRate = upgradeRates.getOrDefault(nextLevel, 0.0);
        String typeKey = cardDataService.getTypeKey(mainCard.getCollectionId());

        JsonNode levelConfig = customUpgradeConfig.get(String.valueOf(nextLevel));
        if (levelConfig == null || !levelConfig.has(typeKey)) {
            throw new RuntimeException("Lỗi cấu hình nâng cấp cho level " + nextLevel);
        }

        double X = levelConfig.get(typeKey).get("X").asDouble();
        double M = levelConfig.get(typeKey).get("M").asDouble();

        int mainOvr = cardDataService.getOvr(mainCard.getCollectionId(), mainCard.getUpgradeLevel());
        double totalFillPercent = 0.0;

        for (UserCard material : materials) {
            int materialOvr = cardDataService.getOvr(material.getCollectionId(), material.getUpgradeLevel());
            int deltaOvr = materialOvr - mainOvr;

            if (deltaOvr >= 0) {
                totalFillPercent += X * Math.pow(M, deltaOvr);
            } else {
                totalFillPercent += X / Math.pow(M, Math.abs(deltaOvr));
            }
        }

        double fillPercent = Math.min(totalFillPercent, 100.0);
        double actualSuccessRate = (fillPercent / 100.0) * maxRate;

        // 4. Quay Gacha (Server Truth) - Sử dụng ChaosTheoryHelper dùng chung để sinh số ngẫu nhiên khí quyển (DRY)
        boolean isSuccess = (ChaosTheoryHelper.nextDouble() * 100.0) <= actualSuccessRate;

        // 5. Cập nhật kết quả
        int oldLevel = mainCard.getUpgradeLevel();
        if (isSuccess) {
            mainCard.setUpgradeLevel(oldLevel + 1);
        } else {
            // Penalty: Rớt 2 cấp, tối thiểu là 1
            mainCard.setUpgradeLevel(Math.max(1, oldLevel - 2));
        }

        // 6. Xóa thẻ nguyên liệu (Consuming)
        // Trước khi xóa thẻ, cần xóa các liên kết Foreign Key trong các table session/lineup
        for (UserCard material : materials) {
            stageSessionMemberRepository.deleteByUserCardId(material.getId());
        }
        userCardRepository.deleteAll(materials);

        // 7. Lưu thẻ chính và trả về kết quả
        userCardRepository.save(mainCard);
        
        int newOvr = cardDataService.getOvr(mainCard.getCollectionId(), mainCard.getUpgradeLevel());
        
        log.info("Upgrade process completed: Success={}, NewLevel={}, NewOVR={}", isSuccess, mainCard.getUpgradeLevel(), newOvr);

        return new UpgradeResponse(
                isSuccess,
                mainCard.getUpgradeLevel(),
                newOvr,
                actualSuccessRate,
                isSuccess ? "Nâng cấp thành công rực rỡ!" : "Rất tiếc, nâng cấp thất bại và thẻ đã bị rớt cấp."
        );
    }
}
