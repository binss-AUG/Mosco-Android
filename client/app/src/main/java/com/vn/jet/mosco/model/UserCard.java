package com.vn.jet.mosco.model;

import com.google.gson.annotations.SerializedName;

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

    public UserCard() {}

    public UserCard(Long id, String collectionId, int level, int exp, int upgradeLevel) {
        this.id = id;
        this.collectionId = collectionId;
        this.level = level;
        this.exp = exp;
        this.upgradeLevel = upgradeLevel;
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
}
