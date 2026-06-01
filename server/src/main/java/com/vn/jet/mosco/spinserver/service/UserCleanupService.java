package com.vn.jet.mosco.spinserver.service;

import com.vn.jet.mosco.spinserver.model.User;
import com.vn.jet.mosco.spinserver.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service dọn dẹp các tài khoản quá hạn 14 ngày (Soft Delete sang Hard Delete).
 * Sử dụng EntityManager để chạy các Native Query dọn dẹp liên hoàn trực tiếp trong DB.
 * Tại sao (WHY): Tránh việc sửa đổi và tạo mới hàng loạt phương thức xóa trong các Repository độc lập,
 * tập trung logic dọn dẹp và đảm bảo tính toàn vẹn dữ liệu tránh lỗi foreign key constraint.
 */
@Service
public class UserCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(UserCleanupService.class);

    private final UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public UserCleanupService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Quét và xóa vĩnh viễn các tài khoản có deletionRequestedAt quá 14 ngày.
     * Chạy vào lúc 2h00 sáng mỗi ngày.
     * Tại sao: Hạn chế ảnh hưởng đến hiệu năng hệ thống khi có lượng lớn người dùng online ban ngày.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupDeletedUsers() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(14);
        List<User> usersToDelete = userRepository.findAll().stream()
                .filter(u -> u.getDeletionRequestedAt() != null && u.getDeletionRequestedAt().isBefore(threshold))
                .toList();

        if (usersToDelete.isEmpty()) {
            return;
        }

        logger.info("Bắt đầu quét dọn tài khoản quá hạn 14 ngày. Số lượng: {}", usersToDelete.size());

        for (User user : usersToDelete) {
            Long userId = user.getId();
            try {
                // Thực hiện dọn dẹp dữ liệu liên hoàn (Cascade Delete) bằng Native SQL
                // Tại sao: Native SQL trực tiếp chạy trên DB giúp xóa nhanh, an toàn và không bị phụ thuộc vào JPA mapping phức tạp

                // 1. Xóa trong friendships (người gửi hoặc người nhận)
                entityManager.createNativeQuery("DELETE FROM friendships WHERE requester_id = :userId OR addressee_id = :userId")
                        .setParameter("userId", userId).executeUpdate();

                // 2. Xóa trong user_likes (người thích hoặc được thích)
                entityManager.createNativeQuery("DELETE FROM user_likes WHERE liker_id = :userId OR target_user_id = :userId")
                        .setParameter("userId", userId).executeUpdate();

                // 3. Xóa trong gacha_histories
                entityManager.createNativeQuery("DELETE FROM gacha_histories WHERE user_id = :userId")
                        .setParameter("userId", userId).executeUpdate();

                // 4. Xóa trong couple_streaks
                entityManager.createNativeQuery("DELETE FROM couple_streaks WHERE user_id = :userId OR partner_id = :userId")
                        .setParameter("userId", userId).executeUpdate();

                // 5. Xóa trong daily_checkins
                entityManager.createNativeQuery("DELETE FROM daily_checkins WHERE user_id = :userId")
                        .setParameter("userId", userId).executeUpdate();

                // 6. Xóa trong gift_histories
                entityManager.createNativeQuery("DELETE FROM gift_histories WHERE sender_id = :userId OR receiver_id = :userId")
                        .setParameter("userId", userId).executeUpdate();

                // 7. Xóa trong private_messages
                entityManager.createNativeQuery("DELETE FROM private_messages WHERE sender_id = :userId OR receiver_id = :userId")
                        .setParameter("userId", userId).executeUpdate();

                // 8. Xóa trong user_mails
                entityManager.createNativeQuery("DELETE FROM user_mails WHERE user_id = :userId")
                        .setParameter("userId", userId).executeUpdate();

                // 9. Xóa trong user_cards
                entityManager.createNativeQuery("DELETE FROM user_cards WHERE user_id = :userId")
                        .setParameter("userId", userId).executeUpdate();

                // 10. Xóa trong user_items
                entityManager.createNativeQuery("DELETE FROM user_items WHERE user_id = :userId")
                        .setParameter("userId", userId).executeUpdate();

                // 11. Xóa trong user_showcase (bảng collection)
                entityManager.createNativeQuery("DELETE FROM user_showcase WHERE user_id = :userId")
                        .setParameter("userId", userId).executeUpdate();

                // 12. Xóa trong user_unlocked_collections
                entityManager.createNativeQuery("DELETE FROM user_unlocked_collections WHERE user_id = :userId")
                        .setParameter("userId", userId).executeUpdate();

                // 13. Cuối cùng, xóa User
                entityManager.createNativeQuery("DELETE FROM users WHERE id = :userId")
                        .setParameter("userId", userId).executeUpdate();

                logger.info("Đã xóa vĩnh viễn thành công tài khoản: {} (ID: {})", user.getUsername(), userId);
            } catch (Exception e) {
                logger.error("Lỗi khi xóa vĩnh viễn tài khoản {} (ID: {}): {}", user.getUsername(), userId, e.getMessage(), e);
            }
        }
    }
}
