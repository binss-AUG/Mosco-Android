package com.vn.jet.mosco.spinserver.repository;

import com.vn.jet.mosco.spinserver.model.StageSession;
import com.vn.jet.mosco.spinserver.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface StageSessionRepository extends JpaRepository<StageSession, Long> {
    List<StageSession> findByUserAndStatus(User user, String status);
    List<StageSession> findByUserAndStatusIn(User user, List<String> statuses);
    List<StageSession> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}
