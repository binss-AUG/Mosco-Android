package com.vn.jet.mosco.spinserver.dto;

/**
 * Request body for POST /api/gacha/spin
 * Used when a user sacrifices an existing card to get a new one.
 */
public class GachaSpinRequest {

    private String cardId;

    public GachaSpinRequest() {}

    public GachaSpinRequest(String cardId) {
        this.cardId = cardId;
    }

    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }
}
