package com.eljaibo.pos.sale.persistence;

import com.eljaibo.pos.sale.domain.CreditPaymentApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;
import java.util.UUID;

@RepositoryRestResource(exported = false)
public interface CreditPaymentApplicationRepository
        extends JpaRepository<
        CreditPaymentApplication,
        UUID
        > {

    List<CreditPaymentApplication>
    findAllByCreditPaymentId(
            UUID creditPaymentId
    );

    List<CreditPaymentApplication>
    findAllByReceivableId(
            UUID receivableId
    );
}