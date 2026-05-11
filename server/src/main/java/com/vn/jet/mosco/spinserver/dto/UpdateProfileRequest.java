package com.vn.jet.mosco.spinserver.dto;

/**
 * DTO cho endpoint cập nhật thông tin cá nhân.
 * Email KHÔNG nằm trong DTO vì KHÔNG cho phép sửa.
 */
public class UpdateProfileRequest {
    private String username;
    private String ingameName;
    private String avatarId;
    private String bio;
    private java.util.List<String> showcaseCardIds;
    private Integer likesCount;
    private Integer friendsCount;

    public UpdateProfileRequest() {}

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getIngameName() { return ingameName; }
    public void setIngameName(String ingameName) { this.ingameName = ingameName; }

    public String getAvatarId() { return avatarId; }
    public void setAvatarId(String avatarId) { this.avatarId = avatarId; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public java.util.List<String> getShowcaseCardIds() { return showcaseCardIds; }
    public void setShowcaseCardIds(java.util.List<String> showcaseCardIds) { this.showcaseCardIds = showcaseCardIds; }

    public Integer getLikesCount() { return likesCount; }
    public void setLikesCount(Integer likesCount) { this.likesCount = likesCount; }

    public Integer getFriendsCount() { return friendsCount; }
    public void setFriendsCount(Integer friendsCount) { this.friendsCount = friendsCount; }
}
