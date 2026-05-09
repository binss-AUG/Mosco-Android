package com.vn.jet.mosco.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.google.gson.annotations.SerializedName;

@Entity(tableName = "user_stats")
public class UserStats {
    @PrimaryKey
    @SerializedName("id")
    private Long id;

    @SerializedName("username")
    private String username;

    @SerializedName("email")
    private String email;

    @SerializedName("ingameName")
    private String ingameName;

    @SerializedName("avatarId")
    private String avatarId;

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

    // Getters
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getIngameName() { return ingameName; }
    public String getAvatarId() { return avatarId; }
    public Long getCoins() { return coins; }
    public Long getDiamonds() { return diamonds; }
    public int getLevel() { return level; }
    public long getExp() { return exp; }
    public int getStreak() { return streak; }
    public int getBestStreak() { return bestStreak; }
    public int getStreakRestoresThisMonth() { return streakRestoresThisMonth; }

    // Setters (Bắt buộc cho Room)
    public void setId(Long id) { this.id = id; }
    public void setUsername(String username) { this.username = username; }
    public void setEmail(String email) { this.email = email; }
    public void setIngameName(String ingameName) { this.ingameName = ingameName; }
    public void setAvatarId(String avatarId) { this.avatarId = avatarId; }
    public void setCoins(Long coins) { this.coins = coins; }
    public void setDiamonds(Long diamonds) { this.diamonds = diamonds; }
    public void setLevel(int level) { this.level = level; }
    public void setExp(long exp) { this.exp = exp; }
    public void setStreak(int streak) { this.streak = streak; }
    public void setBestStreak(int bestStreak) { this.bestStreak = bestStreak; }
    public void setStreakRestoresThisMonth(int streakRestoresThisMonth) { this.streakRestoresThisMonth = streakRestoresThisMonth; }
}
