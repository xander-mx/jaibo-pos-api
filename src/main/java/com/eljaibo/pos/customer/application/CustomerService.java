package com.eljaibo.pos.customer.application;

import com.eljaibo.pos.business.domain.Business;
import com.eljaibo.pos.business.persistence.BusinessRepository;
import com.eljaibo.pos.customer.api.CreateCustomerRequest;
import com.eljaibo.pos.customer.api.CustomerResponse;
import com.eljaibo.pos.customer.api.UpdateCustomerRequest;
import com.eljaibo.pos.customer.domain.Customer;
import com.eljaibo.pos.customer.persistence.CustomerRepository;
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

    public CustomerService(
            CustomerRepository customerRepository,
            BusinessRepository businessRepository
    ) {
        this.customerRepository = customerRepository;
        this.businessRepository = businessRepository;
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
    public List<CustomerResponse> findActive(
            UUID businessId,
            String search
    ) {
        List<Customer> customers =
                search == null || search.isBlank()
                        ? customerRepository
                        .findAllByBusinessIdAndActiveTrueOrderByNameAsc(
                                businessId
                        )
                        : customerRepository
                        .findAllByBusinessIdAndActiveTrueAndNameContainingIgnoreCaseOrderByNameAsc(
                                businessId,
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
}