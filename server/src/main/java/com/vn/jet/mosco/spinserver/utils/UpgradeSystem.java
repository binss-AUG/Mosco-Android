package com.vn.jet.mosco.spinserver.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Component
public class UpgradeSystem {

    public static class CardInfo {
        public String typeKey;
        public int level;
        public int ovr;

        public CardInfo(String typeKey, int level, int ovr) {
            this.typeKey = typeKey;
            this.level = level;
            this.ovr = ovr;
        }
    }

    public static class UpgradeConfig {
        public double X;
        public double M;
    }

    private Map<Integer, Double> upgradeRates;
    private Map<Integer, Map<String, UpgradeConfig>> customUpgrades;
    private Random random = new Random();

    @PostConstruct
    public void init() {
        // Tải config từ JSON (Tương tự ở Client, nhưng bảo mật hơn trên Server)
        ObjectMapper mapper = new ObjectMapper();
        try {
            // Tải cấu hình hợp nhất rates_config.json
            InputStream isConfig = new ClassPathResource("rates_config.json").getInputStream();
            JsonNode configJson = mapper.readTree(isConfig);

            // 1. Tải upgrade_rates
            JsonNode ratesNode = configJson.get("upgrade_rates");
            Map<String, Double> rawRates = mapper.convertValue(ratesNode, new TypeReference<Map<String, Double>>() {});
            upgradeRates = new HashMap<>();
            for (Map.Entry<String, Double> entry : rawRates.entrySet()) {
                upgradeRates.put(Integer.parseInt(entry.getKey()), entry.getValue());
            }

            // 2. Tải custom_upgrade_rates
            JsonNode customJson = configJson.get("custom_upgrade_rates");
            customUpgrades = new HashMap<>();
            
            customJson.fields().forEachRemaining(levelEntry -> {
                int level = Integer.parseInt(levelEntry.getKey());
                JsonNode typeObj = levelEntry.getValue();
                Map<String, UpgradeConfig> typeMap = new HashMap<>();
                
                typeObj.fields().forEachRemaining(typeEntry -> {
                    UpgradeConfig config = mapper.convertValue(typeEntry.getValue(), UpgradeConfig.class);
                    typeMap.put(typeEntry.getKey(), config);
                });
                
                customUpgrades.put(level, typeMap);
            });
            
            System.out.println(">>> Jet Mien Tay - UpgradeSystem loaded!");
        } catch (Exception e) {
            System.err.println("Lỗi nạp dữ liệu Upgrade: " + e.getMessage());
        }
    }

    /**
     * Logic ép thẻ
     */
    public UpgradeResult executeUpgrade(CardInfo target, List<CardInfo> materials) {
        if (target.level >= 10) {
            throw new IllegalArgumentException("Thẻ đã đạt cấp độ tối đa (+10)");
        }
        if (materials == null || materials.isEmpty() || materials.size() > 5) {
            throw new IllegalArgumentException("Số lượng phôi không hợp lệ (yêu cầu từ 1 đến 5 phôi)");
        }

        int targetNextLevel = target.level + 1;
        Double maxSuccessRate = upgradeRates.get(targetNextLevel);
        UpgradeConfig config = customUpgrades.get(targetNextLevel).get(target.typeKey);

        if (maxSuccessRate == null || config == null) {
            throw new RuntimeException("Lỗi cấu hình dữ liệu thẻ");
        }

        double totalFillPercent = 0.0;
        for (CardInfo material : materials) {
            int deltaOvr = material.ovr - target.ovr;
            if (deltaOvr >= 0) {
                totalFillPercent += config.X * Math.pow(config.M, deltaOvr);
            } else {
                totalFillPercent += config.X / Math.pow(config.M, Math.abs(deltaOvr));
            }
        }

        double displayFill = Math.min(totalFillPercent, 100.0);
        double actualSuccessRate = (displayFill / 100.0) * maxSuccessRate;

        // Tránh bị hack RNG bằng cách tái sử dụng logic ngẫu nhiên tương tự SpinSystem (nâng cao hơn)
        // Nhưng tạm thời Random cơ bản đã được bảo vệ vì chạy ở phía Server, Client k can thiệp được
        double roll = random.nextDouble() * 100.0;
        boolean isSuccess = roll <= actualSuccessRate;

        UpgradeResult result = new UpgradeResult();
        result.isSuccess = isSuccess;
        result.actualSuccessRate = actualSuccessRate;

        if (isSuccess) {
            result.newLevel = target.level + 1;
        } else {
            result.newLevel = Math.max(1, target.level - 2); 
        }

        return result;
    }

    public static class UpgradeResult {
        public boolean isSuccess;
        public int newLevel;
        public double actualSuccessRate;
    }
}
