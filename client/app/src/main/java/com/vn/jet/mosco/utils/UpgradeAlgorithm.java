package com.vn.jet.mosco.utils;

import java.util.List;
import java.util.Map;
import java.util.Random;

public class UpgradeAlgorithm {

    // Models đại diện cho dữ liệu từ JSON
    public static class Card {
        public String id;
        public String typeKey; // Phải là: "First", "Welcome", "Double", "SpecialUnit", "Premier"
        public int level; // 1 đến 10
        public int ovr; // Lấy từ cardOvr.json
    }

    public static class UpgradeConfig {
        public double X;
        public double M;
    }

    // Biến giả lập Data lưu trong bộ nhớ (Bạn sẽ load từ JSON bằng Gson/Jackson thực tế)
    private Map<Integer, Double> upgradeRates; 
    private Map<Integer, Map<String, UpgradeConfig>> customUpgrades; 
    private Random random = new Random();

    public UpgradeAlgorithm(Map<Integer, Double> upgradeRates, Map<Integer, Map<String, UpgradeConfig>> customUpgrades) {
        this.upgradeRates = upgradeRates;
        this.customUpgrades = customUpgrades;
    }

    /**
     * Hàm xử lý ép thẻ chính
     * @param target Thẻ mục tiêu cần nâng cấp
     * @param materials Danh sách thẻ phôi (tối đa 5)
     * @return Kết quả thẻ sau khi ép (Lên cấp hoặc bị rớt cấp)
     */
    public UpgradeResult executeUpgrade(Card target, List<Card> materials) {
        if (target.level >= 10) {
            throw new IllegalArgumentException("Thẻ đã đạt cấp độ tối đa (+10)");
        }
        if (materials == null || materials.isEmpty() || materials.size() > 5) {
            throw new IllegalArgumentException("Số lượng phôi không hợp lệ (yêu cầu từ 1 đến 5 phôi)");
        }

        int targetNextLevel = target.level + 1;
        
        // 1. Lấy cấu hình nâng cấp từ DB / JSON
        Double maxSuccessRate = upgradeRates.get(targetNextLevel);
        UpgradeConfig config = null;
        if (customUpgrades.get(targetNextLevel) != null) {
            config = customUpgrades.get(targetNextLevel).get(target.typeKey);
            if (config == null && !customUpgrades.get(targetNextLevel).isEmpty()) {
                config = customUpgrades.get(targetNextLevel).values().iterator().next(); // Fallback
            }
        }

        if (maxSuccessRate == null || config == null) {
            throw new RuntimeException("Lỗi cấu hình dữ liệu thẻ");
        }

        // 2. Tính toán thanh tỷ lệ nạp phôi (%)
        double totalFillPercent = 0.0;
        
        for (Card material : materials) {
            int deltaOvr = material.ovr - target.ovr;
            
            // Công thức nhân/chia hệ số M theo chênh lệch OVR
            if (deltaOvr >= 0) {
                totalFillPercent += config.X * Math.pow(config.M, deltaOvr);
            } else {
                totalFillPercent += config.X / Math.pow(config.M, Math.abs(deltaOvr));
            }
        }

        // Thanh phôi chỉ chứa tối đa 100%
        double displayFill = Math.min(totalFillPercent, 100.0);

        // 3. Tính tỷ lệ thành công thực tế
        double actualSuccessRate = (displayFill / 100.0) * maxSuccessRate;

        // 4. Quay Gacha (RNG)
        double roll = random.nextDouble() * 100.0; // Random từ 0.0 đến 99.99...
        boolean isSuccess = roll <= actualSuccessRate;

        // 5. Cập nhật cấp độ thẻ
        UpgradeResult result = new UpgradeResult();
        result.isSuccess = isSuccess;
        result.actualSuccessRate = actualSuccessRate;
        result.fillPercent = displayFill;

        if (isSuccess) {
            result.newLevel = target.level + 1;
        } else {
            // Hình phạt: Rớt 2 cấp, cấp tối thiểu là 1
            result.newLevel = Math.max(1, target.level - 2); 
        }

        return result;
    }

    /**
     * Tính toán tỷ lệ thanh % (chỉ dùng để hiển thị, không quay gacha)
     */
    public double calculateFillPercent(Card target, List<Card> materials) {
        if (target.level >= 10 || materials == null || materials.isEmpty()) {
            return 0.0;
        }

        int targetNextLevel = target.level + 1;
        Double maxSuccessRate = upgradeRates.get(targetNextLevel);
        UpgradeConfig config = null;
        if (customUpgrades.get(targetNextLevel) != null) {
            config = customUpgrades.get(targetNextLevel).get(target.typeKey);
            if (config == null && !customUpgrades.get(targetNextLevel).isEmpty()) {
                config = customUpgrades.get(targetNextLevel).values().iterator().next(); // Fallback
            }
        }

        if (maxSuccessRate == null || config == null) {
            return 0.0;
        }

        double totalFillPercent = 0.0;
        for (Card material : materials) {
            int deltaOvr = material.ovr - target.ovr;
            if (deltaOvr >= 0) {
                totalFillPercent += config.X * Math.pow(config.M, deltaOvr);
            } else {
                totalFillPercent += config.X / Math.pow(config.M, Math.abs(deltaOvr));
            }
        }

        return Math.min(totalFillPercent, 100.0);
    }

    // DTO trả về kết quả cho Client / Frontend
    public static class UpgradeResult {
        public boolean isSuccess;
        public int newLevel;
        public double fillPercent;
        public double actualSuccessRate;
    }
}
