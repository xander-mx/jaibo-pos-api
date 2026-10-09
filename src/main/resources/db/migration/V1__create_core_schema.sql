CREATE TABLE business
(
    id            UUID         NOT NULL,
    name          VARCHAR(120) NOT NULL,
    legal_name    VARCHAR(180),
    currency_code VARCHAR(3)   NOT NULL DEFAULT 'MXN',
    timezone      VARCHAR(50)  NOT NULL DEFAULT 'America/Mexico_City',
    logo_url      VARCHAR(500),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_business PRIMARY KEY (id),
    CONSTRAINT ck_business_name_not_blank
        CHECK (BTRIM(name) <> ''),
    CONSTRAINT ck_business_currency_code
        CHECK (currency_code ~ '^[A-Z]{3}$')
    );

CREATE TABLE branch
(
    id          UUID         NOT NULL,
    business_id UUID         NOT NULL,
    code        VARCHAR(30)  NOT NULL,
    name        VARCHAR(120) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_branch PRIMARY KEY (id),
    CONSTRAINT fk_branch_business
        FOREIGN KEY (business_id)
            REFERENCES business (id),
    CONSTRAINT uq_branch_business_code
        UNIQUE (business_id, code),
    CONSTRAINT ck_branch_code_not_blank
        CHECK (BTRIM(code) <> ''),
    CONSTRAINT ck_branch_name_not_blank
        CHECK (BTRIM(name) <> '')
);

CREATE TABLE app_user
(
    id            UUID         NOT NULL,
    business_id   UUID         NOT NULL,
    username      VARCHAR(60)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(120) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT fk_app_user_business
        FOREIGN KEY (business_id)
            REFERENCES business (id),
    CONSTRAINT ck_app_user_username_not_blank
        CHECK (BTRIM(username) <> ''),
    CONSTRAINT ck_app_user_display_name_not_blank
        CHECK (BTRIM(display_name) <> ''),
    CONSTRAINT ck_app_user_role
        CHECK (role IN ('ADMIN', 'CASHIER'))
);

CREATE UNIQUE INDEX uq_app_user_username_lower
    ON app_user (LOWER(username));

CREATE INDEX ix_branch_business
    ON branch (business_id);

CREATE INDEX ix_app_user_business
    ON app_user (business_id);