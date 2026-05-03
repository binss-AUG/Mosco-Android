package com.vn.jet.mosco.dto;

public class StageSessionResponse {
    private Long id;
    private int mapId;
    private int durationHours;
    private long startTimeMillis;
    private long endTimeMillis;
    private String status;
    private int teamScore;

    public StageSessionResponse() {}

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
