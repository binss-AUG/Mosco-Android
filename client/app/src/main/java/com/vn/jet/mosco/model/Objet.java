package com.vn.jet.mosco.model;

public class Objet {
    private int id;
    private String collectionId;
    @com.google.gson.annotations.SerializedName("frontImage")
    private String imageUrl;
    private int level;
    private int exp;
    private int upgradeLevel;
    
    // Thuộc tính bổ sung cho chức năng Upgrade
    private int ovr;
    @com.google.gson.annotations.SerializedName("cardClass")
    private String typeKey; // "FirstWelcome", "Double", "SpecialUnit", "Premier"
    
    // Metadata from Server (Replaces local JSON)
    private String backImageUrl;
    private String member;
    private String season;
    private String collectionNo;
    private String slug;
    private String backgroundColor;
    private String textColor;
    private java.util.List<String> availableTags;
    private String dimension;

    public Objet(int id, String collectionId, String imageUrl, int level, int exp, int upgradeLevel) {
        this.id = id;
        this.collectionId = collectionId;
        this.imageUrl = imageUrl;
        this.level = level;
        this.exp = exp;
        this.upgradeLevel = upgradeLevel;
        this.ovr = 0;
        this.typeKey = collectionId; 
    }

    /**
     * Chuyển đổi từ Cache Item sang Objet Model dùng cho UI
     */
    public static Objet fromCacheItem(com.vn.jet.mosco.utils.DatabaseLoader.UserInventoryItem item) {
        Objet obj = new Objet(item.id.intValue(), item.collectionId, item.frontImage, item.level, item.exp, item.upgradeLevel);
        obj.setOvr(item.ovr);
        obj.setTypeKey(item.cardClass);
        obj.setBackImageUrl(item.backImage);
        obj.setMember(item.member);
        obj.setSeason(item.season);
        obj.setCollectionNo(item.collectionNo);
        obj.setSlug(item.slug);
        obj.setBackgroundColor(item.backgroundColor);
        obj.setTextColor(item.textColor);
        obj.setAvailableTags(item.availableTags);
        obj.setDimension(item.dimension);
        return obj;
    }

    /**
     * Chuyển đổi từ UserCard DTO sang Objet Model dùng cho UI
     */
    public static Objet fromUserCard(com.vn.jet.mosco.model.UserCard userCard) {
        Objet obj = new Objet(userCard.getId().intValue(), userCard.getCollectionId(), userCard.getFrontImage(), userCard.getLevel(), userCard.getExp(), userCard.getUpgradeLevel());
        obj.setOvr(userCard.getOvr());
        obj.setTypeKey(userCard.getCardClass());
        obj.setBackImageUrl(userCard.getBackImage());
        obj.setMember(userCard.getMember());
        obj.setSeason(userCard.getSeason());
        obj.setCollectionNo(userCard.getCollectionNo());
        obj.setSlug(userCard.getSlug());
        obj.setBackgroundColor(userCard.getBackgroundColor());
        obj.setTextColor(userCard.getTextColor());
        obj.setAvailableTags(userCard.getAvailableTags());
        obj.setDimension(userCard.getDimension());
        return obj;
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

    public String getBackImageUrl() { return backImageUrl; }
    public void setBackImageUrl(String backImageUrl) { this.backImageUrl = backImageUrl; }

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
}
