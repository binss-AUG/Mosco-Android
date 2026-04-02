package com.vn.jet.mosco.spinserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "gacha_history", indexes = {
    @Index(name = "idx_gacha_history_user", columnList = "user_id"),
    @Index(name = "idx_gacha_history_rolled", columnList = "rolledAt")
})
public class GachaHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String itemId;

    @Column(nullable = false, length = 50)
    private String rarity;

    @Column(nullable = false)
    private int quantity = 1;

    @Column(nullable = false)
    private LocalDateTime rolledAt;

    @Column(length = 100)
    private String packCode;

    @Column(nullable = false, length = 50)
    private String source = "GACHA_ROLL";

    public GachaHistory() {
        this.rolledAt = LocalDateTime.now();
    }

    public GachaHistory(User user, String itemId, String rarity, int quantity, String packCode, String source) {
        this.user = user;
        this.itemId = itemId;
        this.rarity = rarity;
        this.quantity = quantity;
        this.packCode = packCode;
        this.source = source;
        this.rolledAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }

    public String getRarity() { return rarity; }
    public void setRarity(String rarity) { this.rarity = rarity; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public LocalDateTime getRolledAt() { return rolledAt; }
    public void setRolledAt(LocalDateTime rolledAt) { this.rolledAt = rolledAt; }

    public String getPackCode() { return packCode; }
    public void setPackCode(String packCode) { this.packCode = packCode; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
