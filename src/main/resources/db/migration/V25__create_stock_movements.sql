create table stock_movement (
                                id uuid primary key,
                                stock_id uuid not null,
                                movement_type varchar(30) not null,

                                previous_quantity numeric(14,3) not null,
                                new_quantity numeric(14,3) not null,
                                quantity_change numeric(14,3) not null,

                                reason varchar(255),

                                performed_by_id uuid not null,
                                terminal_id uuid not null,

                                occurred_at timestamp with time zone not null,

                                constraint fk_stock_movement_stock
                                    foreign key (stock_id)
                                        references stock(id),

                                constraint fk_stock_movement_user
                                    foreign key (performed_by_id)
                                        references app_user(id),

                                constraint fk_stock_movement_terminal
                                    foreign key (terminal_id)
                                        references terminal(id)
);

create index idx_stock_movement_stock
    on stock_movement(stock_id);

create index idx_stock_movement_occurred_at
    on stock_movement(occurred_at);