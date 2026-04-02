package com.vn.jet.mosco.model;

/**
 * Model đại diện cho thẻ dùng trong tính năng Upgrade.
 * Chứa thông tin typeKey, level, ovr và imageUrl.
 */
public class UpgradeCard {
    private String id;
    private String typeKey; // "FirstWelcome", "Double", "SpecialUnit", "Premier"
    private int level;      // 1 đến 10
    private int ovr;        // OVR lấy từ cardOvr.json
    private String imageUrl;

    public UpgradeCard(String id, String typeKey, int level, int ovr, String imageUrl) {
        this.id = id;
        this.typeKey = typeKey;
        this.level = level;
        this.ovr = ovr;
        this.imageUrl = imageUrl;
    }

    public String getId() {
        return id;
    }

    public String getTypeKey() {
        return typeKey;
    }

    public int getLevel() {
        return level;
    }

    public int getOvr() {
        return ovr;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public void setOvr(int ovr) {
        this.ovr = ovr;
    }
}
