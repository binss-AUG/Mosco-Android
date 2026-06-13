package com.vn.jet.mosco.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.vn.jet.mosco.model.CardEntity;
import com.vn.jet.mosco.model.UserStats;

import androidx.room.TypeConverters;

@Database(entities = {CardEntity.class, UserStats.class, com.vn.jet.mosco.model.MasterObjetEntity.class, com.vn.jet.mosco.model.PrivateChatMessage.class}, version = 10, exportSchema = false)
@TypeConverters({ShowcaseConverter.class})
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;

    public abstract CardDao cardDao();
    public abstract UserStatsDao userStatsDao();
    public abstract MasterObjetDao masterObjetDao();
    public abstract MessageDao messageDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    // Nuclear Reset: Nếu nâng cấp version, ta xóa DB cũ để tránh lỗi schema mismatch
                    android.content.SharedPreferences prefs = context.getSharedPreferences("db_prefs", Context.MODE_PRIVATE);
                    if (prefs.getInt("db_ver", 0) < 10) {
                        context.deleteDatabase("mosco_db");
                        prefs.edit().putInt("db_ver", 10).apply();
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
