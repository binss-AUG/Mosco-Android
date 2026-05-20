package com.vn.jet.mosco.model;

import androidx.room.Embedded;

public class ConversationSummary {
    @Embedded
    private PrivateChatMessage lastMessage;
    
    private String partnerId;
    private String partnerName;
    private String partnerAvatar;
    private int unreadCount;

    public ConversationSummary() {}

    public PrivateChatMessage getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(PrivateChatMessage lastMessage) {
        this.lastMessage = lastMessage;
    }

    public String getPartnerId() {
        return partnerId;
    }

    public void setPartnerId(String partnerId) {
        this.partnerId = partnerId;
    }

    public String getPartnerName() {
        return partnerName;
    }

    public void setPartnerName(String partnerName) {
        this.partnerName = partnerName;
    }

    public String getPartnerAvatar() {
        return partnerAvatar;
    }

    public void setPartnerAvatar(String partnerAvatar) {
        this.partnerAvatar = partnerAvatar;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }
}
