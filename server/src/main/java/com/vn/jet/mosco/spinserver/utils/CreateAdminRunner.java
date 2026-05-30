package com.vn.jet.mosco.spinserver.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vn.jet.mosco.spinserver.model.GachaHistory;
import com.vn.jet.mosco.spinserver.model.User;
import com.vn.jet.mosco.spinserver.model.UserCard;
import com.vn.jet.mosco.spinserver.model.UserItem;
import com.vn.jet.mosco.spinserver.repository.GachaHistoryRepository;
import com.vn.jet.mosco.spinserver.repository.UserCardRepository;
import com.vn.jet.mosco.spinserver.repository.UserItemRepository;
import com.vn.jet.mosco.spinserver.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.vn.jet.mosco.spinserver.model.ShopItem;
import com.vn.jet.mosco.spinserver.repository.ShopItemRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CreateAdminRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final UserCardRepository userCardRepository;
    private final UserItemRepository userItemRepository;
    private final ShopItemRepository shopItemRepository;
    private final com.vn.jet.mosco.spinserver.repository.UserMailRepository userMailRepository;
    private final GachaHistoryRepository gachaHistoryRepository;
    private final com.vn.jet.mosco.spinserver.repository.StageSessionRepository sessionRepository;
    private final com.vn.jet.mosco.spinserver.repository.StageSessionMemberRepository memberRepository;
    private final com.vn.jet.mosco.spinserver.service.CardDataService cardDataService;

    public CreateAdminRunner(UserRepository userRepository, 
                             UserCardRepository userCardRepository, 
                             UserItemRepository userItemRepository, 
                             ShopItemRepository shopItemRepository, 
                             com.vn.jet.mosco.spinserver.repository.UserMailRepository userMailRepository, 
                             GachaHistoryRepository gachaHistoryRepository, 
                             com.vn.jet.mosco.spinserver.repository.StageSessionRepository sessionRepository, 
                             com.vn.jet.mosco.spinserver.repository.StageSessionMemberRepository memberRepository,
                             com.vn.jet.mosco.spinserver.service.CardDataService cardDataService) {
        this.userRepository = userRepository;
        this.userCardRepository = userCardRepository;
        this.userItemRepository = userItemRepository;
        this.shopItemRepository = shopItemRepository;
        this.userMailRepository = userMailRepository;
        this.gachaHistoryRepository = gachaHistoryRepository;
        this.sessionRepository = sessionRepository;
        this.memberRepository = memberRepository;
        this.cardDataService = cardDataService;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (shopItemRepository.count() == 0) {
            log.info("Initializing Shop master data...");
            List<ShopItem> shopData = new ArrayList<>();
            // OBJETS
            shopData.add(new ShopItem("OBJET_S1_RANDOM", "Seoyeon Random Objet", "Get a random objet from Yoon Seoyeon", "OBJET", 800L, 0L, "url_s1_objet", -1L, "{\"artistId\":\"S1\"}"));
            
            // PACKS
            shopData.add(new ShopItem("PACK_METAL", "Metal Pack", "Rates: 80% FirstWelcome, 15% Double", "PACK", 1000L, 0L, "url_pack_metal", -1L, "{\"packType\":\"Metal\"}"));
            shopData.add(new ShopItem("PACK_COPPER", "Copper Pack", "Rates: 75% FW, 25% Double", "PACK", 2000L, 0L, "url_pack_copper", -1L, "{\"packType\":\"Copper\"}"));
            shopData.add(new ShopItem("PACK_EX", "EX Pack", "Rates: 95% Premier", "PACK", 0L, 500L, "url_pack_ex", -1L, "{\"packType\":\"EX\"}"));
            shopData.add(new ShopItem("PACK_S1_ALL", "Seoyeon All Class Pack", "Guaranteed objet from Seoyeon", "PACK", 5000L, 0L, "url_pack_s1", System.currentTimeMillis() + 86400000L * 7, "{\"artistId\":\"S1\", \"packType\":\"Silver\"}")); // 7 days limited
            
            // BUFFS
            shopData.add(new ShopItem("BUFF_ATK_60", "Attack Potion", "Boosts Attack for 60 mins", "BUFF", 300L, 0L, "url_buff_atk", -1L, "{\"buffType\":\"Attack\", \"durationMinutes\": 60}"));
            shopData.add(new ShopItem("BUFF_DEF_60", "Defense Potion", "Boosts Defense for 60 mins", "BUFF", 300L, 0L, "url_buff_def", -1L, "{\"buffType\":\"Defense\", \"durationMinutes\": 60}"));
            
            // RESOURCES
            shopData.add(new ShopItem("RES_COIN_500", "500 Coins", "Exchange 1 Diamond for 500 Coins", "RESOURCE", 0L, 1L, "url_res_coin", -1L, "{\"coinAmount\": 500}"));
            
            shopItemRepository.saveAll(shopData);
        }

        Optional<User> adminOpt = userRepository.findByUsername("admin");
        User admin;
        if (adminOpt.isPresent()) {
            log.info("Admin account already exists. Refilling resources and items...");
            admin = adminOpt.get();
        } else {
            log.info("Creating admin account with full resources...");
            admin = new User();
            admin.setUsername("admin");
            admin.setEmail("admin@gmail.com");
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            admin.setPasswordHash(encoder.encode("admin123"));
        }

        // 1 tỷ Gold và 1 tỷ Diamond cho tài khoản admin
        admin.setCoins(1_000_000_000L);
        admin.setDiamonds(1_000_000_000L);
        admin.setTotalDiamonds(1_000_000_000L);
        userRepository.save(admin);

        // 🛑 CLEANUP STAGE DATA FOR ADMIN (As requested for re-testing)
        log.info("Cleaning up old Stage data for admin...");
        java.util.List<com.vn.jet.mosco.spinserver.model.StageSession> sessions = sessionRepository.findByUserId(admin.getId());
        for (com.vn.jet.mosco.spinserver.model.StageSession session : sessions) {
            memberRepository.deleteByStageSessionId(session.getId());
        }
        sessionRepository.deleteByUserId(admin.getId());
        
        // Reset Card status và đảm bảo không có thẻ nào upgradeLevel = 0 (+0)
        // App Mosco không tồn tại +0 — mức tối thiểu hợp lệ là +1
        java.util.List<com.vn.jet.mosco.spinserver.model.UserCard> adminCards = userCardRepository.findByUserId(admin.getId());
        for (com.vn.jet.mosco.spinserver.model.UserCard card : adminCards) {
            card.setStatus("AVAILABLE");
            if (card.getUpgradeLevel() < 1) {
                card.setUpgradeLevel(1);
            }
        }
        userCardRepository.saveAll(adminCards);

        // Populate items
        userItemRepository.deleteByUser(admin); // Clear old items
        List<UserItem> items = new ArrayList<>();
        items.add(new UserItem(admin, "PACK_STARTER", 999));
        items.add(new UserItem(admin, "PACK_PREMIUM", 999));
        items.add(new UserItem(admin, "PACK_EX", 999));
        items.add(new UserItem(admin, "BUFF_EXP_01", 999));
        items.add(new UserItem(admin, "BUFF_LUCKY_01", 999));
        userItemRepository.saveAll(items);

        // Populate Mailbox
        userMailRepository.deleteByUser(admin);
        List<com.vn.jet.mosco.spinserver.model.UserMail> mails = new java.util.ArrayList<>();
        mails.add(new com.vn.jet.mosco.spinserver.model.UserMail(admin, "Admin Sync", "Server has been restarted. Your resources and collection have been reset to maximum.", "PACK_PREMIUM", 10));
        userMailRepository.saveAll(mails);

        // 🚀 FULL OBJET: Thêm TOÀN BỘ thẻ trong database.json vào kho admin (Level 10, +10)
        try {
            log.info("Syncing missing cards for admin from database.json...");
            List<UserCard> existingCards = userCardRepository.findByUserId(admin.getId());
            java.util.Set<String> existingIds = new java.util.HashSet<>();
            java.util.List<UserCard> cardsToDelete = new java.util.ArrayList<>();
            
            java.util.Map<String, com.fasterxml.jackson.databind.JsonNode> validMetadata = cardDataService.getAllCardMetadata();
            
            for (UserCard c : existingCards) {
                if (validMetadata == null || !validMetadata.containsKey(c.getCollectionId())) {
                    cardsToDelete.add(c);
                } else {
                    existingIds.add(c.getCollectionId());
                }
            }
            
            if (!cardsToDelete.isEmpty()) {
                userCardRepository.deleteAll(cardsToDelete);
                log.info("Cleaned up {} obsolete junk cards from admin's inventory.", cardsToDelete.size());
            }

            ClassPathResource dbResource = new ClassPathResource("database.json");
            JsonObject dbJson = new JsonParser().parse(new InputStreamReader(dbResource.getInputStream(), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray collections = dbJson.getAsJsonArray("collections");

            List<UserCard> allCards = new ArrayList<>();
            for (JsonElement element : collections) {
                JsonObject cardObj = element.getAsJsonObject();
                if (cardObj.has("id")) {
                    String cardId = cardObj.get("id").getAsString();
                    if (validMetadata != null && validMetadata.containsKey(cardId) && !existingIds.contains(cardId)) {
                        // Mỗi cardId thêm 1 thẻ mới: Level 1, EXP 0, upgradeLevel 1 (+1)
                        // App không có +0 — upgradeLevel tối thiểu luôn là 1
                        allCards.add(new UserCard(admin, cardId, 1, 0, 1));
                    }
                }
            }

            // Chia nhỏ để save nếu collections quá lớn (batching thủ công để tránh quá tải transaction)
            int batchSize = 500;
            for (int i = 0; i < allCards.size(); i += batchSize) {
                List<UserCard> batch = allCards.subList(i, Math.min(i + batchSize, allCards.size()));
                userCardRepository.saveAll(batch);
            }
            
            log.info("Admin account sync success: Added {} new unique cards.", allCards.size());
        } catch (Exception e) {
            log.error("CRITICAL: Failed to seed ALL cards for admin: {}", e.getMessage(), e);
        }

        // Seed sample gacha_history records
        if (gachaHistoryRepository.countByUserId(admin.getId()) == 0) {
            log.info("Seeding sample gacha_history for admin...");
            List<GachaHistory> sampleHistory = new ArrayList<>();
            sampleHistory.add(new GachaHistory(admin, "card_001", "FirstWelcome", 1, "PACK_METAL", "GACHA_ROLL"));
            sampleHistory.add(new GachaHistory(admin, "card_042", "Double", 1, "PACK_COPPER", "GACHA_ROLL"));
            sampleHistory.add(new GachaHistory(admin, "card_128", "SpecialUnit", 1, "PACK_EX", "GACHA_ROLL"));
            sampleHistory.add(new GachaHistory(admin, "card_256", "Premier", 1, "PACK_EX", "GACHA_ROLL"));
            sampleHistory.add(new GachaHistory(admin, "card_007", "FirstWelcome", 1, "PACK_METAL", "PACK_OPEN"));
            gachaHistoryRepository.saveAll(sampleHistory);
            log.info("Seeded {} gacha history records.", sampleHistory.size());
        }
    }
}
