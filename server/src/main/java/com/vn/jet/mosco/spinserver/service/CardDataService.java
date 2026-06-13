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
    private JsonNode cardOvrConfig;

    @org.springframework.beans.factory.annotation.Autowired
    private com.vn.jet.mosco.spinserver.repository.CardRepository cardRepository;

    @PostConstruct
    public void init() {
        loadDatabaseJson();
        loadGameConfigJson();
        loadCardOvrJson();
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

    private void loadCardOvrJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            cardOvrConfig = mapper.readTree(new ClassPathResource("cardOvr.json").getInputStream());
            logger.info("CardDataService: Loaded cardOvr.json");
        } catch (Exception e) {
            logger.error("CardDataService: Failed to load cardOvr.json", e);
        }
    }

    private void loadDatabaseJson() {
        cardMetadataCache = new HashMap<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream is;
            java.io.File externalFile = new java.io.File("data/assets/database.json");
            
            if (!externalFile.exists()) {
                throw new RuntimeException("data/assets/database.json not found!");
            }
            logger.info("CardDataService: Loading filtered database from external storage: {}", externalFile.getAbsolutePath());
            is = new java.io.FileInputStream(externalFile);
            
            JsonNode root = mapper.readTree(is);
            JsonNode collections = root.get("collections");

            int totalInJson = 0;
            if (collections != null && collections.isArray()) {
                totalInJson = collections.size();
                for (JsonNode card : collections) {
                    String id = card.has("id") ? card.get("id").asText() : null;
                    if (id != null) {
                        cardMetadataCache.put(id, card);
                    }
                }
            }
            logger.info("CardDataService: Load complete. JSON entries: {}, Unique Cache: {}", totalInJson, cardMetadataCache.size());
            if (cardMetadataCache.size() != 9660) {
                logger.warn("CardDataService: [ALERT] Card count mismatch! Expected 9660, got {}", cardMetadataCache.size());
            }
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
        if (meta == null || cardOvrConfig == null) return DEFAULT_OVR;

        String season = meta.has("season") ? meta.get("season").asText() : "";
        String typeKey = getTypeKey(collectionId);

        // 1. Lấy Base + Progression OVR từ cardOvr.json
        int baseAndProgression = DEFAULT_OVR;
        if (cardOvrConfig.has(typeKey)) {
            JsonNode levels = cardOvrConfig.get(typeKey);
            String lvlStr = String.valueOf(upgradeLevel);
            if (levels.has(lvlStr)) {
                baseAndProgression = levels.get(lvlStr).asInt();
            }
        }

        // 2. Lấy Season Bonus
        int seasonBonus = 0;
        if (gameConfig != null) {
            JsonNode seasons = gameConfig.get("seasons");
            if (seasons != null && seasons.isArray()) {
                for (JsonNode s : seasons) {
                    if (s.get("id").asText().equalsIgnoreCase(season)) {
                        seasonBonus = s.get("bonus").asInt();
                        break;
                    }
                }
            }
        }

        return baseAndProgression + seasonBonus;
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
        if (card.getCreatedAt() != null) {
            dto.setCreatedAt(card.getCreatedAt().toString());
        }
        String slug = "";
        if (meta != null && meta.has("slug")) {
            slug = meta.get("slug").asText().toLowerCase();
        }
        if (slug.isEmpty() && meta != null) {
            String seasonName = meta.has("season") ? meta.get("season").asText().toLowerCase().replaceAll("\\s+", "") : "";
            String memberName = meta.has("member") ? meta.get("member").asText().toLowerCase().replaceAll("\\s+", "") : "";
            String colNo = meta.has("collectionNo") ? meta.get("collectionNo").asText().toLowerCase() : "";
            slug = seasonName + "-" + memberName + "-" + colNo;
        }
        
        String prefix = "mco";
        if (cardClass != null) {
            switch (cardClass.toLowerCase()) {
                case "double": prefix = "dco"; break;
                case "unit": prefix = "uco"; break;
                case "zero": prefix = "zco"; break;
                case "special": prefix = "sco"; break;
                case "welcome": prefix = "wco"; break;
                case "first": prefix = "fco"; break;
                case "premier": prefix = "pco"; break;
            }
        }
        if (!slug.isEmpty()) {
            dto.setFrontVideoUrl("https://cdn.apollo.cafe/" + prefix + "/triples/" + slug + ".mp4");
        }
        return dto;
    }

    public String getTypeKey(String collectionId) {
        String cardClass = getCardClass(collectionId);
        if (cardClass == null) return "First";
        String normalized = cardClass.replaceAll("\\s+", "").toLowerCase();
        if (normalized.contains("welcome")) return "Welcome";
        if (normalized.contains("zero")) return "Zero";
        if (normalized.contains("first")) return "First";
        if (normalized.contains("double")) return "Double";
        if (normalized.contains("motion")) return "Motion";
        if (normalized.contains("special")) return "Special";
        if (normalized.contains("unit")) return "Unit";
        if (normalized.contains("premier")) return "Premier";
        return "First";
    }

    /**
     * Làm mới cache metadata — gọi sau khi AssetManagementService hoàn tất ETL/Sync.
     */
    public void reload() {
        logger.info("CardDataService: Reloading metadata cache...");
        loadDatabaseJson();
        logger.info("CardDataService: Reload complete. New cache size: {}", cardMetadataCache.size());
    }
}
