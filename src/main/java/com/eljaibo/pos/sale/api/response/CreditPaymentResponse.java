package com.eljaibo.pos.sale.api.response;

import com.eljaibo.pos.sale.domain.CreditPayment;
import com.eljaibo.pos.sale.domain.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreditPaymentResponse(
        UUID id,
        PaymentMethod paymentMethod,
        BigDecimal amount,
        Instant receivedAt
) {

    public static CreditPaymentResponse from(
            CreditPayment payment
    ) {
        return new CreditPaymentResponse(
                payment.getId(),
                payment.getPaymentMethod(),
                payment.getAmount(),
                payment.getReceivedAt()
        );
    }
}