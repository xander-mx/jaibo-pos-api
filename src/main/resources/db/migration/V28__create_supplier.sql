CREATE TABLE supplier (
                          id UUID PRIMARY KEY,

                          business_id UUID NOT NULL,

                          name VARCHAR(120) NOT NULL,

                          phone VARCHAR(30),

                          active BOOLEAN NOT NULL DEFAULT TRUE,

                          CONSTRAINT fk_supplier_business
                              FOREIGN KEY (business_id)
                                  REFERENCES business(id),

                          CONSTRAINT ck_supplier_name
                              CHECK (length(trim(name)) > 0)
);

CREATE INDEX idx_supplier_business
    ON supplier(business_id);

CREATE UNIQUE INDEX uk_supplier_business_name
    ON supplier(business_id, lower(name));