package com.vn.jet.mosco.spinserver.repository;

import com.vn.jet.mosco.spinserver.model.ShopItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShopItemRepository extends JpaRepository<ShopItem, Long> {
    Optional<ShopItem> findByProductCode(String productCode);
}
