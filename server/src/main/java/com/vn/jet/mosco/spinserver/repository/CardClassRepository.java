package com.vn.jet.mosco.spinserver.repository;

import com.vn.jet.mosco.spinserver.model.CardClass;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CardClassRepository extends JpaRepository<CardClass, Long> {
    Optional<CardClass> findByName(String name);
}
