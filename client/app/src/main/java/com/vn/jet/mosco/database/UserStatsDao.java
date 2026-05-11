package com.vn.jet.mosco.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.vn.jet.mosco.model.UserStats;

@Dao
public interface UserStatsDao {
    @Query("SELECT * FROM user_stats WHERE id = :userId")
    LiveData<UserStats> getUserStats(Long userId);

    @Query("SELECT * FROM user_stats WHERE id = :userId")
    UserStats getUserStatsSync(Long userId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertUserStats(UserStats userStats);

    @Query("DELETE FROM user_stats WHERE id = :userId")
    void deleteUserStats(Long userId);
}
