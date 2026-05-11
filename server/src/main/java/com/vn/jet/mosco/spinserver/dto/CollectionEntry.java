package com.vn.jet.mosco.spinserver.dto;

/**
 * Một entry trong Bộ Sưu Tập (Collection Book).
 * Chứa thông tin metadata + trạng thái sở hữu của user.
 */
public class CollectionEntry {

    private String collectionId;   // ID gốc trong database.json
    private String member;         // Tên thành viên (SeoYeon, HyeRin...)
    private String season;         // Season (Atom01, Binary01...)
    private String cardClass;      // Class (First Welcome, Double, Premier...)
    private String collectionNo;   // Số thẻ (e.g. "357Z")
    private String frontImage;     // URL hình mặt trước
    private String backImage;      // URL hình mặt sau
    private String backgroundColor;

    // Trạng thái sở hữu
    private boolean owned;         // User đã có thẻ này chưa
    private Long userCardId;       // ID bản ghi user_cards (nếu đã có)
    private int ovr;               // OVR (nếu đã có)
    private int upgradeLevel;      // Cấp cường hóa (nếu đã có)
    private int level;             // Level (nếu đã có)

    public CollectionEntry() {}

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

    public String getBackImage() { return backImage; }
    public void setBackImage(String backImage) { this.backImage = backImage; }

    public String getBackgroundColor() { return backgroundColor; }
    public void setBackgroundColor(String backgroundColor) { this.backgroundColor = backgroundColor; }

    public boolean isOwned() { return owned; }
    public void setOwned(boolean owned) { this.owned = owned; }

    public Long getUserCardId() { return userCardId; }
    public void setUserCardId(Long userCardId) { this.userCardId = userCardId; }

    public int getOvr() { return ovr; }
    public void setOvr(int ovr) { this.ovr = ovr; }

    public int getUpgradeLevel() { return upgradeLevel; }
    public void setUpgradeLevel(int upgradeLevel) { this.upgradeLevel = upgradeLevel; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
}
