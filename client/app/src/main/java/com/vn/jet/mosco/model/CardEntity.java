package com.vn.jet.mosco.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entity lưu trữ dữ liệu thẻ bài ở Client (Room DB).
 * Sử dụng kỹ thuật Denormalization để tối ưu tốc độ render UI.
 */
@Entity(tableName = "cards", indices = {
        @androidx.room.Index(value = {"seasonName"}, name = "idx_season"),
        @androidx.room.Index(value = {"memberName"}, name = "idx_member")
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
    private Integer baseOvr;
    private Integer upgradeLevel;

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

    public Integer getBaseOvr() { return baseOvr; }
    public void setBaseOvr(Integer baseOvr) { this.baseOvr = baseOvr; }

    public Integer getUpgradeLevel() { return upgradeLevel; }
    public void setUpgradeLevel(Integer upgradeLevel) { this.upgradeLevel = upgradeLevel; }
}
