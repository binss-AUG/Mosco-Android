package com.vn.jet.mosco.spinserver.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;

@Data
@Builder
public class CoupleStreakResponse {
    private Long id;
    private String status;
    private int streakCount;
    private int streakLevel;
    private int requesterGrade;
    private int partnerGrade;
    private String requesterObjetId;
    private String partnerObjetId;
    private int objetChangesThisWeek;
    private LocalDate lastInteractionDate;
    private UserSummary requester;
    private UserSummary partner;

    @Data
    @Builder
    public static class UserSummary {
        private Long id;
        private String ingameName;
        private String avatarId;
    }

    public static CoupleStreakResponse fromEntity(com.vn.jet.mosco.spinserver.model.CoupleStreak s) {
        if (s == null) return null;
        return CoupleStreakResponse.builder()
                .id(s.getId())
                .status(s.getStatus())
                .streakCount(s.getStreakCount())
                .streakLevel(s.getStreakLevel())
                .requesterGrade(s.getRequesterGrade())
                .partnerGrade(s.getPartnerGrade())
                .requesterObjetId(s.getRequesterObjetId())
                .partnerObjetId(s.getPartnerObjetId())
                .objetChangesThisWeek(s.getObjetChangesThisWeek())
                .lastInteractionDate(s.getLastInteractionDate())
                .requester(UserSummary.builder()
                        .id(s.getRequester().getId())
                        .ingameName(s.getRequester().getIngameName())
                        .avatarId(s.getRequester().getAvatarId())
                        .build())
                .partner(UserSummary.builder()
                        .id(s.getPartner().getId())
                        .ingameName(s.getPartner().getIngameName())
                        .avatarId(s.getPartner().getAvatarId())
                        .build())
                .build();
    }
}
