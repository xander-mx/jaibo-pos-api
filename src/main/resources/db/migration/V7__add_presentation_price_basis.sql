ALTER TABLE product_presentation
    ADD COLUMN price_basis_quantity NUMERIC(14,3)
        NOT NULL DEFAULT 1;

ALTER TABLE product_presentation
    ADD CONSTRAINT ck_product_presentation_price_basis
        CHECK (price_basis_quantity > 0);