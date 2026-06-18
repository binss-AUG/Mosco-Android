package com.vn.jet.mosco.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "ai_chat_messages")
public class AiChatMessage {
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    @NonNull
    public String message;
    
    public String biasId;
    
    public boolean isFromAi;
    
    public long timestamp;
    
    @androidx.room.Ignore
    public boolean isThinking = false;
    
    public AiChatMessage(String biasId, @NonNull String message, boolean isFromAi, long timestamp) {
        this.biasId = biasId;
        this.message = message;
        this.isFromAi = isFromAi;
        this.timestamp = timestamp;
    }
    
    @androidx.room.Ignore
    public AiChatMessage(boolean isThinking) {
        this.message = "";
        this.isFromAi = true;
        this.isThinking = isThinking;
    }
}
