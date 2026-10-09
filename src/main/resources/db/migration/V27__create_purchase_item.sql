create table purchase_item (
                               id uuid primary key,

                               purchase_id uuid not null,
                               product_id uuid not null,

                               quantity numeric(14,3) not null,
                               unit_cost numeric(14,2) not null,
                               subtotal numeric(14,2) not null,

                               constraint fk_purchase_item_purchase
                                   foreign key (purchase_id)
                                       references purchase(id),

                               constraint fk_purchase_item_product
                                   foreign key (product_id)
                                       references product(id),

                               constraint ck_purchase_item_quantity
                                   check (quantity > 0),

                               constraint ck_purchase_item_unit_cost
                                   check (unit_cost >= 0),

                               constraint ck_purchase_item_subtotal
                                   check (subtotal >= 0)
);

create index idx_purchase_item_purchase
    on purchase_item(purchase_id);