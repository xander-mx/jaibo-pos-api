package com.eljaibo.pos;

import com.eljaibo.pos.realtime.sale.SaleRealtimePublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SaleRealtimePublisherIntegrationTest {

    @Autowired
    SaleRealtimePublisher publisher;

    @Autowired
    TransactionTemplate transactions;

    @MockitoBean
    SimpMessagingTemplate messagingTemplate;

    @Test
    void publishesOnlyAfterTransactionCommit() {
        UUID saleId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();

        transactions.executeWithoutResult(status -> {
            publisher.saleChanged(
                    saleId,
                    branchId
            );

            verify(
                    messagingTemplate,
                    never()
            ).convertAndSend(
                    any(String.class),
                    any(Object.class)
            );
        });

        verify(
                messagingTemplate
        ).convertAndSend(
                eq(
                        "/topic/branches/"
                                + branchId
                                + "/sales"
                ),
                any(Object.class)
        );
    }

    @Test
    void doesNotPublishAfterRollback() {
        UUID saleId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();

        transactions.executeWithoutResult(status -> {
            publisher.saleChanged(
                    saleId,
                    branchId
            );

            status.setRollbackOnly();
        });

        verify(
                messagingTemplate,
                never()
        ).convertAndSend(
                any(String.class),
                any(Object.class)
        );
    }
}