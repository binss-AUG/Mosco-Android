package com.vn.jet.mosco.model;

import com.google.gson.annotations.SerializedName;

public class UserStats {
    @SerializedName("id")
    private Long id;

    @SerializedName("username")
    private String username;

    @SerializedName("email")
    private String email;

    @SerializedName("coins")
    private Long coins;

    @SerializedName("diamonds")
    private Long diamonds;

    @SerializedName("level")
    private int level;

    @SerializedName("exp")
    private long exp;

    @SerializedName("streak")
    private int streak;

    @SerializedName("bestStreak")
    private int bestStreak;

    @SerializedName("streakRestoresThisMonth")
    private int streakRestoresThisMonth;

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public Long getCoins() { return coins; }
    public Long getDiamonds() { return diamonds; }
    public int getLevel() { return level; }
    public long getExp() { return exp; }
    public int getStreak() { return streak; }
    public int getBestStreak() { return bestStreak; }
    public int getStreakRestoresThisMonth() { return streakRestoresThisMonth; }
}
