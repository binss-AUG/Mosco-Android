package com.vn.jet.mosco.spinserver.controller;

import com.vn.jet.mosco.spinserver.dto.ChatMessage;
import com.vn.jet.mosco.spinserver.model.PrivateMessage;
import com.vn.jet.mosco.spinserver.repository.PrivateMessageRepository;
import com.vn.jet.mosco.spinserver.service.CoupleStreakService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;
import org.springframework.data.redis.core.RedisTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final PrivateMessageRepository privateMessageRepository;
    private final CoupleStreakService coupleStreakService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Nhận tin nhắn từ client và broadcast tới tất cả mọi người ở topic /topic/world.
     */
    @MessageMapping("/chat.sendMessage")
    @SendTo("/topic/world")
    public ChatMessage sendMessage(@Payload ChatMessage chatMessage) {
        String safeContent = HtmlUtils.htmlEscape(chatMessage.getContent());
        chatMessage.setContent(safeContent);
        chatMessage.setTimestamp(System.currentTimeMillis());
        log.info("World Chat message from {}: {}", chatMessage.getSenderName(), safeContent);
        return chatMessage;
    }

    /**
     * Nhận tin nhắn Private từ client và gửi trực tiếp tới receiver (và sender để sync).
     */
    @MessageMapping("/chat.private")
    public void sendPrivateMessage(@Payload com.vn.jet.mosco.spinserver.dto.PrivateChatMessage privateMessage) {
        // 1. Sanitization
        String safeContent = HtmlUtils.htmlEscape(privateMessage.getContent());
        privateMessage.setContent(safeContent);
        
        // 2. Set timestamp
        long currentTimestamp = (privateMessage.getTimestamp() > 0) ? privateMessage.getTimestamp() : System.currentTimeMillis();
        privateMessage.setTimestamp(currentTimestamp);
        
        long senderId = Long.parseLong(privateMessage.getSenderId());
        long receiverId = Long.parseLong(privateMessage.getReceiverId());

        // 3. Sinh ID tin nhắn duy nhất bằng Redis Sequence (Distributed ID)
        Long messageId = redisTemplate.opsForValue().increment("chat:msg:id:seq");
        privateMessage.setId(String.valueOf(messageId));

        // 4. Lưu vào Redis Transient Queue (Không tốn Disk I/O cho MySQL)
        try {
            String msgJson = objectMapper.writeValueAsString(privateMessage);
            redisTemplate.opsForList().rightPush("chat:offline:" + receiverId, msgJson);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize private message to JSON", e);
        }

        // 5. Record Streak Interaction
        try {
            coupleStreakService.recordInteraction(senderId, receiverId);
        } catch (Exception e) {
            log.error("Failed to record streak interaction: ", e);
        }

        log.info("Private message from {} to {}: {}", privateMessage.getSenderId(), privateMessage.getReceiverId(), safeContent);
        
        messagingTemplate.convertAndSend("/topic/private." + receiverId, privateMessage);
        messagingTemplate.convertAndSend("/topic/private." + senderId, privateMessage);
    }

    /**
     * Fetch Chat History between two users from Redis In-Memory.
     */
    @GetMapping("/api/chat/history")
    public com.vn.jet.mosco.spinserver.dto.ApiResponse<List<PrivateMessage>> getChatHistory(
            @RequestParam("user1") Long user1, 
            @RequestParam("user2") Long user2) {
        List<PrivateMessage> history = new ArrayList<>();

        // Lấy tin nhắn offline của cả user1 và user2
        fetchOfflineMessages(user1, user2, history);
        fetchOfflineMessages(user2, user1, history);

        // Sắp xếp theo timestamp tăng dần
        history.sort(Comparator.comparing(PrivateMessage::getTimestamp));

        return com.vn.jet.mosco.spinserver.dto.ApiResponse.success("Success", history);
    }

    private void fetchOfflineMessages(Long ownerId, Long partnerId, List<PrivateMessage> historyList) {
        String key = "chat:offline:" + ownerId;
        List<String> rawMsgs = redisTemplate.opsForList().range(key, 0, -1);
        if (rawMsgs != null) {
            for (String raw : rawMsgs) {
                try {
                    com.vn.jet.mosco.spinserver.dto.PrivateChatMessage dto = objectMapper.readValue(raw, com.vn.jet.mosco.spinserver.dto.PrivateChatMessage.class);
                    long sender = Long.parseLong(dto.getSenderId());
                    long receiver = Long.parseLong(dto.getReceiverId());
                    if ((sender == ownerId && receiver == partnerId) || (sender == partnerId && receiver == ownerId)) {
                        PrivateMessage pm = PrivateMessage.builder()
                            .id(Long.parseLong(dto.getId()))
                            .senderId(sender)
                            .receiverId(receiver)
                            .senderName(dto.getSenderName())
                            .avatarId(dto.getAvatarId())
                            .content(dto.getContent())
                            .timestamp(dto.getTimestamp())
                            .build();
                        historyList.add(pm);
                    }
                } catch (Exception e) {
                    log.error("Lỗi parse tin nhắn từ Redis", e);
                }
            }
        }
    }

    /**
     * Xác nhận Client đã lưu thành công các tin nhắn Offline - Xóa khỏi hàng đợi Redis.
     */
    @org.springframework.web.bind.annotation.PostMapping("/api/chat/ack")
    public com.vn.jet.mosco.spinserver.dto.ApiResponse<Void> ackMessages(
            @org.springframework.web.bind.annotation.RequestBody List<Long> messageIds,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return com.vn.jet.mosco.spinserver.dto.ApiResponse.error(401, "Authentication required");
        }

        if (messageIds != null && !messageIds.isEmpty()) {
            String key = "chat:offline:" + userId;
            List<String> rawMsgs = redisTemplate.opsForList().range(key, 0, -1);
            if (rawMsgs != null) {
                // Xóa list cũ và ghi lại các tin nhắn chưa được Ack
                redisTemplate.delete(key);
                for (String raw : rawMsgs) {
                    try {
                        com.vn.jet.mosco.spinserver.dto.PrivateChatMessage dto = objectMapper.readValue(raw, com.vn.jet.mosco.spinserver.dto.PrivateChatMessage.class);
                        long msgId = Long.parseLong(dto.getId());
                        if (!messageIds.contains(msgId)) {
                            redisTemplate.opsForList().rightPush(key, raw);
                        }
                    } catch (Exception e) {
                        log.error("Failed to parse message during ACK", e);
                    }
                }
            }
            log.info("Acknowledged and removed {} synced messages from Redis for user {}.", messageIds.size(), userId);
        }
        return com.vn.jet.mosco.spinserver.dto.ApiResponse.success("Acknowledged", null);
    }
}
