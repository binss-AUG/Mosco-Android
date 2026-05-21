package com.vn.jet.mosco.spinserver.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket Rate Limit Interceptor.
 * Tại sao (WHY): Giới hạn tần suất gửi tin nhắn qua WebSocket STOMP dựa trên Session ID của người dùng.
 * Nếu phát hiện spam (tần suất gửi < 200ms giữa 2 tin nhắn, tương đương > 5 tin nhắn/giây), 
 * tin nhắn sẽ bị chặn đứng (preSend trả về null), tránh gây quá tải hệ thống.
 */
@Slf4j
@Component
public class WebSocketRateLimitInterceptor implements ChannelInterceptor {

    private final ConcurrentHashMap<String, Long> lastMessageTimes = new ConcurrentHashMap<>();
    private static final long MIN_INTERVAL_MS = 200; // Khoảng cách tối thiểu giữa 2 tin nhắn là 200ms (tối đa 5 tin/giây)

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.wrap(message);

        if (SimpMessageType.MESSAGE.equals(headerAccessor.getMessageType())) {
            String destination = headerAccessor.getDestination();

            // Áp dụng giới hạn cho các endpoint gửi tin nhắn chat
            if (destination != null && (destination.equals("/app/chat.private") || destination.equals("/app/chat.sendMessage"))) {
                String sessionId = headerAccessor.getSessionId();
                if (sessionId != null) {
                    long currentTime = System.currentTimeMillis();
                    Long lastTime = lastMessageTimes.get(sessionId);

                    if (lastTime != null) {
                        long interval = currentTime - lastTime;
                        if (interval < MIN_INTERVAL_MS) {
                            log.warn("[WS-RATE-LIMIT] Session {} spamming! Destination: {}. Interval: {}ms. Message rejected.", 
                                    sessionId, destination, interval);
                            // Trả về null để hủy bỏ việc truyền gửi tin nhắn này
                            return null;
                        }
                    }
                    lastMessageTimes.put(sessionId, currentTime);
                }
            }
        }
        return message;
    }
}
