package com.eljaibo.pos.inventory.persistence;

import com.eljaibo.pos.inventory.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockRepository
        extends JpaRepository<Stock, UUID> {

    Optional<Stock>
    findByBranchIdAndProductId(
            UUID branchId,
            UUID productId
    );

    List<Stock>
    findAllByBranchIdOrderByProductNameAsc(
            UUID branchId
    );

}