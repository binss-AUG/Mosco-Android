package com.vn.jet.mosco.spinserver.controller;

import com.vn.jet.mosco.spinserver.dto.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.vn.jet.mosco.spinserver.repository.PrivateMessageRepository;
import com.vn.jet.mosco.spinserver.model.PrivateMessage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;
import java.util.List;

@Slf4j
@RestController
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final PrivateMessageRepository privateMessageRepository;

    public ChatController(SimpMessagingTemplate messagingTemplate, PrivateMessageRepository privateMessageRepository) {
        this.messagingTemplate = messagingTemplate;
        this.privateMessageRepository = privateMessageRepository;
    }

    /**
     * Nhận tin nhắn từ client và broadcast tới tất cả mọi người ở topic /topic/world.
     * Áp dụng Sanitization để chống XSS Injection.
     */
    @MessageMapping("/chat.sendMessage")
    @SendTo("/topic/world")
    public ChatMessage sendMessage(@Payload ChatMessage chatMessage) {
        // 1. Sanitization: Loại bỏ hoặc encode các ký tự HTML nguy hiểm
        String safeContent = HtmlUtils.htmlEscape(chatMessage.getContent());
        chatMessage.setContent(safeContent);
        
        // 2. Set server-side timestamp
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
        
        // 3. Save to MySQL DB for Offline Persistence
        try {
            PrivateMessage dbMessage = PrivateMessage.builder()
                .senderId(Long.parseLong(privateMessage.getSenderId()))
                .receiverId(Long.parseLong(privateMessage.getReceiverId()))
                .senderName(privateMessage.getSenderName())
                .avatarId(privateMessage.getAvatarId())
                .content(safeContent)
                .timestamp(currentTimestamp)
                .build();
            privateMessageRepository.save(dbMessage);
        } catch (Exception e) {
            log.error("Failed to save private message to DB: ", e);
        }

        log.info("Private message from {} to {}: {}", privateMessage.getSenderId(), privateMessage.getReceiverId(), safeContent);
        
        // 4. Gửi tới topic của người nhận
        messagingTemplate.convertAndSend("/topic/private." + privateMessage.getReceiverId(), privateMessage);
        
        // 5. (Tùy chọn) Gửi lại cho người gửi nếu họ đang online ở thiết bị khác để sync
        messagingTemplate.convertAndSend("/topic/private." + privateMessage.getSenderId(), privateMessage);
    }

    /**
     * Fetch Chat History between two users.
     */
    @GetMapping("/api/chat/history")
    public com.vn.jet.mosco.spinserver.dto.ApiResponse<List<PrivateMessage>> getChatHistory(
            @RequestParam("user1") Long user1, 
            @RequestParam("user2") Long user2) {
        // Lấy toàn bộ tin nhắn chờ giữa 2 người
        List<PrivateMessage> history = privateMessageRepository.findChatHistory(user1, user2);
        return com.vn.jet.mosco.spinserver.dto.ApiResponse.success("Success", history);
    }

    /**
     * Xác nhận Client đã lưu thành công các tin nhắn Offline, Server sẽ xóa chúng.
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
