package com.vn.jet.mosco.utils;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
import java.security.SecureRandom;
import java.util.LinkedList;

public class SpinSystem {

    public static final String GROUP_NOTHING = "Nothing";
    public static final String GROUP_DOUBLE = "Double";
    public static final String GROUP_FIRST_WELCOME = "FirstWelcome";
    public static final String GROUP_SPECIAL_UNIT = "SpecialUnit";
    public static final String GROUP_PREMIER = "Premier";

    private final Map<String, List<JsonObject>> groupedCards = new HashMap<>();
    private final Map<String, Double> baseRates = new HashMap<>();
    private final SecureRandom random;
    
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

    public SpinSystem(Long seed) {
        this.random = new SecureRandom();
        if (seed != null) {
            this.random.setSeed(seed);
        }
        initGroups();
    }

    // Lấy số ngẫu nhiên từ tiếng ồn khí quyển (True Random)
    private long fetchTrueRandomSeed() {
        OkHttpClient client = new OkHttpClient();
        String url = "https://www.random.org/integers/?num=1&min=1&max=1000000000&col=1&base=10&format=plain&rnd=new";
        
        Request request = new Request.Builder().url(url).build();
        
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String result = response.body().string().trim();
                return Long.parseLong(result);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Fallback: Nếu rớt mạng hoặc API lỗi, dùng nanoTime của hệ thống để chữa cháy
        return System.nanoTime(); 
    }

    // Bơm sự hỗn loạn vào SecureRandom
    private void injectChaos() {
        long trueRandomSeed = fetchTrueRandomSeed();
        // Trộn số True Random từ API với thời gian thực để tạo ra Hạt giống không thể đoán trước
        this.random.setSeed(trueRandomSeed ^ System.currentTimeMillis());
    }

    private void initGroups() {
        groupedCards.put(GROUP_NOTHING, new ArrayList<>());
        groupedCards.put(GROUP_DOUBLE, new ArrayList<>());
        groupedCards.put(GROUP_FIRST_WELCOME, new ArrayList<>());
        groupedCards.put(GROUP_SPECIAL_UNIT, new ArrayList<>());
        groupedCards.put(GROUP_PREMIER, new ArrayList<>());
        
        // Add dummy cards for 'Nothing' so grid builder has valid items to draw
        for (int i = 0; i < 50; i++) {
            JsonObject dummy = new JsonObject();
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
    public void loadData(Reader databaseReader, Reader spinrateReader) {
        // Load Spin Rates
        JsonObject ratesJson = new JsonParser().parse(spinrateReader).getAsJsonObject();
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
        } else if (lowerClass.contains("first") || lowerClass.contains("welcome")) {
            return GROUP_FIRST_WELCOME;
        } else if (lowerClass.contains("special") || lowerClass.contains("unit")) {
            return GROUP_SPECIAL_UNIT;
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
            double fluctuation = (random.nextDouble() * 2 * maxFluc) - maxFluc; // range: -maxFluc to +maxFluc
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
        double roll = random.nextDouble() * 100.0;
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

        // TÍCH HỢP TRUE RANDOM Ở ĐÂY: Reset lại thuật toán ngẫu nhiên trước khi quay
        injectChaos();

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
        int randomIndex = random.nextInt(cardsInGroup.size());
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
            java.util.Collections.shuffle(safePool, random);
            
            // Cắt đúng số lượng thẻ đưa vào lưới
            for (int i = 0; i < targetSize; i++) {
                revealGrid.add(safePool.get(i));
            }
        }
        
        // Trộn ngẫu nhiên vị trí của 15 thẻ rác trước khi đưa lên UI
        java.util.Collections.shuffle(revealGrid, random);
        result.revealGrid = revealGrid;
        
        // Build display message
        String collectionId = (finalCard.has("collectionId") && !finalCard.get("collectionId").isJsonNull())
                ? finalCard.get("collectionId").getAsString()
                : "Unknown";
        result.message = "You received the " + collectionId;

        return result;
    }

    private Map<String, Integer> buildCaseDistribution(String resultGroup) {
        Map<String, int[]> bounds = new HashMap<>(); // [min, max]
        int pProb = 0;
        int caseRoll = random.nextInt(100);
        if (caseRoll < 24) {
            bounds.put(GROUP_FIRST_WELCOME, new int[]{6, 11});
            bounds.put(GROUP_DOUBLE, new int[]{3, 6});

            int specialCount = random.nextInt(2);
            bounds.put(GROUP_SPECIAL_UNIT, new int[]{specialCount, specialCount});

            pProb = 3;
            bounds.put(GROUP_NOTHING, new int[]{0, 2});
        } else if (caseRoll < 48) {
            bounds.put(GROUP_FIRST_WELCOME, new int[]{7, 10});
            bounds.put(GROUP_DOUBLE, new int[]{4, 6});
            bounds.put(GROUP_SPECIAL_UNIT, new int[]{0, 0});
            pProb = 3;
            bounds.put(GROUP_NOTHING, new int[]{1, 1});
        } else if (caseRoll < 72) {
            bounds.put(GROUP_FIRST_WELCOME, new int[]{7, 10});
            bounds.put(GROUP_DOUBLE, new int[]{4, 6});
            bounds.put(GROUP_SPECIAL_UNIT, new int[]{0, 0});
            pProb = 0;
            bounds.put(GROUP_NOTHING, new int[]{1, 1});
        } else if (caseRoll < 99) {
            bounds.put(GROUP_FIRST_WELCOME, new int[]{8, 10});
            bounds.put(GROUP_DOUBLE, new int[]{4, 6});

            int specialCount = random.nextInt(2);
            bounds.put(GROUP_SPECIAL_UNIT, new int[]{specialCount, specialCount});

            pProb = 3;
            bounds.put(GROUP_NOTHING, new int[]{1, 1});
        } else {
            bounds.put(GROUP_FIRST_WELCOME, new int[]{8, 9});
            bounds.put(GROUP_DOUBLE, new int[]{3, 6});

            int specialCount = random.nextInt(3);
            bounds.put(GROUP_SPECIAL_UNIT, new int[]{specialCount, specialCount});

            pProb = 100;
            bounds.put(GROUP_NOTHING, new int[]{0, 1});
        }

        int premierCount = (resultGroup.equals(GROUP_PREMIER)) ? 1 : (random.nextInt(100) < pProb ? 1 : 0);
        bounds.put(GROUP_PREMIER, new int[]{premierCount, premierCount});
        
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
            java.util.Collections.shuffle(keys, random);
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
