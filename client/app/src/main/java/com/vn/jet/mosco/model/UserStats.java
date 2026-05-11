package com.vn.jet.mosco.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;
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

    // Các trường mới cho Profile V2 kèm Fallback an toàn (Defensive Programming)
    @SerializedName("currentTitle")
    private String currentTitle = "";

    @SerializedName("totalRolls")
    private int totalRolls = 0;

    @SerializedName("showcaseCardIds")
    private List<String> showcaseCardIds = Collections.emptyList();

    @SerializedName("bio")
    private String bio = "";

    @SerializedName("joinedDate")
    private String joinedDate = "";

    @SerializedName("collectionProgress")
    private int collectionProgress = 0;

    @SerializedName("badges")
    private List<String> badges = Collections.emptyList();

    @SerializedName("likesCount")
    private int likesCount = 0;

    @SerializedName("friendsCount")
    private int friendsCount = 0;

    @SerializedName("liked")
    private boolean liked = false;

    @SerializedName("friendshipStatus")
    private int friendshipStatus = 0; // 0: None, 1: Pending, 2: Friends

    @SerializedName("avatarCropParams")
    private String avatarCropParams = ""; // Metadata to re-apply crop if local file is missing

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
    public String getCurrentTitle() { return currentTitle; }
    public int getTotalRolls() { return totalRolls; }
    public List<String> getShowcaseCardIds() { return showcaseCardIds; }
    public String getBio() { return bio; }
    public String getJoinedDate() { return joinedDate; }
    public int getCollectionProgress() { return collectionProgress; }
    public List<String> getBadges() { return badges; }

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
    public void setCurrentTitle(String currentTitle) { this.currentTitle = currentTitle; }
    public void setTotalRolls(int totalRolls) { this.totalRolls = totalRolls; }
    public void setShowcaseCardIds(List<String> showcaseCardIds) { this.showcaseCardIds = showcaseCardIds; }
    public void setBio(String bio) { this.bio = bio; }
    public void setJoinedDate(String joinedDate) { this.joinedDate = joinedDate; }
    public void setCollectionProgress(int collectionProgress) { this.collectionProgress = collectionProgress; }
    public void setBadges(List<String> badges) { this.badges = badges; }
    public int getLikesCount() { return likesCount; }
    public void setLikesCount(int likesCount) { this.likesCount = likesCount; }
    public int getFriendsCount() { return friendsCount; }
    public void setFriendsCount(int friendsCount) { this.friendsCount = friendsCount; }
    public boolean isLiked() { return liked; }
    public void setLiked(boolean liked) { this.liked = liked; }
    public int getFriendshipStatus() { return friendshipStatus; }
    public void setFriendshipStatus(int friendshipStatus) { this.friendshipStatus = friendshipStatus; }

    public String getAvatarCropParams() { return avatarCropParams; }
    public void setAvatarCropParams(String avatarCropParams) { this.avatarCropParams = avatarCropParams; }
}
