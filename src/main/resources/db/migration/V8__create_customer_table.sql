CREATE TABLE customer
(
    id             UUID         NOT NULL,
    business_id    UUID         NOT NULL,
    name           VARCHAR(120) NOT NULL,
    phone          VARCHAR(30),
    email          VARCHAR(254),
    frequent       BOOLEAN      NOT NULL DEFAULT FALSE,
    wholesale      BOOLEAN      NOT NULL DEFAULT FALSE,
    credit_enabled BOOLEAN      NOT NULL DEFAULT FALSE,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_customer
        PRIMARY KEY (id),

    CONSTRAINT fk_customer_business
        FOREIGN KEY (business_id)
            REFERENCES business (id),

    CONSTRAINT ck_customer_name_not_blank
        CHECK (BTRIM(name) <> ''),

    CONSTRAINT ck_customer_phone_not_blank
        CHECK (phone IS NULL OR BTRIM(phone) <> ''),

    CONSTRAINT ck_customer_email_not_blank
        CHECK (email IS NULL OR BTRIM(email) <> '')
);

CREATE INDEX ix_customer_business_name
    ON customer (business_id, LOWER(name));

CREATE INDEX ix_customer_business_phone
    ON customer (business_id, phone);

CREATE INDEX ix_customer_business_active
    ON customer (business_id, active);