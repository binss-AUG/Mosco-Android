package com.vn.jet.mosco.spinserver.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Thực thể theo dõi hành động thích hồ sơ (Like) giữa các người chơi.
 * Bắt buộc áp dụng UniqueConstraint trên cặp (likerId, targetUserId) 
 * nhằm ngăn chặn triệt để tình trạng một người chơi spam bấm thích 
 * hoặc gửi yêu cầu lặp lại làm sai lệch tổng số lượt thích.
 */
@Entity
@Table(name = "user_likes",
       uniqueConstraints = @UniqueConstraint(columnNames = {"likerId", "targetUserId"}))
public class UserLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ID của người thực hiện hành động thích hồ sơ
    @Column(nullable = false)
    private Long likerId;

    // ID của người được thích hồ sơ
    @Column(nullable = false)
    private Long targetUserId;

    // Thời điểm thực hiện hành động thích để hỗ trợ tra cứu lịch sử khi cần
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public UserLike() {
        this.createdAt = LocalDateTime.now();
    }

    public UserLike(Long likerId, Long targetUserId) {
        this.likerId = likerId;
        this.targetUserId = targetUserId;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getLikerId() { return likerId; }
    public void setLikerId(Long likerId) { this.likerId = likerId; }

    public Long getTargetUserId() { return targetUserId; }
    public void setTargetUserId(Long targetUserId) { this.targetUserId = targetUserId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
