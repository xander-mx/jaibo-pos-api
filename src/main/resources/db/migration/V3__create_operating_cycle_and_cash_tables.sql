CREATE TABLE operating_cycle
(
    id                UUID        NOT NULL,
    branch_id         UUID        NOT NULL,
    starts_on         DATE        NOT NULL,
    ends_on           DATE        NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    closed_at         TIMESTAMPTZ,
    closed_by_user_id UUID,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_operating_cycle
        PRIMARY KEY (id),

    CONSTRAINT fk_operating_cycle_branch
        FOREIGN KEY (branch_id)
            REFERENCES branch (id),

    CONSTRAINT fk_operating_cycle_closed_by
        FOREIGN KEY (closed_by_user_id)
            REFERENCES app_user (id),

    CONSTRAINT uq_operating_cycle_branch_start
        UNIQUE (branch_id, starts_on),

    CONSTRAINT ck_operating_cycle_status
        CHECK (status IN ('OPEN', 'CLOSED')),

    CONSTRAINT ck_operating_cycle_starts_friday
        CHECK (EXTRACT(ISODOW FROM starts_on) = 5),

    CONSTRAINT ck_operating_cycle_duration
        CHECK (ends_on = starts_on + 6)
);

CREATE UNIQUE INDEX uq_operating_cycle_open_per_branch
    ON operating_cycle (branch_id)
    WHERE status = 'OPEN';

CREATE INDEX ix_operating_cycle_branch_dates
    ON operating_cycle (branch_id, starts_on, ends_on);


CREATE TABLE cash_session
(
    id                       UUID          NOT NULL,
    operating_cycle_id       UUID          NOT NULL,
    business_date            DATE          NOT NULL,
    status                   VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    opening_amount           NUMERIC(14,2) NOT NULL,
    opened_at                TIMESTAMPTZ   NOT NULL,
    opened_by_user_id        UUID          NOT NULL,
    opened_from_terminal_id  UUID          NOT NULL,
    closing_expected_amount  NUMERIC(14,2),
    closing_counted_amount   NUMERIC(14,2),
    closing_difference       NUMERIC(14,2),
    closed_at                TIMESTAMPTZ,
    closed_by_user_id        UUID,
    closed_from_terminal_id  UUID,
    reopen_count             INTEGER       NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_cash_session
        PRIMARY KEY (id),

    CONSTRAINT fk_cash_session_cycle
        FOREIGN KEY (operating_cycle_id)
            REFERENCES operating_cycle (id),

    CONSTRAINT fk_cash_session_opened_by
        FOREIGN KEY (opened_by_user_id)
            REFERENCES app_user (id),

    CONSTRAINT fk_cash_session_opened_terminal
        FOREIGN KEY (opened_from_terminal_id)
            REFERENCES terminal (id),

    CONSTRAINT fk_cash_session_closed_by
        FOREIGN KEY (closed_by_user_id)
            REFERENCES app_user (id),

    CONSTRAINT fk_cash_session_closed_terminal
        FOREIGN KEY (closed_from_terminal_id)
            REFERENCES terminal (id),

    CONSTRAINT uq_cash_session_cycle_date
        UNIQUE (operating_cycle_id, business_date),

    CONSTRAINT ck_cash_session_status
        CHECK (status IN ('OPEN', 'CLOSED')),

    CONSTRAINT ck_cash_session_opening_amount
        CHECK (opening_amount >= 0),

    CONSTRAINT ck_cash_session_closing_counted
        CHECK (
            closing_counted_amount IS NULL
                OR closing_counted_amount >= 0
            ),

    CONSTRAINT ck_cash_session_reopen_count
        CHECK (reopen_count >= 0)
);

CREATE UNIQUE INDEX uq_cash_session_open_per_cycle
    ON cash_session (operating_cycle_id)
    WHERE status = 'OPEN';

CREATE INDEX ix_cash_session_cycle_date
    ON cash_session (operating_cycle_id, business_date);


CREATE TABLE cash_session_event
(
    id               UUID          NOT NULL,
    cash_session_id  UUID          NOT NULL,
    event_type       VARCHAR(20)   NOT NULL,
    expected_amount  NUMERIC(14,2),
    counted_amount   NUMERIC(14,2),
    difference       NUMERIC(14,2),
    performed_by_id  UUID          NOT NULL,
    terminal_id      UUID          NOT NULL,
    comment          VARCHAR(255),
    occurred_at      TIMESTAMPTZ   NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_cash_session_event
        PRIMARY KEY (id),

    CONSTRAINT fk_cash_session_event_session
        FOREIGN KEY (cash_session_id)
            REFERENCES cash_session (id),

    CONSTRAINT fk_cash_session_event_user
        FOREIGN KEY (performed_by_id)
            REFERENCES app_user (id),

    CONSTRAINT fk_cash_session_event_terminal
        FOREIGN KEY (terminal_id)
            REFERENCES terminal (id),

    CONSTRAINT ck_cash_session_event_type
        CHECK (event_type IN ('OPENED', 'CLOSED', 'REOPENED'))
);

CREATE INDEX ix_cash_session_event_session_time
    ON cash_session_event (cash_session_id, occurred_at);


CREATE TABLE cash_movement
(
    id                UUID          NOT NULL,
    cash_session_id   UUID          NOT NULL,
    direction         VARCHAR(10)   NOT NULL,
    movement_type     VARCHAR(30)   NOT NULL,
    amount            NUMERIC(14,2) NOT NULL,
    reference_type    VARCHAR(30),
    reference_id      UUID,
    description       VARCHAR(255),
    performed_by_id   UUID          NOT NULL,
    terminal_id       UUID          NOT NULL,
    occurred_at       TIMESTAMPTZ   NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_cash_movement
        PRIMARY KEY (id),

    CONSTRAINT fk_cash_movement_session
        FOREIGN KEY (cash_session_id)
            REFERENCES cash_session (id),

    CONSTRAINT fk_cash_movement_user
        FOREIGN KEY (performed_by_id)
            REFERENCES app_user (id),

    CONSTRAINT fk_cash_movement_terminal
        FOREIGN KEY (terminal_id)
            REFERENCES terminal (id),

    CONSTRAINT ck_cash_movement_direction
        CHECK (direction IN ('IN', 'OUT')),

    CONSTRAINT ck_cash_movement_type
        CHECK (
            movement_type IN (
                              'SALE_PAYMENT',
                              'CUSTOMER_PAYMENT',
                              'TRANSFER_PAYMENT',
                              'PURCHASE_PAYMENT',
                              'EXPENSE',
                              'REFUND',
                              'CASH_IN',
                              'CASH_OUT',
                              'ADJUSTMENT'
                )
            ),

    CONSTRAINT ck_cash_movement_amount
        CHECK (amount > 0)
);

CREATE INDEX ix_cash_movement_session_time
    ON cash_movement (cash_session_id, occurred_at);

CREATE INDEX ix_cash_movement_reference
    ON cash_movement (reference_type, reference_id);