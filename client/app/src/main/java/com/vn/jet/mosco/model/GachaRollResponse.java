package com.vn.jet.mosco.model;

import java.util.Map;

/**
 * Response body from POST /api/gacha/roll
 * Mirrors the server's GachaRollResponse DTO.
 */
public class GachaRollResponse {

    private boolean success;
    private String itemId;
    private String rarity;
    private int quantity;
    private Map<String, Object> cardData;
    private String message;
    private Long remainingCoins;
    private Long remainingDiamonds;

    public GachaRollResponse() {}

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }

    public String getRarity() { return rarity; }
    public void setRarity(String rarity) { this.rarity = rarity; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public Map<String, Object> getCardData() { return cardData; }
    public void setCardData(Map<String, Object> cardData) { this.cardData = cardData; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Long getRemainingCoins() { return remainingCoins; }
    public void setRemainingCoins(Long remainingCoins) { this.remainingCoins = remainingCoins; }

    public Long getRemainingDiamonds() { return remainingDiamonds; }
    public void setRemainingDiamonds(Long remainingDiamonds) { this.remainingDiamonds = remainingDiamonds; }
}
