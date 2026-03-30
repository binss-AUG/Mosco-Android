package com.vn.jet.mosco.model;

import com.google.gson.annotations.SerializedName;

public class AuthRequest {
    @SerializedName("username")
    private String username;

    @SerializedName("email")
    private String email;

    @SerializedName("password")
    private String password;

    /** Constructor for sign-in with username */
    public AuthRequest(String username, String password, boolean isSignIn) {
        this.username = username;
        this.password = password;
    }

    /** Constructor for sign-in (email + password only) - old/fallback */
    public AuthRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }

    /** Constructor for sign-up (username + email + password). */
    public AuthRequest(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
