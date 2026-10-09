create table purchase (
                          id uuid primary key,

                          branch_id uuid not null,

                          status varchar(20) not null,

                          total numeric(14,2) not null default 0,

                          note varchar(255),

                          created_by_user_id uuid not null,

                          created_at timestamp with time zone not null,
                          received_at timestamp with time zone,

                          version bigint not null default 0,

                          constraint fk_purchase_branch
                              foreign key (branch_id)
                                  references branch(id),

                          constraint fk_purchase_created_by
                              foreign key (created_by_user_id)
                                  references app_user(id),

                          constraint ck_purchase_status
                              check (
                                  status in (
                                             'DRAFT',
                                             'RECEIVED',
                                             'CANCELLED'
                                      )
                                  )
);