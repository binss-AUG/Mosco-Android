package com.vn.jet.mosco.spinserver.service;

import com.vn.jet.mosco.spinserver.model.DailyCheckin;
import com.vn.jet.mosco.spinserver.model.User;
import com.vn.jet.mosco.spinserver.repository.DailyCheckinRepository;
import com.vn.jet.mosco.spinserver.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service xử lý logic điểm danh hằng ngày.
 */
@Service
public class DailyCheckinService {

    private static final Logger logger = LoggerFactory.getLogger(DailyCheckinService.class);

    private final DailyCheckinRepository checkinRepository;
    private final UserRepository userRepository;

    private static final long[][] SLOT_REWARDS = {
            {500L, 1L},   // MORNING
            {800L, 2L},   // NOON
            {1200L, 3L}   // EVENING
    };

    public DailyCheckinService(DailyCheckinRepository checkinRepository, UserRepository userRepository) {
        this.checkinRepository = checkinRepository;
        this.userRepository = userRepository;
    }

    public int getCurrentSlot() {
        int hour = LocalTime.now().getHour();
        if (hour >= 6 && hour < 12) return 0;
        if (hour >= 12 && hour < 18) return 1;
        if (hour >= 18) return 2;
        return -1;
    }

    public Map<String, Object> getStatus(Long userId) {
        LocalDate today = LocalDate.now();
        List<DailyCheckin> todayCheckins = checkinRepository.findByUserIdAndCheckinDate(userId, today);
        int currentSlot = getCurrentSlot();

        Map<Integer, String> slotStatuses = new HashMap<>();
        for (int i = 0; i < 3; i++) {
            boolean claimed = false;
            for (DailyCheckin c : todayCheckins) {
                if (c.getSlot() == i) {
                    claimed = true;
                    break;
                }
            }
            if (claimed) {
                slotStatuses.put(i, "claimed");
            } else if (i == currentSlot) {
                slotStatuses.put(i, "available");
            } else {
                slotStatuses.put(i, "locked");
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("slotStatuses", slotStatuses);
        result.put("currentSlot", currentSlot);
        result.put("rewards", SLOT_REWARDS);
        return result;
    }

    /**
     * Nhận thưởng điểm danh cho slot hiện tại.
     * Chống double-claim và kiểm tra khung giờ.
     */
    @Transactional
    public Map<String, Object> claim(Long userId) {
        int currentSlot = getCurrentSlot();
        if (currentSlot == -1) return null;

        LocalDate today = LocalDate.now();
        if (checkinRepository.existsByUserIdAndCheckinDateAndSlot(userId, today, currentSlot)) {
            return null;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return null;

        long coinReward = SLOT_REWARDS[currentSlot][0];
        long diamondReward = SLOT_REWARDS[currentSlot][1];
        user.setCoins(user.getCoins() + coinReward);
        user.setDiamonds(user.getDiamonds() + diamondReward);
        userRepository.save(user);

        DailyCheckin checkin = new DailyCheckin(userId, today, currentSlot);
        checkinRepository.save(checkin);

        Map<String, Object> result = new HashMap<>();
        result.put("slot", currentSlot);
        result.put("coinsRewarded", coinReward);
        result.put("diamondsRewarded", diamondReward);
        result.put("newCoins", user.getCoins());
        result.put("newDiamonds", user.getDiamonds());
        return result;
    }
}
