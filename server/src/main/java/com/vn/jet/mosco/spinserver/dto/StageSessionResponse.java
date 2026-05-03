package com.vn.jet.mosco.spinserver.dto;

import com.vn.jet.mosco.spinserver.model.StageSession;
import java.time.ZoneId;

public class StageSessionResponse {
    private Long id;
    private int mapId;
    private int durationHours;
    private long startTimeMillis;
    private long endTimeMillis;
    private String status;
    private int teamScore;

    public StageSessionResponse() {}

    public StageSessionResponse(StageSession session) {
        this.id = session.getId();
        this.mapId = session.getMapId();
        this.durationHours = session.getDurationHours();
        // Server luôn lưu UTC, chuyển về Millis để Client tự so sánh với System.currentTimeMillis()
        this.startTimeMillis = session.getStartTime().toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
        this.endTimeMillis = session.getEndTime().toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
        this.status = session.getStatus();
        this.teamScore = session.getTeamScore();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public int getMapId() { return mapId; }
    public void setMapId(int mapId) { this.mapId = mapId; }

    public int getDurationHours() { return durationHours; }
    public void setDurationHours(int durationHours) { this.durationHours = durationHours; }

    public long getStartTimeMillis() { return startTimeMillis; }
    public void setStartTimeMillis(long startTimeMillis) { this.startTimeMillis = startTimeMillis; }

    public long getEndTimeMillis() { return endTimeMillis; }
    public void setEndTimeMillis(long endTimeMillis) { this.endTimeMillis = endTimeMillis; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getTeamScore() { return teamScore; }
    public void setTeamScore(int teamScore) { this.teamScore = teamScore; }
}
