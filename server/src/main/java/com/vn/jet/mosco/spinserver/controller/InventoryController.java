package com.vn.jet.mosco.spinserver.controller;

import com.vn.jet.mosco.spinserver.dto.UserItemResponse;
import com.vn.jet.mosco.spinserver.model.ShopItem;
import com.vn.jet.mosco.spinserver.model.UserCard;
import com.vn.jet.mosco.spinserver.model.UserItem;
import com.vn.jet.mosco.spinserver.repository.ShopItemRepository;
import com.vn.jet.mosco.spinserver.repository.UserCardRepository;
import com.vn.jet.mosco.spinserver.repository.UserItemRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final UserCardRepository userCardRepository;
    private final UserItemRepository userItemRepository;
    private final ShopItemRepository shopItemRepository;

    public InventoryController(UserCardRepository userCardRepository, UserItemRepository userItemRepository, ShopItemRepository shopItemRepository) {
        this.userCardRepository = userCardRepository;
        this.userItemRepository = userItemRepository;
        this.shopItemRepository = shopItemRepository;
    }

    @GetMapping("/cards/{userId}")
    public ResponseEntity<List<UserCard>> getUserCards(@PathVariable Long userId) {
        List<UserCard> cards = userCardRepository.findByUserId(userId);
        return ResponseEntity.ok(cards);
    }

    @GetMapping("/items/{userId}")
    public ResponseEntity<List<UserItemResponse>> getUserItems(@PathVariable Long userId) {
        List<UserItem> items = userItemRepository.findByUserId(userId);
        List<UserItemResponse> responses = new ArrayList<>();
        
        for (UserItem item : items) {
            Optional<ShopItem> shopOpt = shopItemRepository.findByProductCode(item.getItemCode());
            if (shopOpt.isPresent()) {
                ShopItem s = shopOpt.get();
                responses.add(new UserItemResponse(
                        item.getId(),
                        item.getItemCode(),
                        item.getQuantity(),
                        s.getName(),
                        s.getDescription(),
                        s.getType(),
                        s.getImageUri()
                ));
            } else {
                responses.add(new UserItemResponse(
                        item.getId(),
                        item.getItemCode(),
                        item.getQuantity(),
                        "Unknown Item",
                        "Item no longer exists",
                        "UNKNOWN",
                        ""
                ));
            }
        }
        
        return ResponseEntity.ok(responses);
    }
}
