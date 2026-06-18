package com.vn.jet.mosco.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import com.vn.jet.mosco.model.AiChatMessage;
import java.util.List;

@Dao
public interface AiChatDao {
    @Query("SELECT * FROM ai_chat_messages WHERE biasId = :biasId ORDER BY timestamp ASC")
    List<AiChatMessage> getAllMessagesByBias(String biasId);

    @Insert
    void insert(AiChatMessage message);
    
    @Query("DELETE FROM ai_chat_messages WHERE biasId = :biasId")
    void clearHistoryByBias(String biasId);
}
