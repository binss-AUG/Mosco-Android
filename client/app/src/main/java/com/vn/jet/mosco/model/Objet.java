package com.vn.jet.mosco.model;

public class Objet {
    private int id;
    private String collectionId;
    private String imageUrl;
    private int level;
    private int exp;
    private int upgradeLevel;
    
    // Thuộc tính bổ sung cho chức năng Upgrade
    private int ovr;
    private String typeKey; // "FirstWelcome", "Double", "SpecialUnit", "Premier"

    public Objet(int id, String collectionId, String imageUrl, int level, int exp, int upgradeLevel) {
        this.id = id;
        this.collectionId = collectionId;
        this.imageUrl = imageUrl;
        this.level = level;
        this.exp = exp;
        this.upgradeLevel = upgradeLevel;
        this.ovr = 0;
        this.typeKey = collectionId; // Mặc định, sẽ được override sau
    }

    public int getId() { return id; }
    public String getIdString() { return String.valueOf(id); }
    public String getCollectionId() { return collectionId; }
    public String getTypeKey() { return typeKey; }
    public void setTypeKey(String typeKey) { this.typeKey = typeKey; }
    public String getImageUrl() { return imageUrl; }
    public int getLevel() { return level; }
    public int getExp() { return exp; }
    
    public int getUpgradeLevel() { return upgradeLevel; }
    public int getCardLevel() { return upgradeLevel; }
    public void setCardLevel(int upgradeLevel) { this.upgradeLevel = upgradeLevel; }
    
    public int getOvr() { return ovr; }
    public void setOvr(int ovr) { this.ovr = ovr; }
}
