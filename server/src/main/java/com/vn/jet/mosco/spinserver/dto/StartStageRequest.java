package com.vn.jet.mosco.spinserver.dto;

import java.util.List;

public class StartStageRequest {
    private int mapId;
    private int durationHours;
    private List<Long> cardIds;

    public StartStageRequest() {}

    public int getMapId() { return mapId; }
    public void setMapId(int mapId) { this.mapId = mapId; }

    public int getDurationHours() { return durationHours; }
    public void setDurationHours(int durationHours) { this.durationHours = durationHours; }

    public List<Long> getCardIds() { return cardIds; }
    public void setCardIds(List<Long> cardIds) { this.cardIds = cardIds; }
}
