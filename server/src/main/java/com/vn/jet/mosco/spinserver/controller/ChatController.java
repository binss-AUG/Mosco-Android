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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;
import org.springframework.data.redis.core.RedisTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
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
     * WHY: Try Redis (zero disk I/O) first. If Redis is not running, fall back to
     * MySQL so chat is never interrupted — graceful degradation pattern.
     */
    @MessageMapping("/chat.private")
    public void sendPrivateMessage(@Payload com.vn.jet.mosco.spinserver.dto.PrivateChatMessage privateMessage) {
        String safeContent = HtmlUtils.htmlEscape(privateMessage.getContent());
        privateMessage.setContent(safeContent);

        long currentTimestamp = (privateMessage.getTimestamp() > 0) ? privateMessage.getTimestamp() : System.currentTimeMillis();
        privateMessage.setTimestamp(currentTimestamp);

        long senderId = Long.parseLong(privateMessage.getSenderId());
        long receiverId = Long.parseLong(privateMessage.getReceiverId());

        boolean redisSaved = false;
        try {
            Long messageId = redisTemplate.opsForValue().increment("chat:msg:id:seq");
            privateMessage.setId(String.valueOf(messageId));
            String msgJson = objectMapper.writeValueAsString(privateMessage);
            redisTemplate.opsForList().rightPush("chat:offline:" + receiverId, msgJson);
            redisSaved = true;
        } catch (Exception e) {
            log.warn("[ChatController] Redis unavailable, falling back to MySQL: {}", e.getMessage());
        }

        if (!redisSaved) {
            try {
                PrivateMessage pm = PrivateMessage.builder()
                    .senderId(senderId)
                    .receiverId(receiverId)
                    .senderName(privateMessage.getSenderName())
                    .avatarId(privateMessage.getAvatarId())
                    .content(safeContent)
                    .timestamp(currentTimestamp)
                    .build();
                PrivateMessage saved = privateMessageRepository.save(pm);
                privateMessage.setId(String.valueOf(saved.getId()));
            } catch (Exception ex) {
                log.error("[ChatController] MySQL fallback also failed: {}", ex.getMessage());
            }
        }

        try {
            coupleStreakService.recordInteraction(senderId, receiverId);
        } catch (Exception e) {
            log.error("Failed to record streak interaction: ", e);
        }

        log.info("Private message from {} to {}: {}", senderId, receiverId, safeContent);
        messagingTemplate.convertAndSend("/topic/private." + receiverId, privateMessage);
        messagingTemplate.convertAndSend("/topic/private." + senderId, privateMessage);
    }

    /**
     * WHY: Try Redis first for low-latency history. Fall back to MySQL if Redis is down.
     */
    @GetMapping("/api/chat/history")
    public com.vn.jet.mosco.spinserver.dto.ApiResponse<List<PrivateMessage>> getChatHistory(
            @RequestParam("user1") Long user1,
            @RequestParam("user2") Long user2) {
        List<PrivateMessage> history = new ArrayList<>();

        boolean redisOk = false;
        try {
            fetchOfflineMessages(user1, user2, history);
            fetchOfflineMessages(user2, user1, history);
            redisOk = true;
        } catch (Exception e) {
            log.warn("[ChatController] Redis unavailable for history, falling back to MySQL: {}", e.getMessage());
        }

        if (!redisOk) {
            try {
                List<PrivateMessage> mysqlHistory = privateMessageRepository.findChatHistory(user1, user2);
                history.addAll(mysqlHistory);
            } catch (Exception ex) {
                log.error("[ChatController] MySQL fallback for history failed: {}", ex.getMessage());
            }
        }

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
                    log.error("Failed to parse message from Redis", e);
                }
            }
        }
    }

    /**
     * WHY: If Redis is down, skip ACK gracefully — do not crash the server.
     */
    @PostMapping("/api/chat/ack")
    public com.vn.jet.mosco.spinserver.dto.ApiResponse<Void> ackMessages(
            @RequestBody List<Long> messageIds,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return com.vn.jet.mosco.spinserver.dto.ApiResponse.error(401, "Authentication required");
        }

        if (messageIds != null && !messageIds.isEmpty()) {
            try {
                String key = "chat:offline:" + userId;
                List<String> rawMsgs = redisTemplate.opsForList().range(key, 0, -1);
                if (rawMsgs != null) {
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
                log.info("ACK: removed {} messages from Redis for user {}.", messageIds.size(), userId);
            } catch (Exception e) {
                log.warn("[ChatController] Redis unavailable during ACK, skipping gracefully: {}", e.getMessage());
            }
        }
        return com.vn.jet.mosco.spinserver.dto.ApiResponse.success("Acknowledged", null);
    }
}
