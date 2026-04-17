package com.vn.jet.mosco.model;

import com.google.gson.annotations.SerializedName;

/**
 * Model request gửi tặng Objet — gửi lên Server.
 * Tại sao dùng class riêng thay Map: Rõ ràng, type-safe, dễ serialize.
 */
public class GiftRequest {

    @SerializedName("cardId")
    private Long cardId;

    @SerializedName("receiverId")
    private Long receiverId;

    public GiftRequest() {}

    public GiftRequest(Long cardId, Long receiverId) {
        this.cardId = cardId;
        this.receiverId = receiverId;
    }

    public Long getCardId() { return cardId; }
    public void setCardId(Long cardId) { this.cardId = cardId; }

    public Long getReceiverId() { return receiverId; }
    public void setReceiverId(Long receiverId) { this.receiverId = receiverId; }
}
