package com.vn.jet.mosco.spinserver.utils;

import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedList;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Hệ thống quay số Gacha (RNG).
 * Sử dụng SecureRandom và nạp dữ liệu từ các file cấu hình JSON.
 */
@Component
public class SpinSystem {

    public static final String GROUP_NOTHING = "Nothing";
    public static final String GROUP_FIRST = "First";
    public static final String GROUP_WELCOME = "Welcome";
    public static final String GROUP_ZERO = "Zero";
    public static final String GROUP_DOUBLE = "Double";
    public static final String GROUP_SPECIAL = "Special";
    public static final String GROUP_MOTION = "Motion";
    public static final String GROUP_UNIT = "Unit";
    public static final String GROUP_PREMIER = "Premier";

    private final Map<String, List<JsonObject>> groupedCards = new HashMap<>();
    private final Map<String, Double> baseRates = new HashMap<>();
    
    // History to avoid recent duplicates in results
    // private final LinkedList<JsonObject> recentHistory = new LinkedList<>();
    // private static final int MAX_HISTORY = 40;

    public static class SpinResult {
        public JsonObject result;
        public List<JsonObject> revealGrid;
        public String group;
        public Map<String, Double> finalRates;
        public String message;

        public String toJson() {
            return new Gson().toJson(this);
        }
    }

    /**
     * Constructor mặc định cho Spring Boot.
     */
    public SpinSystem() {
        this(null);
    }

    public SpinSystem(Long seed) {
        initGroups();
    }

    /**
     * Tự động nạp dữ liệu sau khi bean được khởi tạo.
     * Giải thích: Đọc các file config từ thư mục resources.
     */
    @PostConstruct
    public void init() {
        try {
            ClassPathResource dbResource = new ClassPathResource("database.json");
            ClassPathResource rateResource = new ClassPathResource("rates_config.json");
            
            try (Reader dbReader = new InputStreamReader(dbResource.getInputStream(), StandardCharsets.UTF_8);
                 Reader rateReader = new InputStreamReader(rateResource.getInputStream(), StandardCharsets.UTF_8)) {
                loadData(dbReader, rateReader);
            }
        } catch (Exception e) {
            System.err.println("Lỗi nạp dữ liệu Gacha: " + e.getMessage());
        }
    }

    // Lấy số ngẫu nhiên thực sự qua ChaosTheoryHelper bất đồng bộ định kỳ

    private void initGroups() {
        groupedCards.put(GROUP_NOTHING, new ArrayList<>());
        groupedCards.put(GROUP_FIRST, new ArrayList<>());
        groupedCards.put(GROUP_WELCOME, new ArrayList<>());
        groupedCards.put(GROUP_ZERO, new ArrayList<>());
        groupedCards.put(GROUP_DOUBLE, new ArrayList<>());
        groupedCards.put(GROUP_SPECIAL, new ArrayList<>());
        groupedCards.put(GROUP_MOTION, new ArrayList<>());
        groupedCards.put(GROUP_UNIT, new ArrayList<>());
        groupedCards.put(GROUP_PREMIER, new ArrayList<>());
        
        // Add dummy cards for 'Nothing' so grid builder has valid items to draw
        for (int i = 0; i < 50; i++) {
            JsonObject dummy = new JsonObject();
            dummy.addProperty("id", GROUP_NOTHING); // CRITICAL: Cần ID để Client tìm thấy vị trí trong Grid
            dummy.addProperty("class", GROUP_NOTHING);
            dummy.addProperty("frontImage", "dummy://trash_object");
            dummy.addProperty("backImage", "dummy://trash_object");
            dummy.addProperty("collectionId", "Nothing");
            groupedCards.get(GROUP_NOTHING).add(dummy);
        }
    }

