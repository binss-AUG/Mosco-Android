package com.vn.jet.mosco.spinserver.controller;

import com.vn.jet.mosco.spinserver.dto.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.web.util.HtmlUtils;

@Slf4j
@Controller
public class ChatController {

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
}
