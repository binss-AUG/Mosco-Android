package com.vn.jet.mosco.spinserver.dto;

import com.vn.jet.mosco.spinserver.model.User;

public class AuthResponse {
    private boolean success;
    private String message;
    private UserData data;

    public AuthResponse() {}

    public AuthResponse(boolean success, String message, User user, String token) {
        this.success = success;
        this.message = message;
        if (user != null) {
            this.data = new UserData(user.getId(), user.getUsername(), user.getEmail(), token);
        }
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public UserData getData() { return data; }
    public void setData(UserData data) { this.data = data; }

    public static class UserData {
        private Long id;
        private String username;
        private String email;
        private String token;

        public UserData() {}

        public UserData(Long id, String username, String email, String token) {
            this.id = id;
            this.username = username;
            this.email = email;
            this.token = token;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
    }
}
