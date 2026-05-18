package com.vn.jet.mosco.model;

import com.google.gson.annotations.SerializedName;

/**
 * Model thẻ bài của user — nhận từ Server API.
 * Server tính sẵn OVR + cardClass, Client chỉ hiển thị.
 */
public class UserCard {
    @SerializedName("id")
    private Long id;

    @SerializedName("collectionId")
    private String collectionId;

    @SerializedName("level")
    private int level;        // Cấp độ thẻ bài (1-MAX)

    @SerializedName("exp")
    private int exp;          // Kinh nghiệm tích lũy

    @SerializedName("upgradeLevel")
    private int upgradeLevel; // Cấp cường hóa (+1 đến +10)

    @SerializedName("ovr")
    private int ovr;          // Overall Rating — Server tính sẵn (Server Truth)

    @SerializedName("cardClass")
    private String cardClass; // Class thẻ — Server trả sẵn ("Premier", "Double"...)

    @SerializedName("availableTags")
    private java.util.List<String> availableTags; // Tags có sẵn để chọn

    @SerializedName("dimension")
    private String dimension; // Hệ gốc

    @SerializedName("frontImage")
    private String frontImage;

    @SerializedName("backImage")
    private String backImage;

    @SerializedName("member")
    private String member;

    @SerializedName("season")
    private String season;

    @SerializedName("collectionNo")
    private String collectionNo;

    @SerializedName("slug")
    private String slug;

    @SerializedName("backgroundColor")
    private String backgroundColor;

    @SerializedName("textColor")
    private String textColor;

    @SerializedName("status")
    private String status;

    @SerializedName("uuid")
    private String uuid;

    @SerializedName("createdAt")
    private String createdAt; // ISO string from server

    @SerializedName("frontVideoUrl")
    private String frontVideoUrl;

    public UserCard() {}

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getFrontImage() { return frontImage; }
    public void setFrontImage(String frontImage) { this.frontImage = frontImage; }

    public String getBackImage() { return backImage; }
    public void setBackImage(String backImage) { this.backImage = backImage; }

    public String getMember() { return member; }
    public void setMember(String member) { this.member = member; }

    public String getSeason() { return season; }
    public void setSeason(String season) { this.season = season; }

    public String getCollectionNo() { return collectionNo; }
    public void setCollectionNo(String collectionNo) { this.collectionNo = collectionNo; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getBackgroundColor() { return backgroundColor; }
    public void setBackgroundColor(String backgroundColor) { this.backgroundColor = backgroundColor; }

    public String getTextColor() { return textColor; }
    public void setTextColor(String textColor) { this.textColor = textColor; }

    public java.util.List<String> getAvailableTags() { return availableTags; }
    public void setAvailableTags(java.util.List<String> availableTags) { this.availableTags = availableTags; }

    public String getDimension() { return dimension; }
    public void setDimension(String dimension) { this.dimension = dimension; }

    public UserCard(Long id, String collectionId, int level, int exp, int upgradeLevel, int ovr, String cardClass) {
        this.id = id;
        this.collectionId = collectionId;
        this.level = level;
        this.exp = exp;
        this.upgradeLevel = upgradeLevel;
        this.ovr = ovr;
        this.cardClass = cardClass;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCollectionId() { return collectionId; }
    public void setCollectionId(String collectionId) { this.collectionId = collectionId; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public int getExp() { return exp; }
    public void setExp(int exp) { this.exp = exp; }

    public int getUpgradeLevel() { return upgradeLevel; }
    public void setUpgradeLevel(int upgradeLevel) { this.upgradeLevel = upgradeLevel; }

    public int getOvr() { return ovr; }
    public void setOvr(int ovr) { this.ovr = ovr; }

    public String getCardClass() { return cardClass; }
    public void setCardClass(String cardClass) { this.cardClass = cardClass; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getFrontVideoUrl() { return frontVideoUrl; }
    public void setFrontVideoUrl(String frontVideoUrl) { this.frontVideoUrl = frontVideoUrl; }
}
