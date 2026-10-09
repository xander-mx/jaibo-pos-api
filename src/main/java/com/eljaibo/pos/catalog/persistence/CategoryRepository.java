package com.eljaibo.pos.catalog.persistence;

import com.eljaibo.pos.catalog.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository
        extends JpaRepository<Category, UUID> {

    List<Category>
    findAllByBusinessIdAndActiveTrueOrderByDisplayOrderAscNameAsc(
            UUID businessId
    );

    boolean existsByBusinessIdAndNameIgnoreCase(
            UUID businessId,
            String name
    );

    Optional<Category> findByIdAndBusinessId(
            UUID id,
            UUID businessId
    );
}