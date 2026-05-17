package com.vn.jet.mosco.spinserver.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "couple_streaks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoupleStreak {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id", nullable = false)
    private User partner;

    @Column(nullable = false)
    private String status; // PENDING, ACTIVE, DECLINED

    @Column(name = "streak_count")
    private int streakCount;

    private LocalDate lastInteractionDate;
    private LocalDate requestDate;

    @Column(name = "requester_interaction_date")
    private LocalDate requesterInteractionDate;

    @Column(name = "partner_interaction_date")
    private LocalDate partnerInteractionDate;

    @Column(name = "requester_objet_id")
    private String requesterObjetId;

    @Column(name = "partner_objet_id")
    private String partnerObjetId;

    @Column(name = "objet_changes_this_week")
    @Builder.Default
    private int objetChangesThisWeek = 0;

    private LocalDate lastObjetChangeDate;

    @Column(name = "streak_level")
    @Builder.Default
    private int streakLevel = 1;

    @Column(name = "requester_grade")
    @Builder.Default
    private int requesterGrade = 1;

    @Column(name = "partner_grade")
    @Builder.Default
    private int partnerGrade = 1;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
