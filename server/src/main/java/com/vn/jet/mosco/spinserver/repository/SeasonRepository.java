package com.vn.jet.mosco.spinserver.repository;

import com.vn.jet.mosco.spinserver.model.Season;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SeasonRepository extends JpaRepository<Season, Long> {
    Optional<Season> findByName(String name);
}
