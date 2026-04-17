package com.vn.jet.mosco.spinserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    private String activeFormation = "null,null,null,null,null,null";

    @Column(length = 800)
    private String activeToken;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_unlocked_collections", 
                     joinColumns = @JoinColumn(name = "user_id"),
                     indexes = {@Index(name = "idx_unlocked_coll_user", columnList = "user_id")})
    @Column(name = "collection_id", nullable = false)
    private Set<String> unlockedCollections = new HashSet<>();

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

    public String getActiveFormation() { return activeFormation; }
    public void setActiveFormation(String activeFormation) { this.activeFormation = activeFormation; }

    public String getActiveToken() { return activeToken; }
    public void setActiveToken(String activeToken) { this.activeToken = activeToken; }

    public Set<String> getUnlockedCollections() { return unlockedCollections; }
    public void setUnlockedCollections(Set<String> unlockedCollections) { this.unlockedCollections = unlockedCollections; }
}
