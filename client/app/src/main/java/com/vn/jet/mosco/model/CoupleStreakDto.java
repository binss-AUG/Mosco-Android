package com.vn.jet.mosco.model;

import com.google.gson.annotations.SerializedName;

public class CoupleStreakDto {
    @SerializedName("id")
    private Long id;

    @SerializedName("status")
    private String status;

    @SerializedName("streakCount")
    private int streakCount;

    @SerializedName("streakLevel")
    private int streakLevel;

    @SerializedName("requesterGrade")
    private int requesterGrade;

    @SerializedName("partnerGrade")
    private int partnerGrade;

    @SerializedName("requesterObjetId")
    private String requesterObjetId;

    @SerializedName("partnerObjetId")
    private String partnerObjetId;

    @SerializedName("objetChangesThisWeek")
    private int objetChangesThisWeek;

    @SerializedName("lastInteractionDate")
    private String lastInteractionDate;

    @SerializedName("requester")
    private UserDto requester;

    @SerializedName("partner")
    private UserDto partner;

    public Long getId() { return id; }
    public String getStatus() { return status; }
    public int getStreakCount() { return streakCount; }
    public int getStreakLevel() { return streakLevel; }
    public int getRequesterGrade() { return requesterGrade; }
    public int getPartnerGrade() { return partnerGrade; }
    public String getRequesterObjetId() { return requesterObjetId; }
    public String getPartnerObjetId() { return partnerObjetId; }
    public int getObjetChangesThisWeek() { return objetChangesThisWeek; }
    public String getLastInteractionDate() { return lastInteractionDate; }
    public Long getRequesterId() { return requester != null ? requester.getId() : null; }
    public Long getPartnerId() { return partner != null ? partner.getId() : null; }

    public static class UserDto {
        @SerializedName("id")
        private Long id;
        public Long getId() { return id; }
    }
}
