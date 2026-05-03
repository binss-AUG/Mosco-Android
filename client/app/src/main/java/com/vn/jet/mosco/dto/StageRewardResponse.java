package com.vn.jet.mosco.dto;

import com.google.gson.annotations.SerializedName;

public class StageRewardResponse {
    @SerializedName("coins")
    private long coins;
    
    @SerializedName("diamonds")
    private long diamonds;
    
    @SerializedName("message")
    private String message;

    public long getCoins() { return coins; }
    public long getDiamonds() { return diamonds; }
    public String getMessage() { return message; }
}
