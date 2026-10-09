CREATE TABLE receivable (
                            id UUID PRIMARY KEY,

                            sale_id UUID NOT NULL,
                            customer_id UUID NOT NULL,

                            original_amount NUMERIC(14, 2) NOT NULL,
                            balance NUMERIC(14, 2) NOT NULL,

                            status VARCHAR(20) NOT NULL,

                            opened_at TIMESTAMPTZ NOT NULL,
                            paid_at TIMESTAMPTZ,

                            version BIGINT NOT NULL DEFAULT 0,

                            CONSTRAINT uk_receivable_sale
                                UNIQUE (sale_id),

                            CONSTRAINT fk_receivable_sale
                                FOREIGN KEY (sale_id)
                                    REFERENCES sale(id),

                            CONSTRAINT fk_receivable_customer
                                FOREIGN KEY (customer_id)
                                    REFERENCES customer(id),

                            CONSTRAINT ck_receivable_original_amount
                                CHECK (original_amount > 0),

                            CONSTRAINT ck_receivable_balance
                                CHECK (
                                    balance >= 0
                                        AND balance <= original_amount
                                    ),

                            CONSTRAINT ck_receivable_status
                                CHECK (
                                    status IN (
                                               'OPEN',
                                               'PAID'
                                        )
                                    )
);

CREATE INDEX idx_receivable_customer_status_opened
    ON receivable(
                  customer_id,
                  status,
                  opened_at
        );