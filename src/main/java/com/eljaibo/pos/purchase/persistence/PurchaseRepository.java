package com.eljaibo.pos.purchase.persistence;

import com.eljaibo.pos.purchase.domain.Purchase;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseRepository
        extends JpaRepository<Purchase, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT p
        FROM Purchase p
        WHERE p.id = :purchaseId
        AND p.branch.id = :branchId
        """)
    Optional<Purchase> findForPayment(
            @Param("purchaseId") UUID purchaseId,
            @Param("branchId") UUID branchId
    );

    List<Purchase> findAllByBranchIdOrderByCreatedAtDesc(
            UUID branchId
    );
}