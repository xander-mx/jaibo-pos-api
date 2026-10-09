package com.eljaibo.pos.sale.persistence;

import com.eljaibo.pos.sale.domain.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SaleItemRepository
        extends JpaRepository<SaleItem, UUID> {

    List<SaleItem> findAllBySaleIdAndRemovedFalseOrderByCreatedAtAsc(
            UUID saleId
    );

    Optional<SaleItem> findByIdAndSaleIdAndRemovedFalse(
            UUID id,
            UUID saleId
    );
}