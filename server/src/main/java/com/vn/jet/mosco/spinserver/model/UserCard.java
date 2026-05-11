package com.vn.jet.mosco.spinserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "user_cards")
public class UserCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String collectionId;

    @Column(nullable = false)
    private int level = 1;

    @Column(nullable = false)
    private int exp = 0;

    @Column(nullable = false)
    private int upgradeLevel = 1;

    @Column(nullable = true)
    private String uuid;

    @Column(name = "created_at", nullable = true, updatable = false)
    @org.hibernate.annotations.CreationTimestamp
    private java.time.LocalDateTime createdAt;

    public UserCard() {}

    public UserCard(User user, String collectionId, int level, int exp, int upgradeLevel) {
        this.user = user;
        this.collectionId = collectionId;
        this.uuid = collectionId; // Fallback: collectionId thường chứa UUID từ PackService
        this.level = level;
        this.exp = exp;
        this.upgradeLevel = upgradeLevel;
        this.status = "AVAILABLE";
    }

    @Column(nullable = false)
    private String status = "AVAILABLE";

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getCollectionId() { return collectionId; }
    public void setCollectionId(String collectionId) { this.collectionId = collectionId; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public int getExp() { return exp; }
    public void setExp(int exp) { this.exp = exp; }

    public int getUpgradeLevel() { return upgradeLevel; }
    public void setUpgradeLevel(int upgradeLevel) { this.upgradeLevel = upgradeLevel; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
}
