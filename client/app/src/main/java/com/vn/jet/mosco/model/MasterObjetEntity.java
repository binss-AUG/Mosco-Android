package com.vn.jet.mosco.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * MasterObjetEntity - Chứa Metadata gốc của thẻ bài (Galactic Master Data).
 * Dữ liệu này được đồng bộ từ Server (database.json) vào Room để phục vụ Local-First.
 * Tránh việc phải parse JSON hàng chục MB mỗi lần vào kho đồ.
 */
@Entity(tableName = "master_objets")
public class MasterObjetEntity {
    @PrimaryKey
    @NonNull
    private String collectionId; // VD: "S1-Member-001"
    
    private String memberName;
    private String seasonName;
    private String rarityClass;
    private String frontImageId;
    private String backImageId;
    private Integer baseOvr;
    private Integer totalCardCount; // Số lượng thẻ này đã được phát hành (optional)

    public MasterObjetEntity() {}

    @NonNull
    public String getCollectionId() { return collectionId; }
    public void setCollectionId(@NonNull String collectionId) { this.collectionId = collectionId; }

    public String getMemberName() { return memberName; }
    public void setMemberName(String memberName) { this.memberName = memberName; }

    public String getSeasonName() { return seasonName; }
    public void setSeasonName(String seasonName) { this.seasonName = seasonName; }

    public String getRarityClass() { return rarityClass; }
    public void setRarityClass(String rarityClass) { this.rarityClass = rarityClass; }

    public String getFrontImageId() { return frontImageId; }
    public void setFrontImageId(String frontImageId) { this.frontImageId = frontImageId; }

    public String getBackImageId() { return backImageId; }
    public void setBackImageId(String backImageId) { this.backImageId = backImageId; }

    public Integer getBaseOvr() { return baseOvr; }
    public void setBaseOvr(Integer baseOvr) { this.baseOvr = baseOvr; }

    public Integer getTotalCardCount() { return totalCardCount; }
    public void setTotalCardCount(Integer totalCardCount) { this.totalCardCount = totalCardCount; }
}
