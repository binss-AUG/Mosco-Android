package com.vn.jet.mosco.spinserver.controller;

import com.vn.jet.mosco.spinserver.dto.ApiResponse;
import com.vn.jet.mosco.spinserver.dto.CoupleStreakResponse;
import com.vn.jet.mosco.spinserver.model.CoupleStreak;
import com.vn.jet.mosco.spinserver.service.CoupleStreakService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/streaks")
@RequiredArgsConstructor
public class CoupleStreakController {

    private final CoupleStreakService streakService;
    private final com.vn.jet.mosco.spinserver.repository.FriendshipRepository friendshipRepository;

    @GetMapping("/check")
    public ApiResponse<CoupleStreakResponse> checkStreak(@RequestParam Long user1, @RequestParam Long user2) {
        // Kiểm tra quan hệ bạn bè trước
        var friendship = friendshipRepository.findExistingFriendship(user1, user2);
        if (friendship.isEmpty() || friendship.get().getStatus() != 1) {
            return ApiResponse.success("Not friends", CoupleStreakResponse.builder()
                    .status("NOT_FRIENDS")
                    .build());
        }

        Optional<CoupleStreak> streak = streakService.findStreak(user1, user2);
        return streak.map(s -> ApiResponse.success("Streak found", mapToResponse(s)))
                     .orElseGet(() -> ApiResponse.success("No streak record", CoupleStreakResponse.builder()
                             .status("NONE")
                             .streakCount(0)
                             .build()));
    }


    @PostMapping("/request")
    public ApiResponse<CoupleStreakResponse> requestStreak(@RequestParam Long requesterId, @RequestParam Long partnerId) {
        // Chặn nếu không phải bạn bè
        var friendship = friendshipRepository.findExistingFriendship(requesterId, partnerId);
        if (friendship.isEmpty() || friendship.get().getStatus() != 1) {
            return ApiResponse.error(403, "Must be friends to ignite a streak!");
        }
        
        CoupleStreak streak = streakService.requestStreak(requesterId, partnerId);
        return ApiResponse.success("Request sent", mapToResponse(streak));
    }

    @PostMapping("/accept")
    public ApiResponse<CoupleStreakResponse> acceptStreak(@RequestParam Long userId, @RequestParam Long requesterId) {
        CoupleStreak streak = streakService.acceptStreak(userId, requesterId);
        return ApiResponse.success("Streak accepted", mapToResponse(streak));
    }

    @PostMapping("/decline")
    public ApiResponse<Void> declineStreak(@RequestParam Long userId, @RequestParam Long requesterId) {
        streakService.declineStreak(userId, requesterId);
        return ApiResponse.success("Streak declined", null);
    }

    @PostMapping("/update-objet")
    public ApiResponse<CoupleStreakResponse> updateObjet(@RequestParam Long streakId, @RequestParam Long userId, @RequestParam String objetId, @RequestParam int grade) {
        CoupleStreak streak = streakService.updateObjet(streakId, userId, objetId, grade);
        return ApiResponse.success("Objet updated", CoupleStreakResponse.fromEntity(streak));
    }

    private CoupleStreakResponse mapToResponse(CoupleStreak s) {
        return CoupleStreakResponse.fromEntity(s);
    }
}


