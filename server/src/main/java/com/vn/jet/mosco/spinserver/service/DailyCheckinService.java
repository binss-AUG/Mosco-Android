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
 * 
 * Khung giờ (theo chuẩn Genshin/HSR):
 *   - Slot 0 (MORNING):  06:00 – 11:59
 *   - Slot 1 (NOON):     12:00 – 17:59
 *   - Slot 2 (EVENING):  18:00 – 23:59
 *   - Ngoài 3 khung trên (00:00 – 05:59): Không có slot nào mở
 *
 * Phần thưởng tăng dần (khuyến khích chơi xuyên ngày):
 *   - Slot 0: 500 Coins + 1 Diamond
 *   - Slot 1: 800 Coins + 2 Diamonds
 *   - Slot 2: 1200 Coins + 3 Diamonds
 */
@Service
public class DailyCheckinService {

    private static final Logger logger = LoggerFactory.getLogger(DailyCheckinService.class);

    private final DailyCheckinRepository checkinRepository;
    private final UserRepository userRepository;

    // Phần thưởng cho mỗi slot: [coins, diamonds]
    private static final long[][] SLOT_REWARDS = {
            {500L, 1L},   // MORNING
            {800L, 2L},   // NOON
            {1200L, 3L}   // EVENING
    };

    public DailyCheckinService(DailyCheckinRepository checkinRepository, UserRepository userRepository) {
        this.checkinRepository = checkinRepository;
        this.userRepository = userRepository;
    }

    /**
     * Xác định slot hiện tại dựa trên giờ server.
     * @return 0/1/2 hoặc -1 nếu ngoài khung giờ
     */
    public int getCurrentSlot() {
        int hour = LocalTime.now().getHour();
        if (hour >= 6 && hour < 12) return 0;   // MORNING
        if (hour >= 12 && hour < 18) return 1;   // NOON
        if (hour >= 18) return 2;                 // EVENING
        return -1; // 00:00 – 05:59: Không mở
    }

    /**
     * Lấy trạng thái 3 slot trong ngày cho user.
     * @return Map chứa slotStatuses (0/1/2 → "claimed"/"available"/"locked"), currentSlot, rewards
     */
    public Map<String, Object> getStatus(Long userId) {
        LocalDate today = LocalDate.now();
        List<DailyCheckin> todayCheckins = checkinRepository.findByUserIdAndCheckinDate(userId, today);
        int currentSlot = getCurrentSlot();

        // Xây dựng trạng thái cho 3 slot
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
     * Claim phần thưởng cho slot hiện tại.
     * Atomic: Kiểm tra + cộng thưởng + lưu record trong 1 transaction.
     * @return Map chứa thông tin phần thưởng nhận được, hoặc null nếu thất bại
     */
    @Transactional
    public Map<String, Object> claim(Long userId) {
        int currentSlot = getCurrentSlot();
        if (currentSlot == -1) {
            return null; // Ngoài khung giờ
        }

        LocalDate today = LocalDate.now();

        // Chống double-claim
        if (checkinRepository.existsByUserIdAndCheckinDateAndSlot(userId, today, currentSlot)) {
            return null;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return null;

        // Cộng thưởng
        long coinReward = SLOT_REWARDS[currentSlot][0];
        long diamondReward = SLOT_REWARDS[currentSlot][1];
        user.setCoins(user.getCoins() + coinReward);
        user.setDiamonds(user.getDiamonds() + diamondReward);
        userRepository.save(user);

        // Lưu record điểm danh
        DailyCheckin checkin = new DailyCheckin(userId, today, currentSlot);
        checkinRepository.save(checkin);

        logger.info("Daily claim: userId={}, slot={}, coins=+{}, diamonds=+{}", userId, currentSlot, coinReward, diamondReward);

        Map<String, Object> result = new HashMap<>();
        result.put("slot", currentSlot);
        result.put("coinsRewarded", coinReward);
        result.put("diamondsRewarded", diamondReward);
        result.put("newCoins", user.getCoins());
        result.put("newDiamonds", user.getDiamonds());
        return result;
    }
}
