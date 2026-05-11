package com.vn.jet.mosco.model;

import com.google.gson.annotations.SerializedName;

/**
 * Model entry cho Bộ Sưu Tập (Collection Book).
 * Mỗi entry tương ứng 1 thẻ bài trong database —
 * đánh dấu user đã sở hữu hay chưa.
 */
public class CollectionEntry {

    @SerializedName("collectionId")
    private String collectionId;

    @SerializedName("member")
    private String member;

    @SerializedName("season")
    private String season;

    @SerializedName("cardClass")
    private String cardClass;

    @SerializedName("collectionNo")
    private String collectionNo;

    @SerializedName("frontImage")
    private String frontImage;

    @SerializedName("backgroundColor")
    private String backgroundColor;

    @SerializedName("backImage")
    private String backImage;

    @SerializedName("owned")
    private boolean owned;

    @SerializedName("userCardId")
    private Long userCardId;

    @SerializedName("ovr")
    private int ovr;

    @SerializedName("upgradeLevel")
    private int upgradeLevel;

    @SerializedName("level")
    private int level;

    public CollectionEntry() {}

    // === Getters ===

    // === Getters & Setters ===
    public String getCollectionId() { return collectionId; }
    public void setCollectionId(String collectionId) { this.collectionId = collectionId; }

    public String getMember() { return member; }
    public void setMember(String member) { this.member = member; }

    public String getSeason() { return season; }
    public void setSeason(String season) { this.season = season; }

    public String getCardClass() { return cardClass; }
    public void setCardClass(String cardClass) { this.cardClass = cardClass; }

    public String getCollectionNo() { return collectionNo; }
    public void setCollectionNo(String collectionNo) { this.collectionNo = collectionNo; }

    public String getFrontImage() { return frontImage; }
    public void setFrontImage(String frontImage) { this.frontImage = frontImage; }

    public String getBackgroundColor() { return backgroundColor; }
    public void setBackgroundColor(String backgroundColor) { this.backgroundColor = backgroundColor; }

    public String getBackImage() { return backImage; }
    public void setBackImage(String backImage) { this.backImage = backImage; }

    public boolean isOwned() { return owned; }
    public void setOwned(boolean owned) { this.owned = owned; }

    public Long getUserCardId() { return userCardId; }
    public void setUserCardId(Long userCardId) { this.userCardId = userCardId; }
    
    // Setter nạp chồng để hỗ trợ gán từ String (ID từ API)
    public void setUserCardId(String userCardIdStr) {
        try {
            this.userCardId = Long.parseLong(userCardIdStr);
        } catch (Exception e) {
            this.userCardId = null;
        }
    }

    public int getOvr() { return ovr; }
    public void setOvr(int ovr) { this.ovr = ovr; }

    public int getUpgradeLevel() { return upgradeLevel; }
    public void setUpgradeLevel(int upgradeLevel) { this.upgradeLevel = upgradeLevel; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    @SerializedName("createdAt")
    private String createdAt;

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
