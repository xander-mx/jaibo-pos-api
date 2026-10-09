CREATE TABLE sale
(
    id                       UUID          NOT NULL,
    business_id              UUID          NOT NULL,
    branch_id                UUID          NOT NULL,
    cash_session_id          UUID          NOT NULL,
    customer_id              UUID,
    sale_number              INTEGER       NOT NULL,
    reference                VARCHAR(80),
    status                   VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    subtotal                 NUMERIC(14,2) NOT NULL DEFAULT 0,
    line_discount_total      NUMERIC(14,2) NOT NULL DEFAULT 0,
    sale_discount_type       VARCHAR(20)   NOT NULL DEFAULT 'NONE',
    sale_discount_value      NUMERIC(14,4) NOT NULL DEFAULT 0,
    sale_discount_amount     NUMERIC(14,2) NOT NULL DEFAULT 0,
    total                    NUMERIC(14,2) NOT NULL DEFAULT 0,
    opened_by_user_id        UUID          NOT NULL,
    opened_from_terminal_id  UUID          NOT NULL,
    finalized_at             TIMESTAMPTZ,
    finalized_by_user_id     UUID,
    finalized_from_terminal_id UUID,
    cancelled_at             TIMESTAMPTZ,
    cancelled_by_user_id     UUID,
    cancelled_from_terminal_id UUID,
    version                  BIGINT        NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_sale
        PRIMARY KEY (id),

    CONSTRAINT fk_sale_business
        FOREIGN KEY (business_id)
            REFERENCES business (id),

    CONSTRAINT fk_sale_branch
        FOREIGN KEY (branch_id)
            REFERENCES branch (id),

    CONSTRAINT fk_sale_cash_session
        FOREIGN KEY (cash_session_id)
            REFERENCES cash_session (id),

    CONSTRAINT fk_sale_customer
        FOREIGN KEY (customer_id)
            REFERENCES customer (id),

    CONSTRAINT fk_sale_opened_by
        FOREIGN KEY (opened_by_user_id)
            REFERENCES app_user (id),

    CONSTRAINT fk_sale_opened_terminal
        FOREIGN KEY (opened_from_terminal_id)
            REFERENCES terminal (id),

    CONSTRAINT fk_sale_finalized_by
        FOREIGN KEY (finalized_by_user_id)
            REFERENCES app_user (id),

    CONSTRAINT fk_sale_finalized_terminal
        FOREIGN KEY (finalized_from_terminal_id)
            REFERENCES terminal (id),

    CONSTRAINT fk_sale_cancelled_by
        FOREIGN KEY (cancelled_by_user_id)
            REFERENCES app_user (id),

    CONSTRAINT fk_sale_cancelled_terminal
        FOREIGN KEY (cancelled_from_terminal_id)
            REFERENCES terminal (id),

    CONSTRAINT uq_sale_session_number
        UNIQUE (cash_session_id, sale_number),

    CONSTRAINT ck_sale_number
        CHECK (sale_number > 0),

    CONSTRAINT ck_sale_reference
        CHECK (
            reference IS NULL
                OR BTRIM(reference) <> ''
            ),

    CONSTRAINT ck_sale_status
        CHECK (
            status IN (
                       'OPEN',
                       'PAID',
                       'CREDIT',
                       'CANCELLED'
                )
            ),

    CONSTRAINT ck_sale_discount_type
        CHECK (
            sale_discount_type IN (
                                   'NONE',
                                   'AMOUNT',
                                   'PERCENT'
                )
            ),

    CONSTRAINT ck_sale_discount_values
        CHECK (
            subtotal >= 0
                AND line_discount_total >= 0
                AND sale_discount_value >= 0
                AND sale_discount_amount >= 0
                AND total >= 0
                AND (
                sale_discount_type <> 'PERCENT'
                    OR sale_discount_value <= 100
                )
            ),

    CONSTRAINT ck_sale_no_discount
        CHECK (
            sale_discount_type <> 'NONE'
                OR (
                sale_discount_value = 0
                    AND sale_discount_amount = 0
                )
            )
);

CREATE INDEX ix_sale_business_status
    ON sale (business_id, status);

CREATE INDEX ix_sale_cash_session_status
    ON sale (cash_session_id, status);

CREATE INDEX ix_sale_customer_created
    ON sale (customer_id, created_at);


