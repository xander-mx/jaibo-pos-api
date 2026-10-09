package com.eljaibo.pos.terminal.persistence;

import com.eljaibo.pos.terminal.domain.Terminal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TerminalRepository
        extends JpaRepository<Terminal, UUID> {

    List<Terminal>
    findAllByBranchBusinessIdAndActiveTrueOrderByNameAsc(
            UUID businessId
    );

    Optional<Terminal>
    findByIdAndBranchBusinessIdAndActiveTrue(
            UUID id,
            UUID businessId
    );

    Optional<Terminal> findByIdAndBranchBusinessId(
            UUID id,
            UUID businessId
    );
}