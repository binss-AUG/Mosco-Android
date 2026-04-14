package com.vn.jet.mosco.spinserver.service;

import com.vn.jet.mosco.spinserver.dto.GachaRollResponse;
import com.vn.jet.mosco.spinserver.dto.GachaSpinResponse;
import com.vn.jet.mosco.spinserver.model.GachaHistory;
import com.vn.jet.mosco.spinserver.model.User;
import com.vn.jet.mosco.spinserver.model.UserCard;
import com.vn.jet.mosco.spinserver.model.UserItem;
import com.vn.jet.mosco.spinserver.repository.GachaHistoryRepository;
import com.vn.jet.mosco.spinserver.repository.UserCardRepository;
import com.vn.jet.mosco.spinserver.repository.UserItemRepository;
import com.vn.jet.mosco.spinserver.repository.UserRepository;
import com.vn.jet.mosco.spinserver.utils.SpinSystem;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service layer for the Gacha roll endpoint.
 * Wraps PackService with:
 * - Currency validation
 * - Concurrent lock via @Transactional
 * - Roll history logging to gacha_history
 */
@Service
public class GachaService {

    private static final Logger logger = LoggerFactory.getLogger(GachaService.class);

    private final PackService packService;
    private final UserRepository userRepository;
    private final UserItemRepository userItemRepository;
    private final UserCardRepository userCardRepository;
    private final GachaHistoryRepository gachaHistoryRepository;
    private final SpinSystem spinSystem;

    public GachaService(PackService packService,
                        UserRepository userRepository,
                        UserItemRepository userItemRepository,
                        UserCardRepository userCardRepository,
                        GachaHistoryRepository gachaHistoryRepository,
                        SpinSystem spinSystem) {
        this.packService = packService;
        this.userRepository = userRepository;
        this.userItemRepository = userItemRepository;
        this.userCardRepository = userCardRepository;
        this.gachaHistoryRepository = gachaHistoryRepository;
        this.spinSystem = spinSystem;
    }

    /**
     * Execute a gacha roll for a user with a specific pack.
     * <p>
     * Flow:
     * 1. Validate user exists
     * 2. Check user has the pack in inventory (quantity > 0)
     * 3. Delegate to PackService.openPack (handles pack deduction + card creation)
     * 4. Log the roll to gacha_history
     * 5. Return result with remaining balance
     * </p>
     *
     * @param userId   Authenticated user ID (from JWT)
     * @param packCode The pack to open (e.g. "PACK_METAL", "PACK_S1_ALL")
     * @return GachaRollResponse with item details or error
     */
    @Transactional(rollbackFor = Exception.class)
    public GachaRollResponse roll(Long userId, String packCode) {
        logger.info("Gacha roll requested: userId={}, packCode={}", userId, packCode);

        // 1. Validate user
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            logger.warn("User not found: {}", userId);
            return GachaRollResponse.error("User not found");
        }

        // 2. Check inventory for pack
        UserItem packItem = userItemRepository.findByUserIdAndItemCode(userId, packCode).orElse(null);
        if (packItem == null || packItem.getQuantity() <= 0) {
            logger.warn("User {} does not have pack {} (or quantity is 0)", userId, packCode);
            return GachaRollResponse.error("You don't have this pack: " + packCode);
        }

        // 3. Delegate to PackService (handles deduction, random roll, card save)
        Map<String, Object> result;
        try {
            result = packService.openPack(userId, packCode);
        } catch (RuntimeException e) {
            logger.error("PackService.openPack failed for userId={}, packCode={}: {}",
                    userId, packCode, e.getMessage(), e);
            return GachaRollResponse.error(e.getMessage());
        }

        // 4. Extract card info from result
        String cardId = (String) result.get("cardId");
        @SuppressWarnings("unchecked")
        Map<String, Object> cardData = (Map<String, Object>) result.get("cardData");
        String rarity = "Unknown";
        if (cardData != null && cardData.containsKey("class")) {
            rarity = String.valueOf(cardData.get("class"));
        }

        // 5. Log to gacha_history
        try {
            // Re-fetch user for up-to-date balance after PackService transaction
            user = userRepository.findById(userId).orElse(user);
            GachaHistory history = new GachaHistory(user, cardId, rarity, 1, packCode, "GACHA_ROLL");
            gachaHistoryRepository.save(history);
            logger.info("Gacha history saved: userId={}, cardId={}, rarity={}, packCode={}",
                    userId, cardId, rarity, packCode);
        } catch (Exception e) {
            // Don't fail the entire roll if history logging fails
            logger.error("Failed to save gacha history (non-fatal): {}", e.getMessage(), e);
        }

