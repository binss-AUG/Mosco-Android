package com.vn.jet.mosco.spinserver.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "stage_sessions")
public class StageSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private int mapId;

    @Column(nullable = false)
    private int durationHours;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    @Column(nullable = false)
    private String status = "RUNNING"; // RUNNING, COMPLETED, CANCELED

    @Column(nullable = false)
    private int teamScore;

    @Column(name = "member_ids", nullable = false)
    private String memberIds;

    public StageSession() {}

    public StageSession(User user, int mapId, int durationHours, LocalDateTime startTime, LocalDateTime endTime, int teamScore, String memberIds) {
        this.user = user;
        this.mapId = mapId;
        this.durationHours = durationHours;
        this.startTime = startTime;
        this.endTime = endTime;
        this.teamScore = teamScore;
        this.memberIds = memberIds;
        this.status = "RUNNING";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public int getMapId() { return mapId; }
    public void setMapId(int mapId) { this.mapId = mapId; }

    public int getDurationHours() { return durationHours; }
    public void setDurationHours(int durationHours) { this.durationHours = durationHours; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getTeamScore() { return teamScore; }
    public void setTeamScore(int teamScore) { this.teamScore = teamScore; }

    public String getMemberIds() { return memberIds; }
    public void setMemberIds(String memberIds) { this.memberIds = memberIds; }
}
