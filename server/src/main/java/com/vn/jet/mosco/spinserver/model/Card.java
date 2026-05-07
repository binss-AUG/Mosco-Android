package com.vn.jet.mosco.spinserver.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Bảng chính lưu thông tin thẻ bài (Cards).
 * id được lấy từ JSON (UUID String).
 */
@Entity
@Table(name = "cards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Card {

    @Id
    @Column(length = 36)
    private String id; // UUID từ JSON

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "class_id", nullable = false)
    private CardClass cardClass;

    @Column(name = "front_image_id", nullable = false)
    private String frontImageId;

    @Column(name = "back_image_id", nullable = false)
    private String backImageId;

    @Column(name = "base_ovr", nullable = false)
    private int baseOvr = 70;

    @Column(name = "upgrade_level", nullable = false)
    private int upgradeLevel = 1;

    // Các trường bổ sung nếu cần trong tương lai
    @Column(name = "collection_no")
    private String collectionNo;

    @org.hibernate.annotations.UpdateTimestamp
    @Column(name = "updated_at")
    private java.time.LocalDateTime updatedAt;
}
