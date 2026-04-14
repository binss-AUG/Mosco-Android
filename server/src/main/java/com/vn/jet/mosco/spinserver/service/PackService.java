package com.vn.jet.mosco.spinserver.service;

import com.google.gson.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

@Service
public class PackService {

    private static final Logger logger = LoggerFactory.getLogger(PackService.class);
    private static final Gson gson = new Gson();

    private final UserRepository userRepository;
    private final UserItemRepository userItemRepository;
    private final UserCardRepository userCardRepository;

    private JsonObject gameConfig;
    private List<JsonObject> allCards;

    public PackService(UserRepository userRepository, UserItemRepository userItemRepository, UserCardRepository userCardRepository) {
        this.userRepository = userRepository;
        this.userItemRepository = userItemRepository;
        this.userCardRepository = userCardRepository;
        loadData();
    }

    /**
     * Load game_config.json and database.json from resources.
     */
    public void loadData() {
        try {
            logger.info("Loading game configuration and card database...");
            ClassPathResource configResource = new ClassPathResource("game_config.json");
            gameConfig = JsonParser.parseReader(new InputStreamReader(configResource.getInputStream(), StandardCharsets.UTF_8)).getAsJsonObject();
            logger.debug("Successfully loaded game_config.json");
            
            // Log some artists to verify loading
            if (gameConfig.has("artists")) {
                JsonArray artists = gameConfig.getAsJsonArray("artists");
                if (artists.size() > 0) {
                    logger.info("Sample artist loaded: {}", artists.get(0).toString());
                }
            }

            ClassPathResource dbResource = new ClassPathResource("database.json");
            JsonObject dbJson = JsonParser.parseReader(new InputStreamReader(dbResource.getInputStream(), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray collections = dbJson.getAsJsonArray("collections");
            
            allCards = new ArrayList<>();
            for (JsonElement element : collections) {
                allCards.add(element.getAsJsonObject());
            }
            logger.info("Successfully loaded {} cards from database.json", allCards.size());
        } catch (Exception e) {
            logger.error("Failed to load game data (game_config.json or database.json): {}", e.getMessage(), e);
            // Optionally throw a runtime exception if this is critical
            // throw new RuntimeException("Fatal error: Could not load game configuration.", e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> openPack(Long userId, String packCode) {
        logger.info("User {} is attempting to open pack: {}", userId, packCode);
        
        if (gameConfig == null || allCards == null || allCards.isEmpty()) {
            logger.error("System configuration or card pool is missing. Attempting emergency reload.");
            loadData();
            if (gameConfig == null || allCards == null || allCards.isEmpty()) {
                throw new RuntimeException("Server is not properly configured. Game data is missing.");
            }
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
        
        // 1. Check if user has the pack
        UserItem packItem = userItemRepository.findByUserIdAndItemCode(userId, packCode)
                .orElseThrow(() -> {
                    logger.warn("User {} tried to open pack {}, but they don't own it.", userId, packCode);
                    return new RuntimeException("You don't have this pack: " + packCode);
                });

        if (packItem.getQuantity() <= 0) {
            logger.warn("User {} tried to open pack {}, but quantity is zero.", userId, packCode);
            throw new RuntimeException("Pack quantity is zero for: " + packCode);
        }

        // 2. Determine pack type and rates
        String packType = determinePackType(packCode);
        logger.debug("Determined pack type for {}: {}", packCode, packType);

        JsonObject packRatesObj = gameConfig.getAsJsonObject("pack_rates");
        if (packRatesObj == null || !packRatesObj.has(packType)) {
            logger.error("Missing pack rates configuration for type: {}", packType);
            throw new RuntimeException("Invalid pack type configuration: " + packType);
        }

        JsonArray ratesArray = packRatesObj.getAsJsonArray(packType);
        double[] rates = new double[ratesArray.size()];
        for (int i = 0; i < ratesArray.size(); i++) {
            rates[i] = ratesArray.get(i).getAsDouble();
        }

        // 3. Roll for Class
        String selectedClass = rollClass(rates);
        logger.debug("Rolled class: {} for user {} with pack {}", selectedClass, userId, packCode);
        
        // 4. Filter cards based on pack code
        List<JsonObject> pool = filterPool(packCode, selectedClass);
        if (pool == null || pool.isEmpty()) {
            logger.error("No cards available in pool for pack {} and class {}", packCode, selectedClass);
            throw new RuntimeException("No cards found for this pack and class: " + selectedClass);
        }

        // 5. Select random card
        JsonObject selectedCard = pool.get(new Random().nextInt(pool.size()));
        String cardId = selectedCard.has("id") ? selectedCard.get("id").getAsString() : "unknown";

        // 6. Update database
        // Important: Decrease quantity first. If this fails, the whole transaction rolls back.
        packItem.setQuantity(packItem.getQuantity() - 1);
        if (packItem.getQuantity() == 0) {
            userItemRepository.delete(packItem);
            logger.debug("Removed last pack {} from user {}", packCode, userId);
        } else {
            userItemRepository.save(packItem);
            logger.debug("Decremented quantity for pack {} for user {}. Remaining: {}", packCode, userId, packItem.getQuantity());
        }

        // Then add the card. Default: Level 1, Exp 0, Upgrade +1
        UserCard newUserCard = new UserCard(user, cardId, 1, 0, 1);
        userCardRepository.save(newUserCard);
        
        // Cập nhật thẻ vào danh sách đã từng sở hữu (Ever Owned)
        user.getUnlockedCollections().add(cardId);
        userRepository.save(user);

        logger.info("User {} successfully opened pack {} and received card: {}", userId, packCode, cardId);

        // 7. Return result (Convert Gson JsonObject to Jackson-friendly Map)
        Map<String, Object> result = new HashMap<>();
        result.put("cardId", cardId);
        
        // Crucial: Convert JsonObject to Map to avoid Jackson serialization errors
        Type type = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> cardDataMap = gson.fromJson(selectedCard, type);
        result.put("cardData", cardDataMap);
        
        return result;
    }

    private String determinePackType(String packCode) {
        String code = packCode.toUpperCase();
        if (code.contains("METAL")) return "Metal";
        if (code.contains("COPPER")) return "Copper";
        if (code.contains("SILVER")) return "Silver";
        if (code.contains("GOLD")) return "Gold";
        if (code.contains("DIAMOND")) return "Diamond";
        if (code.contains("EX")) return "EX";
        
        // Check dynamic keys from game_config
        if (gameConfig != null && gameConfig.has("pack_rates")) {
            JsonObject rates = gameConfig.getAsJsonObject("pack_rates");
            for (String key : rates.keySet()) {
                if (code.contains(key.toUpperCase())) return key;
            }
        }
        
        return "Metal"; // Default
    }

    private String rollClass(double[] rates) {
        double r = new Random().nextDouble();
        double cumulative = 0;
        
        List<Map.Entry<String, JsonElement>> classEntries = new ArrayList<>(gameConfig.getAsJsonObject("classes").entrySet());
        classEntries.sort(Comparator.comparingInt(e -> e.getValue().getAsJsonObject().get("rank").getAsInt()));
        String[] classes = classEntries.stream().map(Map.Entry::getKey).toArray(String[]::new);
        
        for (int i = 0; i < rates.length; i++) {
            cumulative += rates[i];
            if (r <= cumulative) {
                return classes[Math.min(i, classes.length - 1)];
            }
        }
        return classes.length > 0 ? classes[0] : "FirstWelcome";
    }

    private List<JsonObject> filterPool(String packCode, String cardClass) {
        if (allCards == null || allCards.isEmpty()) {
            loadData(); // Emergency reload if pool is empty
        }

        String artistName = null;
        String code = packCode.toUpperCase();

        // 1. Identify if this is an artist-specific pack (e.g., PACK_S1, PACK_ARTIST_S1)
        if (code.startsWith("PACK_ARTIST_")) {
            String artistId = code.substring("PACK_ARTIST_".length()); 
            artistName = getArtistNameById(artistId);
        } else if (code.matches("PACK_S\\d+")) {
            // Handle S1, S2, etc.
            String artistId = code.substring("PACK_".length());
            artistName = getArtistNameById(artistId);
        }

        List<JsonObject> pool;
        if (artistName != null && !artistName.equals("Unknown")) {
            final String finalArtistName = artistName.trim();
            logger.info("Filtering pool for artist: {} (Original: {}) and class: {}", finalArtistName, artistName, cardClass);
            
            pool = allCards.stream()
                    .filter(c -> c.has("member") && c.get("member").getAsString().trim().equalsIgnoreCase(finalArtistName))
                    .filter(c -> c.has("class") && isClassMatch(c.get("class").getAsString(), cardClass))
                    .collect(Collectors.toList());

            // Fallback: If no cards for specific class, return all cards for that artist
            if (pool.isEmpty()) {
                logger.warn("No cards found for artist {} and class {}. Using all cards for artist.", finalArtistName, cardClass);
                pool = allCards.stream()
                        .filter(c -> c.has("member") && c.get("member").getAsString().trim().equalsIgnoreCase(finalArtistName))
                        .collect(Collectors.toList());
            }
            
            if (pool.isEmpty()) {
                logger.error("STILL NO CARDS FOUND FOR ARTIST: {}. Artist name may not match any card in database.json.", finalArtistName);
            }
        } else {
            // Regular packs filter by class
            logger.info("Filtering pool for class: {}", cardClass);
            pool = allCards.stream()
                    .filter(c -> c.has("class") && isClassMatch(c.get("class").getAsString(), cardClass))
                    .collect(Collectors.toList());
        }
        
        // Final fallback: If still empty, return full pool
        if (pool.isEmpty()) {
            if (artistName != null && !artistName.equals("Unknown")) {
                final String finalArtistName = artistName.trim();
                logger.warn("No cards found for artist {} even in artist fallback. This artist might not exist in database.json.", finalArtistName);
                // Return all cards for this artist if possible, otherwise return full pool
                List<JsonObject> artistPool = allCards.stream()
                        .filter(c -> c.has("member") && c.get("member").getAsString().trim().equalsIgnoreCase(finalArtistName))
                        .collect(Collectors.toList());
                if (!artistPool.isEmpty()) {
                    return artistPool;
                }
            }
            logger.error("CRITICAL: Final fallback triggered. Returning FULL pool of {} cards.", allCards.size());
            return allCards;
        }
        return pool;
    }

    private boolean isClassMatch(String dbClass, String targetClass) {
        if (dbClass == null || targetClass == null) return false;
        String db = dbClass.trim().toLowerCase();
        String target = targetClass.trim().toLowerCase();
        
        JsonObject classesObj = gameConfig.getAsJsonObject("classes");
        if (classesObj != null && classesObj.has(targetClass)) {
            JsonArray aliases = classesObj.getAsJsonObject(targetClass).getAsJsonArray("aliases");
            if (aliases != null) {
                for (JsonElement aliasObj : aliases) {
                    if (db.contains(aliasObj.getAsString().toLowerCase())) {
                        return true;
                    }
                }
            }
        }
        
        // Default check (exact match or contains)
        return db.equals(target) || db.contains(target) || target.contains(db);
    }

    @Transactional
    public void givePack(Long userId, String packCode, int quantity) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        Optional<UserItem> existing = userItemRepository.findByUserIdAndItemCode(userId, packCode);
        if (existing.isPresent()) {
            UserItem ui = existing.get();
            ui.setQuantity(ui.getQuantity() + quantity);
            userItemRepository.save(ui);
        } else {
            userItemRepository.save(new UserItem(user, packCode, quantity));
        }
    }

    private String getArtistNameById(String id) {
        JsonArray artists = gameConfig.getAsJsonArray("artists");
        for (JsonElement e : artists) {
            JsonObject a = e.getAsJsonObject();
            if (a.get("id").getAsString().equalsIgnoreCase(id)) {
                return a.get("name").getAsString();
            }
        }
        return "Unknown";
    }
}
