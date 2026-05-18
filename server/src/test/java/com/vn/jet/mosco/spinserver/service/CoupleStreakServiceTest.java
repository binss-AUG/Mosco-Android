    package com.vn.jet.mosco.spinserver.service;

import com.vn.jet.mosco.spinserver.model.CoupleStreak;
import com.vn.jet.mosco.spinserver.model.User;
import com.vn.jet.mosco.spinserver.repository.CoupleStreakRepository;
import com.vn.jet.mosco.spinserver.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CoupleStreakServiceTest {

    @Mock
    private CoupleStreakRepository streakRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private CoupleStreakService coupleStreakService;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userA = new User();
        userA.setId(1L);
        userA.setIngameName("User A");

        userB = new User();
        userB.setId(2L);
        userB.setIngameName("User B");
    }

    @Test
    void testStreakLifecycle() {
        // 1. Gửi Request
        when(userRepository.findById(1L)).thenReturn(Optional.of(userA));
        when(userRepository.findById(2L)).thenReturn(Optional.of(userB));
        when(streakRepository.save(any(CoupleStreak.class))).thenAnswer(i -> i.getArguments()[0]);

        CoupleStreak streak = coupleStreakService.requestStreak(1L, 2L);
        assertEquals("PENDING", streak.getStatus());
        assertEquals(0, streak.getStreakCount());

        // 2. Chấp nhận Request
        when(streakRepository.findBetweenUserIds(2L, 1L)).thenReturn(Optional.of(streak));
        CoupleStreak activeStreak = coupleStreakService.acceptStreak(2L, 1L);

        
        assertEquals("ACTIVE", activeStreak.getStatus());
        assertEquals(1, activeStreak.getStreakCount());
        assertEquals(LocalDate.now(), activeStreak.getLastInteractionDate());

        // 3. Tương tác cùng ngày (Không tăng streak)
        when(streakRepository.findBetweenUserIds(1L, 2L)).thenReturn(Optional.of(activeStreak));
        coupleStreakService.recordInteraction(1L, 2L);
        coupleStreakService.recordInteraction(2L, 1L);
        assertEquals(1, activeStreak.getStreakCount());

        // 4. Tương tác ngày hôm sau (Tăng streak)
        activeStreak.setLastInteractionDate(LocalDate.now().minusDays(1));
        activeStreak.setRequesterInteractionDate(LocalDate.now().minusDays(1));
        activeStreak.setPartnerInteractionDate(LocalDate.now().minusDays(1));
        coupleStreakService.recordInteraction(1L, 2L);
        coupleStreakService.recordInteraction(2L, 1L);
        assertEquals(2, activeStreak.getStreakCount());
        assertEquals(LocalDate.now(), activeStreak.getLastInteractionDate());
    }
}
