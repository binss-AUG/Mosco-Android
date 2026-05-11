package com.vn.jet.mosco.network;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * DTO cho Request cập nhật Profile - Đảm bảo đồng bộ với Server UserController.
 */
public class UpdateProfileRequest {
    @SerializedName("username")
    private String username;

    @SerializedName("ingameName")
    private String ingameName;

    @SerializedName("avatarId")
    private String avatarId;

    @SerializedName("bio")
    private String bio;

    @SerializedName("showcaseCardIds")
    private List<String> showcaseCardIds;

    @SerializedName("likesCount")
    private Integer likesCount;

    @SerializedName("friendsCount")
    private Integer friendsCount;

    @SerializedName("avatarCropParams")
    private String avatarCropParams;

    public UpdateProfileRequest() {}

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getIngameName() { return ingameName; }
    public void setIngameName(String ingameName) { this.ingameName = ingameName; }

    public String getAvatarId() { return avatarId; }
    public void setAvatarId(String avatarId) { this.avatarId = avatarId; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public List<String> getShowcaseCardIds() { return showcaseCardIds; }
    public void setShowcaseCardIds(List<String> showcaseCardIds) { this.showcaseCardIds = showcaseCardIds; }

    public Integer getLikesCount() { return likesCount; }
    public void setLikesCount(Integer likesCount) { this.likesCount = likesCount; }

    public Integer getFriendsCount() { return friendsCount; }
    public void setFriendsCount(Integer friendsCount) { this.friendsCount = friendsCount; }

    public String getAvatarCropParams() { return avatarCropParams; }
    public void setAvatarCropParams(String avatarCropParams) { this.avatarCropParams = avatarCropParams; }
}
