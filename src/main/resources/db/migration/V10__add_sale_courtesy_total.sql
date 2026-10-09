ALTER TABLE sale
    ADD COLUMN courtesy_total NUMERIC(14,2)
        NOT NULL DEFAULT 0;

ALTER TABLE sale
    ADD CONSTRAINT ck_sale_courtesy_total
        CHECK (
            courtesy_total >= 0
                AND line_discount_total + courtesy_total <= subtotal
            );

ALTER TABLE sale
    ADD CONSTRAINT ck_sale_final_amounts
        CHECK (
            sale_discount_amount
                <= subtotal
                - line_discount_total
                - courtesy_total
                AND total
                = subtotal
                        - line_discount_total
                        - courtesy_total
                        - sale_discount_amount
            );