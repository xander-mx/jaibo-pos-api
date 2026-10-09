package com.eljaibo.pos.purchase.persistence;

import com.eljaibo.pos.purchase.domain.PurchasePayment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PurchasePaymentRepository
        extends JpaRepository<PurchasePayment, UUID> {

    List<PurchasePayment>
    findAllByPurchaseIdOrderByPaidAtAscIdAsc(
            UUID purchaseId
    );

    @Query("""
        SELECT COALESCE(SUM(p.amount), 0)
        FROM PurchasePayment p
        WHERE p.purchase.id = :purchaseId
        """)
    BigDecimal sumAmountByPurchaseId(
            @Param("purchaseId") UUID purchaseId
    );
}