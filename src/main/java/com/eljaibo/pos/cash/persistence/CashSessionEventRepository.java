package com.eljaibo.pos.cash.persistence;

import com.eljaibo.pos.cash.domain.CashSessionEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CashSessionEventRepository
        extends JpaRepository<CashSessionEvent, UUID> {

    List<CashSessionEvent>
    findAllByCashSessionIdOrderByOccurredAtAsc(
            UUID cashSessionId
    );
}