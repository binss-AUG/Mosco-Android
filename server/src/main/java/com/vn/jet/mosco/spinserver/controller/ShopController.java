package com.vn.jet.mosco.spinserver.controller;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vn.jet.mosco.spinserver.model.ShopItem;
import com.vn.jet.mosco.spinserver.model.User;
import com.vn.jet.mosco.spinserver.model.UserItem;
import com.vn.jet.mosco.spinserver.repository.ShopItemRepository;
import com.vn.jet.mosco.spinserver.repository.UserItemRepository;
import com.vn.jet.mosco.spinserver.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/shop")
public class ShopController {

    private final ShopItemRepository shopItemRepository;
    private final UserRepository userRepository;
    private final UserItemRepository userItemRepository;

    public ShopController(ShopItemRepository shopItemRepository, UserRepository userRepository, UserItemRepository userItemRepository) {
        this.shopItemRepository = shopItemRepository;
        this.userRepository = userRepository;
        this.userItemRepository = userItemRepository;
    }

    @GetMapping
    public ResponseEntity<List<ShopItem>> getShopItems() {
        long now = System.currentTimeMillis();
        List<ShopItem> activeItems = shopItemRepository.findAll().stream()
                .filter(item -> item.getEndTime() == -1L || item.getEndTime() > now)
                .collect(Collectors.toList());
        return ResponseEntity.ok(activeItems);
    }

    @PostMapping("/buy")
    public ResponseEntity<?> buyItem(@RequestBody Map<String, Object> requestBody) {
        try {
            Long userId = ((Number) requestBody.get("userId")).longValue();
            String productCode = (String) requestBody.get("productCode");
            int quantity = requestBody.containsKey("quantity") ? ((Number) requestBody.get("quantity")).intValue() : 1;

            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) return ResponseEntity.badRequest().body("User not found");
            User user = userOpt.get();

            Optional<ShopItem> shopItemOpt = shopItemRepository.findByProductCode(productCode);
            if (shopItemOpt.isEmpty()) return ResponseEntity.badRequest().body("Item not found");
            ShopItem shopItem = shopItemOpt.get();

            // Validate limited-time items
            if (shopItem.getEndTime() != -1L && shopItem.getEndTime() < System.currentTimeMillis()) {
                return ResponseEntity.badRequest().body("This item is no longer available.");
            }

            long totalC = shopItem.getPriceCoins() * quantity;
            long totalD = shopItem.getPriceDiamonds() * quantity;

            if (user.getCoins() < totalC || user.getDiamonds() < totalD) {
                return ResponseEntity.badRequest().body("Not enough resources");
            }

            // Deduct cost
            user.setCoins(user.getCoins() - totalC);
            user.setDiamonds(user.getDiamonds() - totalD);

            // Handle pure resource exchange directly (like Diamond to Coin)
            if ("RESOURCE".equalsIgnoreCase(shopItem.getType())) {
                if (shopItem.getMetadata() != null) {
                    JsonObject meta = JsonParser.parseString(shopItem.getMetadata()).getAsJsonObject();
                    if (meta.has("coinAmount")) {
                        long rewardCoins = meta.get("coinAmount").getAsLong() * quantity;
                        user.setCoins(user.getCoins() + rewardCoins);
                    }
                    if (meta.has("diamondAmount")) {
                        long rewardDiamonds = meta.get("diamondAmount").getAsLong() * quantity;
                        user.setDiamonds(user.getDiamonds() + rewardDiamonds);
                    }
                }
                userRepository.save(user);
                log.info("[SHOP] User {} purchased resource exchange '{}' x{}. Cost: {}C/{}D", userId, productCode, quantity, totalC, totalD);
                return ResponseEntity.ok("Purchase successful. Resources added.");
            }

            // Regular Items / Packs / Buffs -> Add to inventory
            userRepository.save(user);
            Optional<UserItem> uItemOpt = userItemRepository.findByUserIdAndItemCode(userId, productCode);
            if (uItemOpt.isPresent()) {
                UserItem ui = uItemOpt.get();
                ui.setQuantity(ui.getQuantity() + quantity);
                userItemRepository.save(ui);
            } else {
                UserItem newItem = new UserItem(user, productCode, quantity);
                userItemRepository.save(newItem);
            }

            log.info("[SHOP] User {} purchased item '{}' x{}. Cost: {}C/{}D", userId, productCode, quantity, totalC, totalD);
            return ResponseEntity.ok("Purchase successful");
        } catch (Exception e) {
            log.error("Error processing purchase with request: {}", requestBody, e);
            return ResponseEntity.internalServerError().body("Error processing purchase");
        }
    }

    @PostMapping("/admin/add-item")
    public ResponseEntity<?> addShopItemLive(@RequestBody ShopItem newItem) {
        // Simple security - optionally you can add authorization token checking here
        if (newItem.getProductCode() == null || newItem.getName() == null) {
            return ResponseEntity.badRequest().body("Missing required valid fields.");
        }
        shopItemRepository.save(newItem);
        return ResponseEntity.ok("Item added to live shop successfully.");
    }
}
