package com.vn.jet.mosco.model;

import com.google.gson.annotations.SerializedName;

public class UserStats {
    @SerializedName("id")
    private Long id;

    @SerializedName("username")
    private String username;

    @SerializedName("email")
    private String email;

    @SerializedName("coins")
    private Long coins;

    @SerializedName("diamonds")
    private Long diamonds;

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public Long getCoins() { return coins; }
    public Long getDiamonds() { return diamonds; }
}
