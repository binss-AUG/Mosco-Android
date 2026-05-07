package com.vn.jet.mosco.spinserver.repository;

import com.vn.jet.mosco.spinserver.model.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByName(String name);
}
