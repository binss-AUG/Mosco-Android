package com.vn.jet.mosco.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.vn.jet.mosco.model.PrivateChatMessage;

import java.util.List;

@Dao
public interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMessage(PrivateChatMessage message);

    /**
     * Lấy lịch sử trò chuyện giữa người dùng hiện tại và một đối tác cụ thể.
     * Cần lọc theo cả hai hướng (Gửi và Nhận) để tạo thành một cuộc hội thoại.
     */
    @Query("SELECT * FROM private_messages WHERE " +
           "(senderId = :myId AND receiverId = :partnerId) OR " +
           "(senderId = :partnerId AND receiverId = :myId) " +
           "ORDER BY timestamp ASC")
    List<PrivateChatMessage> getChatHistory(String myId, String partnerId);

    @Query("DELETE FROM private_messages")
    void deleteAllMessages();
}
