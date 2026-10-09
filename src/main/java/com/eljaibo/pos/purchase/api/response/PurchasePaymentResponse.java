package com.eljaibo.pos.purchase.api.response;

import com.eljaibo.pos.purchase.domain.PurchasePayment;
import com.eljaibo.pos.purchase.domain.PurchasePaymentSource;
import com.eljaibo.pos.sale.domain.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PurchasePaymentResponse(
        UUID id,
        UUID purchaseId,
        PaymentMethod paymentMethod,
        PurchasePaymentSource paymentSource,
        BigDecimal amount,
        Instant paidAt
) {

    public static PurchasePaymentResponse from(
            PurchasePayment payment
    ) {
        return new PurchasePaymentResponse(
                payment.getId(),
                payment.getPurchase().getId(),
                payment.getPaymentMethod(),
                payment.getPaymentSource(),
                payment.getAmount(),
                payment.getPaidAt()
        );
    }
}