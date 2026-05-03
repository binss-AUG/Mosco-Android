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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Trung tâm cache dữ liệu thẻ bài — Single Source of Truth cho OVR & Class.
 * Load database.json + cardOvr.json 1 lần duy nhất khi Server khởi động.
 * Mọi Service khác (Upgrade, Gacha, Inventory) đều gọi vào đây.
 */
@Service
public class CardDataService {

    private static final Logger logger = LoggerFactory.getLogger(CardDataService.class);

    private static final int DEFAULT_OVR = 80;
    private static final String DEFAULT_CLASS = "First Welcome";

    private Map<String, JsonNode> cardMetadataCache;
    private JsonNode gameConfig;

    @PostConstruct
    public void init() {
        loadDatabaseJson();
        loadGameConfigJson();
    }

    private void loadGameConfigJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            gameConfig = mapper.readTree(new ClassPathResource("game_config.json").getInputStream());
            logger.info("CardDataService: Loaded game_config.json");
        } catch (Exception e) {
            logger.error("CardDataService: Failed to load game_config.json", e);
        }
    }

    private void loadDatabaseJson() {
        cardMetadataCache = new HashMap<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream is = new ClassPathResource("database.json").getInputStream();
            JsonNode root = mapper.readTree(is);
            JsonNode collections = root.get("collections");

            if (collections != null && collections.isArray()) {
                for (JsonNode card : collections) {
                    String id = card.has("id") ? card.get("id").asText() : null;
                    if (id != null) {
                        cardMetadataCache.put(id, card);
                    }
                }
            }
            logger.info("CardDataService: Loaded {} cards from database.json", cardMetadataCache.size());
        } catch (Exception e) {
            logger.error("CardDataService: Failed to load database.json", e);
        }
    }

    public JsonNode getCardMetadata(String collectionId) {
        return cardMetadataCache.get(collectionId);
    }

    /**
     * Trả về toàn bộ card metadata cache — dùng cho Bộ Sưu Tập (Collection Book).
     */
    public Map<String, JsonNode> getAllCardMetadata() {
        return cardMetadataCache;
    }

    /**
     * Lấy class gốc từ database.json theo collectionId.
     */
    public String getCardClass(String collectionId) {
        JsonNode meta = getCardMetadata(collectionId);
        if (meta == null) return DEFAULT_CLASS;
        return meta.get("class").asText();
    }

    /**
     * Tra cứu OVR dựa trên công thức cân bằng mới:
     * Final_OVR = base_ovr(Class) + bonus(Season) + progression(Badge)
     */
    public int getOvr(String collectionId, int upgradeLevel) {
        JsonNode meta = getCardMetadata(collectionId);
        if (meta == null || gameConfig == null) return DEFAULT_OVR;

        String season = meta.has("season") ? meta.get("season").asText() : "";

        // 1. Lấy Base OVR từ Class
        int base = DEFAULT_OVR;
        String typeKey = getTypeKey(collectionId);
        JsonNode classesInfo = gameConfig.get("classes");
        if (classesInfo != null && classesInfo.has(typeKey)) {
            base = classesInfo.get(typeKey).get("base_ovr").asInt();
        }

        // 2. Lấy Season Bonus
        int seasonBonus = 0;
        JsonNode seasons = gameConfig.get("seasons");
        if (seasons != null && seasons.isArray()) {
            for (JsonNode s : seasons) {
                if (s.get("id").asText().equalsIgnoreCase(season)) {
                    seasonBonus = s.get("bonus").asInt();
                    break;
                }
            }
        }

        // 3. Lấy Badge Progression Bonus
        int badgeBonus = 0;
        JsonNode progression = gameConfig.get("badge_progression");
        if (progression != null && progression.has(String.valueOf(upgradeLevel))) {
            badgeBonus = progression.get(String.valueOf(upgradeLevel)).asInt();
        }

        return base + seasonBonus + badgeBonus;
    }

    public List<String> getAvailableTags(String collectionId) {
        JsonNode meta = getCardMetadata(collectionId);
        if (meta == null || gameConfig == null) return new ArrayList<>();
        String memberName = meta.has("member") ? meta.get("member").asText() : "";
        
        List<String> tags = new ArrayList<>();
        JsonNode artists = gameConfig.get("artists");
        if (artists != null && artists.isArray()) {
            for (JsonNode a : artists) {
                if (a.has("name") && a.get("name").asText().equalsIgnoreCase(memberName)) {
                    JsonNode tagsNode = a.get("tags");
                    if (tagsNode != null && tagsNode.isArray()) {
                        for (JsonNode t : tagsNode) tags.add(t.asText());
                    }
                    break;
                }
            }
        }
        return tags;
    }

    public String getDimension(String collectionId) {
        JsonNode meta = getCardMetadata(collectionId);
        if (meta == null || gameConfig == null) return null;
        String memberName = meta.has("member") ? meta.get("member").asText() : "";
        
        JsonNode artists = gameConfig.get("artists");
        if (artists != null && artists.isArray()) {
            for (JsonNode a : artists) {
                if (a.has("name") && a.get("name").asText().equalsIgnoreCase(memberName)) {
                    return a.has("dimension") ? a.get("dimension").asText() : null;
                }
            }
        }
        return null;
    }

    /**
     * Chuyển đổi UserCard Entity → UserCardDTO có OVR + class + tags + full metadata.
     */
    public UserCardDTO toDTO(UserCard card) {
        String collectionId = card.getCollectionId();
        JsonNode meta = getCardMetadata(collectionId);
        
        String cardClass = getCardClass(collectionId);
        int ovr = getOvr(collectionId, card.getUpgradeLevel());
        List<String> availableTags = getAvailableTags(collectionId);
        String dimension = getDimension(collectionId);

        UserCardDTO dto = new UserCardDTO(
                card.getId(),
                collectionId,
                card.getLevel(),
                card.getExp(),
                card.getUpgradeLevel(),
                ovr,
                cardClass
        );
        
        if (meta != null) {
            dto.setFrontImage(meta.has("frontImage") ? meta.get("frontImage").asText() : "");
            dto.setBackImage(meta.has("backImage") ? meta.get("backImage").asText() : "");
            dto.setMember(meta.has("member") ? meta.get("member").asText() : "");
            dto.setSeason(meta.has("season") ? meta.get("season").asText() : "");
            dto.setCollectionNo(meta.has("collectionNo") ? meta.get("collectionNo").asText() : "");
            dto.setSlug(meta.has("slug") ? meta.get("slug").asText() : "");
            dto.setBackgroundColor(meta.has("backgroundColor") ? meta.get("backgroundColor").asText() : "#FFFFFF");
            dto.setTextColor(meta.has("textColor") ? meta.get("textColor").asText() : "#000000");
        }
        
        dto.setAvailableTags(availableTags);
        dto.setDimension(dimension);
        dto.setStatus(card.getStatus());
        return dto;
    }

    public String getTypeKey(String collectionId) {
        String cardClass = getCardClass(collectionId);
        if (cardClass == null || gameConfig == null) return "FirstWelcome";
        String normalized = cardClass.replaceAll("\\s+", "").toLowerCase();

        JsonNode classes = gameConfig.get("classes");
        if (classes != null && classes.isObject()) {
            java.util.Iterator<Map.Entry<String, JsonNode>> fields = classes.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode aliases = entry.getValue().get("aliases");
                if (aliases != null && aliases.isArray()) {
                    for (JsonNode alias : aliases) {
                        if (normalized.contains(alias.asText().toLowerCase())) {
                            return entry.getKey(); // e.g., "FirstWelcome", "SpecialUnit"
                        }
                    }
                }
            }
        }
        return "FirstWelcome";
    }
}
