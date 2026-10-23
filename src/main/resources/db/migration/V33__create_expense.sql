CREATE TABLE expense (
    id UUID PRIMARY KEY,

    branch_id UUID NOT NULL,
    cash_session_id UUID,

    category VARCHAR(30) NOT NULL,
    description VARCHAR(150) NOT NULL,

    amount NUMERIC(14,2) NOT NULL,

    payment_method VARCHAR(20) NOT NULL,
    payment_source VARCHAR(20) NOT NULL,

    created_by_user_id UUID NOT NULL,
    created_from_terminal_id UUID NOT NULL,

    occurred_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_expense_branch
        FOREIGN KEY (branch_id)
        REFERENCES branch(id),

    CONSTRAINT fk_expense_cash_session
        FOREIGN KEY (cash_session_id)
        REFERENCES cash_session(id),

    CONSTRAINT fk_expense_user
        FOREIGN KEY (created_by_user_id)
        REFERENCES app_user(id),

    CONSTRAINT fk_expense_terminal
        FOREIGN KEY (created_from_terminal_id)
        REFERENCES terminal(id),

    CONSTRAINT chk_expense_amount_positive
        CHECK (amount > 0)
);

CREATE INDEX idx_expense_branch_occurred_at
    ON expense(branch_id, occurred_at DESC);

CREATE INDEX idx_expense_cash_session
    ON expense(cash_session_id);