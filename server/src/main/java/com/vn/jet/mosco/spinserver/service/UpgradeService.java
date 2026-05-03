package com.vn.jet.mosco.spinserver.service;

import com.vn.jet.mosco.spinserver.model.UpgradeRequest;
import com.vn.jet.mosco.spinserver.model.UpgradeResponse;
import com.vn.jet.mosco.spinserver.model.UserCard;
import com.vn.jet.mosco.spinserver.repository.UserCardRepository;
import com.vn.jet.mosco.spinserver.utils.UpgradeSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Service xử lý logic ép thẻ (Upgrade).
 * Sử dụng CardDataService làm nguồn sự thật cho OVR + typeKey,
 * thay vì mockup "FirstWelcome" như trước.
 */
@Service
public class UpgradeService {
    private static final Logger logger = LoggerFactory.getLogger(UpgradeService.class);

    private final UserCardRepository userCardRepository;
    private final UpgradeSystem upgradeSystem;
    private final CardDataService cardDataService;

    public UpgradeService(UserCardRepository userCardRepository,
                          UpgradeSystem upgradeSystem,
                          CardDataService cardDataService) {
        this.userCardRepository = userCardRepository;
        this.upgradeSystem = upgradeSystem;
        this.cardDataService = cardDataService;
    }

    @Transactional(rollbackFor = Exception.class)
    public UpgradeResponse upgradeCard(UpgradeRequest request) {
        Long userId = request.getUserId();
        
        // 1. Kiểm tra thẻ chính
        UserCard mainCard = userCardRepository.findByIdAndUserId(request.getBaseCardId(), userId)
                .orElseThrow(() -> new RuntimeException("Thẻ chính không tồn tại hoặc không thuộc về bạn"));

        if (!"AVAILABLE".equals(mainCard.getStatus())) {
            throw new RuntimeException("Thẻ chính đang bận hoạt động khác, không thể nâng cấp");
        }
                
        // 2. Lấy thẻ phôi và xóa khỏi CSDL
        List<UpgradeSystem.CardInfo> materialInfos = new ArrayList<>();
        List<UserCard> materialsToDelete = new ArrayList<>();
        
        for (Long matId : request.getMaterialCardIds()) {
            UserCard matCard = userCardRepository.findByIdAndUserId(matId, userId)
                    .orElseThrow(() -> new RuntimeException("Thẻ phôi không hợp lệ: " + matId));
            
            if (!"AVAILABLE".equals(matCard.getStatus())) {
                throw new RuntimeException("Thẻ phôi " + matId + " đang bận hoạt động khác");
            }

            // Lấy OVR chính xác từ CardDataService (thay vì mockup cũ)
            String typeKey = cardDataService.getTypeKey(matCard.getCollectionId());
            int matOvr = cardDataService.getOvr(matCard.getCollectionId(), matCard.getUpgradeLevel());
            
            materialInfos.add(new UpgradeSystem.CardInfo(typeKey, matCard.getUpgradeLevel(), matOvr));
            materialsToDelete.add(matCard);
        }
        
        // Tiêu huỷ nguyên liệu
        userCardRepository.deleteAll(materialsToDelete);
        logger.info("Đã tiêu phôi: {} thẻ của user {}", materialsToDelete.size(), userId);
        
        // 3. Thực thi thuật toán ép thẻ — OVR giờ đã chính xác theo class thực tế
        String mainTypeKey = cardDataService.getTypeKey(mainCard.getCollectionId());
        int mainOvr = cardDataService.getOvr(mainCard.getCollectionId(), mainCard.getUpgradeLevel());
        UpgradeSystem.CardInfo mainCardInfo = new UpgradeSystem.CardInfo(mainTypeKey, mainCard.getUpgradeLevel(), mainOvr);
        
        UpgradeSystem.UpgradeResult result = upgradeSystem.executeUpgrade(mainCardInfo, materialInfos);
        
        // 4. Cập nhật thẻ chính
        mainCard.setUpgradeLevel(result.newLevel);
        userCardRepository.save(mainCard);
        
        String message = result.isSuccess 
            ? "Nâng cấp thành công lên cấp " + result.newLevel
            : "Nâng cấp thất bại, thẻ bị rơi xuống cấp " + result.newLevel;
            
        logger.info("Upgrade Result - User: {}, Success: {}, New Level: {}", userId, result.isSuccess, result.newLevel);

        return new UpgradeResponse(result.isSuccess, result.newLevel,
                cardDataService.getOvr(mainCard.getCollectionId(), result.newLevel),
                result.actualSuccessRate, message);
    }
}
