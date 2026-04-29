package com.vn.jet.mosco.model;

import com.google.gson.annotations.SerializedName;

/**
 * SocialAuthRequest - Model for Google and Discord login requests.
 * Standardized for the Mosco Backend architecture.
 */
public class SocialAuthRequest {
    @SerializedName("provider")
    private String provider; // "google" or "discord"

    @SerializedName("token")
    private String token; // IdToken (Google) or AccessToken (Discord)

    @SerializedName("email")
    private String email;

    public SocialAuthRequest(String provider, String token, String email) {
        this.provider = provider;
        this.token = token;
        this.email = email;
    }

    public String getProvider() {
        return provider;
    }

    public String getToken() {
        return token;
    }

    public String getEmail() {
        return email;
    }
}
