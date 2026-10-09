package com.eljaibo.pos.catalog.persistence;

import com.eljaibo.pos.catalog.domain.ProductPresentation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductPresentationRepository
        extends JpaRepository<ProductPresentation, UUID> {

    Optional<ProductPresentation>
    findByIdAndProductBusinessId(
            UUID id,
            UUID businessId
    );

    boolean existsByProductIdAndNameIgnoreCase(
            UUID productId,
            String name
    );

    List<ProductPresentation>
    findAllByProductIdAndActiveTrueOrderByDisplayOrderAscNameAsc(
            UUID productId
    );

    List<ProductPresentation>
    findAllByProductIdAndActiveTrueOrderByDisplayOrderAsc(
            UUID productId
    );
}