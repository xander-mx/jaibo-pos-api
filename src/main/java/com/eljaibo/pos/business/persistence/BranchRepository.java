package com.eljaibo.pos.business.persistence;

import com.eljaibo.pos.business.domain.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchRepository extends JpaRepository<Branch, UUID> {

    Optional<Branch> findByBusinessIdAndCodeIgnoreCase(
            UUID businessId,
            String code
    );

    List<Branch> findAllByBusinessIdAndActiveTrueOrderByName(
            UUID businessId
    );
}