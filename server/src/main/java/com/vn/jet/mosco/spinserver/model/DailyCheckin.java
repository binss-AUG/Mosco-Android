package com.vn.jet.mosco.spinserver.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Entity lưu lịch sử điểm danh hằng ngày.
 * Mỗi ngày có 3 slot: MORNING (0), NOON (1), EVENING (2).
 * Unique constraint (userId, checkinDate, slot) chống claim trùng.
 */
@Entity
@Table(name = "daily_checkins",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "checkin_date", "slot"}))
public class DailyCheckin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ID người chơi thực hiện điểm danh
    @Column(nullable = false)
    private Long userId;

    // Ngày điểm danh (chỉ lưu ngày, không lưu giờ)
    @Column(nullable = false)
    private LocalDate checkinDate;

    // Slot điểm danh: 0 = MORNING (06:00-11:59), 1 = NOON (12:00-17:59), 2 = EVENING (18:00-23:59)
    @Column(nullable = false)
    private int slot;

    public DailyCheckin() {}

    public DailyCheckin(Long userId, LocalDate checkinDate, int slot) {
        this.userId = userId;
        this.checkinDate = checkinDate;
        this.slot = slot;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public LocalDate getCheckinDate() { return checkinDate; }
    public void setCheckinDate(LocalDate checkinDate) { this.checkinDate = checkinDate; }

    public int getSlot() { return slot; }
    public void setSlot(int slot) { this.slot = slot; }
}
