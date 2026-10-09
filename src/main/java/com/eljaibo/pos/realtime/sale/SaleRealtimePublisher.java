package com.eljaibo.pos.realtime.sale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Component
public class SaleRealtimePublisher {

    private static final Logger log =
            LoggerFactory.getLogger(SaleRealtimePublisher.class);

    private final ApplicationEventPublisher eventPublisher;
    private final SimpMessagingTemplate messagingTemplate;
    private final Clock clock;

    public SaleRealtimePublisher(
            ApplicationEventPublisher eventPublisher,
            SimpMessagingTemplate messagingTemplate,
            Clock clock
    ) {
        this.eventPublisher = eventPublisher;
        this.messagingTemplate = messagingTemplate;
        this.clock = clock;
    }

    public void saleChanged(
            UUID saleId,
            UUID branchId
    ) {
        SaleChangedMessage event =
                new SaleChangedMessage(
                        saleId,
                        branchId,
                        "UPDATED",
                        Instant.now(clock)
                );

        eventPublisher.publishEvent(event);
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void afterCommit(
            SaleChangedMessage message
    ) {
        log.info(
                "Publishing sale realtime event after commit saleId={} branchId={} type={}",
                message.saleId(),
                message.branchId(),
                message.type()
        );

        messagingTemplate.convertAndSend(
                "/topic/branches/"
                        + message.branchId()
                        + "/sales",
                message
        );
    }
}