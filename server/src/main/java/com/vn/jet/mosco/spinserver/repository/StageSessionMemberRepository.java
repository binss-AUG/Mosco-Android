package com.vn.jet.mosco.spinserver.repository;

import com.vn.jet.mosco.spinserver.model.StageSession;
import com.vn.jet.mosco.spinserver.model.StageSessionMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface StageSessionMemberRepository extends JpaRepository<StageSessionMember, Long> {
    List<StageSessionMember> findByStageSession(StageSession stageSession);
    void deleteByStageSessionId(Long sessionId);
}
