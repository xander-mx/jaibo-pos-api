package com.eljaibo.pos.cycle.application;

import com.eljaibo.pos.business.domain.Branch;
import com.eljaibo.pos.cash.domain.CashSessionStatus;
import com.eljaibo.pos.cash.persistence.CashSessionRepository;
import com.eljaibo.pos.cycle.domain.OperatingCycle;
import com.eljaibo.pos.cycle.domain.OperatingCycleStatus;
import com.eljaibo.pos.cycle.persistence.OperatingCycleRepository;
import com.eljaibo.pos.user.domain.AppUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

@Service
public class OperatingCycleService {

    private final OperatingCycleRepository operatingCycleRepository;
    private final CashSessionRepository cashSessionRepository;

    public OperatingCycleService(
            OperatingCycleRepository operatingCycleRepository,
            CashSessionRepository cashSessionRepository
    ) {
        this.operatingCycleRepository = operatingCycleRepository;
        this.cashSessionRepository = cashSessionRepository;
    }

    @Transactional
    public OperatingCycle resolveOpenCycle(
            Branch branch,
            AppUser currentUser,
            LocalDate businessDate
    ) {
        LocalDate startsOn = calculateStartsOn(businessDate);

        Optional<OperatingCycle> targetCycle =
                operatingCycleRepository
                        .findByBranchIdAndStartsOn(
                                branch.getId(),
                                startsOn
                        );

        if (targetCycle.isPresent()) {
            return resolveExistingCycle(
                    targetCycle.get(),
                    branch,
                    currentUser
            );
        }

        closePreviousCycleIfNecessary(
                branch,
                currentUser,
                startsOn
        );

        return operatingCycleRepository.save(
                OperatingCycle.open(branch, startsOn)
        );
    }

    private OperatingCycle resolveExistingCycle(
            OperatingCycle cycle,
            Branch branch,
            AppUser currentUser
    ) {
        if (cycle.getStatus() == OperatingCycleStatus.OPEN) {
            return cycle;
        }

        if (cycle.getStatus() == OperatingCycleStatus.CLOSED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The operating cycle is already closed"
            );
        }

        closePreviousCycleIfNecessary(
                branch,
                currentUser,
                cycle.getStartsOn()
        );

        cycle.activate();
        return cycle;
    }

    private void closePreviousCycleIfNecessary(
            Branch branch,
            AppUser currentUser,
            LocalDate newCycleStartsOn
    ) {
        Optional<OperatingCycle> currentCycle =
                operatingCycleRepository.findByBranchIdAndStatus(
                        branch.getId(),
                        OperatingCycleStatus.OPEN
                );

        if (currentCycle.isEmpty()) {
            return;
        }

        OperatingCycle previousCycle = currentCycle.get();

        if (previousCycle.getStartsOn().equals(newCycleStartsOn)) {
            return;
        }

        if (previousCycle.getStartsOn().isAfter(newCycleStartsOn)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot open a cycle before the current cycle"
            );
        }

        boolean hasOpenCashSession =
                cashSessionRepository.existsByOperatingCycleIdAndStatus(
                        previousCycle.getId(),
                        CashSessionStatus.OPEN
                );

        if (hasOpenCashSession) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The previous cash session must be closed first"
            );
        }

        previousCycle.close(currentUser);
    }

    private LocalDate calculateStartsOn(LocalDate businessDate) {
        if (businessDate == null) {
            throw new IllegalArgumentException(
                    "businessDate is required"
            );
        }

        return businessDate.with(
                TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY)
        );
    }
}