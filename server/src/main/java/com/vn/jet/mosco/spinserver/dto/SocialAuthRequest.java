package com.vn.jet.mosco.spinserver.dto;

public class SocialAuthRequest {
    private String provider; // "discord" or "google"
    private String token;
    private String email;

    public SocialAuthRequest() {
    }

    public SocialAuthRequest(String provider, String token, String email) {
        this.provider = provider;
        this.token = token;
        this.email = email;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
