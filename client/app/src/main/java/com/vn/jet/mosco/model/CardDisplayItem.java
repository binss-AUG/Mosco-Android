package com.vn.jet.mosco.model;

import java.util.List;

/**
 * Model thống nhất cho hiển thị thẻ bài trong Grid (Inventory + Album).
 * Kết hợp toàn bộ thuộc tính cần thiết từ Objet và CollectionEntry,
 * đảm bảo 1 nguồn duy nhất cho cả 2 loại Adapter.
 *
 * Hỗ trợ 2 chế độ hiển thị:
 * - INVENTORY: Hiện kho đồ, multi-select, busy/disabled states
 * - ALBUM: Hiện bộ sưu tập, owned/locked overlay
 */
public class CardDisplayItem {

    // === Core Identity ===
    private long id;                    // UserCard ID (kho đồ) hoặc -1 (album chưa sở hữu)
    private String collectionId;        // ID gốc trong database.json

    // === Images ===
    private String frontImage;          // URL ảnh mặt trước (thống nhất từ Objet.imageUrl / CollectionEntry.frontImage)
    private String backImage;           // URL ảnh mặt sau

    // === Stats ===
    private int level;
    private int exp;
    private int upgradeLevel;           // Cấp cường hóa (= badge / cardLevel)
    private int ovr;

    // === Metadata ===
    private String cardClass;           // Class thẻ: "First", "Welcome", "Double", "Premier", "Special", "Unit"
    private String member;              // Tên thành viên (SeoYeon, HyeRin...)
    private String season;              // Season (Atom01, Binary01...)
    private String collectionNo;        // Số thẻ (e.g. "357Z")
    private String slug;
    private String backgroundColor;
    private String textColor;
    private List<String> availableTags;
    private String dimension;
    private String status;              // "AVAILABLE", "BUSY_AFK_1", etc.
    private String createdAt;           // Acquisition date

    // === Display State (Album mode) ===
    private boolean owned;              // User đã từng sở hữu chưa (Ever Owned)
    private Long userCardId;            // ID bản ghi user_cards (null nếu chưa có)

    public CardDisplayItem() {}

    // =====================================
    // FACTORY METHODS — Chuyển đổi từ Model cũ
    // =====================================

    /**
     * Chuyển đổi từ Objet (Inventory mode) sang CardDisplayItem.
     * Mọi thẻ trong kho đồ đều owned = true.
     */
    public static CardDisplayItem fromObjet(@androidx.annotation.NonNull Objet obj) {
        CardDisplayItem item = new CardDisplayItem();
        item.id = obj.getId();
        item.collectionId = obj.getCollectionId();
        item.frontImage = obj.getImageUrl();
        item.backImage = obj.getBackImageUrl();
        item.level = obj.getLevel();
        item.exp = obj.getExp();
        item.upgradeLevel = obj.getUpgradeLevel();
        item.ovr = obj.getOvr();
        item.cardClass = obj.getTypeKey();
        item.member = obj.getMember();
        item.season = obj.getSeason();
        item.collectionNo = obj.getCollectionNo();
        item.slug = obj.getSlug();
        item.backgroundColor = obj.getBackgroundColor();
        item.textColor = obj.getTextColor();
        item.availableTags = obj.getAvailableTags();
        item.dimension = obj.getDimension();
        item.status = obj.getStatus();
        item.createdAt = obj.getCreatedAt();
        item.owned = true;
        item.userCardId = obj.getId();
        return item;
    }

    /**
     * Chuyển đổi từ CollectionEntry (Album mode) sang CardDisplayItem.
     * Giữ nguyên trạng thái owned/locked từ server.
     */
    public static CardDisplayItem fromCollectionEntry(@androidx.annotation.NonNull CollectionEntry entry) {
        CardDisplayItem item = new CardDisplayItem();
        item.id = entry.getUserCardId() != null ? entry.getUserCardId() : -1L;
        item.collectionId = entry.getCollectionId();
        item.frontImage = entry.getFrontImage();
        item.backImage = entry.getBackImage();
        item.level = entry.getLevel();
        item.exp = 0;
        item.upgradeLevel = entry.getUpgradeLevel();
        item.ovr = entry.getOvr();
        item.cardClass = entry.getCardClass();
        item.member = entry.getMember();
        item.season = entry.getSeason();
        item.collectionNo = entry.getCollectionNo();
        item.slug = null;
        item.backgroundColor = entry.getBackgroundColor();
        item.textColor = null;
        item.availableTags = null;
        item.dimension = null;
        item.status = null;
        item.owned = entry.isOwned();
        item.userCardId = entry.getUserCardId();
        item.createdAt = entry.getCreatedAt();
        return item;
    }

    /**
     * Chuyển đổi từ Cache Item (Local-First) sang CardDisplayItem.
     */
    public static CardDisplayItem fromCacheItem(@androidx.annotation.NonNull com.vn.jet.mosco.utils.DatabaseLoader.UserInventoryItem cache) {
        CardDisplayItem item = new CardDisplayItem();
        item.id = cache.id;
        item.collectionId = cache.collectionId;
        item.frontImage = cache.frontImage;
        item.backImage = cache.backImage;
        item.level = cache.level;
        item.exp = cache.exp;
        item.upgradeLevel = cache.upgradeLevel;
        item.ovr = cache.ovr;
        item.cardClass = cache.cardClass;
        item.member = cache.member;
        item.season = cache.season;
        item.collectionNo = cache.collectionNo;
        item.slug = cache.slug;
        item.backgroundColor = cache.backgroundColor;
        item.textColor = cache.textColor;
        item.availableTags = cache.availableTags;
        item.dimension = cache.dimension;
        item.status = cache.status;
        item.createdAt = cache.createdAt;
        item.owned = true;
        item.userCardId = cache.id;
        return item;
    }

    // =====================================
    // HELPER — Tạo Name Tag chuẩn hóa
    // =====================================

    /**
     * Tạo chuỗi hiển thị Name Tag theo format: [Tên] [ClassPrefix][Số]
     * Ví dụ: JiYeon F503Z
     */
    @androidx.annotation.NonNull
    public String getFormattedNameTag() {
        String memberStr = member != null ? member : "";
        String colNoStr = collectionNo != null ? collectionNo : "";
        String classPrefix = "";
        if (cardClass != null && !cardClass.isEmpty()) {
            classPrefix = cardClass.substring(0, 1).toUpperCase();
        }
        String metaSuffix = (classPrefix + colNoStr).trim();
        String result = (memberStr + " " + metaSuffix).trim();
        return result.isEmpty() ? "" : result;
    }

    // =====================================
    // GETTERS & SETTERS
    // =====================================

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getCollectionId() { return collectionId; }
    public void setCollectionId(String collectionId) { this.collectionId = collectionId; }

    public String getFrontImage() { return frontImage; }
    public void setFrontImage(String frontImage) { this.frontImage = frontImage; }

    public String getBackImage() { return backImage; }
    public void setBackImage(String backImage) { this.backImage = backImage; }

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

    public List<String> getAvailableTags() { return availableTags; }
    public void setAvailableTags(List<String> availableTags) { this.availableTags = availableTags; }

    public String getDimension() { return dimension; }
    public void setDimension(String dimension) { this.dimension = dimension; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public boolean isOwned() { return owned; }
    public void setOwned(boolean owned) { this.owned = owned; }

    public Long getUserCardId() { return userCardId; }
    public void setUserCardId(Long userCardId) { this.userCardId = userCardId; }
}