    /**
     * Step 1-4: Load and preprocess data
     */
    public void loadData(Reader databaseReader, Reader ratesConfigReader) {
        // Load Spin Rates
        JsonObject ratesJson = new JsonParser().parse(ratesConfigReader).getAsJsonObject().getAsJsonObject("spin_rates");
        for (Map.Entry<String, JsonElement> entry : ratesJson.entrySet()) {
            baseRates.put(entry.getKey(), entry.getValue().getAsDouble());
        }

        // Load Database
        JsonObject dbJson = new JsonParser().parse(databaseReader).getAsJsonObject();
        JsonArray collections = dbJson.getAsJsonArray("collections");

        for (JsonElement element : collections) {
            JsonObject card = element.getAsJsonObject();
            String cardClass = card.has("class") && !card.get("class").isJsonNull() 
                    ? card.get("class").getAsString() 
                    : "";

            String group = determineGroup(cardClass);
            if (!group.equals(GROUP_NOTHING)) {
                groupedCards.get(group).add(card);
            }
        }
    }

    /**
     * Map card class to group.
     */
    private String determineGroup(String cardClass) {
        if (cardClass == null || cardClass.trim().isEmpty()) {
            return GROUP_NOTHING;
        }
        
        String lowerClass = cardClass.toLowerCase();
        if (lowerClass.contains("double")) {
            return GROUP_DOUBLE;
        } else if (lowerClass.contains("welcome")) {
            return GROUP_WELCOME;
        } else if (lowerClass.contains("zero")) {
            return GROUP_ZERO;
        } else if (lowerClass.contains("first")) {
            return GROUP_FIRST;
        } else if (lowerClass.contains("motion")) {
            return GROUP_MOTION;
        } else if (lowerClass.contains("special")) {
            return GROUP_SPECIAL;
        } else if (lowerClass.contains("unit")) {
            return GROUP_UNIT;
        } else if (lowerClass.contains("premier")) {
            return GROUP_PREMIER;
        }
        
        return GROUP_NOTHING;
    }

    /**
     * Step 5-7: Calculate final normalized rates
     */
    private Map<String, Double> calculateFinalRates() {
        Map<String, Double> finalRates = new HashMap<>();
        double totalInitial = 0.0;

        // Apply fluctuation
        for (Map.Entry<String, Double> entry : baseRates.entrySet()) {
            String group = entry.getKey();
            double base = entry.getValue();

            // Skip group if no cards available (except Nothing)
            if (!group.equals(GROUP_NOTHING) && groupedCards.get(group).isEmpty()) {
                finalRates.put(group, 0.0);
                continue;
            }

            double maxFluc = base * 0.10; // 10%
            double fluctuation = (ChaosTheoryHelper.nextDouble() * 2 * maxFluc) - maxFluc; // range: -maxFluc to +maxFluc
            double val = base + fluctuation;

            if (val < 0) val = 0; // Ensure no negative values
            
            finalRates.put(group, val);
            totalInitial += val;
        }

        // Normalize to 100%
        Map<String, Double> normalizedRates = new HashMap<>();
        double checkTotal = 0.0;
        
        for (Map.Entry<String, Double> entry : finalRates.entrySet()) {
            String group = entry.getKey();
            double val = entry.getValue();
            double normalized = 0;
            if (totalInitial > 0) {
                normalized = (val / totalInitial) * 100.0;
            }
            // Round to 4 decimal places
            normalized = roundVal(normalized, 4);
            normalizedRates.put(group, normalized);
            checkTotal += normalized;
        }
        
        // Handle rounding drift by attaching remainder to Nothing
        if (checkTotal != 100.0 && totalInitial > 0) {
            double diff = 100.0 - checkTotal;
            double currentNothing = normalizedRates.getOrDefault(GROUP_NOTHING, 0.0);
            normalizedRates.put(GROUP_NOTHING, roundVal(currentNothing + diff, 4));
        }

        return normalizedRates;
    }

