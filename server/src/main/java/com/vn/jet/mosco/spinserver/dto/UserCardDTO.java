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

    public UserCardDTO() {}

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
