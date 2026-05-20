package com.vn.jet.mosco.spinserver.repository;

import com.vn.jet.mosco.spinserver.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByIngameName(String ingameName);
    
    // Ranking Fallbacks
    java.util.List<User> findTop10ByOrderByLevelDesc();
    java.util.List<User> findTop10ByOrderByTotalDiamondsDesc();
    java.util.List<User> findTop10ByOrderByBestStreakDesc();

    @org.springframework.data.jpa.repository.Query(value = "SELECT u.* FROM users u LEFT JOIN (SELECT user_id, COUNT(*) as cnt FROM user_unlocked_collections GROUP BY user_id) as sub ON u.id = sub.user_id ORDER BY COALESCE(sub.cnt, 0) DESC LIMIT 10", nativeQuery = true)
    java.util.List<User> findTop10ByCollectionCount();

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("UPDATE User u SET u.totalDiamonds = u.diamonds WHERE u.totalDiamonds = 0 AND u.diamonds > 0")
    int repairTotalDiamondsDirectly();
}
