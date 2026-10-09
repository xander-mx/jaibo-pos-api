create table stock (
                       id uuid primary key,
                       branch_id uuid not null,
                       product_id uuid not null,
                       quantity numeric(14,3) not null default 0,
                       version bigint not null default 0,

                       constraint fk_stock_branch
                           foreign key (branch_id)
                               references branch(id),

                       constraint fk_stock_product
                           foreign key (product_id)
                               references product(id),

                       constraint uk_stock_branch_product
                           unique (branch_id, product_id)
);