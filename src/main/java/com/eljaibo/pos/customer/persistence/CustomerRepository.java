package com.eljaibo.pos.customer.persistence;

import com.eljaibo.pos.customer.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository
        extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByIdAndBusinessId(
            UUID id,
            UUID businessId
    );

    List<Customer> findAllByBusinessIdAndActiveOrderByNameAsc(
            UUID businessId,
            boolean active
    );

    List<Customer> findAllByBusinessIdAndActiveAndNameContainingIgnoreCaseOrderByNameAsc(
            UUID businessId,
            boolean active,
            String name
    );
}