package com.vn.jet.mosco.spinserver.repository;

import com.vn.jet.mosco.spinserver.model.UserItem;
import com.vn.jet.mosco.spinserver.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserItemRepository extends JpaRepository<UserItem, Long> {
    List<UserItem> findByUserId(Long userId);
    Optional<UserItem> findByUserIdAndItemCode(Long userId, String itemCode);
    void deleteByUser(User user);
}
