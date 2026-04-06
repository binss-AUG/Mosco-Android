package com.vn.jet.mosco.spinserver.repository;

import com.vn.jet.mosco.spinserver.model.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    /**
     * Lấy danh sách bạn bè đã ACCEPTED của user.
     * Tại sao dùng OR: user có thể là requester HOẶC addressee.
     */
    @Query("SELECT f FROM Friendship f WHERE f.status = 1 AND (f.requesterId = :userId OR f.addresseeId = :userId)")
    List<Friendship> findAcceptedFriendships(@Param("userId") Long userId);

    /**
     * Lấy danh sách lời mời đang PENDING mà user nhận được.
     * Chỉ hiện khi user là addressee (người được mời).
     */
    @Query("SELECT f FROM Friendship f WHERE f.status = 0 AND f.addresseeId = :userId")
    List<Friendship> findPendingRequests(@Param("userId") Long userId);

    /**
     * Kiểm tra đã tồn tại quan hệ chưa (cả 2 chiều).
     * Tại sao: Chống gửi trùng lời mời kết bạn.
     */
    @Query("SELECT f FROM Friendship f WHERE (f.requesterId = :uid1 AND f.addresseeId = :uid2) OR (f.requesterId = :uid2 AND f.addresseeId = :uid1)")
    Optional<Friendship> findExistingFriendship(@Param("uid1") Long uid1, @Param("uid2") Long uid2);
}
