package com.eljaibo.pos.sale.persistence;

import com.eljaibo.pos.sale.domain.Receivable;
import com.eljaibo.pos.sale.domain.ReceivableStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RepositoryRestResource(exported = false)
public interface ReceivableRepository
        extends JpaRepository<Receivable, UUID> {

    Optional<Receivable> findBySaleId(UUID saleId);

    List<Receivable>
    findAllByCustomerIdAndStatusOrderByOpenedAtAsc(
            UUID customerId,
            ReceivableStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Receivable>
    findAllByCustomerIdAndStatusOrderByOpenedAtAscIdAsc(
            UUID customerId,
            ReceivableStatus status
    );

    List<Receivable>
    findAllByCustomerIdAndStatusOrderByPaidAtDesc(
            UUID customerId,
            ReceivableStatus status
    );
}