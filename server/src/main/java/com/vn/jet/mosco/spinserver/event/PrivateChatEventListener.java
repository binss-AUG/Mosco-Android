package com.vn.jet.mosco.spinserver.event;

import com.vn.jet.mosco.spinserver.model.PrivateMessage;
import com.vn.jet.mosco.spinserver.repository.PrivateMessageRepository;
import com.vn.jet.mosco.spinserver.service.CoupleStreakService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Trình xử lý sự kiện tin nhắn riêng tư bất đồng bộ.
 * Tại sao (WHY): Chạy các thao tác DB I/O (lưu tin nhắn, cập nhật streak cặp đôi) 
 * trên luồng ThreadPool bất đồng bộ 'chatAsyncExecutor' giúp giải phóng nhanh luồng WebSocket chính,
 * ngăn ngừa triệt để cạn kiệt kết nối DB (HikariCP) và deadlock.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PrivateChatEventListener {

    private final PrivateMessageRepository privateMessageRepository;
    private final CoupleStreakService coupleStreakService;

    @Async("chatAsyncExecutor")
    @EventListener
    public void handlePrivateChatEvent(PrivateChatEvent event) {
        log.debug("Processing private chat event asynchronously for sender {} and receiver {}", 
                event.getSenderId(), event.getReceiverId());

        // 1. Lưu tin nhắn chat vào database ngầm
        try {
            PrivateMessage pm = PrivateMessage.builder()
                .senderId(event.getSenderId())
                .receiverId(event.getReceiverId())
                .senderName(event.getSenderName())
                .avatarId(event.getAvatarId())
                .content(event.getContent())
                .timestamp(event.getMsgTimestamp())
                .build();
            privateMessageRepository.save(pm);
            log.debug("Private message saved asynchronously to DB");
        } catch (Exception ex) {
            log.error("[PrivateChatEventListener] MySQL save failed: {}", ex.getMessage());
        }

        // 2. Cập nhật streak của cặp đôi ngầm
        try {
            coupleStreakService.recordInteraction(event.getSenderId(), event.getReceiverId());
        } catch (Exception e) {
            log.error("[PrivateChatEventListener] Failed to record streak interaction: ", e);
        }
    }
}
