package com.eljaibo.pos.customer.application;

import com.eljaibo.pos.business.domain.Business;
import com.eljaibo.pos.business.persistence.BusinessRepository;
import com.eljaibo.pos.customer.api.CreateCustomerRequest;
import com.eljaibo.pos.customer.api.CustomerResponse;
import com.eljaibo.pos.customer.api.UpdateCustomerRequest;
import com.eljaibo.pos.customer.domain.Customer;
import com.eljaibo.pos.customer.persistence.CustomerRepository;
import com.eljaibo.pos.sale.domain.ReceivableStatus;
import com.eljaibo.pos.sale.persistence.ReceivableRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final BusinessRepository businessRepository;
    private final ReceivableRepository receivableRepository;

    public CustomerService(
            CustomerRepository customerRepository,
            BusinessRepository businessRepository,
            ReceivableRepository receivableRepository
    ) {
        this.customerRepository = customerRepository;
        this.businessRepository = businessRepository;
        this.receivableRepository = receivableRepository;
    }

    @Transactional
    public CustomerResponse create(
            UUID businessId,
            CreateCustomerRequest request
    ) {
        Business business = businessRepository.findById(businessId)
                .filter(Business::isActive)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Active business not found"
                ));

        Customer customer = new Customer(
                business,
                request.name(),
                request.phone(),
                request.email(),
                Boolean.TRUE.equals(request.frequent()),
                Boolean.TRUE.equals(request.wholesale()),
                Boolean.TRUE.equals(request.creditEnabled())
        );

        return toResponse(customerRepository.save(customer));
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> findAll(
            UUID businessId,
            String search,
            boolean active
    ) {
        List<Customer> customers =
                search == null || search.isBlank()
                        ? customerRepository
                        .findAllByBusinessIdAndActiveOrderByNameAsc(
                                businessId,
                                active
                        )
                        : customerRepository
                        .findAllByBusinessIdAndActiveAndNameContainingIgnoreCaseOrderByNameAsc(
                                businessId,
                                active,
                                search.trim()
                        );

        return customers.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CustomerResponse update(
            UUID businessId,
            UUID customerId,
            UpdateCustomerRequest request
    ) {
        Customer customer = customerRepository
                .findByIdAndBusinessId(
                        customerId,
                        businessId
                )
                .orElseThrow(
                        () -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Customer not found"
                        )
                );

        customer.changeContactInformation(
                request.name(),
                request.phone(),
                request.email()
        );

        customer.configureClassification(
                request.frequent(),
                request.wholesale()
        );

        if (request.creditEnabled()) {
            customer.enableCredit();
        } else {
            customer.disableCredit();
        }

        return toResponse(customer);
    }

    @Transactional
    public CustomerResponse deactivate(
            UUID businessId,
            UUID customerId
    ) {
        Customer customer = findCustomer(
                businessId,
                customerId
        );

        boolean hasOpenReceivables =
                receivableRepository.existsByCustomerIdAndStatus(
                        customerId,
                        ReceivableStatus.OPEN
                );

        if (hasOpenReceivables) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Customer has outstanding receivables"
            );
        }

        customer.deactivate();

        return toResponse(customer);
    }

    @Transactional
    public CustomerResponse activate(
            UUID businessId,
            UUID customerId
    ) {
        Customer customer = findCustomer(
                businessId,
                customerId
        );

        customer.activate();

        return toResponse(customer);
    }

    private CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getName(),
                customer.getPhone(),
                customer.getEmail(),
                customer.isFrequent(),
                customer.isWholesale(),
                customer.isCreditEnabled(),
                customer.isActive()
        );
    }

    private Customer findCustomer(
            UUID businessId,
            UUID customerId
    ) {
        return customerRepository
                .findByIdAndBusinessId(
                        customerId,
                        businessId
                )
                .orElseThrow(
                        () -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Customer not found"
                        )
                );
    }
}