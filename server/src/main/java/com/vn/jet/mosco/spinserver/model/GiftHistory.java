package com.vn.jet.mosco.spinserver.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity lưu lịch sử giao dịch tặng Objet giữa các user.
 * Đóng vai trò vừa là audit trail, vừa là inbox cho người nhận.
 * receiverRead = false → hiển thị dấu "mới" trong tab Nhận.
 */
@Entity
@Table(name = "gift_history")
public class GiftHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Người gửi tặng thẻ
    @Column(nullable = false)
    private Long senderId;

    // Người nhận thẻ
    @Column(nullable = false)
    private Long receiverId;

    // ID thẻ bài được tặng (tham chiếu tại thời điểm giao dịch)
    @Column(nullable = false)
    private Long cardId;

    // Mã collection gốc — lưu lại để truy vết ngay cả khi thẻ bị xóa
    @Column(nullable = false)
    private String collectionId;

    // Người nhận đã xem chưa — dùng để hiển thị badge "mới" trên UI
    @Column(nullable = false)
    private boolean receiverRead = false;

    // Thời điểm giao dịch
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public GiftHistory() {
        this.createdAt = LocalDateTime.now();
    }

    public GiftHistory(Long senderId, Long receiverId, Long cardId, String collectionId) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.cardId = cardId;
        this.collectionId = collectionId;
        this.receiverRead = false;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSenderId() { return senderId; }
    public void setSenderId(Long senderId) { this.senderId = senderId; }

    public Long getReceiverId() { return receiverId; }
    public void setReceiverId(Long receiverId) { this.receiverId = receiverId; }

    public Long getCardId() { return cardId; }
    public void setCardId(Long cardId) { this.cardId = cardId; }

    public String getCollectionId() { return collectionId; }
    public void setCollectionId(String collectionId) { this.collectionId = collectionId; }

    public boolean isReceiverRead() { return receiverRead; }
    public void setReceiverRead(boolean receiverRead) { this.receiverRead = receiverRead; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
