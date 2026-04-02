package com.vn.jet.mosco.spinserver.repository;

import com.vn.jet.mosco.spinserver.model.UserCard;
import com.vn.jet.mosco.spinserver.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserCardRepository extends JpaRepository<UserCard, Long> {
    List<UserCard> findByUserId(Long userId);
    java.util.Optional<UserCard> findByUserIdAndCollectionId(Long userId, String collectionId);
    
    // Tìm thẻ theo đúng ID (Primary Key) và đối chiếu với User để đảm bảo chính chủ
    java.util.Optional<UserCard> findByIdAndUserId(Long id, Long userId);
    
    long countByUser(User user);
    void deleteByUser(User user);
}
