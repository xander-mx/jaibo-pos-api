ALTER TABLE purchase
    ADD COLUMN supplier_id UUID;

ALTER TABLE purchase
    ADD CONSTRAINT fk_purchase_supplier
        FOREIGN KEY (supplier_id)
            REFERENCES supplier(id);

CREATE INDEX idx_purchase_supplier
    ON purchase(supplier_id);