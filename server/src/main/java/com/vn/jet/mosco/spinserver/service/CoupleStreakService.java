package com.vn.jet.mosco.spinserver.service;

import com.vn.jet.mosco.spinserver.model.CoupleStreak;
import com.vn.jet.mosco.spinserver.model.User;
import com.vn.jet.mosco.spinserver.repository.CoupleStreakRepository;
import com.vn.jet.mosco.spinserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CoupleStreakService {

    private final CoupleStreakRepository streakRepository;
    private final UserRepository userRepository;

    public Optional<CoupleStreak> findStreak(Long u1, Long u2) {
        return streakRepository.findBetweenUserIds(u1, u2);
    }


    @Transactional
    public CoupleStreak requestStreak(Long requesterId, Long partnerId) {
        log.info("[STREAK] User {} requesting streak with User {}", requesterId, partnerId);
        
        Optional<CoupleStreak> existing = streakRepository.findBetweenUserIds(requesterId, partnerId);
        if (existing.isPresent()) {
            log.warn("[STREAK] Streak request already exists between {} and {}", requesterId, partnerId);
            return existing.get();
        }

        User requester = userRepository.findById(requesterId).orElseThrow(() -> new RuntimeException("User not found: " + requesterId));
        User partner = userRepository.findById(partnerId).orElseThrow(() -> new RuntimeException("User not found: " + partnerId));

        CoupleStreak streak = CoupleStreak.builder()
                .requester(requester)
                .partner(partner)
                .status("PENDING")
                .streakCount(0)
                .requestDate(LocalDate.now())
                .build();

        return streakRepository.save(streak);
    }

    @Transactional
    public CoupleStreak acceptStreak(Long userId, Long requesterId) {
        log.info("[STREAK] User {} accepting streak request from User {}", userId, requesterId);
        
        CoupleStreak streak = streakRepository.findBetweenUserIds(userId, requesterId)
                .orElseThrow(() -> new RuntimeException("Streak request not found"));

        if (!streak.getPartner().getId().equals(userId)) {
            log.error("[STREAK] User {} unauthorized to accept request meant for User {}", userId, streak.getPartner().getId());
            throw new RuntimeException("Unauthorized to accept this request");
        }

        streak.setStatus("ACTIVE");
        streak.setStreakCount(1);
        streak.setLastInteractionDate(LocalDate.now());

        log.info("[STREAK] Streak ACTIVATED between {} and {}. Count: 1", userId, requesterId);
        return streakRepository.save(streak);
    }

    @Transactional
    public void declineStreak(Long userId, Long requesterId) {
        log.info("[STREAK] User {} declining streak request from User {}", userId, requesterId);
        
        CoupleStreak streak = streakRepository.findBetweenUserIds(userId, requesterId)
                .orElseThrow(() -> new RuntimeException("Streak request not found"));

        if (!streak.getPartner().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized to decline this request");
        }

        streak.setStatus("DECLINED");
        streakRepository.save(streak);
        log.info("[STREAK] Streak request DECLINED between {} and {}", userId, requesterId);
    }

    @Transactional
    public void recordInteraction(Long user1Id, Long user2Id) {
        Optional<CoupleStreak> streakOpt = streakRepository.findBetweenUserIds(user1Id, user2Id);
        
        if (streakOpt.isPresent()) {
            CoupleStreak streak = streakOpt.get();
            if ("ACTIVE".equals(streak.getStatus())) {
                LocalDate last = streak.getLastInteractionDate();
                LocalDate today = LocalDate.now();

                if (last == null || last.isBefore(today)) {
                    if (last != null && last.isEqual(today.minusDays(1))) {
                        streak.setStreakCount(streak.getStreakCount() + 1);
                        log.info("[STREAK] Streak INCREASED for users {} and {}. New count: {}", user1Id, user2Id, streak.getStreakCount());
                    } else if (last != null) {
                        // Reset if broken? Or just restart at 1?
                        // Logic for "Broken" can be complex, here we restart at 1 if more than 1 day gap
                        streak.setStreakCount(1);
                        log.info("[STREAK] Streak RESTARTED for users {} and {}. Count: 1", user1Id, user2Id);
                    }
                    streak.setLastInteractionDate(today);
                    streakRepository.save(streak);
                }
            }
        }
    }

    @Transactional
    public CoupleStreak updateObjet(Long streakId, Long userId, String objetId) {
        log.info("[STREAK] User {} updating objet in streak {} to {}", userId, streakId, objetId);
        
        CoupleStreak streak = streakRepository.findById(streakId)
                .orElseThrow(() -> new RuntimeException("Streak not found"));

        if (streak.getRequester().getId().equals(userId)) {
            streak.setRequesterObjetId(objetId);
        } else if (streak.getPartner().getId().equals(userId)) {
            streak.setPartnerObjetId(objetId);
        } else {
            throw new RuntimeException("User not part of this streak");
        }

        streak.setLastObjetChangeDate(LocalDate.now());
        streak.setObjetChangesThisWeek(streak.getObjetChangesThisWeek() + 1);
        
        return streakRepository.save(streak);
    }
}
