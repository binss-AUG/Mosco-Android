package com.vn.jet.mosco.model;

/**
 * Request for card sacrifice (Spin mechanism).
 */
public class GachaSpinRequest {
    private String cardId;

    public GachaSpinRequest(String cardId) {
        this.cardId = cardId;
    }

    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }
}
