CREATE TABLE purchase_payment (

                                  id UUID PRIMARY KEY,

                                  purchase_id UUID NOT NULL,

                                  payment_method VARCHAR(20) NOT NULL,

                                  payment_source VARCHAR(20) NOT NULL,

                                  cash_session_id UUID,

                                  amount NUMERIC(14,2) NOT NULL,

                                  performed_by_id UUID NOT NULL,

                                  terminal_id UUID NOT NULL,

                                  paid_at TIMESTAMP WITH TIME ZONE NOT NULL,

                                  CONSTRAINT fk_purchase_payment_purchase
                                      FOREIGN KEY (purchase_id)
                                          REFERENCES purchase(id),

                                  CONSTRAINT fk_purchase_payment_cash_session
                                      FOREIGN KEY (cash_session_id)
                                          REFERENCES cash_session(id),

                                  CONSTRAINT fk_purchase_payment_user
                                      FOREIGN KEY (performed_by_id)
                                          REFERENCES app_user(id),

                                  CONSTRAINT fk_purchase_payment_terminal
                                      FOREIGN KEY (terminal_id)
                                          REFERENCES terminal(id),

                                  CONSTRAINT ck_purchase_payment_amount
                                      CHECK (amount > 0),

                                  CONSTRAINT ck_purchase_payment_method
                                      CHECK (
                                          payment_method IN (
                                                             'CASH',
                                                             'TRANSFER',
                                                             'CARD'
                                              )
                                          ),

                                  CONSTRAINT ck_purchase_payment_source
                                      CHECK (
                                          payment_source IN (
                                                             'CASH_REGISTER',
                                                             'EXTERNAL'
                                              )
                                          ),

                                  CONSTRAINT ck_purchase_payment_cash_session
                                      CHECK (
                                          (
                                              payment_source = 'CASH_REGISTER'
                                                  AND payment_method = 'CASH'
                                                  AND cash_session_id IS NOT NULL
                                              )
                                              OR
                                          (
                                              payment_source = 'EXTERNAL'
                                                  AND cash_session_id IS NULL
                                              )
                                          )
);

CREATE INDEX idx_purchase_payment_purchase
    ON purchase_payment(purchase_id);

CREATE INDEX idx_purchase_payment_cash_session
    ON purchase_payment(cash_session_id);