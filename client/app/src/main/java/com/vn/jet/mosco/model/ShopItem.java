package com.vn.jet.mosco.model;

import com.google.gson.annotations.SerializedName;

public class ShopItem {

    @SerializedName("id")
    private Long id;

    @SerializedName("productCode")
    private String productCode;

    @SerializedName("name")
    private String name;

    @SerializedName("description")
    private String description;

    @SerializedName("type")
    private String type;

    @SerializedName("priceCoins")
    private Long priceCoins;

    @SerializedName("priceDiamonds")
    private Long priceDiamonds;

    @SerializedName("imageUri")
    private String imageUri;

    @SerializedName("endTime")
    private Long endTime;

    @SerializedName("metadata")
    private String metadata;

    public Long getId() { return id; }
    public String getProductCode() { return productCode; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getType() { return type; }
    public Long getPriceCoins() { return priceCoins; }
    public Long getPriceDiamonds() { return priceDiamonds; }
    public String getImageUri() { return imageUri; }
    public Long getEndTime() { return endTime; }
    public String getMetadata() { return metadata; }
}
