package com.eljaibo.pos.sale.persistence;

import com.eljaibo.pos.sale.domain.SalePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;
import java.util.UUID;

@RepositoryRestResource(exported = false)
public interface SalePaymentRepository
        extends JpaRepository<SalePayment, UUID> {

    List<SalePayment> findAllBySaleIdOrderByReceivedAtAsc(
            UUID saleId
    );
}