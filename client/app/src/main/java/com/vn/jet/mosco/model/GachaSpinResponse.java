package com.vn.jet.mosco.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

/**
 * Response for card sacrifice (Spin mechanism).
 */
public class GachaSpinResponse {

    private boolean success;
    @SerializedName("win")
    private boolean win;
    private String itemId;
    private String rarity;
    private Map<String, Object> cardData;
    private List<Map<String, Object>> revealGrid;
    private String message;

    public GachaSpinResponse() {}

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public boolean isWin() { return win; }
    public void setWin(boolean win) { this.win = win; }

    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }

    public String getRarity() { return rarity; }
    public void setRarity(String rarity) { this.rarity = rarity; }

    public Map<String, Object> getCardData() { return cardData; }
    public void setCardData(Map<String, Object> cardData) { this.cardData = cardData; }

    public List<Map<String, Object>> getRevealGrid() { return revealGrid; }
    public void setRevealGrid(List<Map<String, Object>> revealGrid) { this.revealGrid = revealGrid; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
