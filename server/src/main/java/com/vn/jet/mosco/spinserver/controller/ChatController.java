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
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.util.HtmlUtils;
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
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final com.vn.jet.mosco.spinserver.service.AiModeratorService aiModeratorService;

    private void sendSystemError(long userId, String message) {
        com.vn.jet.mosco.spinserver.dto.ApiResponse<String> errorRes = com.vn.jet.mosco.spinserver.dto.ApiResponse.error(403, message);
        messagingTemplate.convertAndSend("/topic/errors." + userId, errorRes);
    }

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessage chatMessage) {
        long senderId = Long.parseLong(chatMessage.getSenderId());
        
        if (aiModeratorService.isBanned(senderId)) {
            long remaining = aiModeratorService.getBanRemainingSeconds(senderId);
            sendSystemError(senderId, "Bạn đang bị cấm chat. Còn lại: " + remaining + " giây.");
            return;
        }

        String content = chatMessage.getContent();
        
        // Layer 1: Regex Blacklist
        if (aiModeratorService.containsBadWords(content)) {
            aiModeratorService.applyPenalty(senderId);
            long remaining = aiModeratorService.getBanRemainingSeconds(senderId);
            sendSystemError(senderId, "Phát hiện ngôn từ độc hại (Lớp 1)! Bạn bị cấm chat " + remaining + " giây.");
            return;
        }

        // Layer 2: AI Context
        if (aiModeratorService.checkContextWithAi(content)) {
            aiModeratorService.applyPenalty(senderId);
            long remaining = aiModeratorService.getBanRemainingSeconds(senderId);
            sendSystemError(senderId, "Phát hiện nội dung vi phạm chuẩn mực (Lớp 2 AI)! Bạn bị cấm chat " + remaining + " giây.");
            return;
        }

        String safeContent = HtmlUtils.htmlEscape(content);
        chatMessage.setContent(safeContent);
        chatMessage.setTimestamp(System.currentTimeMillis());
        log.info("World Chat message from {}: {}", chatMessage.getSenderName(), safeContent);
        messagingTemplate.convertAndSend("/topic/world", chatMessage);
    }


    /**
     * Private Chat - saved directly to MySQL database.
     */
    @MessageMapping("/chat.private")
    public void sendPrivateMessage(@Payload com.vn.jet.mosco.spinserver.dto.PrivateChatMessage privateMessage) {
        long senderId = Long.parseLong(privateMessage.getSenderId());
        
        if (aiModeratorService.isBanned(senderId)) {
            long remaining = aiModeratorService.getBanRemainingSeconds(senderId);
            sendSystemError(senderId, "Bạn đang bị cấm chat. Còn lại: " + remaining + " giây.");
            return;
        }

        String content = privateMessage.getContent();
        
        if (aiModeratorService.containsBadWords(content) || aiModeratorService.checkContextWithAi(content)) {
            aiModeratorService.applyPenalty(senderId);
            long remaining = aiModeratorService.getBanRemainingSeconds(senderId);
            sendSystemError(senderId, "Hành vi chat độc hại bị phát hiện! Bạn bị cấm chat " + remaining + " giây.");
            return;
        }

        String safeContent = HtmlUtils.htmlEscape(content);
        privateMessage.setContent(safeContent);

        long currentTimestamp = (privateMessage.getTimestamp() > 0) ? privateMessage.getTimestamp() : System.currentTimeMillis();
        privateMessage.setTimestamp(currentTimestamp);

        long receiverId = Long.parseLong(privateMessage.getReceiverId());
        
        com.vn.jet.mosco.spinserver.utils.UserSessionTracker.updateActivity(senderId);

        // Phát tán Event bất đồng bộ để ghi DB và cập nhật streak ngầm dưới background
        privateMessage.setId(String.valueOf(System.currentTimeMillis())); // Tạo ID tạm thời
        eventPublisher.publishEvent(new com.vn.jet.mosco.spinserver.event.PrivateChatEvent(
            this, senderId, receiverId, privateMessage.getSenderName(),
            privateMessage.getAvatarId(), safeContent, currentTimestamp
        ));

        log.info("Private message from {} to {}: {}", senderId, receiverId, safeContent);
        messagingTemplate.convertAndSend("/topic/private." + receiverId, privateMessage);
        messagingTemplate.convertAndSend("/topic/private." + senderId, privateMessage);
    }

    /**
     * Get chat history directly from MySQL.
     */
    @GetMapping("/api/chat/history")
    public com.vn.jet.mosco.spinserver.dto.ApiResponse<List<PrivateMessage>> getChatHistory(
            @RequestParam("user1") Long user1,
            @RequestParam("user2") Long user2) {
        List<PrivateMessage> history = new ArrayList<>();

        try {
            List<PrivateMessage> mysqlHistory = privateMessageRepository.findChatHistory(user1, user2);
            history.addAll(mysqlHistory);
        } catch (Exception ex) {
            log.error("[ChatController] MySQL history fetch failed: {}", ex.getMessage());
        }

        history.sort(Comparator.comparing(PrivateMessage::getTimestamp));
        return com.vn.jet.mosco.spinserver.dto.ApiResponse.success("Success", history);
    }

    /**
     * Acknowledge messages - stub for MySQL only compatibility.
     */
    @PostMapping("/api/chat/ack")
    public com.vn.jet.mosco.spinserver.dto.ApiResponse<Void> ackMessages(
            @RequestBody List<Long> messageIds,
            @RequestAttribute("userId") Long userId) {
        return com.vn.jet.mosco.spinserver.dto.ApiResponse.success("Acknowledged", null);
    }
}
