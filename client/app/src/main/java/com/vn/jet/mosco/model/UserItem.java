package com.vn.jet.mosco.model;

import com.google.gson.annotations.SerializedName;

public class UserItem {

    @SerializedName("id")
    private Long id;

    @SerializedName("itemCode")
    private String itemCode;

    @SerializedName("quantity")
    private Integer quantity;

    @SerializedName("name")
    private String name;

    @SerializedName("description")
    private String description;

    @SerializedName("type")
    private String type;

    @SerializedName("imageUri")
    private String imageUri;

    public Long getId() {
        return id;
    }

    public String getItemCode() {
        return itemCode;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getType() { return type; }
    public String getImageUri() { return imageUri; }
}
