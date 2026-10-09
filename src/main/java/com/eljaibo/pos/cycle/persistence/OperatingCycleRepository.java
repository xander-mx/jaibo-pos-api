package com.eljaibo.pos.cycle.persistence;

import com.eljaibo.pos.cycle.domain.OperatingCycle;
import com.eljaibo.pos.cycle.domain.OperatingCycleStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OperatingCycleRepository
        extends JpaRepository<OperatingCycle, UUID> {

    Optional<OperatingCycle> findByBranchIdAndStartsOn(
            UUID branchId,
            LocalDate startsOn
    );

    Optional<OperatingCycle> findByBranchIdAndStatus(
            UUID branchId,
            OperatingCycleStatus status
    );

    List<OperatingCycle> findAllByBranchIdOrderByStartsOnDesc(
            UUID branchId
    );
}