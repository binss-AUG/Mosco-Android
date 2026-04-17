package com.vn.jet.mosco.spinserver.dto;

/**
 * DTO cho request gửi tặng Objet.
 * Tại sao dùng DTO thay vì Map: Type-safe, dễ validate, dễ đọc.
 */
public class GiftRequest {

    // ID thẻ bài người gửi muốn tặng
    private Long cardId;

    // ID người nhận (phải là bạn bè)
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
