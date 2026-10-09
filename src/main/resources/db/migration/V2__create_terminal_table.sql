CREATE TABLE terminal
(
    id                    UUID         NOT NULL,
    branch_id             UUID         NOT NULL,
    registered_by_user_id UUID         NOT NULL,
    name                  VARCHAR(120) NOT NULL,
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    last_seen_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_terminal
        PRIMARY KEY (id),

    CONSTRAINT fk_terminal_branch
        FOREIGN KEY (branch_id)
            REFERENCES branch (id),

    CONSTRAINT fk_terminal_registered_by
        FOREIGN KEY (registered_by_user_id)
            REFERENCES app_user (id),

    CONSTRAINT ck_terminal_name_not_blank
        CHECK (BTRIM(name) <> '')
);

CREATE INDEX ix_terminal_branch
    ON terminal (branch_id);

CREATE INDEX ix_terminal_registered_by
    ON terminal (registered_by_user_id);

CREATE INDEX ix_terminal_branch_active
    ON terminal (branch_id, active);