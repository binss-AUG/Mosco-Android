package com.vn.jet.mosco.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entity lưu trữ dữ liệu thẻ bài ở Client (Room DB).
 * Sử dụng kỹ thuật Denormalization để tối ưu tốc độ render UI.
 */
@Entity(tableName = "cards", indices = {
        @androidx.room.Index(value = {"memberName"}),
        @androidx.room.Index(value = {"seasonName"})
})
public class CardEntity {
    @PrimaryKey
    @NonNull
    private String id;
    private String memberName;
    private String seasonName;
    private String rarityClass;
    private String frontImageId;
    private String backImageId;
    private int baseOvr;
    private int upgradeLevel;

    public CardEntity() {}

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

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

    public int getBaseOvr() { return baseOvr; }
    public void setBaseOvr(int baseOvr) { this.baseOvr = baseOvr; }

    public int getUpgradeLevel() { return upgradeLevel; }
    public void setUpgradeLevel(int upgradeLevel) { this.upgradeLevel = upgradeLevel; }
}
