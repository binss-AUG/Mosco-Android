package com.vn.jet.mosco.spinserver.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.vn.jet.mosco.spinserver.dto.PackOpenResponse;
import com.vn.jet.mosco.spinserver.model.User;
import com.vn.jet.mosco.spinserver.model.UserCard;
import com.vn.jet.mosco.spinserver.model.UserItem;
import com.vn.jet.mosco.spinserver.repository.UserCardRepository;
import com.vn.jet.mosco.spinserver.repository.UserItemRepository;
import com.vn.jet.mosco.spinserver.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dịch vụ xử lý logic mở Pack.
 * Tuân thủ quy tắc: Java only, Comment tiếng Việt, Luồng logic Controller -> Service -> Repository.
 */
@Service
public class PackService {

    private static final Logger logger = LoggerFactory.getLogger(PackService.class);
    private static final Gson gson = new Gson();

    private final UserRepository userRepository;
    private final UserItemRepository userItemRepository;
    private final UserCardRepository userCardRepository;

    private JsonObject gameConfig;
    private JsonObject ratesConfig;
    private List<JsonObject> allCards;

    public PackService(UserRepository userRepository, UserItemRepository userItemRepository, UserCardRepository userCardRepository) {
        this.userRepository = userRepository;
        this.userItemRepository = userItemRepository;
        this.userCardRepository = userCardRepository;
        loadData();
    }

