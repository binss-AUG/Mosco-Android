package com.vn.jet.mosco.spinserver.repository;

import com.vn.jet.mosco.spinserver.model.GiftHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface GiftHistoryRepository extends JpaRepository<GiftHistory, Long> {

    /**
     * Đếm số lần gửi tặng trong khoảng thời gian — phục vụ giới hạn daily limit (5/ngày).
     * Tại sao dùng countBy: hiệu quả hơn findAll rồi .size() vì DB tự đếm.
     */
    int countBySenderIdAndCreatedAtAfter(Long senderId, LocalDateTime after);

    /**
     * Đếm số lần nhận quà trong khoảng thời gian — phục vụ giới hạn daily limit nhận (5/ngày).
     */
    int countByReceiverIdAndCreatedAtAfter(Long receiverId, LocalDateTime after);

    /**
     * Lấy lịch sử giao dịch liên quan đến user (cả gửi và nhận).
     * Sắp xếp mới nhất trước để hiển thị timeline.
     */
    @Query("SELECT g FROM GiftHistory g WHERE g.senderId = :userId OR g.receiverId = :userId ORDER BY g.createdAt DESC")
    List<GiftHistory> findByUserInvolved(@Param("userId") Long userId);

    /**
     * Lấy danh sách quà đã nhận (cho tab "Nhận" trong GiftActivity).
     */
    @Query("SELECT g FROM GiftHistory g WHERE g.receiverId = :userId ORDER BY g.createdAt DESC")
    List<GiftHistory> findReceivedGifts(@Param("userId") Long userId);

    /**
     * Lấy danh sách quà đã gửi (cho tab "Gửi" trong GiftActivity).
     */
    @Query("SELECT g FROM GiftHistory g WHERE g.senderId = :userId ORDER BY g.createdAt DESC")
    List<GiftHistory> findSentGifts(@Param("userId") Long userId);

    /**
     * Đếm số quà chưa đọc — để hiển thị badge thông báo trên Quick Tool.
     */
    int countByReceiverIdAndReceiverReadFalse(Long receiverId);
}
