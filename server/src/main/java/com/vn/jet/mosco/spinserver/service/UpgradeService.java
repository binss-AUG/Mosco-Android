package com.vn.jet.mosco.spinserver.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vn.jet.mosco.spinserver.model.UpgradeRequest;
import com.vn.jet.mosco.spinserver.model.UpgradeResponse;
import com.vn.jet.mosco.spinserver.model.UserCard;
import com.vn.jet.mosco.spinserver.repository.UserCardRepository;
import com.vn.jet.mosco.spinserver.utils.UpgradeSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UpgradeService {
    private static final Logger logger = LoggerFactory.getLogger(UpgradeService.class);

    private final UserCardRepository userCardRepository;
    private final UpgradeSystem upgradeSystem;
    private Map<String, Map<String, Integer>> cardOvrData;

    public UpgradeService(UserCardRepository userCardRepository, UpgradeSystem upgradeSystem) {
        this.userCardRepository = userCardRepository;
        this.upgradeSystem = upgradeSystem;
    }

    @PostConstruct
    public void init() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream isOvr = new ClassPathResource("cardOvr.json").getInputStream();
            JsonNode ovrJson = mapper.readTree(isOvr);
            cardOvrData = new HashMap<>();
            
            ovrJson.fields().forEachRemaining(typeEntry -> {
                String typeKey = typeEntry.getKey();
                JsonNode levels = typeEntry.getValue();
                Map<String, Integer> levelMap = new HashMap<>();
                levels.fields().forEachRemaining(lvlEntry -> {
                    levelMap.put(lvlEntry.getKey(), lvlEntry.getValue().asInt());
                });
                cardOvrData.put(typeKey, levelMap);
            });
        } catch (Exception e) {
            logger.error("Lỗi nạp cardOvr.json", e);
        }
    }

    private int getOvrFromData(String typeKey, int level) {
        if (cardOvrData != null && cardOvrData.containsKey(typeKey)) {
            Map<String, Integer> levelMap = cardOvrData.get(typeKey);
            Integer ovr = levelMap.get(String.valueOf(level));
            if (ovr != null) return ovr;
        }
        return 80; // fallback
    }

    private String mapClassToTypeKey(String cardClass) {
        if (cardClass == null) return "FirstWelcome";
        switch (cardClass) {
            case "First":
            case "FirstWelcome": return "FirstWelcome";
            case "Double": return "Double";
            case "SpecialUnit":
            case "Special":
            case "Motion": return "SpecialUnit";
            case "Premier": return "Premier";
            default: return "FirstWelcome";
        }
    }
    
    // Tạm thời để query Class. Thực tế cần load database.json để map collectionId ra Class. 
    // Trong GachaService đã load db, ta có thể dùng chung nếu cần. (Để tối ưu, mockup ở đây)
    private String getCardTypeByCollectionId(String collectionId) {
        // TODO: Map từ database.json -> Class thực tế
        return mapClassToTypeKey("FirstWelcome"); // Fallback
    }

    @Transactional(rollbackFor = Exception.class)
    public UpgradeResponse upgradeCard(UpgradeRequest request) {
        Long userId = request.getUserId();
        
        // 1. Kiểm tra thẻ chính
        UserCard mainCard = userCardRepository.findByIdAndUserId(request.getBaseCardId(), userId)
                .orElseThrow(() -> new RuntimeException("Thẻ chính không tồn tại hoặc không thuộc về bạn"));
                
        // 2. Lấy thẻ phôi và xóa khỏi CSDL
        List<UpgradeSystem.CardInfo> materialInfos = new ArrayList<>();
        List<UserCard> materialsToDelete = new ArrayList<>();
        
        for (Long matId : request.getMaterialCardIds()) {
            UserCard matCard = userCardRepository.findByIdAndUserId(matId, userId)
                    .orElseThrow(() -> new RuntimeException("Thẻ phôi không hợp lệ: " + matId));
            
            // Lấy OVR (Tạm mockup TypeKey)
            String typeKey = getCardTypeByCollectionId(matCard.getCollectionId());
            int matOvr = getOvrFromData(typeKey, matCard.getUpgradeLevel());
            
            materialInfos.add(new UpgradeSystem.CardInfo(typeKey, matCard.getUpgradeLevel(), matOvr));
            materialsToDelete.add(matCard);
        }
        
        // Tiêu huỷ nguyên liệu
        userCardRepository.deleteAll(materialsToDelete);
        logger.info("Đã tiêu phôi: " + materialsToDelete.size() + " thẻ của user " + userId);
        
        // 3. Thực thi thuật toán ép thẻ
        String mainTypeKey = getCardTypeByCollectionId(mainCard.getCollectionId());
        int mainOvr = getOvrFromData(mainTypeKey, mainCard.getUpgradeLevel());
        UpgradeSystem.CardInfo mainCardInfo = new UpgradeSystem.CardInfo(mainTypeKey, mainCard.getUpgradeLevel(), mainOvr);
        
        UpgradeSystem.UpgradeResult result = upgradeSystem.executeUpgrade(mainCardInfo, materialInfos);
        
        // 4. Cập nhật thẻ chính
        mainCard.setUpgradeLevel(result.newLevel);
        // Có thể reset kinh nghiệm (nếu muốn) nhưng không nên reset cấp độ chính (level)
        // mainCard.setExp(0); 
        userCardRepository.save(mainCard);
        
        String message = result.isSuccess 
            ? "Nâng cấp thành công lên cấp " + result.newLevel
            : "Nâng cấp thất bại, thẻ bị rơi xuống cấp " + result.newLevel;
            
        logger.info("Upgrade Result - User: {}, Success: {}, New Level: {}", userId, result.isSuccess, result.newLevel);

        return new UpgradeResponse(result.isSuccess, result.newLevel, result.actualSuccessRate, message);
    }
}
