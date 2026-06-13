package com.vn.jet.mosco.spinserver.repository;

import com.vn.jet.mosco.spinserver.model.CoupleStreak;
import com.vn.jet.mosco.spinserver.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CoupleStreakRepository extends JpaRepository<CoupleStreak, Long> {

    @Query("SELECT s FROM CoupleStreak s WHERE " +
           "(s.requester = :u1 AND s.partner = :u2) OR " +
           "(s.requester = :u2 AND s.partner = :u1)")
    Optional<CoupleStreak> findBetweenUsers(@Param("u1") User u1, @Param("u2") User u2);

    @Query("SELECT s FROM CoupleStreak s WHERE " +
           "(s.requester.id = :id1 AND s.partner.id = :id2) OR " +
           "(s.requester.id = :id2 AND s.partner.id = :id1)")
    Optional<CoupleStreak> findBetweenUserIds(@Param("id1") Long id1, @Param("id2") Long id2);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM CoupleStreak s WHERE " +
           "(s.requester.id = :id1 AND s.partner.id = :id2) OR " +
           "(s.requester.id = :id2 AND s.partner.id = :id1)")
    void deleteBetweenUsers(@Param("id1") Long id1, @Param("id2") Long id2);

    @Query("SELECT MAX(s.streakCount) FROM CoupleStreak s WHERE (s.requester.id = :userId OR s.partner.id = :userId) AND s.status = 'ACTIVE'")
    Integer findMaxStreakCountByUserId(@Param("userId") Long userId);

    java.util.List<CoupleStreak> findTop10ByStatusOrderByStreakCountDesc(String status);
}
