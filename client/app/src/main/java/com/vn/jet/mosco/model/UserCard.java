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

    public UserCard() {}

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
}
