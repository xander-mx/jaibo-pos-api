package com.eljaibo.pos.cash.persistence;

import com.eljaibo.pos.cash.domain.CashMovement;
import com.eljaibo.pos.cash.domain.CashMovementDirection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface CashMovementRepository
        extends JpaRepository<CashMovement, UUID> {

    @Query("""
            SELECT COALESCE(SUM(m.amount), 0)
            FROM CashMovement m
            WHERE m.cashSession.id = :cashSessionId
              AND m.direction = :direction
            """)
    BigDecimal sumAmountByCashSessionAndDirection(
            @Param("cashSessionId") UUID cashSessionId,
            @Param("direction") CashMovementDirection direction
    );

    List<CashMovement>
    findAllByCashSessionIdOrderByOccurredAtAsc(
            UUID cashSessionId
    );
}