    /**
     * Nạp cấu hình game và cơ sở dữ liệu thẻ bài từ tài nguyên hệ thống.
     */
    public void loadData() {
        try {
            logger.info("Đang nạp cấu hình game và dữ liệu thẻ bài...");
            ClassPathResource configResource = new ClassPathResource("game_config.json");
            gameConfig = JsonParser.parseReader(new InputStreamReader(configResource.getInputStream(), StandardCharsets.UTF_8)).getAsJsonObject();

            ClassPathResource ratesResource = new ClassPathResource("rates_config.json");
            ratesConfig = JsonParser.parseReader(new InputStreamReader(ratesResource.getInputStream(), StandardCharsets.UTF_8)).getAsJsonObject();

            ClassPathResource dbResource = new ClassPathResource("database.json");
            JsonObject dbJson = JsonParser.parseReader(new InputStreamReader(dbResource.getInputStream(), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray collections = dbJson.getAsJsonArray("collections");
            
            allCards = new ArrayList<>();
            for (JsonElement element : collections) {
                allCards.add(element.getAsJsonObject());
            }
            logger.info("Đã nạp thành công {} thẻ bài.", allCards.size());
        } catch (Exception e) {
            logger.error("Lỗi khi nạp dữ liệu game: {}", e.getMessage(), e);
        }
    }

    /**
     * Thực hiện mở Pack và trả về kết quả theo định dạng DTO.
     */
    @Transactional(rollbackFor = Exception.class)
    public PackOpenResponse openPack(Long userId, String packCode, int quantity) {
        logger.info("Người dùng {} đang mở {}x pack: {}", userId, quantity, packCode);
        
        if (gameConfig == null || ratesConfig == null || allCards == null || allCards.isEmpty()) {
            loadData();
            if (gameConfig == null || ratesConfig == null) throw new RuntimeException("Cấu hình hệ thống bị thiếu.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng ID: " + userId));
        
        UserItem packItem = userItemRepository.findByUserIdAndItemCode(userId, packCode)
                .orElseThrow(() -> new RuntimeException("Bạn không sở hữu Pack này: " + packCode));

        if (packItem.getQuantity() < quantity) {
            throw new RuntimeException("Không đủ số lượng Pack để mở.");
        }

        List<PackOpenResponse.CardResult> cardsResults = new ArrayList<>();
        String packType = determinePackType(packCode);
        JsonArray ratesArray = ratesConfig.getAsJsonObject("pack_rates").getAsJsonArray(packType);
        double[] rates = new double[ratesArray.size()];
        for (int i = 0; i < ratesArray.size(); i++) rates[i] = ratesArray.get(i).getAsDouble();

        Random random = new Random();

        for (int q = 0; q < quantity; q++) {
            // 1. Quay Class dựa trên Rank
            String selectedRankClass = rollClassByRank(rates);
            
            // 2. Lọc Pool thẻ bài
            List<JsonObject> pool = filterPool(packCode, selectedRankClass);
            if (pool.isEmpty()) {
                logger.warn("Pool thẻ trống cho Class {}. Dùng fallback toàn bộ pool.", selectedRankClass);
                pool = allCards;
            }

            // 3. Chọn thẻ ngẫu nhiên
            JsonObject selectedCard = pool.get(random.nextInt(pool.size()));
            String cardId = selectedCard.has("id") ? selectedCard.get("id").getAsString() : "unknown";
            String actualClass = selectedCard.has("class") ? selectedCard.get("class").getAsString() : selectedRankClass;

            // 4. Lưu vào Database
            UserCard newUserCard = new UserCard(user, cardId, 1, 0, 1);
            userCardRepository.save(newUserCard);
            user.getUnlockedCollections().add(cardId);

            // 5. Chuẩn bị dữ liệu trả về kèm Màu sắc (Rarity Color)
            Object rarityColor = getRarityColorForClass(actualClass);
            Type type = new TypeToken<Map<String, Object>>(){}.getType();
            Map<String, Object> cardDataMap = gson.fromJson(selectedCard, type);

            cardsResults.add(new PackOpenResponse.CardResult(cardId, actualClass, rarityColor, cardDataMap));
        }

        // Cập nhật số lượng Pack
        packItem.setQuantity(packItem.getQuantity() - quantity);
        if (packItem.getQuantity() == 0) userItemRepository.delete(packItem);
        else userItemRepository.save(packItem);
        
        userRepository.save(user);

        return new PackOpenResponse(packCode, cardsResults);
    }

    private String rollClassByRank(double[] rates) {
        double r = new Random().nextDouble();
        double cumulative = 0;
        
        // Tỷ lệ quay theo 4 Rank mặc định trong pack_rates
        for (int i = 0; i < rates.length; i++) {
            cumulative += rates[i];
            if (r <= cumulative) {
                int rank = i + 1;
                return selectSubClassByRank(rank);
            }
        }
        return "First";
    }

    /**
     * Chọn subclass ngẫu nhiên (50/50) nếu rank có nhiều class.
     */
    private String selectSubClassByRank(int rank) {
        Random rnd = new Random();
        switch (rank) {
            case 1: 
                int r = rnd.nextInt(3);
                return r == 0 ? "First" : (r == 1 ? "Welcome" : "Zero");
            case 2: 
                return "Double";
            case 3: 
                return rnd.nextBoolean() ? "Special" : "Motion";
            case 4: 
                return rnd.nextBoolean() ? "Premier" : "Unit"; // Unit được nâng độ hiếm lên bằng Premier
            default: 
                return "First";
        }
    }

    private Object getRarityColorForClass(String cardClass) {
        if (gameConfig == null) return "#FFFFFF";
        JsonObject classes = gameConfig.getAsJsonObject("classes");
        
        String lookupClass = cardClass;
        if (cardClass != null) {
            String normalized = cardClass.replaceAll("\\s+", "").toLowerCase();
            if (normalized.contains("welcome")) lookupClass = "Welcome";
            else if (normalized.contains("zero")) lookupClass = "Zero";
            else if (normalized.contains("first")) lookupClass = "First";
            else if (normalized.contains("double")) lookupClass = "Double";
            else if (normalized.contains("motion")) lookupClass = "Motion";
            else if (normalized.contains("special")) lookupClass = "Special";
            else if (normalized.contains("unit")) lookupClass = "Unit";
            else if (normalized.contains("premier")) lookupClass = "Premier";
        }
        
        if (classes != null && classes.has(lookupClass)) {
            JsonObject classInfo = classes.getAsJsonObject(lookupClass);
            if (classInfo.has("colors")) {
                return gson.fromJson(classInfo.getAsJsonArray("colors"), List.class);
            } else if (classInfo.has("color")) {
                return classInfo.get("color").getAsString();
            }
        }
        return "#FFFFFF";
    }

    private String determinePackType(String packCode) {
        String code = packCode.toUpperCase();
        if (code.contains("DIAMOND")) return "Diamond";
        if (code.contains("GOLD")) return "Gold";
        if (code.contains("SILVER")) return "Silver";
        if (code.contains("EX")) return "EX";
        return "Metal";
    }

    private List<JsonObject> filterPool(String packCode, String targetClass) {
        String artistName = null;
        String code = packCode.toUpperCase();

        if (code.startsWith("PACK_ARTIST_")) {
            artistName = getArtistNameById(code.substring(12));
        }

        final String finalArtist = artistName;
        return allCards.stream()
                .filter(c -> finalArtist == null || (c.has("member") && c.get("member").getAsString().equalsIgnoreCase(finalArtist)))
                .filter(c -> c.has("class") && isClassMatch(c.get("class").getAsString(), targetClass))
                .collect(Collectors.toList());
    }

    private boolean isClassMatch(String dbClass, String targetClass) {
        if (dbClass == null || targetClass == null) return false;
        String db = dbClass.replaceAll("\\s+", "").toLowerCase();
        String target = targetClass.replaceAll("\\s+", "").toLowerCase();
        return db.contains(target) || target.contains(db);
    }

    @Transactional
    public void givePack(Long userId, String packCode, int quantity) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        Optional<UserItem> existing = userItemRepository.findByUserIdAndItemCode(userId, packCode);
        if (existing.isPresent()) {
            existing.get().setQuantity(existing.get().getQuantity() + quantity);
            userItemRepository.save(existing.get());
        } else {
            userItemRepository.save(new UserItem(user, packCode, quantity));
        }
    }

    private String getArtistNameById(String id) {
        JsonArray artists = gameConfig.getAsJsonArray("artists");
        for (JsonElement e : artists) {
            if (e.getAsJsonObject().get("id").getAsString().equalsIgnoreCase(id)) {
                return e.getAsJsonObject().get("name").getAsString();
            }
        }
        return null;
    }
}