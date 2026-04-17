package com.vn.jet.mosco.spinserver.dto;

import java.time.LocalDateTime;

/**
 * DTO trả về lịch sử giao dịch tặng Objet cho Client.
 * Tại sao dùng DTO: Không trả trực tiếp Entity (theo Rule),
 * và cần kèm thêm thông tin hiển thị (tên user, hình thẻ).
 */
public class GiftHistoryDTO {

    private Long id;
    private Long senderId;
    private String senderName;
    private Long receiverId;
    private String receiverName;
    private String collectionId;
    private String cardFrontImage; // Thumbnail thẻ để hiển thị trên UI
    private LocalDateTime createdAt;

    public GiftHistoryDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSenderId() { return senderId; }
    public void setSenderId(Long senderId) { this.senderId = senderId; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public Long getReceiverId() { return receiverId; }
    public void setReceiverId(Long receiverId) { this.receiverId = receiverId; }

    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }

    public String getCollectionId() { return collectionId; }
    public void setCollectionId(String collectionId) { this.collectionId = collectionId; }

    public String getCardFrontImage() { return cardFrontImage; }
    public void setCardFrontImage(String cardFrontImage) { this.cardFrontImage = cardFrontImage; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
