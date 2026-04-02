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

@Component
public class CreateAdminRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final UserCardRepository userCardRepository;
    private final UserItemRepository userItemRepository;
    private final ShopItemRepository shopItemRepository;
    private final com.vn.jet.mosco.spinserver.repository.UserMailRepository userMailRepository;
    private final GachaHistoryRepository gachaHistoryRepository;

    public CreateAdminRunner(UserRepository userRepository, UserCardRepository userCardRepository, UserItemRepository userItemRepository, ShopItemRepository shopItemRepository, com.vn.jet.mosco.spinserver.repository.UserMailRepository userMailRepository, GachaHistoryRepository gachaHistoryRepository) {
        this.userRepository = userRepository;
        this.userCardRepository = userCardRepository;
        this.userItemRepository = userItemRepository;
        this.shopItemRepository = shopItemRepository;
        this.userMailRepository = userMailRepository;
        this.gachaHistoryRepository = gachaHistoryRepository;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (shopItemRepository.count() == 0) {
            System.out.println("Initializing Shop master data...");
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
            System.out.println("Admin account already exists. Refilling resources and items...");
            admin = adminOpt.get();
        } else {
            System.out.println("Creating admin account with full resources...");
            admin = new User();
            admin.setUsername("admin");
            admin.setEmail("admin@gmail.com");
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            admin.setPasswordHash(encoder.encode("admin123"));
        }

        admin.setCoins(999999999L);
        admin.setDiamonds(999999999L);
        userRepository.save(admin);

        // Populate items
        userItemRepository.deleteByUser(admin); // Clear old items
        List<UserItem> items = new ArrayList<>();
        items.add(new UserItem(admin, "PACK_STARTER", 999));
        items.add(new UserItem(admin, "PACK_PREMIUM", 999));
        items.add(new UserItem(admin, "BUFF_EXP_01", 999));
        items.add(new UserItem(admin, "BUFF_LUCKY_01", 999));
        userItemRepository.saveAll(items);

        // Populate Mailbox
        userMailRepository.deleteByUser(admin);
        List<com.vn.jet.mosco.spinserver.model.UserMail> mails = new java.util.ArrayList<>();
        mails.add(new com.vn.jet.mosco.spinserver.model.UserMail(admin, "Welcome Gift", "Welcome to Mosco! Enjoy these items.", "PACK_STARTER", 1));
        mails.add(new com.vn.jet.mosco.spinserver.model.UserMail(admin, "Server Update", "Maintenance completed. Compensation sent.", "RES_COIN_500", 1));
        mails.add(new com.vn.jet.mosco.spinserver.model.UserMail(admin, "Season Rewards", "Top 1% rank achievement.", null, 1));
        userMailRepository.saveAll(mails);

        // 🚀 REFACTOR: Admin chỉ sở hữu DUY NHẤT 1 thẻ Yeonji Premier +10
        try {
            System.out.println("Refactoring admin card collection (Yeonji Premier +10)...");
            userCardRepository.deleteByUser(admin);

            ClassPathResource dbResource = new ClassPathResource("database.json");
            JsonObject dbJson = new JsonParser().parse(new InputStreamReader(dbResource.getInputStream(), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray collections = dbJson.getAsJsonArray("collections");

            String targetId = null;
            for (JsonElement element : collections) {
                JsonObject card = element.getAsJsonObject();
                if (card.has("slug") && card.get("slug").getAsString().equalsIgnoreCase("binary02-yeonji-333a")) {
                    targetId = card.has("id") ? card.get("id").getAsString() : null;
                    break;
                }
            }

            if (targetId != null) {
                // Tạo 10 thẻ Yeonji Premier với upgradeLevel từ 1 đến 10
                List<UserCard> adminTenCards = new ArrayList<>();
                for (int i = 1; i <= 10; i++) {
                    // Constructor 5 tham số: (user, collectionId, level, exp, upgradeLevel)
                    adminTenCards.add(new UserCard(admin, targetId, i, 0, i)); 
                }
                userCardRepository.saveAll(adminTenCards);
                System.out.println("Admin account refactor success: Added 10 Yeonji Premier cards (Upgrade +1 to +10)");
            } else {
                System.err.println("CRITICAL: Could not find Yeonji Premier in database.json!");
            }
        } catch (Exception e) {
            System.err.println("Failed to refactor admin cards: " + e.getMessage());
            e.printStackTrace();
        }

        // Seed sample gacha_history records
        if (gachaHistoryRepository.countByUserId(admin.getId()) == 0) {
            System.out.println("Seeding sample gacha_history for admin...");
            List<GachaHistory> sampleHistory = new ArrayList<>();
            sampleHistory.add(new GachaHistory(admin, "card_001", "FirstWelcome", 1, "PACK_METAL", "GACHA_ROLL"));
            sampleHistory.add(new GachaHistory(admin, "card_042", "Double", 1, "PACK_COPPER", "GACHA_ROLL"));
            sampleHistory.add(new GachaHistory(admin, "card_128", "SpecialUnit", 1, "PACK_EX", "GACHA_ROLL"));
            sampleHistory.add(new GachaHistory(admin, "card_256", "Premier", 1, "PACK_EX", "GACHA_ROLL"));
            sampleHistory.add(new GachaHistory(admin, "card_007", "FirstWelcome", 1, "PACK_METAL", "PACK_OPEN"));
            gachaHistoryRepository.saveAll(sampleHistory);
            System.out.println("Seeded " + sampleHistory.size() + " gacha history records.");
        }
    }
}
