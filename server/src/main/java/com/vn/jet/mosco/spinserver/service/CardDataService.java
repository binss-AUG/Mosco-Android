package com.vn.jet.mosco.spinserver.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vn.jet.mosco.spinserver.dto.UserCardDTO;
import com.vn.jet.mosco.spinserver.model.UserCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Trung tâm cache dữ liệu thẻ bài — Single Source of Truth cho OVR & Class.
 * Load database.json + cardOvr.json 1 lần duy nhất khi Server khởi động.
 * Mọi Service khác (Upgrade, Gacha, Inventory) đều gọi vào đây.
 */
@Service
public class CardDataService {

    private static final Logger logger = LoggerFactory.getLogger(CardDataService.class);

    // collectionId → class (VD: "Premier", "Double", "Special Unit", "First Welcome")
    private Map<String, String> cardClassMap;

    // typeKey → level → ovr
    private Map<String, Map<String, Integer>> cardOvrData;

    @PostConstruct
    public void init() {
        loadDatabaseJson();
        loadCardOvrJson();
    }

    /**
     * Nạp database.json để xây dựng bảng tra cứu collectionId → class.
     * Tại sao: Server cần biết class của thẻ để tính OVR chính xác,
     * thay vì fallback về "FirstWelcome" như trước.
     */
    private void loadDatabaseJson() {
        cardClassMap = new HashMap<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream is = new ClassPathResource("database.json").getInputStream();
            JsonNode root = mapper.readTree(is);
            JsonNode collections = root.get("collections");

            if (collections != null && collections.isArray()) {
                for (JsonNode card : collections) {
                    String id = card.has("id") ? card.get("id").asText() : null;
                    String cardClass = card.has("class") ? card.get("class").asText() : null;
                    if (id != null && cardClass != null) {
                        cardClassMap.put(id, cardClass);
                    }
                }
            }
            logger.info("CardDataService: Đã nạp {} thẻ từ database.json", cardClassMap.size());
        } catch (Exception e) {
            logger.error("CardDataService: Lỗi nạp database.json", e);
        }
    }

    /**
     * Nạp cardOvr.json để tra cứu OVR theo typeKey + level.
     */
    private void loadCardOvrJson() {
        cardOvrData = new HashMap<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream is = new ClassPathResource("cardOvr.json").getInputStream();
            JsonNode root = mapper.readTree(is);

            root.fields().forEachRemaining(typeEntry -> {
                String typeKey = typeEntry.getKey();
                JsonNode levels = typeEntry.getValue();
                Map<String, Integer> levelMap = new HashMap<>();
                levels.fields().forEachRemaining(lvlEntry ->
                        levelMap.put(lvlEntry.getKey(), lvlEntry.getValue().asInt())
                );
                cardOvrData.put(typeKey, levelMap);
            });
            logger.info("CardDataService: Đã nạp {} typeKey từ cardOvr.json", cardOvrData.size());
        } catch (Exception e) {
            logger.error("CardDataService: Lỗi nạp cardOvr.json", e);
        }
    }

    /**
     * Lấy class gốc từ database.json theo collectionId.
     * VD: "Premier", "Double", "Special Unit", "First Welcome"
     */
    public String getCardClass(String collectionId) {
        if (collectionId == null) return "First Welcome";
        return cardClassMap.getOrDefault(collectionId, "First Welcome");
    }

    /**
     * Ánh xạ class từ database.json sang typeKey chuẩn của cardOvr.json.
     * Tại sao: database.json dùng tên có dấu cách ("First Welcome"),
     * nhưng cardOvr.json dùng key liền ("FirstWelcome").
     */
    public String getTypeKey(String collectionId) {
        String cardClass = getCardClass(collectionId);
        return mapClassToTypeKey(cardClass);
    }

    /**
     * Tra cứu OVR chính xác theo collectionId + upgradeLevel.
     * Đây là nguồn sự thật DUY NHẤT — thay thế hoàn toàn logic Client cũ.
     */
    public int getOvr(String collectionId, int upgradeLevel) {
        String typeKey = getTypeKey(collectionId);
        if (cardOvrData.containsKey(typeKey)) {
            Map<String, Integer> levelMap = cardOvrData.get(typeKey);
            Integer ovr = levelMap.get(String.valueOf(upgradeLevel));
            if (ovr != null) return ovr;
        }
        return 80; // fallback an toàn
    }

    /**
     * Chuyển đổi UserCard Entity → UserCardDTO có OVR + class.
     * Dùng tại InventoryController để trả về Client.
     */
    public UserCardDTO toDTO(UserCard card) {
        String cardClass = getCardClass(card.getCollectionId());
        int ovr = getOvr(card.getCollectionId(), card.getUpgradeLevel());

        return new UserCardDTO(
                card.getId(),
                card.getCollectionId(),
                card.getLevel(),
                card.getExp(),
                card.getUpgradeLevel(),
                ovr,
                cardClass
        );
    }

    /**
     * Ánh xạ nội bộ: class text → typeKey cho cardOvr.json
     */
    private String mapClassToTypeKey(String cardClass) {
        if (cardClass == null) return "FirstWelcome";
        String normalized = cardClass.replaceAll("\\s+", "").toLowerCase();

        if (normalized.contains("first") || normalized.contains("welcome")) return "FirstWelcome";
        if (normalized.equals("double")) return "Double";
        if (normalized.contains("special") || normalized.contains("unit") || normalized.contains("motion")) return "SpecialUnit";
        if (normalized.equals("premier")) return "Premier";

        return "FirstWelcome";
    }
}
