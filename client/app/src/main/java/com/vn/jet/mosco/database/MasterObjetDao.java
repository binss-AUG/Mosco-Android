package com.vn.jet.mosco.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.vn.jet.mosco.model.MasterObjetEntity;

import java.util.List;

@Dao
public interface MasterObjetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<MasterObjetEntity> objets);

    @Query("SELECT * FROM master_objets WHERE collectionId = :id LIMIT 1")
    MasterObjetEntity findById(String id);

    @Query("SELECT * FROM master_objets")
    List<MasterObjetEntity> getAll();

    @Query("SELECT COUNT(*) FROM master_objets")
    int getCount();

    @Query("DELETE FROM master_objets")
    void deleteAll();

    @Query("SELECT DISTINCT seasonName FROM master_objets ORDER BY seasonName ASC")
    List<String> getUniqueSeasons();

    @Query("SELECT DISTINCT rarityClass FROM master_objets ORDER BY rarityClass ASC")
    List<String> getUniqueClasses();

    @Query("SELECT memberName, frontImageId FROM master_objets GROUP BY memberName ORDER BY memberName ASC")
    List<MemberAvatar> getUniqueMembers();

    class MemberAvatar {
        public String memberName;
        public String frontImageId;
    }

    @Query("SELECT frontImageId FROM master_objets WHERE memberName LIKE '%' || :memberName || '%' AND rarityClass = 'Premier' ORDER BY collectionId DESC LIMIT 1")
    String getLatestPremierImageByMember(String memberName);
}
