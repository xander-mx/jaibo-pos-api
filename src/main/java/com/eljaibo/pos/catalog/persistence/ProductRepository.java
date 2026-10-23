package com.eljaibo.pos.catalog.persistence;

import com.eljaibo.pos.catalog.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository
        extends JpaRepository<Product, UUID> {

    Optional<Product> findByIdAndBusinessId(
            UUID id,
            UUID businessId
    );

    boolean existsByBusinessIdAndNameIgnoreCase(
            UUID businessId,
            String name
    );

    List<Product>
    findAllByBusinessIdAndSellableTrueAndActiveTrueOrderByCategoryDisplayOrderAscDisplayOrderAscNameAsc(
            UUID businessId
    );

    List<Product>
    findAllByBusinessIdAndActiveTrueOrderByCategoryDisplayOrderAscDisplayOrderAscNameAsc(
            UUID businessId
    );

    List<Product>
    findAllByBusinessIdAndPurchasableTrueAndActiveTrueOrderByCategoryDisplayOrderAscDisplayOrderAscNameAsc(
            UUID businessId
    );

    boolean existsByBusinessIdAndNameIgnoreCaseAndIdNot(
            UUID businessId,
            String name,
            UUID id
    );
}