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

    @Query("DELETE FROM private_messages WHERE senderId = :myId AND receiverId = :partnerId AND content = :content AND timestamp BETWEEN :minTs AND :maxTs")
    void deleteTentativeMessage(String myId, String partnerId, String content, long minTs, long maxTs);

    /**
     * Lấy lịch sử trò chuyện giữa người dùng hiện tại và một đối tác cụ thể.
     * Cần lọc theo cả hai hướng (Gửi và Nhận) để tạo thành một cuộc hội thoại.
     */
    @Query("SELECT * FROM private_messages WHERE " +
           "((senderId = :myId AND receiverId = :partnerId) OR " +
           "(senderId = :partnerId AND receiverId = :myId)) " +
           "AND content NOT LIKE '[SEEN]:%' " +
           "ORDER BY timestamp ASC")
    List<PrivateChatMessage> getChatHistory(String myId, String partnerId);

    @Query("DELETE FROM private_messages")
    void deleteAllMessages();

    /**
     * Lấy danh sách các cuộc hội thoại gần đây nhất của người dùng hiện tại.
     * Tại sao (WHY): Gom nhóm theo từng đối tác trò chuyện (partnerId) bằng biểu thức CASE WHEN,
     * tìm thời gian của tin nhắn mới nhất (MAX(timestamp)), sau đó INNER JOIN lại chính nó để lấy đầy đủ
     * thông tin của tin nhắn mới nhất đó nhằm hiển thị lên danh sách Inbox một cách tối ưu.
     */
    @Query("SELECT m1.* FROM private_messages m1 " +
           "INNER JOIN (" +
           "    SELECT " +
           "        CASE WHEN senderId = :myId THEN receiverId ELSE senderId END AS partnerId, " +
           "        MAX(timestamp) AS max_ts " +
           "    FROM private_messages " +
           "    WHERE (senderId = :myId OR receiverId = :myId) AND content NOT LIKE '[SEEN]:%' " +
           "    GROUP BY partnerId" +
           ") m2 ON (" +
           "    (m1.senderId = :myId AND m1.receiverId = m2.partnerId) OR " +
           "    (m1.senderId = m2.partnerId AND m1.receiverId = :myId)" +
           ") AND m1.timestamp = m2.max_ts " +
           "AND m1.content NOT LIKE '[SEEN]:%' " +
           "ORDER BY m1.timestamp DESC")
    List<PrivateChatMessage> getRecentConversations(String myId);

    /**
     * Lấy tên của đối tác từ tin nhắn họ gửi để hiển thị đúng tên trên danh sách cuộc hội thoại.
     */
    @Query("SELECT senderName FROM private_messages WHERE senderId = :partnerId LIMIT 1")
    String getPartnerName(String partnerId);

    /**
     * Lấy Avatar ID của đối tác để hiển thị đúng Avatar trên danh sách cuộc hội thoại.
     */
    @Query("SELECT avatarId FROM private_messages WHERE senderId = :partnerId LIMIT 1")
    String getPartnerAvatar(String partnerId);

    @Query("SELECT COUNT(*) FROM private_messages WHERE senderId = :partnerId AND receiverId = :myId AND isRead = 0 AND content NOT LIKE '[SEEN]:%'")
    int getUnreadCount(String myId, String partnerId);

    @Query("UPDATE private_messages SET isRead = 1 WHERE senderId = :partnerId AND receiverId = :myId AND isRead = 0 AND content NOT LIKE '[SEEN]:%'")
    void markAsRead(String myId, String partnerId);

    /**
     * Cập nhật tên và avatar của đối tác vào DB dựa trên senderId.
     * Tại sao (WHY): Khi ta gửi tin nhắn ra trước thì DB không có row nào với senderId = partnerId,
     * nên getPartnerName trả về null. Sau khi fetch từ server thì lưu vào đây để lần sau load được.
     */
    @Query("UPDATE private_messages SET senderName = :name WHERE senderId = :partnerId")
    void updatePartnerName(String partnerId, String name);

    @Query("UPDATE private_messages SET avatarId = :avatarId WHERE senderId = :partnerId")
    void updatePartnerAvatar(String partnerId, String avatarId);

    /**
     * Tại sao (WHY): Giới hạn số message mỗi conversation trong Room DB để tránh heap overflow
     * khi tích lũy quá nhiều tin nhắn theo thời gian. Giữ lại keepCount tin nhắn mới nhất,
     * xóa các tin nhắn cũ hơn. Gọi sau mỗi lần insert message thành công.
     */
    @Query("DELETE FROM private_messages WHERE " +
           "((senderId = :myId AND receiverId = :partnerId) OR (senderId = :partnerId AND receiverId = :myId)) " +
           "AND id NOT IN (" +
           "    SELECT id FROM private_messages " +
           "    WHERE ((senderId = :myId AND receiverId = :partnerId) OR (senderId = :partnerId AND receiverId = :myId)) " +
           "    ORDER BY timestamp DESC " +
           "    LIMIT :keepCount" +
           ")")
    int trimConversation(String myId, String partnerId, int keepCount);
}
