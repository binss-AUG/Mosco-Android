package com.vn.jet.mosco.spinserver.dto;

/**
 * DTO cho endpoint cập nhật thông tin cá nhân.
 * Email KHÔNG nằm trong DTO vì KHÔNG cho phép sửa.
 */
public class UpdateProfileRequest {
    private String username;
    private String ingameName;
    private String avatarId;

    public UpdateProfileRequest() {}

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getIngameName() { return ingameName; }
    public void setIngameName(String ingameName) { this.ingameName = ingameName; }

    public String getAvatarId() { return avatarId; }
    public void setAvatarId(String avatarId) { this.avatarId = avatarId; }
}