CREATE TABLE sale_item
(
    id                        UUID          NOT NULL,
    sale_id                   UUID          NOT NULL,
    product_id                UUID          NOT NULL,
    presentation_id           UUID          NOT NULL,
    product_name              VARCHAR(120)  NOT NULL,
    presentation_name         VARCHAR(100)  NOT NULL,
    unit_code                 VARCHAR(20)   NOT NULL,
    unit_symbol               VARCHAR(15)   NOT NULL,
    quantity                  NUMERIC(14,3) NOT NULL,
    conversion_factor         NUMERIC(14,6) NOT NULL,
    unit_price                NUMERIC(14,2) NOT NULL,
    price_basis_quantity      NUMERIC(14,3) NOT NULL,
    gross_amount              NUMERIC(14,2) NOT NULL,
    discount_type             VARCHAR(20)   NOT NULL DEFAULT 'NONE',
    discount_value            NUMERIC(14,4) NOT NULL DEFAULT 0,
    discount_amount           NUMERIC(14,2) NOT NULL DEFAULT 0,
    line_total                NUMERIC(14,2) NOT NULL,
    courtesy                  BOOLEAN       NOT NULL DEFAULT FALSE,
    removed                   BOOLEAN       NOT NULL DEFAULT FALSE,
    created_by_user_id        UUID          NOT NULL,
    created_from_terminal_id  UUID          NOT NULL,
    removed_at                TIMESTAMPTZ,
    removed_by_user_id        UUID,
    removed_from_terminal_id  UUID,
    version                   BIGINT        NOT NULL DEFAULT 0,
    created_at                TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_sale_item
        PRIMARY KEY (id),

    CONSTRAINT fk_sale_item_sale
        FOREIGN KEY (sale_id)
            REFERENCES sale (id),

    CONSTRAINT fk_sale_item_product
        FOREIGN KEY (product_id)
            REFERENCES product (id),

    CONSTRAINT fk_sale_item_presentation
        FOREIGN KEY (presentation_id)
            REFERENCES product_presentation (id),

    CONSTRAINT fk_sale_item_created_by
        FOREIGN KEY (created_by_user_id)
            REFERENCES app_user (id),

    CONSTRAINT fk_sale_item_created_terminal
        FOREIGN KEY (created_from_terminal_id)
            REFERENCES terminal (id),

    CONSTRAINT fk_sale_item_removed_by
        FOREIGN KEY (removed_by_user_id)
            REFERENCES app_user (id),

    CONSTRAINT fk_sale_item_removed_terminal
        FOREIGN KEY (removed_from_terminal_id)
            REFERENCES terminal (id),

    CONSTRAINT ck_sale_item_quantity
        CHECK (quantity > 0),

    CONSTRAINT ck_sale_item_conversion
        CHECK (conversion_factor > 0),

    CONSTRAINT ck_sale_item_price_basis
        CHECK (price_basis_quantity > 0),

    CONSTRAINT ck_sale_item_amounts
        CHECK (
            unit_price >= 0
                AND gross_amount >= 0
                AND discount_value >= 0
                AND discount_amount >= 0
                AND line_total >= 0
                AND discount_amount <= gross_amount
                AND (
                discount_type <> 'PERCENT'
                    OR discount_value <= 100
                )
            ),

    CONSTRAINT ck_sale_item_discount_type
        CHECK (
            discount_type IN (
                              'NONE',
                              'AMOUNT',
                              'PERCENT'
                )
            ),

    CONSTRAINT ck_sale_item_no_discount
        CHECK (
            discount_type <> 'NONE'
                OR (
                discount_value = 0
                    AND discount_amount = 0
                )
            ),

    CONSTRAINT ck_sale_item_courtesy
        CHECK (
            courtesy = FALSE
                OR line_total = 0
            ),

    CONSTRAINT ck_sale_item_removed_data
        CHECK (
            (
                removed = FALSE
                    AND removed_at IS NULL
                    AND removed_by_user_id IS NULL
                    AND removed_from_terminal_id IS NULL
                )
                OR
            (
                removed = TRUE
                    AND removed_at IS NOT NULL
                    AND removed_by_user_id IS NOT NULL
                    AND removed_from_terminal_id IS NOT NULL
                )
            )
);

CREATE INDEX ix_sale_item_sale_active
    ON sale_item (sale_id, removed);

CREATE INDEX ix_sale_item_product
    ON sale_item (product_id);


CREATE TABLE sale_event
(
    id               UUID         NOT NULL,
    sale_id          UUID         NOT NULL,
    sale_item_id     UUID,
    event_type       VARCHAR(30)  NOT NULL,
    details          JSONB        NOT NULL DEFAULT '{}'::JSONB,
    performed_by_id  UUID         NOT NULL,
    terminal_id      UUID         NOT NULL,
    occurred_at      TIMESTAMPTZ  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_sale_event
        PRIMARY KEY (id),

    CONSTRAINT fk_sale_event_sale
        FOREIGN KEY (sale_id)
            REFERENCES sale (id),

    CONSTRAINT fk_sale_event_item
        FOREIGN KEY (sale_item_id)
            REFERENCES sale_item (id),

    CONSTRAINT fk_sale_event_user
        FOREIGN KEY (performed_by_id)
            REFERENCES app_user (id),

    CONSTRAINT fk_sale_event_terminal
        FOREIGN KEY (terminal_id)
            REFERENCES terminal (id),

    CONSTRAINT ck_sale_event_type
        CHECK (
            event_type IN (
                           'SALE_OPENED',
                           'REFERENCE_CHANGED',
                           'CUSTOMER_CHANGED',
                           'ITEM_ADDED',
                           'ITEM_QUANTITY_CHANGED',
                           'ITEM_PRICE_CHANGED',
                           'ITEM_DISCOUNT_CHANGED',
                           'ITEM_COURTESY_CHANGED',
                           'ITEM_REMOVED',
                           'ITEM_MOVED',
                           'SALE_DISCOUNT_CHANGED',
                           'FINALIZED',
                           'CANCELLED'
                )
            )
);

CREATE INDEX ix_sale_event_sale_time
    ON sale_event (sale_id, occurred_at);

CREATE INDEX ix_sale_event_item
    ON sale_event (sale_item_id);