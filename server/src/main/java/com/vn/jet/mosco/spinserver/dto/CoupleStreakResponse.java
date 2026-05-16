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
}
