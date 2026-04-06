package com.vn.jet.mosco.model;

import com.google.gson.annotations.SerializedName;

public class AuthResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("message")
    private String message;

    @SerializedName("data")
    private UserData data;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public UserData getData() { return data; }
    public void setData(UserData data) { this.data = data; }

    public static class UserData {
        @SerializedName("id")
        private Long id;

        @SerializedName("username")
        private String username;

        @SerializedName("email")
        private String email;

        @SerializedName("ingameName")
        private String ingameName;

        @SerializedName("avatarId")
        private String avatarId;

        @SerializedName("token")
        private String token;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getIngameName() { return ingameName; }
        public void setIngameName(String ingameName) { this.ingameName = ingameName; }

        public String getAvatarId() { return avatarId; }
        public void setAvatarId(String avatarId) { this.avatarId = avatarId; }

        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
    }
}
