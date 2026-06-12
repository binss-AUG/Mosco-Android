package com.vn.jet.mosco.model;

public class SavedAccount {
    private long userId;
    private String username;
    private String email;
    private String ingameName;
    private String avatarId;
    private String token;
    private String authType; // "email", "google", "discord"

    public SavedAccount() {
    }

    public SavedAccount(long userId, String username, String email, String ingameName, String avatarId, String token, String authType) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.ingameName = ingameName;
        this.avatarId = avatarId;
        this.token = token;
        this.authType = authType;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getIngameName() {
        return ingameName;
    }

    public void setIngameName(String ingameName) {
        this.ingameName = ingameName;
    }

    public String getAvatarId() {
        return avatarId;
    }

    public void setAvatarId(String avatarId) {
        this.avatarId = avatarId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }
}
