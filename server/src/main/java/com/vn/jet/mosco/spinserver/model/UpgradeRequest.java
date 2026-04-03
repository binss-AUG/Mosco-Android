package com.vn.jet.mosco.spinserver.model;

import java.util.List;

/**
 * DTO nhận yêu cầu nâng cấp thẻ từ client.
 */
public class UpgradeRequest {
    private Long userId;
    private Long baseCardId;
    private List<Long> materialCardIds;

    public UpgradeRequest() {}

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getBaseCardId() { return baseCardId; }
    public void setBaseCardId(Long baseCardId) { this.baseCardId = baseCardId; }

    public List<Long> getMaterialCardIds() { return materialCardIds; }
    public void setMaterialCardIds(List<Long> materialCardIds) { this.materialCardIds = materialCardIds; }
}
