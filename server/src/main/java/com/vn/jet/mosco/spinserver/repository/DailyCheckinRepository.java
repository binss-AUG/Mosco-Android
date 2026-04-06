package com.vn.jet.mosco.spinserver.repository;

import com.vn.jet.mosco.spinserver.model.DailyCheckin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DailyCheckinRepository extends JpaRepository<DailyCheckin, Long> {

    /**
     * Lấy danh sách slot đã claim trong 1 ngày cụ thể của user.
     * Dùng để kiểm tra trạng thái 3 slot (đã claim / chưa claim).
     */
    List<DailyCheckin> findByUserIdAndCheckinDate(Long userId, LocalDate checkinDate);

    /**
     * Kiểm tra nhanh: user đã claim slot cụ thể trong ngày chưa?
     * Tại sao: Chống double-claim ở Service trước khi insert.
     */
    boolean existsByUserIdAndCheckinDateAndSlot(Long userId, LocalDate checkinDate, int slot);
}
