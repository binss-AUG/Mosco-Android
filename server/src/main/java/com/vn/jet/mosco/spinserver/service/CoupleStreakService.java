package com.vn.jet.mosco.spinserver.service;

import com.vn.jet.mosco.spinserver.dto.CoupleStreakResponse;
import com.vn.jet.mosco.spinserver.model.CoupleStreak;
import com.vn.jet.mosco.spinserver.model.User;
import com.vn.jet.mosco.spinserver.repository.CoupleStreakRepository;
import com.vn.jet.mosco.spinserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CoupleStreakService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CoupleStreakService.class);

    private final CoupleStreakRepository streakRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public Optional<CoupleStreak> findStreak(Long u1, Long u2) {
        return streakRepository.findBetweenUserIds(u1, u2);
    }

    @Transactional
    public CoupleStreak requestStreak(Long requesterId, Long partnerId) {
        log.info("[STREAK] User {} requesting streak with User {}", requesterId, partnerId);
        
        Optional<CoupleStreak> existing = streakRepository.findBetweenUserIds(requesterId, partnerId);
        if (existing.isPresent()) {
            CoupleStreak s = existing.get();
            if (!"ACTIVE".equals(s.getStatus())) {
                log.info("[STREAK] Reactivating existing streak request (status: {}) to PENDING", s.getStatus());
                s.setStatus("PENDING");
                s.setRequester(userRepository.findById(requesterId).get());
                s.setPartner(userRepository.findById(partnerId).get());
                s.setRequestDate(LocalDate.now());
                s = streakRepository.save(s);
            }
            notifyStreakUpdate(s);
            return s;
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

        CoupleStreak saved = streakRepository.save(streak);
        notifyStreakUpdate(saved);
        return saved;
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
        CoupleStreak saved = streakRepository.save(streak);
        notifyStreakUpdate(saved);
        return saved;
    }

    @Transactional
    public void declineStreak(Long userId, Long requesterId) {
        log.info("[STREAK] User {} declining streak request from User {}", userId, requesterId);
        
        CoupleStreak streak = streakRepository.findBetweenUserIds(userId, requesterId)
                .orElseThrow(() -> new RuntimeException("Streak request not found"));

        if (!streak.getPartner().getId().equals(userId) && !streak.getRequester().getId().equals(userId)) {
            log.error("[STREAK] User {} unauthorized to decline streak for users {} and {}", userId, streak.getRequester().getId(), streak.getPartner().getId());
            throw new RuntimeException("Unauthorized to decline this request");
        }

        // Fix lỗ hổng: Không cho phép Cancel nếu đối phương đã Accept (ACTIVE)
        if ("ACTIVE".equals(streak.getStatus())) {
            log.info("[STREAK] Cannot decline. Streak is already ACTIVE.");
            notifyStreakUpdate(streak); // Đồng bộ lại trạng thái ACTIVE cho client
            return;
        }

        streak.setStatus("DECLINED");
        streak.setStreakCount(0); // Reset bộ đếm nếu có lỗi
        streakRepository.save(streak);
        notifyStreakUpdate(streak);
        log.info("[STREAK] Streak request DECLINED between {} and {}", userId, requesterId);
    }

    @Transactional
    public void recordInteraction(Long senderId, Long receiverId) {
        Optional<CoupleStreak> streakOpt = streakRepository.findBetweenUserIds(senderId, receiverId);
        
        if (streakOpt.isPresent()) {
            CoupleStreak streak = streakOpt.get();
            if ("ACTIVE".equals(streak.getStatus())) {
                LocalDate today = LocalDate.now();
                
                // Cập nhật ngày tương tác cho người gửi
                boolean isRequester = senderId.equals(streak.getRequester().getId());
                if (isRequester) {
                    streak.setRequesterInteractionDate(today);
                } else {
                    streak.setPartnerInteractionDate(today);
                }

                // Nếu cả hai người đều đã nhắn tin trong ngày hôm nay, mới tiến hành tính streak
                boolean shouldNotify = false;
                if (today.equals(streak.getRequesterInteractionDate()) && today.equals(streak.getPartnerInteractionDate())) {
                    LocalDate last = streak.getLastInteractionDate();
                    
                    if (last == null || last.isBefore(today)) {
                        if (last != null && last.isEqual(today.minusDays(1))) {
                            streak.setStreakCount(streak.getStreakCount() + 1);
                            log.info("[STREAK] Streak INCREASED for users {} and {}. New count: {}", senderId, receiverId, streak.getStreakCount());
                            shouldNotify = true;
                        } else if (last != null) {
                            streak.setStreakCount(1);
                            log.info("[STREAK] Streak RESTARTED for users {} and {}. Count: 1", senderId, receiverId);
                            shouldNotify = true;
                        } else {
                            // First time streak activated
                            streak.setStreakCount(1);
                            shouldNotify = true;
                        }
                        streak.setLastInteractionDate(today);
                    }
                }
                
                CoupleStreak saved = streakRepository.save(streak);
                if (shouldNotify) {
                    notifyStreakUpdate(saved);
                }
            }
        }
    }

    @Transactional
    public CoupleStreak updateObjet(Long streakId, Long userId, String objetId, int grade) {
        log.info("[STREAK-UPDATE] START updateObjet: streakId={}, userId={}, objetId={}, grade={}", streakId, userId, objetId, grade);
        
        CoupleStreak streak = streakRepository.findById(streakId)
                .orElseThrow(() -> new RuntimeException("Streak not found: " + streakId));

        log.info("[STREAK-UPDATE] Found streak. Current status: {}, Requester: {}, Partner: {}", 
                streak.getStatus(), streak.getRequester().getId(), streak.getPartner().getId());

        if (streak.getRequester().getId().equals(userId)) {
            log.info("[STREAK-UPDATE] Updating Requester's Objet: {} -> {}, Grade: {} -> {}", 
                    streak.getRequesterObjetId(), objetId, streak.getRequesterGrade(), grade);
            streak.setRequesterObjetId(objetId);
            streak.setRequesterGrade(grade);
        } else if (streak.getPartner().getId().equals(userId)) {
            log.info("[STREAK-UPDATE] Updating Partner's Objet: {} -> {}, Grade: {} -> {}", 
                    streak.getPartnerObjetId(), objetId, streak.getPartnerGrade(), grade);
            streak.setPartnerObjetId(objetId);
            streak.setPartnerGrade(grade);
        } else {
            log.error("[STREAK-UPDATE] User {} is not part of streak {}", userId, streakId);
            throw new RuntimeException("User not part of this streak");
        }

        streak.setLastObjetChangeDate(LocalDate.now());
        streak.setObjetChangesThisWeek(streak.getObjetChangesThisWeek() + 1);
        
        CoupleStreak saved = streakRepository.save(streak);
        log.info("[STREAK-UPDATE] SUCCESSFULLY saved streak {}. Objet changes this week: {}", streakId, saved.getObjetChangesThisWeek());
        
        notifyStreakUpdate(saved);
        return saved;
    }

    private void notifyStreakUpdate(CoupleStreak streak) {
        if (streak == null) return;
        CoupleStreakResponse response = CoupleStreakResponse.fromEntity(streak);
        
        // Gửi cho cả hai người trong cặp đôi
        String topic1 = "/topic/streak." + streak.getRequester().getId();
        String topic2 = "/topic/streak." + streak.getPartner().getId();
        
        log.info("[WS] Notifying streak update to: {} and {}", topic1, topic2);
        messagingTemplate.convertAndSend(topic1, response);
        messagingTemplate.convertAndSend(topic2, response);
    }
}
