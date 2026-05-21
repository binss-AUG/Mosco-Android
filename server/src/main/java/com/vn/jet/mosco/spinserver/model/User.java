package com.vn.jet.mosco.spinserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @JsonIgnore
    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = true, unique = true)
    private String ingameName;

    @Column(nullable = false)
    private Long coins = 0L;

    @Column(nullable = false)
    private Long diamonds = 0L;

    // Tổng số Diamond đã từng sở hữu (tích lũy) — dùng cho Ranking Wealth
    @Column(nullable = false)
    private Long totalDiamonds = 0L;

    // Cấp độ người chơi — dùng cho Ranking
    @Column(nullable = false)
    private int level = 1;

    // Kinh nghiệm tích lũy — dùng cho hệ thống Level Up
    @Column(nullable = false)
    private long exp = 0L;

    // Mã Objet đang chọn làm Avatar (collectionId)
    @Column(nullable = false)
    private String avatarId = "1";

    @Column(length = 255)
    private String bio;

    @Column(length = 255)
    private String avatarCropParams; // Metadata cho việc crop avatar (Survive Reinstall)

    @Column(length = 255)
    private String activeFormation = "null,null,null,null,null,null";

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_showcase", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "card_id")
    @OrderColumn(name = "slot_index")
    private java.util.List<String> showcaseCardIds = new java.util.ArrayList<>(java.util.Arrays.asList(null, null, null, null));

    @Column(length = 800)
    private String activeToken;

    @Column(nullable = false)
    private int streak = 0;

    @JsonProperty("bestStreak")
    @Column(nullable = false)
    private int bestStreak = 0;

    @Column(nullable = false)
    private int streakRestoresThisMonth = 0;

    @Column
    private Integer lastRestoreMonth = 0; // Lưu tháng cuối cùng khôi phục để reset số lượt free

    @Column
    private java.time.LocalDateTime lastLoginAt;

    @Column(nullable = false)
    private int likesCount = 0;

    @Column(nullable = false)
    private int friendsCount = 0;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_unlocked_collections", 
                     joinColumns = @JoinColumn(name = "user_id"),
                     indexes = {@Index(name = "idx_unlocked_coll_user", columnList = "user_id")})
    @Column(name = "collection_id", nullable = false)
    private Set<String> unlockedCollections = new HashSet<>();

    // Các trường động (Transient) hỗ trợ trả về trạng thái tương tác xã hội cho Client
    @Transient
    private boolean liked = false;

    @Transient
    private int friendshipStatus = 0; // 0: None, 1: Pending, 2: Friends

    @Transient
    private boolean online = false;

    public User() {}

    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public Long getCoins() { return coins; }
    public void setCoins(Long coins) { this.coins = coins; }

    public Long getDiamonds() { return diamonds; }
    public void setDiamonds(Long diamonds) { this.diamonds = diamonds; }

    public Long getTotalDiamonds() { return totalDiamonds; }
    public void setTotalDiamonds(Long totalDiamonds) { this.totalDiamonds = totalDiamonds; }

    public String getIngameName() { return ingameName; }
    public void setIngameName(String ingameName) { this.ingameName = ingameName; }

    // Công thức Level chuẩn: (EXP / 1000) + 1
    public int getLevel() { 
        return (int) (this.exp / 1000) + 1; 
    }
    public void setLevel(int level) { this.level = level; }

    public long getExp() { return exp; }
    public void setExp(long exp) { this.exp = exp; }

    public String getAvatarId() { return avatarId; }
    public void setAvatarId(String avatarId) { this.avatarId = avatarId; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getActiveFormation() { return activeFormation; }
    public void setActiveFormation(String activeFormation) { this.activeFormation = activeFormation; }

    public java.util.List<String> getShowcaseCardIds() { return showcaseCardIds; }
    public void setShowcaseCardIds(java.util.List<String> showcaseCardIds) { this.showcaseCardIds = showcaseCardIds; }

    public String getActiveToken() { return activeToken; }
    public void setActiveToken(String activeToken) { this.activeToken = activeToken; }

    public Set<String> getUnlockedCollections() { return unlockedCollections; }
    public void setUnlockedCollections(Set<String> unlockedCollections) { this.unlockedCollections = unlockedCollections; }

    public int getStreak() { return streak; }
    public void setStreak(int streak) { 
        this.streak = streak; 
        // LUÔN ĐẢM BẢO BEST STREAK CẬP NHẬT (Auto-Repair logic)
        if (this.streak > this.bestStreak) {
            this.bestStreak = this.streak;
            System.out.println(">>> [STREAK] Record Updated! New Best: " + this.bestStreak);
        }
        
        // Đảm bảo nếu có streak thì record không được bằng 0
        if (this.streak > 0 && this.bestStreak == 0) {
            this.bestStreak = this.streak;
            System.out.println(">>> [STREAK] Emergency Repair: Best streak was 0, fixed to " + this.streak);
        }
    }

    public int getBestStreak() { return bestStreak; }
    public void setBestStreak(int bestStreak) { this.bestStreak = bestStreak; }

    public int getStreakRestoresThisMonth() { return streakRestoresThisMonth; }
    public void setStreakRestoresThisMonth(int streakRestoresThisMonth) { this.streakRestoresThisMonth = streakRestoresThisMonth; }

    public Integer getLastRestoreMonth() { return lastRestoreMonth; }
    public void setLastRestoreMonth(Integer lastRestoreMonth) { this.lastRestoreMonth = lastRestoreMonth; }

    public java.time.LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(java.time.LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public int getLikesCount() { return likesCount; }
    public void setLikesCount(int likesCount) { this.likesCount = likesCount; }

    public int getFriendsCount() { return friendsCount; }
    public void setFriendsCount(int friendsCount) { this.friendsCount = friendsCount; }

    public String getAvatarCropParams() { return avatarCropParams; }
    public void setAvatarCropParams(String avatarCropParams) { this.avatarCropParams = avatarCropParams; }

    public boolean isLiked() { return liked; }
    public void setLiked(boolean liked) { this.liked = liked; }

    public int getFriendshipStatus() { return friendshipStatus; }
    public void setFriendshipStatus(int friendshipStatus) { this.friendshipStatus = friendshipStatus; }

    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }
}
