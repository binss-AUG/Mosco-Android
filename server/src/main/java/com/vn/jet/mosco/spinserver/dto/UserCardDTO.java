package com.vn.jet.mosco.spinserver.dto;

/**
 * DTO trả về Client chứa đầy đủ OVR + cardClass.
 * Tại sao dùng DTO: Không trả trực tiếp Entity (theo Rule),
 * và Client không cần tự tính OVR nữa — Server là nguồn sự thật duy nhất.
 */
public class UserCardDTO {

    private Long id;
    private String collectionId;
    private int level;
    private int exp;
    private int upgradeLevel;
    private int ovr;           // Server tính sẵn từ cardOvr.json
    private String cardClass;  // "Premier", "Double", "Special Unit", "First Welcome"
    private java.util.List<String> availableTags;
    private String dimension;
    
    // Metadata for UI (Moved from Client JSON to Server Truth)
    private String frontImage;
    private String backImage;
    private String member;
    private String season;
    private String collectionNo;
    private String slug;
    private String backgroundColor;
    private String textColor;

    public UserCardDTO() {}

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

    public UserCardDTO(Long id, String collectionId, int level, int exp, int upgradeLevel, int ovr, String cardClass) {
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
