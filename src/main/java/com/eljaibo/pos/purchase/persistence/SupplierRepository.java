package com.eljaibo.pos.purchase.persistence;

import com.eljaibo.pos.purchase.domain.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierRepository
        extends JpaRepository<Supplier, UUID> {

    List<Supplier>
    findAllByBusinessIdOrderByNameAsc(
            UUID businessId
    );

    List<Supplier>
    findAllByBusinessIdAndActiveTrueOrderByNameAsc(
            UUID businessId
    );

    Optional<Supplier>
    findByIdAndBusinessId(
            UUID id,
            UUID businessId
    );

    boolean existsByBusinessIdAndNameIgnoreCase(
            UUID businessId,
            String name
    );

    boolean existsByBusinessIdAndNameIgnoreCaseAndIdNot(
            UUID businessId,
            String name,
            UUID id
    );
}