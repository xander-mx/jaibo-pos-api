CREATE TABLE category
(
    id            UUID         NOT NULL,
    business_id   UUID         NOT NULL,
    name          VARCHAR(80)  NOT NULL,
    display_order INTEGER      NOT NULL DEFAULT 0,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_category
        PRIMARY KEY (id),

    CONSTRAINT fk_category_business
        FOREIGN KEY (business_id)
            REFERENCES business (id),

    CONSTRAINT ck_category_name_not_blank
        CHECK (BTRIM(name) <> ''),

    CONSTRAINT ck_category_display_order
        CHECK (display_order >= 0)
);

CREATE UNIQUE INDEX uq_category_business_name_lower
    ON category (business_id, LOWER(name));

CREATE INDEX ix_category_business_active
    ON category (business_id, active);


CREATE TABLE unit
(
    id               UUID        NOT NULL,
    code             VARCHAR(20) NOT NULL,
    name             VARCHAR(60) NOT NULL,
    symbol           VARCHAR(15) NOT NULL,
    decimal_places   SMALLINT    NOT NULL DEFAULT 0,
    active           BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_unit
        PRIMARY KEY (id),

    CONSTRAINT uq_unit_code
        UNIQUE (code),

    CONSTRAINT ck_unit_code_not_blank
        CHECK (BTRIM(code) <> ''),

    CONSTRAINT ck_unit_name_not_blank
        CHECK (BTRIM(name) <> ''),

    CONSTRAINT ck_unit_symbol_not_blank
        CHECK (BTRIM(symbol) <> ''),

    CONSTRAINT ck_unit_decimal_places
        CHECK (decimal_places BETWEEN 0 AND 3)
);


CREATE TABLE product
(
    id                UUID         NOT NULL,
    business_id       UUID         NOT NULL,
    category_id       UUID         NOT NULL,
    inventory_unit_id UUID,
    name              VARCHAR(120) NOT NULL,
    description       VARCHAR(500),
    image_url         VARCHAR(500),
    inventory_mode    VARCHAR(30)  NOT NULL,
    sellable          BOOLEAN      NOT NULL DEFAULT TRUE,
    purchasable       BOOLEAN      NOT NULL DEFAULT FALSE,
    display_order     INTEGER      NOT NULL DEFAULT 0,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_product
        PRIMARY KEY (id),

    CONSTRAINT fk_product_business
        FOREIGN KEY (business_id)
            REFERENCES business (id),

    CONSTRAINT fk_product_category
        FOREIGN KEY (category_id)
            REFERENCES category (id),

    CONSTRAINT fk_product_inventory_unit
        FOREIGN KEY (inventory_unit_id)
            REFERENCES unit (id),

    CONSTRAINT ck_product_name_not_blank
        CHECK (BTRIM(name) <> ''),

    CONSTRAINT ck_product_inventory_mode
        CHECK (
            inventory_mode IN (
                               'DIRECT',
                               'MANUAL_AT_CLOSE',
                               'NOT_TRACKED'
                )
            ),

    CONSTRAINT ck_product_inventory_unit
        CHECK (
            inventory_mode = 'NOT_TRACKED'
                OR inventory_unit_id IS NOT NULL
            ),

    CONSTRAINT ck_product_usage
        CHECK (sellable OR purchasable),

    CONSTRAINT ck_product_display_order
        CHECK (display_order >= 0)
);

CREATE UNIQUE INDEX uq_product_business_name_lower
    ON product (business_id, LOWER(name));

CREATE INDEX ix_product_business_category
    ON product (business_id, category_id);

CREATE INDEX ix_product_business_sellable
    ON product (business_id, sellable, active);


CREATE TABLE product_presentation
(
    id                  UUID          NOT NULL,
    product_id          UUID          NOT NULL,
    unit_id             UUID          NOT NULL,
    name                VARCHAR(100)  NOT NULL,
    conversion_factor   NUMERIC(14,6) NOT NULL DEFAULT 1,
    sale_price          NUMERIC(14,2),
    variable_quantity   BOOLEAN       NOT NULL DEFAULT FALSE,
    minimum_quantity    NUMERIC(14,3),
    open_price          BOOLEAN       NOT NULL DEFAULT FALSE,
    display_order       INTEGER       NOT NULL DEFAULT 0,
    active              BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_product_presentation
        PRIMARY KEY (id),

    CONSTRAINT fk_product_presentation_product
        FOREIGN KEY (product_id)
            REFERENCES product (id),

    CONSTRAINT fk_product_presentation_unit
        FOREIGN KEY (unit_id)
            REFERENCES unit (id),

    CONSTRAINT ck_product_presentation_name_not_blank
        CHECK (BTRIM(name) <> ''),

    CONSTRAINT ck_product_presentation_conversion
        CHECK (conversion_factor > 0),

    CONSTRAINT ck_product_presentation_price
        CHECK (
            (
                open_price = TRUE
                    OR sale_price IS NOT NULL
                )
                AND (
                sale_price IS NULL
                    OR sale_price >= 0
                )
            ),

    CONSTRAINT ck_product_presentation_minimum
        CHECK (
            minimum_quantity IS NULL
                OR minimum_quantity > 0
            ),

    CONSTRAINT ck_product_presentation_display_order
        CHECK (display_order >= 0)
);

CREATE UNIQUE INDEX uq_product_presentation_name_lower
    ON product_presentation (product_id, LOWER(name));

CREATE INDEX ix_product_presentation_product_active
    ON product_presentation (
                             product_id,
                             active,
                             display_order
        );