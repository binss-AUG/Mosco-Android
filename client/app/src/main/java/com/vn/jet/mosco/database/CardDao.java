package com.vn.jet.mosco.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.vn.jet.mosco.model.CardEntity;

import java.util.List;

@Dao
public interface CardDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(CardEntity card);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<CardEntity> cards);

    @Query("SELECT * FROM cards ORDER BY baseOvr DESC")
    LiveData<List<CardEntity>> getAllCards();

    @Query("SELECT * FROM cards WHERE memberName = :memberName")
    LiveData<List<CardEntity>> getCardsByMember(String memberName);

    @Query("SELECT * FROM cards WHERE id = :id")
    CardEntity getCardById(String id);

    @Query("SELECT DISTINCT memberName FROM cards ORDER BY memberName ASC")
    List<String> getOwnedMembersSync();
}
