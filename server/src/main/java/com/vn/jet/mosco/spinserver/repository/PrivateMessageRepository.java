package com.vn.jet.mosco.spinserver.repository;

import com.vn.jet.mosco.spinserver.model.PrivateMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PrivateMessageRepository extends JpaRepository<PrivateMessage, Long> {

    @Query("SELECT pm FROM PrivateMessage pm WHERE (pm.senderId = :user1 AND pm.receiverId = :user2) OR (pm.senderId = :user2 AND pm.receiverId = :user1) ORDER BY pm.timestamp ASC")
    List<PrivateMessage> findChatHistory(@Param("user1") Long user1, @Param("user2") Long user2);
}