        // 6. Build response
        return new GachaRollResponse(
                true,
                cardId,
                rarity,
                1,
                cardData,
                "You received: " + cardId,
                user.getCoins(),
                user.getDiamonds()
        );
    }

    /**
     * Execute a gacha spin (Trade Thẻ Đổi Thẻ).
     * <p>
     * Flow:
     * 1. Validate user has the card to sacrifice (usually a duplicate)
     * 2. Remove the card from inventory
     * 3. Call SpinSystem.spin() for a win/loss result
     * 4. If win: Add the new card to inventory
     * 5. Log history
     * </p>
     *
     * @param userId Authenticated user ID
     * @param sacrificedCardId The ID of the card being used for the spin
     * @return GachaSpinResponse with result details
     */
    @Transactional(rollbackFor = Exception.class)
    public GachaSpinResponse spin(Long userId, String sacrificedCardId) {
        logger.info("Gacha spin requested: userId={}, sacrificedCardId={}", userId, sacrificedCardId);

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return new GachaSpinResponse(false, false, null, null, null, null, "User not found");

        // 1. Check ownership of sacrificed card
        // 1. Kiểm tra quyền sở hữu thẻ (Check ownership)
        // Lưu ý: sacrificedCardId truyền lên từ Client là ID Primary Key (Long) của thẻ trong DB
        UserCard userCard = null;
        try {
            Long pkId = Long.parseLong(sacrificedCardId);
            userCard = userCardRepository.findByIdAndUserId(pkId, userId).orElse(null);
        } catch (NumberFormatException e) {
            logger.error("Invalid card ID format: {}", sacrificedCardId);
        }

        if (userCard == null) {
            return new GachaSpinResponse(false, false, null, null, null, null, "Thẻ này không thuộc về bạn hoặc ID không hợp lệ: " + sacrificedCardId);
        }

        // 2. Consume the card (Sacrifice)
        // If your business rule is "deduct quantity", handle it here.
        // For simplicity, we delete the record and later re-add card if it was just a quantity-based item system.
        // Currently, UserCard seems to be a record of ownership.
        userCardRepository.delete(userCard);
        logger.info("Card sacrificed: userId={}, cardId={}", userId, sacrificedCardId);

        // 3. Roll RNG via SpinSystem
        SpinSystem.SpinResult spinResult = spinSystem.spin();

        // 4. Xử lý Thắng/Thua (Win/Loss)
        boolean isWin = spinResult.result != null && !spinResult.group.equals(SpinSystem.GROUP_NOTHING);
        String newItemId = "Nothing";
        String newRarity = spinResult.group;
        Map<String, Object> newCardData = null;

        if (spinResult.result != null) {
            newItemId = spinResult.result.has("id") && !spinResult.result.get("id").isJsonNull()
                    ? spinResult.result.get("id").getAsString()
                    : "Nothing";
            // Luôn trả về dữ liệu thẻ (kể cả thẻ rác/Nothing) để Client hiển thị animation
            newCardData = new Gson().fromJson(spinResult.result, new TypeToken<Map<String, Object>>(){}.getType());
        }

        if (isWin) {
            // Chỉ lưu vào DB nếu trúng thẻ thực thụ. Mặc định: Level 1, Exp 0, Upgrade +1
            UserCard newUserCard = new UserCard(user, newItemId, 1, 0, 1);
            userCardRepository.save(newUserCard);
            
            // Cập nhật thẻ vào danh sách đã từng sở hữu (Ever Owned)
            user.getUnlockedCollections().add(newItemId);
            userRepository.save(user);
            
            logger.info("Spin WIN: userId={} received newItemId={}", userId, newItemId);
        } else {
            logger.info("Spin LOSS: userId={} lost cardId={}", userId, sacrificedCardId);
        }

        // 5. Convert revealGrid for DTO
        List<Map<String, Object>> revealGrid = new ArrayList<>();
        if (spinResult.revealGrid != null) {
            for (JsonObject obj : spinResult.revealGrid) {
                revealGrid.add(new Gson().fromJson(obj, new TypeToken<Map<String, Object>>(){}.getType()));
            }
        }

        // 6. Log to history
        GachaHistory history = new GachaHistory(
                user,
                isWin ? newItemId : "LOSS",
                isWin ? newRarity : "Nothing",
                1,
                sacrificedCardId,
                "GACHA_SPIN"
        );
        gachaHistoryRepository.save(history);

        // Xây dựng message thân thiện với người dùng
        String displayMessage;
        if (isWin) {
            String cardName = (newCardData != null && newCardData.get("collectionId") != null)
                    ? String.valueOf(newCardData.get("collectionId"))
                    : newItemId;
            displayMessage = "Success! You got: " + cardName;
        } else {
            displayMessage = "Unlucky! The sacrifice failed.";
        }

        return new GachaSpinResponse(
                true,
                isWin,
                newItemId,
                newRarity,
                newCardData,
                revealGrid,
                displayMessage
        );
    }
}
