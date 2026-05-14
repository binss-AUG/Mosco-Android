package com.vn.jet.mosco.model;

import java.util.Date;

public class WorldChatMessage {
    private String senderId;
    private String senderName;
    private String avatarId;
    private String content;
    private long timestamp;

    public WorldChatMessage(String senderId, String senderName, String avatarId, String content) {
        this.senderId = senderId;
        this.senderName = senderName;
        this.avatarId = avatarId;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    public String getSenderId() { return senderId; }
    public String getSenderName() { return senderName; }
    public String getAvatarId() { return avatarId; }
    public String getContent() { return content; }
    public long getTimestamp() { return timestamp; }
}
