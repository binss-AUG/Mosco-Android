package com.vn.jet.mosco.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Ignore;

/**
 * Entity for Private Chat Messages (Local-First).
 * Stores messages exchanged between the current user and their friends.
 */
@Entity(tableName = "private_messages")
public class PrivateChatMessage {
    @PrimaryKey(autoGenerate = true)
    private long id;
    
    private String senderId;
    private String receiverId;
    private String senderName;
    private String avatarId;
    private String content;
    private long timestamp;
    
    @Ignore
    private String partnerName;
    @Ignore
    private String partnerAvatar;
    
    public PrivateChatMessage() {}

    @Ignore
    public PrivateChatMessage(String senderId, String receiverId, String senderName, String avatarId, String content) {
        this(senderId, receiverId, senderName, avatarId, content, System.currentTimeMillis());
    }

    @Ignore
    public PrivateChatMessage(String senderId, String receiverId, String senderName, String avatarId, String content, long timestamp) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.senderName = senderName;
        this.avatarId = avatarId;
        this.content = content;
        this.timestamp = timestamp;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getReceiverId() { return receiverId; }
    public void setReceiverId(String receiverId) { this.receiverId = receiverId; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getAvatarId() { return avatarId; }
    public void setAvatarId(String avatarId) { this.avatarId = avatarId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getPartnerName() { return partnerName; }
    public void setPartnerName(String partnerName) { this.partnerName = partnerName; }

    public String getPartnerAvatar() { return partnerAvatar; }
    public void setPartnerAvatar(String partnerAvatar) { this.partnerAvatar = partnerAvatar; }
}
