package com.vn.jet.mosco.spinserver.repository;

import com.vn.jet.mosco.spinserver.dto.CardSummaryDto;
import com.vn.jet.mosco.spinserver.model.Card;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CardRepository extends JpaRepository<Card, String> {

    @Query("SELECT new com.vn.jet.mosco.spinserver.dto.CardSummaryDto(c.id, m.name, s.name, c.frontImageId) " +
           "FROM Card c " +
           "JOIN c.member m " +
           "JOIN c.season s " +
           "WHERE (:memberId IS NULL OR m.id = :memberId) " +
           "AND (:seasonId IS NULL OR s.id = :seasonId) " +
           "AND (:search IS NULL OR LOWER(m.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(c.collectionNo) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<CardSummaryDto> findCards(@Param("memberId") Long memberId, 
                                   @Param("seasonId") Long seasonId, 
                                   @Param("search") String search, 
                                   Pageable pageable);

    @Query("SELECT new com.vn.jet.mosco.spinserver.dto.CardSummaryDto(c.id, m.name, s.name, c.frontImageId) " +
           "FROM Card c " +
           "JOIN c.member m " +
           "JOIN c.season s " +
           "WHERE c.updatedAt > :lastSyncTime")
    List<CardSummaryDto> findUpdatedCards(@Param("lastSyncTime") java.time.LocalDateTime lastSyncTime);
}
