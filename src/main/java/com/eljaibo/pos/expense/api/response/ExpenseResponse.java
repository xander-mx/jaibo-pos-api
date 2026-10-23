package com.eljaibo.pos.expense.api.response;

import com.eljaibo.pos.expense.domain.Expense;
import com.eljaibo.pos.expense.domain.ExpenseCategory;
import com.eljaibo.pos.expense.domain.ExpensePaymentSource;
import com.eljaibo.pos.sale.domain.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ExpenseResponse(
        UUID id,
        UUID branchId,
        UUID cashSessionId,
        ExpenseCategory category,
        String description,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        ExpensePaymentSource paymentSource,
        UUID createdByUserId,
        UUID createdFromTerminalId,
        Instant occurredAt
) {

    public static ExpenseResponse from(Expense expense) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getBranch().getId(),
                expense.getCashSession() == null
                        ? null
                        : expense.getCashSession().getId(),
                expense.getCategory(),
                expense.getDescription(),
                expense.getAmount(),
                expense.getPaymentMethod(),
                expense.getPaymentSource(),
                expense.getCreatedBy().getId(),
                expense.getCreatedFromTerminal().getId(),
                expense.getOccurredAt()
        );
    }
}