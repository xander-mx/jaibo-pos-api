package com.eljaibo.pos.expense.application;

import com.eljaibo.pos.cash.domain.CashMovement;
import com.eljaibo.pos.cash.domain.CashMovementDirection;
import com.eljaibo.pos.cash.domain.CashMovementType;
import com.eljaibo.pos.cash.domain.CashSession;
import com.eljaibo.pos.cash.domain.CashSessionStatus;
import com.eljaibo.pos.cash.persistence.CashMovementRepository;
import com.eljaibo.pos.cash.persistence.CashSessionRepository;
import com.eljaibo.pos.expense.api.request.CreateExpenseRequest;
import com.eljaibo.pos.expense.api.response.ExpenseResponse;
import com.eljaibo.pos.expense.domain.Expense;
import com.eljaibo.pos.expense.domain.ExpensePaymentSource;
import com.eljaibo.pos.expense.persistence.ExpenseRepository;
import com.eljaibo.pos.terminal.domain.Terminal;
import com.eljaibo.pos.terminal.persistence.TerminalRepository;
import com.eljaibo.pos.user.domain.AppUser;
import com.eljaibo.pos.user.persistence.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ExpenseService {

    private static final Logger log =
            LoggerFactory.getLogger(ExpenseService.class);

    private final ExpenseRepository expenses;
    private final CashSessionRepository cashSessions;
    private final CashMovementRepository cashMovements;
    private final TerminalRepository terminals;
    private final AppUserRepository users;
    private final Clock clock;

    public ExpenseService(
            ExpenseRepository expenses,
            CashSessionRepository cashSessions,
            CashMovementRepository cashMovements,
            TerminalRepository terminals,
            AppUserRepository users,
            Clock clock
    ) {
        this.expenses = expenses;
        this.cashSessions = cashSessions;
        this.cashMovements = cashMovements;
        this.terminals = terminals;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public ExpenseResponse create(
            UUID userId,
            UUID businessId,
            UUID terminalId,
            CreateExpenseRequest request
    ) {
        log.info(
                "Creating expense. businessId={}, terminalId={}, category={}, amount={}, paymentMethod={}, paymentSource={}",
                businessId,
                terminalId,
                request.category(),
                request.amount(),
                request.paymentMethod(),
                request.paymentSource()
        );

        AppUser user =
                findUser(
                        userId,
                        businessId
                );

        Terminal terminal =
                findTerminal(
                        terminalId,
                        businessId
                );

        CashSession cashSession = null;

        if (request.paymentSource()
                == ExpensePaymentSource.CASH_REGISTER) {

            cashSession =
                    cashSessions
                            .findOpenSessionForUpdate(
                                    terminal.getBranch().getId(),
                                    CashSessionStatus.OPEN
                            )
                            .orElseThrow(
                                    () -> new ResponseStatusException(
                                            HttpStatus.CONFLICT,
                                            "An open cash session is required for a cash register expense"
                                    )
                            );
        }

        Instant now = clock.instant();

        Expense expense =
                expenses.save(
                        new Expense(
                                terminal.getBranch(),
                                cashSession,
                                request.category(),
                                request.description(),
                                request.amount(),
                                request.paymentMethod(),
                                request.paymentSource(),
                                user,
                                terminal,
                                now
                        )
                );

        if (request.paymentSource()
                == ExpensePaymentSource.CASH_REGISTER) {

            CashMovement movement =
                    new CashMovement(
                            cashSession,
                            CashMovementDirection.OUT,
                            CashMovementType.EXPENSE,
                            expense.getAmount(),
                            "EXPENSE",
                            expense.getId(),
                            expense.getDescription(),
                            user,
                            terminal
                    );

            cashMovements.save(movement);

            log.info(
                    "Expense cash movement created. expenseId={}, cashSessionId={}, amount={}",
                    expense.getId(),
                    cashSession.getId(),
                    expense.getAmount()
            );
        }

        terminal.markSeen();

        log.info(
                "Expense created successfully. expenseId={}, branchId={}, amount={}",
                expense.getId(),
                terminal.getBranch().getId(),
                expense.getAmount()
        );

        return ExpenseResponse.from(expense);
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> findAll(
            UUID businessId,
            UUID terminalId
    ) {
        Terminal terminal =
                findTerminal(
                        terminalId,
                        businessId
                );

        return expenses
                .findAllByBranchIdOrderByOccurredAtDesc(
                        terminal.getBranch().getId()
                )
                .stream()
                .map(ExpenseResponse::from)
                .toList();
    }

    private AppUser findUser(
            UUID userId,
            UUID businessId
    ) {
        return users
                .findById(userId)
                .filter(user ->
                        user.isActive()
                                && user.getBusiness()
                                .getId()
                                .equals(businessId)
                )
                .orElseThrow(
                        () -> new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Active user required"
                        )
                );
    }

    private Terminal findTerminal(
            UUID terminalId,
            UUID businessId
    ) {
        return terminals
                .findByIdAndBranchBusinessIdAndActiveTrue(
                        terminalId,
                        businessId
                )
                .filter(terminal ->
                        terminal.getBranch().isActive()
                )
                .orElseThrow(
                        () -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Active terminal not found"
                        )
                );
    }
}