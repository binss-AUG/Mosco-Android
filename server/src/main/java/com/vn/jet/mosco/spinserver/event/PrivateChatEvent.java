package com.vn.jet.mosco.spinserver.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Sự kiện gửi tin nhắn riêng tư.
 * Tại sao (WHY): Sử dụng cơ chế Spring ApplicationEvent giúp tách biệt (decouple) luồng WebSocket 
 * nhận tin nhắn và luồng ghi Database/xử lý Streak, ngăn ngừa blocking thread.
 */
@Getter
public class PrivateChatEvent extends ApplicationEvent {
    private final Long senderId;
    private final Long receiverId;
    private final String senderName;
    private final String avatarId;
    private final String content;
    private final long msgTimestamp;

    public PrivateChatEvent(Object source, Long senderId, Long receiverId, String senderName, String avatarId, String content, long msgTimestamp) {
        super(source);
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.senderName = senderName;
        this.avatarId = avatarId;
        this.content = content;
        this.msgTimestamp = msgTimestamp;
    }
}
