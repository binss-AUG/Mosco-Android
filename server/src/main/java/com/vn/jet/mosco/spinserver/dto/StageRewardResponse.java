package com.vn.jet.mosco.spinserver.dto;

public class StageRewardResponse {
    private long coins;
    private long diamonds;
    private String message;

    public StageRewardResponse() {}

    public StageRewardResponse(long coins, long diamonds, String message) {
        this.coins = coins;
        this.diamonds = diamonds;
        this.message = message;
    }

    // Getters and Setters
    public long getCoins() { return coins; }
    public void setCoins(long coins) { this.coins = coins; }

    public long getDiamonds() { return diamonds; }
    public void setDiamonds(long diamonds) { this.diamonds = diamonds; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
