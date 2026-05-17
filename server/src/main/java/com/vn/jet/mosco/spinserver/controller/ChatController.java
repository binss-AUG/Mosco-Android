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

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final PrivateMessageRepository privateMessageRepository;
    private final CoupleStreakService coupleStreakService;

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

        // 3. Save to MySQL DB
        try {
            PrivateMessage dbMessage = PrivateMessage.builder()
                .senderId(senderId)
                .receiverId(receiverId)
                .senderName(privateMessage.getSenderName())
                .avatarId(privateMessage.getAvatarId())
                .content(safeContent)
                .timestamp(currentTimestamp)
                .build();
            privateMessageRepository.save(dbMessage);
        } catch (Exception e) {
            log.error("Failed to save private message to DB: ", e);
        }

        // 4. Record Streak Interaction (NEW)
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
     * Fetch Chat History between two users.
     */
    @GetMapping("/api/chat/history")
    public com.vn.jet.mosco.spinserver.dto.ApiResponse<List<PrivateMessage>> getChatHistory(
            @RequestParam("user1") Long user1, 
            @RequestParam("user2") Long user2) {
        List<PrivateMessage> history = privateMessageRepository.findChatHistory(user1, user2);
        return com.vn.jet.mosco.spinserver.dto.ApiResponse.success("Success", history);
    }

    /**
     * Xác nhận Client đã lưu thành công các tin nhắn Offline.
     */
    @org.springframework.web.bind.annotation.PostMapping("/api/chat/ack")
    public com.vn.jet.mosco.spinserver.dto.ApiResponse<Void> ackMessages(@org.springframework.web.bind.annotation.RequestBody List<Long> messageIds) {
        if (messageIds != null && !messageIds.isEmpty()) {
            privateMessageRepository.deleteAllById(messageIds);
            log.info("Acknowledged and deleted {} synced messages from Server DB.", messageIds.size());
        }
        return com.vn.jet.mosco.spinserver.dto.ApiResponse.success("Acknowledged", null);
    }
}
