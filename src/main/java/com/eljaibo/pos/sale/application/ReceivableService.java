package com.eljaibo.pos.sale.application;

import com.eljaibo.pos.customer.domain.Customer;
import com.eljaibo.pos.customer.persistence.CustomerRepository;
import com.eljaibo.pos.sale.api.response.ReceivableResponse;
import com.eljaibo.pos.sale.domain.ReceivableStatus;
import com.eljaibo.pos.sale.persistence.ReceivableRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class ReceivableService {

    private final ReceivableRepository receivables;
    private final CustomerRepository customers;

    public ReceivableService(
            ReceivableRepository receivables,
            CustomerRepository customers
    ) {
        this.receivables = receivables;
        this.customers = customers;
    }

    @Transactional(readOnly = true)
    public List<ReceivableResponse> findOpenByCustomer(
            UUID businessId,
            UUID customerId
    ) {
        customers
                .findByIdAndBusinessId(
                        customerId,
                        businessId
                )
                .filter(Customer::isActive)
                .orElseThrow(
                        () -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Active customer not found"
                        )
                );

        return receivables
                .findAllByCustomerIdAndStatusOrderByOpenedAtAsc(
                        customerId,
                        ReceivableStatus.OPEN
                )
                .stream()
                .map(ReceivableResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReceivableResponse> findPaidByCustomer(
            UUID businessId,
            UUID customerId
    ) {
        customers
                .findByIdAndBusinessId(
                        customerId,
                        businessId
                )
                .filter(Customer::isActive)
                .orElseThrow(
                        () -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Active customer not found"
                        )
                );

        return receivables
                .findAllByCustomerIdAndStatusOrderByPaidAtDesc(
                        customerId,
                        ReceivableStatus.PAID
                )
                .stream()
                .map(ReceivableResponse::from)
                .toList();
    }
}