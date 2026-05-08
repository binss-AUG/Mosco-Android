package com.vn.jet.mosco.dto;

/**
 * DTO nhận dữ liệu thẻ bài rút gọn từ Server.
 */
public class CardSummaryDto {
    private String id;
    private String memberName;
    private String seasonName;
    private String thumbnailId;

    public CardSummaryDto() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getMemberName() { return memberName; }
    public void setMemberName(String memberName) { this.memberName = memberName; }

    public String getSeasonName() { return seasonName; }
    public void setSeasonName(String seasonName) { this.seasonName = seasonName; }

    public String getThumbnailId() { return thumbnailId; }
    public void setThumbnailId(String thumbnailId) { this.thumbnailId = thumbnailId; }
}