    private double roundVal(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    /**
     * Implement weighted random selection
     */
    private String selectGroupWeighted(Map<String, Double> finalRates) {
        double roll = ChaosTheoryHelper.nextDouble() * 100.0;
        double cumulative = 0.0;

        for (Map.Entry<String, Double> entry : finalRates.entrySet()) {
            cumulative += entry.getValue();
            if (roll <= cumulative) {
                return entry.getKey();
            }
        }
        return GROUP_NOTHING; // Fallback
    }

    /**
     * Spin logic implementation
     */
    public SpinResult spin() {
        SpinResult result = new SpinResult();
        
        // Step 1: Generate final probability table
        Map<String, Double> finalRates = calculateFinalRates();
        result.finalRates = finalRates;

        // Step 2: Randomly select a group
        String selectedGroup = selectGroupWeighted(finalRates);
        result.group = selectedGroup;

        // Step 3: Pick real result from selected group
        List<JsonObject> cardsInGroup = groupedCards.get(selectedGroup);
        if (cardsInGroup == null || cardsInGroup.isEmpty()) {
            result.result = null;
            result.revealGrid = new ArrayList<>();
            result.message = "No reward (Group empty)";
            return result;
        }

        // Bốc ngẫu nhiên 1 thẻ trúng thưởng (Đã xóa logic chặn thẻ trùng lặp)
        int randomIndex = ChaosTheoryHelper.nextInt(cardsInGroup.size());
        JsonObject finalCard = cardsInGroup.get(randomIndex);
        result.result = finalCard;

        // Build Fake Grid Distribution (Xác định số lượng thẻ của từng group cần làm nền)
        Map<String, Integer> dist = buildCaseDistribution(selectedGroup);
        dist.put(selectedGroup, dist.getOrDefault(selectedGroup, 1) - 1);

        // Generate tracking grid (Bốc 15 thẻ fake an toàn tuyệt đối)
        List<JsonObject> revealGrid = new ArrayList<>();
        
        for (Map.Entry<String, Integer> entry : dist.entrySet()) {
            String g = entry.getKey();
            int count = entry.getValue();
            if (count <= 0) continue;
            
            List<JsonObject> pool = groupedCards.get(g);
            if (pool == null || pool.isEmpty()) continue;
            
            // TỐI ƯU: Tạo bản sao của kho thẻ và loại bỏ thẻ trúng thưởng (để tránh lặp mặt sau)
            List<JsonObject> safePool = new ArrayList<>(pool);
            safePool.remove(finalCard);
            
            // Giới hạn số lượng thẻ bốc không vượt quá số thẻ an toàn đang có
            int targetSize = Math.min(count, safePool.size());
            
            // Trộn ngẫu nhiên danh sách bằng chính thuật toán mã hóa của SecureRandom
            ChaosTheoryHelper.shuffle(safePool);
            
            // Cắt đúng số lượng thẻ đưa vào lưới
            for (int i = 0; i < targetSize; i++) {
                revealGrid.add(safePool.get(i));
            }
        }
        
        // Trộn ngẫu nhiên vị trí của 15 thẻ rác trước khi đưa lên UI
        ChaosTheoryHelper.shuffle(revealGrid);
        result.revealGrid = revealGrid;
        
        // Build display message
        String collectionId = (finalCard.has("collectionId") && !finalCard.get("collectionId").isJsonNull())
                ? finalCard.get("collectionId").getAsString()
                : "Unknown";
        result.message = "You received the " + collectionId;

        return result;
    }

    private Map<String, int[]> createBounds(String resultGroup, int pProb, int firstWelcomeMin, int firstWelcomeMax, int doubleMin, int doubleMax, int specialUnitMax, int nothingMin, int nothingMax) {
        Map<String, int[]> bounds = new HashMap<>();
        
        // 1. Phân phối nhóm First, Welcome, Zero (tách từ FirstWelcome cũ)
        int fMin = firstWelcomeMin / 3;
        int fMax = firstWelcomeMax / 3 + 1;
        bounds.put(GROUP_FIRST, new int[]{fMin, fMax});
        bounds.put(GROUP_WELCOME, new int[]{fMin, fMax});
        bounds.put(GROUP_ZERO, new int[]{fMin, fMax});

        // 2. Phân phối nhóm Double
        bounds.put(GROUP_DOUBLE, new int[]{doubleMin, doubleMax});

        // 3. Phân phối nhóm Special, Motion (tách từ SpecialUnit cũ)
        int sMax = specialUnitMax > 0 ? ChaosTheoryHelper.nextInt(specialUnitMax + 1) : 0;
        int sMin = sMax / 2;
        bounds.put(GROUP_SPECIAL, new int[]{sMin, sMax});
        bounds.put(GROUP_MOTION, new int[]{sMin, sMax});

        // 4. Phân phối nhóm Unit, Premier (nhóm Rank 4)
        int premierCount = (resultGroup.equals(GROUP_PREMIER)) ? 1 : (ChaosTheoryHelper.nextInt(100) < pProb ? 1 : 0);
        int unitCount = (resultGroup.equals(GROUP_UNIT)) ? 1 : (ChaosTheoryHelper.nextInt(100) < pProb ? 1 : 0);
        bounds.put(GROUP_PREMIER, new int[]{premierCount, premierCount});
        bounds.put(GROUP_UNIT, new int[]{unitCount, unitCount});

        // 5. Phân phối Nothing
        bounds.put(GROUP_NOTHING, new int[]{nothingMin, nothingMax});

        return bounds;
    }

    private Map<String, Integer> buildCaseDistribution(String resultGroup) {
        Map<String, int[]> bounds;
        int caseRoll = ChaosTheoryHelper.nextInt(100);
        if (caseRoll < 24) {
            bounds = createBounds(resultGroup, 3, 6, 11, 3, 6, 2, 0, 2);
        } else if (caseRoll < 48) {
            bounds = createBounds(resultGroup, 3, 7, 10, 4, 6, 0, 1, 1);
        } else if (caseRoll < 72) {
            bounds = createBounds(resultGroup, 0, 7, 10, 4, 6, 0, 1, 1);
        } else if (caseRoll < 99) {
            bounds = createBounds(resultGroup, 3, 8, 10, 4, 6, 2, 1, 1);
        } else {
            bounds = createBounds(resultGroup, 100, 8, 9, 3, 6, 3, 0, 1);
        }

        // Force resultGroup min/max to at least 1
        if (bounds.containsKey(resultGroup)) {
            int[] b = bounds.get(resultGroup);
            b[0] = Math.max(b[0], 1);
            b[1] = Math.max(b[1], 1);
        }

        // Cap bounds to actual available cards
        for (String g : bounds.keySet()) {
            int available = groupedCards.get(g) != null ? groupedCards.get(g).size() : 0;
            int[] b = bounds.get(g);
            b[1] = Math.min(b[1], available);
            b[0] = Math.min(b[0], b[1]);
        }

        Map<String, Integer> dist = new HashMap<>();
        int currentSum = 0;
        for (String g : bounds.keySet()) {
            dist.put(g, bounds.get(g)[0]);
            currentSum += bounds.get(g)[0];
        }

        int totalAvailableCards = 0;
        for (List<JsonObject> list : groupedCards.values()) totalAvailableCards += list.size();
        int targetSum = Math.min(16, totalAvailableCards);

        // Strip excesses if baseline sum is too high
        while (currentSum > targetSum) {
            boolean reduced = false;
            for (String g : bounds.keySet()) {
                if (dist.get(g) > 0 && !g.equals(resultGroup)) { // preserve resultGroup's guaranteed 1
                    dist.put(g, dist.get(g) - 1);
                    currentSum--;
                    reduced = true;
                    if (currentSum == targetSum) break;
                }
            }
            if (!reduced) break;
        }

        // Add remaining needed cards favoring standard ranges
        while (currentSum < targetSum) {
            boolean added = false;
            List<String> keys = new ArrayList<>(bounds.keySet());
            ChaosTheoryHelper.shuffle(keys);
            for (String g : keys) {
                if (dist.get(g) < bounds.get(g)[1]) {
                    dist.put(g, dist.get(g) + 1);
                    currentSum++;
                    added = true;
                    if (currentSum == targetSum) break;
                }
            }
            if (!added) break;
        }
        
        // If still under, break caps and add anything available
        while (currentSum < targetSum) {
            boolean added = false;
            for (String g : bounds.keySet()) {
                int available = groupedCards.get(g) != null ? groupedCards.get(g).size() : 0;
                if (dist.get(g) < available) {
                    dist.put(g, dist.get(g) + 1);
                    currentSum++;
                    added = true;
                    if (currentSum == targetSum) break;
                }
            }
            if (!added) break;
        }

        return dist;
    }

    /**
     * Simulates spins for testing distribution.
     */
    public Map<String, Integer> simulateSpins(int numSpins) {
        Map<String, Integer> results = new HashMap<>();
        for (int i = 0; i < numSpins; i++) {
            SpinResult res = spin();
            results.put(res.group, results.getOrDefault(res.group, 0) + 1);
        }
        return results;
    }
}
