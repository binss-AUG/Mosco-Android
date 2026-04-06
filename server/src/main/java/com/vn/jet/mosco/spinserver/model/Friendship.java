package com.vn.jet.mosco.spinserver.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity lưu quan hệ bạn bè giữa hai người chơi.
 * Status: 0 = PENDING (đang chờ), 1 = ACCEPTED (đã kết bạn).
 * Unique constraint (requesterId, addresseeId) chống gửi trùng lời mời.
 */
@Entity
@Table(name = "friendships",
       uniqueConstraints = @UniqueConstraint(columnNames = {"requesterId", "addresseeId"}))
public class Friendship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Người gửi lời mời kết bạn
    @Column(nullable = false)
    private Long requesterId;

    // Người nhận lời mời kết bạn
    @Column(nullable = false)
    private Long addresseeId;

    // Trạng thái: 0 = PENDING, 1 = ACCEPTED
    @Column(nullable = false)
    private int status = 0;

    // Thời điểm tạo lời mời
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Friendship() {
        this.createdAt = LocalDateTime.now();
    }

    public Friendship(Long requesterId, Long addresseeId) {
        this.requesterId = requesterId;
        this.addresseeId = addresseeId;
        this.status = 0;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRequesterId() { return requesterId; }
    public void setRequesterId(Long requesterId) { this.requesterId = requesterId; }

    public Long getAddresseeId() { return addresseeId; }
    public void setAddresseeId(Long addresseeId) { this.addresseeId = addresseeId; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
