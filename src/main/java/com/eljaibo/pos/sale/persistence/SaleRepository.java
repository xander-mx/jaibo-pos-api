package com.eljaibo.pos.sale.persistence;

import com.eljaibo.pos.sale.domain.Sale;
import com.eljaibo.pos.sale.domain.SaleStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RepositoryRestResource(exported = false)
public interface SaleRepository extends JpaRepository<Sale, UUID> {

    // Call only while holding the cash-session write lock in the same transaction.
    @Query("select coalesce(max(s.saleNumber), 0) from Sale s where s.cashSession.id = :sessionId")
    int findLastSaleNumber(@Param("sessionId") UUID sessionId);

    List<Sale> findAllByBusinessIdAndBranchIdAndStatusOrderByCreatedAtAscSaleNumberAsc(
            UUID businessId, UUID branchId, SaleStatus status);

    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE
    )
    Optional<Sale> findByIdAndBusinessIdAndBranchId(
            UUID id,
            UUID businessId,
            UUID branchId
    );

    boolean existsByCashSessionIdAndStatus(
            UUID cashSessionId,
            SaleStatus status
    );

}
