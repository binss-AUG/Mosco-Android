package com.vn.jet.mosco.spinserver.repository;

import com.vn.jet.mosco.spinserver.model.User;
import com.vn.jet.mosco.spinserver.model.UserMail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserMailRepository extends JpaRepository<UserMail, Long> {
    List<UserMail> findByUserId(Long userId);
    void deleteByUser(User user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from UserMail m where m.id = :id")
    Optional<UserMail> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from UserMail m where m.user.id = :userId and m.received = false")
    List<UserMail> findUnreceivedMailsForUpdate(@Param("userId") Long userId);
}
