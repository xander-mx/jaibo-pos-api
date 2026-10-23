package com.eljaibo.pos.expense.api.request;

import com.eljaibo.pos.expense.domain.ExpenseCategory;
import com.eljaibo.pos.expense.domain.ExpensePaymentSource;
import com.eljaibo.pos.sale.domain.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateExpenseRequest(

        @NotNull
        ExpenseCategory category,

        @NotBlank
        @Size(max = 150)
        String description,

        @NotNull
        @DecimalMin("0.01")
        @Digits(integer = 12, fraction = 2)
        BigDecimal amount,

        @NotNull
        PaymentMethod paymentMethod,

        @NotNull
        ExpensePaymentSource paymentSource

) {
}