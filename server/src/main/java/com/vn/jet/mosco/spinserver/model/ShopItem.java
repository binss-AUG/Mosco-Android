package com.vn.jet.mosco.spinserver.model;

import jakarta.persistence.*;

@Entity
@Table(name = "shop_items")
public class ShopItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String productCode;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private String type; // PACK, BUFF, CARD

    @Column(nullable = false)
    private Long priceCoins = 0L;

    @Column(nullable = false)
    private Long priceDiamonds = 0L;

    private String imageUri;

    @Column(nullable = false)
    private Long endTime = -1L; // -1 means permanent

    @Column(columnDefinition = "TEXT")
    private String metadata; // JSON config for packs/buffs

    public ShopItem() {}

    public ShopItem(String productCode, String name, String description, String type, Long priceCoins, Long priceDiamonds, String imageUri, Long endTime, String metadata) {
        this.productCode = productCode;
        this.name = name;
        this.description = description;
        this.type = type;
        this.priceCoins = priceCoins;
        this.priceDiamonds = priceDiamonds;
        this.imageUri = imageUri;
        this.endTime = endTime;
        this.metadata = metadata;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Long getPriceCoins() { return priceCoins; }
    public void setPriceCoins(Long priceCoins) { this.priceCoins = priceCoins; }

    public Long getPriceDiamonds() { return priceDiamonds; }
    public void setPriceDiamonds(Long priceDiamonds) { this.priceDiamonds = priceDiamonds; }

    public String getImageUri() { return imageUri; }
    public void setImageUri(String imageUri) { this.imageUri = imageUri; }

    public Long getEndTime() { return endTime; }
    public void setEndTime(Long endTime) { this.endTime = endTime; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
}
