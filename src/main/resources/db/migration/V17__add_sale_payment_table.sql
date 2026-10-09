CREATE TABLE sale_payment (
                              id UUID PRIMARY KEY,

                              sale_id UUID NOT NULL,

                              payment_method VARCHAR(20) NOT NULL,

                              amount NUMERIC(14, 2) NOT NULL,

                              received_by_user_id UUID NOT NULL,

                              received_from_terminal_id UUID NOT NULL,

                              received_at TIMESTAMPTZ NOT NULL,

                              CONSTRAINT fk_sale_payment_sale
                                  FOREIGN KEY (sale_id)
                                      REFERENCES sale(id),

                              CONSTRAINT fk_sale_payment_user
                                  FOREIGN KEY (received_by_user_id)
                                      REFERENCES app_user(id),

                              CONSTRAINT fk_sale_payment_terminal
                                  FOREIGN KEY (received_from_terminal_id)
                                      REFERENCES terminal(id),

                              CONSTRAINT chk_sale_payment_amount
                                  CHECK (amount > 0)
);

CREATE INDEX idx_sale_payment_sale_id
    ON sale_payment(sale_id);