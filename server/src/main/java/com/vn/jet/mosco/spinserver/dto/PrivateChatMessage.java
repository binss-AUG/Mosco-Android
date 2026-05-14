package com.vn.jet.mosco.spinserver.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrivateChatMessage {
    private String id;
    private String senderId;
    private String receiverId;
    private String senderName;
    private String avatarId;
    private String content;
    private long timestamp;
}
