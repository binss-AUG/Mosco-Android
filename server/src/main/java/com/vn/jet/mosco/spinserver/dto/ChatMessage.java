package com.vn.jet.mosco.spinserver.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatMessage {
    private String senderId;
    private String senderName;
    private String avatarId;
    private String content;
    private long timestamp;
}
