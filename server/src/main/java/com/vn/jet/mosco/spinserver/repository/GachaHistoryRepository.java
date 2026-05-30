package com.vn.jet.mosco.spinserver.repository;

import com.vn.jet.mosco.spinserver.model.GachaHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GachaHistoryRepository extends JpaRepository<GachaHistory, Long> {

    List<GachaHistory> findByUserIdOrderByRolledAtDesc(Long userId);

    long countByUserId(Long userId);

    long countByUserIdAndSource(Long userId, String source);
}
