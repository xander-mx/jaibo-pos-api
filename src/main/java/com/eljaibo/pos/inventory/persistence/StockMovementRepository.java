package com.eljaibo.pos.inventory.persistence;

import com.eljaibo.pos.inventory.domain.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StockMovementRepository
        extends JpaRepository<StockMovement, UUID> {

    boolean existsByStockProductId(UUID productId);
}