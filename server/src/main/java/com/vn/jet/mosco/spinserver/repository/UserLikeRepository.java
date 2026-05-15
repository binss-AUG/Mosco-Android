package com.vn.jet.mosco.spinserver.repository;

import com.vn.jet.mosco.spinserver.model.UserLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserLikeRepository extends JpaRepository<UserLike, Long> {

    /**
     * Truy vấn bản ghi thích hồ sơ dựa trên người thích và người được thích.
     * Tại sao: Hỗ trợ xác minh trạng thái đã thích (Liked) để Client hiển thị chính xác,
     * đồng thời dùng để xác định đối tượng cần xóa khi người dùng thực hiện Bỏ thích (Unlike).
     */
    Optional<UserLike> findByLikerIdAndTargetUserId(Long likerId, Long targetUserId);

    /**
     * Kiểm tra nhanh sự tồn tại của lượt thích mà không cần tải toàn bộ đối tượng.
     */
    boolean existsByLikerIdAndTargetUserId(Long likerId, Long targetUserId);
}
