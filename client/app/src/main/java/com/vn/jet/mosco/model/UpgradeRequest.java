package com.vn.jet.mosco.model;

import java.util.List;

public class UpgradeRequest {
    private Long userId;
    private Long baseCardId;
    private List<Long> materialCardIds;

    public UpgradeRequest() {}

    public UpgradeRequest(Long userId, Long baseCardId, List<Long> materialCardIds) {
        this.userId = userId;
        this.baseCardId = baseCardId;
        this.materialCardIds = materialCardIds;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getBaseCardId() { return baseCardId; }
    public void setBaseCardId(Long baseCardId) { this.baseCardId = baseCardId; }
    public List<Long> getMaterialCardIds() { return materialCardIds; }
    public void setMaterialCardIds(List<Long> materialCardIds) { this.materialCardIds = materialCardIds; }
}
