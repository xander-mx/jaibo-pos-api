package com.eljaibo.pos.cash.application;

import com.eljaibo.pos.business.domain.Business;
import com.eljaibo.pos.cash.api.CashMovementSummaryResponse;
import com.eljaibo.pos.cash.api.CashMovementTypeSummaryResponse;
import com.eljaibo.pos.cash.api.CashSessionResponse;
import com.eljaibo.pos.cash.api.CloseCashSessionRequest;
import com.eljaibo.pos.cash.api.OpenCashSessionRequest;
import com.eljaibo.pos.cash.api.ReopenCashSessionRequest;
import com.eljaibo.pos.cash.domain.CashMovement;
import com.eljaibo.pos.cash.domain.CashMovementDirection;
import com.eljaibo.pos.cash.domain.CashMovementType;
import com.eljaibo.pos.cash.domain.CashSession;
import com.eljaibo.pos.cash.domain.CashSessionEvent;
import com.eljaibo.pos.cash.domain.CashSessionStatus;
import com.eljaibo.pos.cash.persistence.CashMovementRepository;
import com.eljaibo.pos.cash.persistence.CashSessionEventRepository;
import com.eljaibo.pos.cash.persistence.CashSessionRepository;
import com.eljaibo.pos.cycle.application.OperatingCycleService;
import com.eljaibo.pos.cycle.domain.OperatingCycle;
import com.eljaibo.pos.cycle.domain.OperatingCycleStatus;
import com.eljaibo.pos.sale.domain.SaleStatus;
import com.eljaibo.pos.sale.persistence.SaleRepository;
import com.eljaibo.pos.terminal.domain.Terminal;
import com.eljaibo.pos.terminal.persistence.TerminalRepository;
import com.eljaibo.pos.user.domain.AppUser;
import com.eljaibo.pos.user.domain.UserRole;
import com.eljaibo.pos.user.persistence.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CashSessionService {

    private final CashSessionRepository cashSessionRepository;
    private final CashSessionEventRepository eventRepository;
    private final OperatingCycleService operatingCycleService;
    private final TerminalRepository terminalRepository;
    private final AppUserRepository appUserRepository;
    private final CashMovementRepository cashMovementRepository;
    private final SaleRepository saleRepository;
    private final Clock clock;

    public CashSessionService(
            CashSessionRepository cashSessionRepository,
            CashSessionEventRepository eventRepository,
            OperatingCycleService operatingCycleService,
            TerminalRepository terminalRepository,
            AppUserRepository appUserRepository,
            CashMovementRepository cashMovementRepository,
            SaleRepository saleRepository,
            Clock clock
    ) {
        this.cashSessionRepository = cashSessionRepository;
        this.eventRepository = eventRepository;
        this.operatingCycleService = operatingCycleService;
        this.terminalRepository = terminalRepository;
        this.appUserRepository = appUserRepository;
        this.cashMovementRepository = cashMovementRepository;
        this.saleRepository = saleRepository;
        this.clock = clock;
    }

    @Transactional
    public CashSessionResponse open(
            UUID currentUserId,
            UUID businessId,
            UUID terminalId,
            OpenCashSessionRequest request
    ) {
        AppUser currentUser = findCurrentUser(
                currentUserId,
                businessId
        );

        Terminal terminal = findActiveTerminal(
                terminalId,
                businessId
        );

        LocalDate businessDate = currentBusinessDate(
                terminal.getBranch().getBusiness()
        );

        OperatingCycle cycle =
                operatingCycleService.resolveOpenCycle(
                        terminal.getBranch(),
                        currentUser,
                        businessDate
                );

        cashSessionRepository
                .findByOperatingCycleIdAndStatus(
                        cycle.getId(),
                        CashSessionStatus.OPEN
                )
                .ifPresent(existing -> {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Ya existe una sesion de caja abierta"
                    );
                });

        cashSessionRepository
                .findByOperatingCycleIdAndBusinessDate(
                        cycle.getId(),
                        businessDate
                )
                .ifPresent(existing -> {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "The cash session for this date was already closed"
                    );
                });

        CashSession cashSession = cashSessionRepository.save(
                new CashSession(
                        cycle,
                        businessDate,
                        request.openingAmount(),
                        currentUser,
                        terminal
                )
        );

        eventRepository.save(
                CashSessionEvent.opened(
                        cashSession,
                        currentUser,
                        terminal
                )
        );

        terminal.markSeen();

        return toResponse(cashSession);
    }

    @Transactional(readOnly = true)
    public Optional<CashSessionResponse> findCurrent(
            UUID businessId,
            UUID terminalId
    ) {
        Terminal terminal = findActiveTerminal(
                terminalId,
                businessId
        );

        return cashSessionRepository
                .findByOperatingCycleBranchIdAndStatus(
                        terminal.getBranch().getId(),
                        CashSessionStatus.OPEN
                )
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<CashSessionResponse> findLatest(
            UUID businessId,
            UUID terminalId
    ) {
        Terminal terminal = findActiveTerminal(
                terminalId,
                businessId
        );

        return cashSessionRepository
                .findFirstByOperatingCycleBranchIdAndOperatingCycleStatusOrderByBusinessDateDescOpenedAtDesc(
                        terminal.getBranch().getId(),
                        OperatingCycleStatus.OPEN
                )
                .map(this::toResponse);
    }

    @Transactional
    public CashSessionResponse close(
            UUID currentUserId,
            UUID businessId,
            UUID terminalId,
            UUID cashSessionId,
            CloseCashSessionRequest request
    ) {
        AppUser currentUser = findCurrentUser(
                currentUserId,
                businessId
        );

        requireAdministrator(currentUser);

        Terminal terminal = findActiveTerminal(
                terminalId,
                businessId
        );

        CashSession cashSession = findCashSession(
                cashSessionId,
                businessId,
                terminal
        );

        boolean hasOpenSales =
                saleRepository.existsByCashSessionIdAndStatus(
                        cashSession.getId(),
                        SaleStatus.OPEN
                );

        if (hasOpenSales) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No puedes cerrar la caja mientras existan cuentas abiertas."
            );
        }

        BigDecimal expectedAmount =
                calculateExpectedAmount(cashSession);

        cashSession.close(
                expectedAmount,
                request.countedAmount(),
                currentUser,
                terminal
        );

        eventRepository.save(
                CashSessionEvent.closed(
                        cashSession,
                        currentUser,
                        terminal
                )
        );

        terminal.markSeen();

        return toResponse(cashSession);
    }

    @Transactional
    public CashSessionResponse reopen(
            UUID currentUserId,
            UUID businessId,
            UUID terminalId,
            UUID cashSessionId,
            ReopenCashSessionRequest request
    ) {
        AppUser currentUser = findCurrentUser(
                currentUserId,
                businessId
        );

        requireAdministrator(currentUser);

        Terminal terminal = findActiveTerminal(
                terminalId,
                businessId
        );

        CashSession cashSession = findCashSession(
                cashSessionId,
                businessId,
                terminal
        );

        if (cashSession.getOperatingCycle().getStatus()
                != OperatingCycleStatus.OPEN) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A cash session from a closed cycle cannot be reopened"
            );
        }

        cashSessionRepository
                .findByOperatingCycleBranchIdAndStatus(
                        terminal.getBranch().getId(),
                        CashSessionStatus.OPEN
                )
                .ifPresent(openSession -> {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Another cash session is already open"
                    );
                });

        cashSession.reopen(currentUser);

        eventRepository.save(
                CashSessionEvent.reopened(
                        cashSession,
                        currentUser,
                        terminal,
                        request.comment()
                )
        );

        terminal.markSeen();

        return toResponse(cashSession);
    }

    @Transactional(readOnly = true)
    public CashMovementSummaryResponse findMovementSummary(
            UUID businessId,
            UUID terminalId,
            UUID cashSessionId
    ) {
        Terminal terminal = findActiveTerminal(
                terminalId,
                businessId
        );

        CashSession cashSession = findCashSessionForRead(
                cashSessionId,
                businessId,
                terminal
        );

        var movements =
                cashMovementRepository
                        .findAllByCashSessionIdOrderByOccurredAtAsc(
                                cashSessionId
                        );

        var grouped =
                movements.stream()
                        .collect(
                                Collectors.groupingBy(
                                        movement ->
                                                new MovementKey(
                                                        movement.getMovementType(),
                                                        movement.getDirection()
                                                ),
                                        Collectors.reducing(
                                                BigDecimal.ZERO,
                                                CashMovement::getAmount,
                                                BigDecimal::add
                                        )
                                )
                        );

        List<CashMovementTypeSummaryResponse> summary =
                grouped.entrySet()
                        .stream()
                        .map(entry ->
                                new CashMovementTypeSummaryResponse(
                                        entry.getKey().type(),
                                        entry.getKey().direction(),
                                        entry.getValue()
                                                .setScale(
                                                        2,
                                                        RoundingMode.HALF_UP
                                                )
                                )
                        )
                        .toList();

        BigDecimal incomeTotal =
                summary.stream()
                        .filter(item ->
                                item.direction()
                                        == CashMovementDirection.IN
                        )
                        .map(CashMovementTypeSummaryResponse::total)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        BigDecimal outcomeTotal =
                summary.stream()
                        .filter(item ->
                                item.direction()
                                        == CashMovementDirection.OUT
                        )
                        .map(CashMovementTypeSummaryResponse::total)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        BigDecimal expectedAmount =
                cashSession.getOpeningAmount()
                        .add(incomeTotal)
                        .subtract(outcomeTotal)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        return new CashMovementSummaryResponse(
                cashSession.getId(),
                cashSession.getOpeningAmount(),
                incomeTotal,
                outcomeTotal,
                expectedAmount,
                summary
        );
    }

    private record MovementKey(
            CashMovementType type,
            CashMovementDirection direction
    ) {
    }

    private CashSession findCashSession(
            UUID cashSessionId,
            UUID businessId,
            Terminal terminal
    ) {
        CashSession cashSession = cashSessionRepository
                .findByIdAndOperatingCycleBranchBusinessId(
                        cashSessionId,
                        businessId
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Cash session not found"
                ));

        UUID sessionBranchId = cashSession
                .getOperatingCycle()
                .getBranch()
                .getId();

        if (!sessionBranchId.equals(terminal.getBranch().getId())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Cash session not found for this terminal"
            );
        }

        return cashSession;
    }

    private BigDecimal calculateExpectedAmount(
            CashSession cashSession
    ) {
        BigDecimal income =
                cashMovementRepository
                        .sumAmountByCashSessionAndDirection(
                                cashSession.getId(),
                                CashMovementDirection.IN
                        );

        BigDecimal outcome =
                cashMovementRepository
                        .sumAmountByCashSessionAndDirection(
                                cashSession.getId(),
                                CashMovementDirection.OUT
                        );

        return cashSession.getOpeningAmount()
                .add(income)
                .subtract(outcome)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void requireAdministrator(AppUser user) {
        if (user.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Administrator role is required"
            );
        }
    }

    private AppUser findCurrentUser(
            UUID currentUserId,
            UUID businessId
    ) {
        AppUser user = appUserRepository.findById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user no longer exists"
                ));

        boolean validUser = user.isActive()
                && user.getBusiness().getId().equals(businessId);

        if (!validUser) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user is not active"
            );
        }

        return user;
    }

    private Terminal findActiveTerminal(
            UUID terminalId,
            UUID businessId
    ) {
        return terminalRepository
                .findByIdAndBranchBusinessId(
                        terminalId,
                        businessId
                )
                .filter(Terminal::isActive)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Active terminal not found"
                ));
    }

    private LocalDate currentBusinessDate(Business business) {
        ZoneId zoneId = ZoneId.of(business.getTimezone());

        return clock.instant()
                .atZone(zoneId)
                .toLocalDate();
    }

    private CashSessionResponse toResponse(
            CashSession cashSession
    ) {
        OperatingCycle cycle = cashSession.getOperatingCycle();

        return new CashSessionResponse(
                cashSession.getId(),
                cycle.getId(),
                cycle.getStartsOn(),
                cycle.getEndsOn(),
                cashSession.getBusinessDate(),
                cashSession.getStatus(),
                cashSession.getOpeningAmount(),
                cashSession.getOpenedAt(),
                cashSession.getOpenedBy().getId(),
                cashSession.getOpenedBy().getDisplayName(),
                cashSession.getOpenedFromTerminal().getId(),
                cashSession.getOpenedFromTerminal().getName(),
                cashSession.getClosingExpectedAmount(),
                cashSession.getClosingCountedAmount(),
                cashSession.getClosingDifference(),
                cashSession.getClosedAt(),
                cashSession.getReopenCount()
        );
    }

    private CashSession findCashSessionForRead(
            UUID cashSessionId,
            UUID businessId,
            Terminal terminal
    ) {
        CashSession cashSession = cashSessionRepository
                .findForRead(
                        cashSessionId,
                        businessId
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Cash session not found"
                ));

        UUID sessionBranchId = cashSession
                .getOperatingCycle()
                .getBranch()
                .getId();

        if (!sessionBranchId.equals(
                terminal.getBranch().getId()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Cash session not found for this terminal"
            );
        }

        return cashSession;
    }
}