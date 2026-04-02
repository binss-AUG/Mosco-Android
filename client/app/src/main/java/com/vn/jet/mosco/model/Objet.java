package com.vn.jet.mosco.model;

public class Objet {
    private int id;
    private String collectionId;
    private String imageUrl;
    private int level;
    private int exp;
    private int upgradeLevel;

    public Objet(int id, String collectionId, String imageUrl, int level, int exp, int upgradeLevel) {
        this.id = id;
        this.collectionId = collectionId;
        this.imageUrl = imageUrl;
        this.level = level;
        this.exp = exp;
        this.upgradeLevel = upgradeLevel;
    }

    public int getId() { return id; }
    public String getCollectionId() { return collectionId; }
    public String getImageUrl() { return imageUrl; }
    public int getLevel() { return level; }
    public int getExp() { return exp; }
    public int getUpgradeLevel() { return upgradeLevel; }
}
