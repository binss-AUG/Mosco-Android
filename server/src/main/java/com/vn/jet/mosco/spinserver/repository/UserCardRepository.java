package com.vn.jet.mosco.spinserver.repository;

import com.vn.jet.mosco.spinserver.model.UserCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserCardRepository extends JpaRepository<UserCard, Long> {
    List<UserCard> findByUserId(Long userId);
    java.util.Optional<UserCard> findByIdAndUserId(Long id, Long userId);
    void deleteByUser(com.vn.jet.mosco.spinserver.model.User user);

    @org.springframework.data.jpa.repository.Query("SELECT uc FROM UserCard uc JOIN FETCH uc.user")
    java.util.List<UserCard> findAllWithUser();

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT c FROM UserCard c WHERE c.id = :id")
    java.util.Optional<UserCard> findWithLockById(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(DISTINCT uc.collectionId) FROM UserCard uc WHERE uc.user.id = :userId")
    long countUniqueUnlockedCardsByUserId(@org.springframework.data.repository.query.Param("userId") Long userId);

    @org.springframework.data.jpa.repository.Query("SELECT MAX(uc.upgradeLevel) FROM UserCard uc JOIN Card c ON uc.collectionId = c.id WHERE uc.user.id = :userId AND c.cardClass.name = :className")
    Integer findMaxUpgradeLevelByUserIdAndClassName(@org.springframework.data.repository.query.Param("userId") Long userId, @org.springframework.data.repository.query.Param("className") String className);
}
