package com.vn.jet.mosco.network;

import com.google.gson.annotations.SerializedName;

public class BuyRequest {
    @SerializedName("userId")
    private Long userId;

    @SerializedName("productCode")
    private String productCode;

    @SerializedName("quantity")
    private int quantity;

    public BuyRequest(Long userId, String productCode, int quantity) {
        this.userId = userId;
        this.productCode = productCode;
        this.quantity = quantity;
    }
}
