package com.eljaibo.pos.cash.persistence;

import com.eljaibo.pos.cash.domain.CashSession;
import com.eljaibo.pos.cash.domain.CashSessionStatus;
import com.eljaibo.pos.cycle.domain.OperatingCycleStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CashSessionRepository
        extends JpaRepository<CashSession, UUID> {

    Optional<CashSession>
    findByOperatingCycleIdAndStatus(
            UUID operatingCycleId,
            CashSessionStatus status
    );

    Optional<CashSession>
    findByOperatingCycleIdAndBusinessDate(
            UUID operatingCycleId,
            LocalDate businessDate
    );

    boolean existsByOperatingCycleIdAndStatus(
            UUID operatingCycleId,
            CashSessionStatus status
    );

    List<CashSession>
    findAllByOperatingCycleIdOrderByBusinessDateDesc(
            UUID operatingCycleId
    );

    Optional<CashSession>
    findByOperatingCycleBranchIdAndStatus(
            UUID branchId,
            CashSessionStatus status
    );

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<CashSession>
    findByIdAndOperatingCycleBranchBusinessId(
            UUID id,
            UUID businessId
    );

    @Query("""
        select cs
        from CashSession cs
        where cs.id = :id
          and cs.operatingCycle.branch.business.id = :businessId
        """)
    Optional<CashSession> findForRead(
            @Param("id") UUID id,
            @Param("businessId") UUID businessId
    );

    Optional<CashSession>
    findFirstByOperatingCycleBranchIdAndOperatingCycleStatusOrderByBusinessDateDescOpenedAtDesc(
            UUID branchId,
            OperatingCycleStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT cs
        FROM CashSession cs
        WHERE cs.operatingCycle.branch.id = :branchId
          AND cs.status = :status
        """)
    Optional<CashSession> findOpenSessionForUpdate(
            @Param("branchId") UUID branchId,
            @Param("status") CashSessionStatus status
    );
}
