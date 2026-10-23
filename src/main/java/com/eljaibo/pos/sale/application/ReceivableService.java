package com.eljaibo.pos.sale.application;

import com.eljaibo.pos.customer.domain.Customer;
import com.eljaibo.pos.customer.persistence.CustomerRepository;
import com.eljaibo.pos.sale.api.response.ReceivableDetailResponse;
import com.eljaibo.pos.sale.api.response.ReceivableResponse;
import com.eljaibo.pos.sale.api.response.SaleItemResponse;
import com.eljaibo.pos.sale.domain.Receivable;
import com.eljaibo.pos.sale.domain.ReceivableStatus;
import com.eljaibo.pos.sale.persistence.CreditPaymentApplicationRepository;
import com.eljaibo.pos.sale.persistence.ReceivableRepository;
import com.eljaibo.pos.sale.persistence.SaleItemRepository;
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
    private final SaleItemRepository saleItems;
    private final CreditPaymentApplicationRepository applications;

    public ReceivableService(
            ReceivableRepository receivables,
            CustomerRepository customers,
            SaleItemRepository saleItems,
            CreditPaymentApplicationRepository applications
    ) {
        this.receivables = receivables;
        this.customers = customers;
        this.saleItems = saleItems;
        this.applications = applications;
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

    @Transactional(readOnly = true)
    public ReceivableDetailResponse findById(
            UUID businessId,
            UUID customerId,
            UUID receivableId
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

        Receivable receivable =
                receivables.findById(receivableId)
                        .filter(value ->
                                value.getCustomer()
                                        .getId()
                                        .equals(customerId)
                        )
                        .filter(value ->
                                value.getCustomer()
                                        .getBusiness()
                                        .getId()
                                        .equals(businessId)
                        )
                        .orElseThrow(
                                () -> new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Receivable not found"
                                )
                        );

        var sale = receivable.getSale();

        List<SaleItemResponse> items =
                saleItems
                        .findAllBySaleIdAndRemovedFalseOrderByCreatedAtAsc(
                                sale.getId()
                        )
                        .stream()
                        .map(SaleItemResponse::from)
                        .toList();

        List<ReceivableDetailResponse.Payment> payments =
                applications
                        .findAllByReceivableId(receivableId)
                        .stream()
                        .map(application ->
                                new ReceivableDetailResponse.Payment(
                                        application
                                                .getCreditPayment()
                                                .getId(),

                                        application.getAmount(),

                                        application
                                                .getCreditPayment()
                                                .getReceivedAt()
                                )
                        )
                        .toList();

        return new ReceivableDetailResponse(
                receivable.getId(),
                sale.getId(),
                sale.getSaleNumber(),
                receivable.getOpenedAt(),

                receivable.getOriginalAmount(),
                receivable.getBalance(),

                sale.getSubtotal(),
                sale.getLineDiscountTotal(),
                sale.getCourtesyTotal(),
                sale.getSaleDiscountAmount(),
                sale.getTotal(),

                items,
                payments
        );
    }
}