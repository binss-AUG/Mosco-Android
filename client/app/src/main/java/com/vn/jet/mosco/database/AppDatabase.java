package com.vn.jet.mosco.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.vn.jet.mosco.model.CardEntity;
import com.vn.jet.mosco.model.UserStats;

@Database(entities = {CardEntity.class, UserStats.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;

    public abstract CardDao cardDao();
    public abstract UserStatsDao userStatsDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    // Nuclear Reset: Nếu nâng cấp lên Version 2, ta xóa DB cũ để tránh lỗi schema mismatch với Starter Pack
                    android.content.SharedPreferences prefs = context.getSharedPreferences("db_prefs", Context.MODE_PRIVATE);
                    if (prefs.getInt("db_ver", 0) < 2) {
                        context.deleteDatabase("mosco_db");
                        prefs.edit().putInt("db_ver", 2).apply();
                    }

                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "mosco_db")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
