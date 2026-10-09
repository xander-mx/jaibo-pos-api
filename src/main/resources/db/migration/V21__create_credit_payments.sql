CREATE TABLE credit_payment (
                                id UUID PRIMARY KEY,

                                customer_id UUID NOT NULL,
                                cash_session_id UUID NOT NULL,

                                payment_method VARCHAR(20) NOT NULL,

                                amount NUMERIC(14, 2) NOT NULL,

                                received_by_user_id UUID NOT NULL,
                                received_from_terminal_id UUID NOT NULL,
                                received_at TIMESTAMPTZ NOT NULL,

                                CONSTRAINT fk_credit_payment_customer
                                    FOREIGN KEY (customer_id)
                                        REFERENCES customer(id),

                                CONSTRAINT fk_credit_payment_cash_session
                                    FOREIGN KEY (cash_session_id)
                                        REFERENCES cash_session(id),

                                CONSTRAINT fk_credit_payment_user
                                    FOREIGN KEY (received_by_user_id)
                                        REFERENCES app_user(id),

                                CONSTRAINT fk_credit_payment_terminal
                                    FOREIGN KEY (received_from_terminal_id)
                                        REFERENCES terminal(id),

                                CONSTRAINT ck_credit_payment_method
                                    CHECK (
                                        payment_method IN (
                                                           'CASH',
                                                           'TRANSFER',
                                                           'CARD'
                                            )
                                        ),

                                CONSTRAINT ck_credit_payment_amount
                                    CHECK (amount > 0)
);

CREATE INDEX idx_credit_payment_customer_received
    ON credit_payment(
                      customer_id,
                      received_at
        );

CREATE INDEX idx_credit_payment_cash_session
    ON credit_payment(cash_session_id);


CREATE TABLE credit_payment_application (
                                            id UUID PRIMARY KEY,

                                            credit_payment_id UUID NOT NULL,
                                            receivable_id UUID NOT NULL,

                                            amount NUMERIC(14, 2) NOT NULL,

                                            CONSTRAINT fk_credit_payment_application_payment
                                                FOREIGN KEY (credit_payment_id)
                                                    REFERENCES credit_payment(id),

                                            CONSTRAINT fk_credit_payment_application_receivable
                                                FOREIGN KEY (receivable_id)
                                                    REFERENCES receivable(id),

                                            CONSTRAINT uk_credit_payment_application
                                                UNIQUE (
                                                        credit_payment_id,
                                                        receivable_id
                                                    ),

                                            CONSTRAINT ck_credit_payment_application_amount
                                                CHECK (amount > 0)
);

CREATE INDEX idx_credit_payment_application_receivable
    ON credit_payment_application(receivable_id);