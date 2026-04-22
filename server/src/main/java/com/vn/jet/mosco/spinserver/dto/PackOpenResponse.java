package com.vn.jet.mosco.spinserver.dto;

import java.util.List;
import java.util.Map;

/**
 * DTO chứa kết quả mở Pack.
 */
public class PackOpenResponse {
    private String packCode;
    private List<CardResult> cards;

    public PackOpenResponse() {}

    public PackOpenResponse(String packCode, List<CardResult> cards) {
        this.packCode = packCode;
        this.cards = cards;
    }

    public String getPackCode() { return packCode; }
    public void setPackCode(String packCode) { this.packCode = packCode; }

    public List<CardResult> getCards() { return cards; }
    public void setCards(List<CardResult> cards) { this.cards = cards; }

    public static class CardResult {
        private String cardId;
        private String cardClass;
        private Object rarityColor; // Mã Hex hoặc mảng màu cho Gradient
        private Map<String, Object> cardData;

        public CardResult() {}

        public CardResult(String cardId, String cardClass, Object rarityColor, Map<String, Object> cardData) {
            this.cardId = cardId;
            this.cardClass = cardClass;
            this.rarityColor = rarityColor;
            this.cardData = cardData;
        }

        public String getCardId() { return cardId; }
        public void setCardId(String cardId) { this.cardId = cardId; }

        public String getCardClass() { return cardClass; }
        public void setCardClass(String cardClass) { this.cardClass = cardClass; }

        public Object getRarityColor() { return rarityColor; }
        public void setRarityColor(Object rarityColor) { this.rarityColor = rarityColor; }

        public Map<String, Object> getCardData() { return cardData; }
        public void setCardData(Map<String, Object> cardData) { this.cardData = cardData; }
    }
}