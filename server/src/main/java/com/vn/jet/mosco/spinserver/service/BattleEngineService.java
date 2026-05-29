package com.vn.jet.mosco.spinserver.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vn.jet.mosco.spinserver.dto.BattleRequest;
import com.vn.jet.mosco.spinserver.dto.BattleResponse;
import com.vn.jet.mosco.spinserver.model.UserCard;
import com.vn.jet.mosco.spinserver.repository.UserCardRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;

/**
 * Service xử lý tính toán OVR và Cộng hưởng Đội hình.
 * CHÚ Ý: Tính năng Cộng hưởng (Passive Synergy) này đang TẠM DỪNG PHÁT TRIỂN và tạm thời chưa áp dụng cho gameplay chính.
 */
@Service
public class BattleEngineService {

    private static final Logger logger = LoggerFactory.getLogger(BattleEngineService.class);
    private final UserCardRepository userCardRepository;
    private final CardDataService cardDataService;

    private JsonNode synergyConfig;

    public BattleEngineService(UserCardRepository userCardRepository, CardDataService cardDataService) {
        this.userCardRepository = userCardRepository;
        this.cardDataService = cardDataService;
    }

    @PostConstruct
    public void init() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            synergyConfig = mapper.readTree(new ClassPathResource("synergy_config.json").getInputStream());
            logger.info("BattleEngineService: Loaded configuration.");
        } catch (Exception e) {
            logger.error("BattleEngineService: Failed to load config files", e);
        }
    }

    public BattleResponse calculateFormationOvr(BattleRequest request) {
        List<BattleRequest.FormationSlot> slots = request.getFormation();
        if (slots == null || slots.isEmpty()) {
            return new BattleResponse();
        }

        Map<String, Integer> tagCounts = new HashMap<>();
        Map<String, Integer> classCounts = new HashMap<>();
        Map<String, Integer> tierCounts = new HashMap<>();
        
        List<String> activeSynergies = new ArrayList<>();
        Map<String, String> buffSummary = new HashMap<>();
        Map<Long, Integer> cardOvrMap = new HashMap<>();

        // 1. Quét thông tin (Counts)
        for (BattleRequest.FormationSlot slot : slots) {
            UserCard card = userCardRepository.findById(slot.getUserCardId()).orElse(null);
            if (card == null) continue;

            String cardClass = cardDataService.getCardClass(card.getCollectionId());
            classCounts.put(cardClass, classCounts.getOrDefault(cardClass, 0) + 1);
            
            String tier = getBadgeTier(card.getUpgradeLevel());
            tierCounts.put(tier, tierCounts.getOrDefault(tier, 0) + 1);

            List<String> tags = cardDataService.getAvailableTags(card.getCollectionId());
            for (String tag : tags) {
                tagCounts.put(tag, tagCounts.getOrDefault(tag, 0) + 1);
            }
            
            String dimension = cardDataService.getDimension(card.getCollectionId());
            if (dimension != null && !dimension.isEmpty()) {
                tagCounts.put(dimension, tagCounts.getOrDefault(dimension, 0) + 1);
            }
        }

        // 2. Tính toán Buff Mềm (% Multiplier) và lưu Mô tả
        double synergyMultiplier = 1.0;
        synergyMultiplier += calculateDimensionBuffs(tagCounts, activeSynergies, buffSummary);
        synergyMultiplier += calculateMajorUnitBuffs(tagCounts, activeSynergies, buffSummary);
        
        // 3. Tính toán Buff Cứng (Map giá trị Flat Bonus mỗi thẻ)
        Map<String, Integer> classBonusMap = calculateClassResonance(classCounts, activeSynergies, buffSummary);
        Map<String, Integer> tierBonusMap = calculateBadgeHarmony(tierCounts, activeSynergies, buffSummary);

        // 4. Quét lại tính tổng OVR và lưu OVR từng thẻ (cardOvrMap)
        double totalBaseOvr = 0;
        int flatBonusTotal = 0;
        
        for (BattleRequest.FormationSlot slot : slots) {
            UserCard card = userCardRepository.findById(slot.getUserCardId()).orElse(null);
            if (card == null) continue;

            int staticOvr = cardDataService.getOvr(card.getCollectionId(), card.getUpgradeLevel());
            totalBaseOvr += staticOvr;
            
            String cardClass = cardDataService.getCardClass(card.getCollectionId());
            String tier = getBadgeTier(card.getUpgradeLevel());
            
            int cB = classBonusMap.getOrDefault(cardClass, 0);
            int tB = tierBonusMap.getOrDefault(tier, 0);
            int individualFlatBonus = cB + tB;
            
            flatBonusTotal += individualFlatBonus;
            
            // Ép vào Map cho thẻ này
            cardOvrMap.put(slot.getUserCardId(), staticOvr + individualFlatBonus);
        }

        // Vòng tính cuối: Hệ số nhân tỉ lệ * Base -> cộng với Buff tĩnh
        int finalOvr = (int) Math.round(totalBaseOvr * synergyMultiplier) + flatBonusTotal;

        BattleResponse response = new BattleResponse();
        response.setTotalOvr(finalOvr);
        response.setActiveSynergies(activeSynergies);
        response.setBuffSummary(buffSummary); 
        response.setCardOvrMap(cardOvrMap);
        
        return response;
    }

    private double calculateDimensionBuffs(Map<String, Integer> tagCounts, List<String> activeSynergies, Map<String, String> buffSummary) {
        double bonus = 0;
        JsonNode dimConfig = synergyConfig.get("synergy_layers").get("dimensions");
        JsonNode counters = synergyConfig.get("grand_gravity").get("counters");
        
        if (counters != null && counters.isObject()) {
            Iterator<String> fieldNames = counters.fieldNames();
            while (fieldNames.hasNext()) {
                String dim = fieldNames.next();
                int count = tagCounts.getOrDefault(dim, 0);
                JsonNode thresholds = dimConfig.get("thresholds");
                
                String key = null;
                double val = 0;
                
                if (count >= 6 && thresholds.has("6")) {
                    val = thresholds.get("6").get("ovr_bonus_pct").asDouble();
                    key = dim + " (6)";
                } else if (count >= 4 && thresholds.has("4")) {
                    val = thresholds.get("4").get("ovr_bonus_pct").asDouble();
                    key = dim + " (4)";
                } else if (count >= 2 && thresholds.has("2")) {
                    val = thresholds.get("2").get("ovr_bonus_pct").asDouble();
                    key = dim + " (2)";
                }
                
                if (key != null) {
                    bonus += val;
                    activeSynergies.add(key);
                    int pct = (int) Math.round(val * 100);
                    buffSummary.put(key, "+" + pct + "% OVR Cùng hệ");
                }
            }
        }
        return bonus;
    }

    private double calculateMajorUnitBuffs(Map<String, Integer> tagCounts, List<String> activeSynergies, Map<String, String> buffSummary) {
        double bonus = 0;
        JsonNode majorConfig = synergyConfig.get("synergy_layers").get("major_units");
        
        Iterator<Map.Entry<String, JsonNode>> fields = majorConfig.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String unitName = field.getKey();
            int count = tagCounts.getOrDefault(unitName, 0);
            
            JsonNode thresholds = field.getValue().get("thresholds");
            int bestThreshold = 0;
            double thresholdBonus = 0;
            String attrType = "";
            
            Iterator<Map.Entry<String, JsonNode>> tFields = thresholds.fields();
            while (tFields.hasNext()) {
                Map.Entry<String, JsonNode> tField = tFields.next();
                int tCount = Integer.parseInt(tField.getKey());
                if (count >= tCount && tCount > bestThreshold) {
                    bestThreshold = tCount;
                    // Xử lý đọc đúng khóa Config Buff %
                    if (tField.getValue().has("team_ovr_bonus_pct")) {
                        thresholdBonus = tField.getValue().get("team_ovr_bonus_pct").asDouble();
                        attrType = "OVR Team";
                    } else if (tField.getValue().has("enemy_ovr_debuff_pct")) {
                        thresholdBonus = -tField.getValue().get("enemy_ovr_debuff_pct").asDouble();
                        attrType = "OVR Kẻ địch";
                    } else if (tField.getValue().has("team_visual_bonus_pct")) {
                        thresholdBonus = tField.getValue().get("team_visual_bonus_pct").asDouble();
                        attrType = "Visual Team";
                    } else if (tField.getValue().has("vocal_charm_bonus_pct")) {
                        thresholdBonus = tField.getValue().get("vocal_charm_bonus_pct").asDouble();
                        attrType = "Vocal Charm";
                    } else if (tField.getValue().has("all_enemy_ovr_debuff_pct")) {
                        thresholdBonus = -tField.getValue().get("all_enemy_ovr_debuff_pct").asDouble();
                        attrType = "OVR Mọi Kẻ Địch";
                    }
                }
            }
            
            if (bestThreshold > 0) {
                if (attrType.contains("Team") || attrType.contains("OVR")) {
                    bonus += thresholdBonus; // Chú ý: trong thực tế, chỉ số buff lên team tính qua final multiplier. Debuff có thể xử lý ở module Battle.
                }
                String key = unitName + " (" + bestThreshold + ")";
                activeSynergies.add(key);
                
                int pct = (int) Math.round(thresholdBonus * 100);
                String prefix = pct > 0 ? "+" : ""; // Debuff pct is already negative from above
                buffSummary.put(key, prefix + pct + "% " + attrType);
            }
        }
        return bonus;
    }

    private String getBadgeTier(int level) {
        JsonNode tiers = synergyConfig.get("flat_synergies").get("badge_harmony").get("tiers");
        Iterator<Map.Entry<String, JsonNode>> it = tiers.fields();
        String firstTier = "BRONZE";
        boolean isFirst = true;
        
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            if (isFirst) {
                firstTier = entry.getKey();
                isFirst = false;
            }
            int min = entry.getValue().get("min_level").asInt();
            int max = entry.getValue().get("max_level").asInt();
            if (level >= min && level <= max) return entry.getKey();
        }
        return firstTier;
    }

    private Map<String, Integer> calculateClassResonance(Map<String, Integer> classCounts, List<String> activeSynergies, Map<String, String> buffSummary) {
        Map<String, Integer> bonusMap = new HashMap<>();
        JsonNode rules = synergyConfig.get("flat_synergies").get("class_resonance").get("rules");
        
        for (Map.Entry<String, Integer> entry : classCounts.entrySet()) {
            String className = entry.getKey();
            int count = entry.getValue();
            
            int bonusPerCard = 0;
            if (count >= 6) bonusPerCard = rules.get("6").get("bonus_ovr").asInt();
            else if (count >= 3) bonusPerCard = rules.get("3").get("bonus_ovr").asInt();
            else if (count >= 2) bonusPerCard = rules.get("2").get("bonus_ovr").asInt();
            
            if (bonusPerCard > 0) {
                bonusMap.put(className, bonusPerCard);
                String key = className + " Resonance (" + count + ")";
                activeSynergies.add(key);
                buffSummary.put(key, "+" + bonusPerCard + " OVR Cơ bản / thẻ");
            }
        }
        return bonusMap;
    }

    private Map<String, Integer> calculateBadgeHarmony(Map<String, Integer> tierCounts, List<String> activeSynergies, Map<String, String> buffSummary) {
        Map<String, Integer> bonusMap = new HashMap<>();
        JsonNode rules = synergyConfig.get("flat_synergies").get("badge_harmony").get("rules");
        
        for (Map.Entry<String, Integer> entry : tierCounts.entrySet()) {
            String tierName = entry.getKey();
            int count = entry.getValue();
            
            int bonusPerCard = 0;
            if (count >= 6) {
                bonusPerCard = rules.get("6").get(tierName).get("bonus_ovr").asInt();
            } else if (count >= 3) {
                bonusPerCard = rules.get("3").get(tierName).get("bonus_ovr").asInt();
            }
            
            if (bonusPerCard > 0) {
                bonusMap.put(tierName, bonusPerCard);
                String key = tierName + " Harmony (" + count + ")";
                activeSynergies.add(key);
                buffSummary.put(key, "+" + bonusPerCard + " OVR theo hạng");
            }
        }
        return bonusMap;
    }
}
