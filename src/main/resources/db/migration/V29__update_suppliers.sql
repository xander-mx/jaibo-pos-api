UPDATE supplier
SET name = regexp_replace(
        trim(name),
        '[[:space:]]+',
        ' ',
        'g'
           );

DROP INDEX uk_supplier_business_name;

CREATE UNIQUE INDEX uk_supplier_business_name
    ON supplier (
                 business_id,
                 lower(
        regexp_replace(
            trim(name),
            '[[:space:]]+',
            ' ',
            'g'
        )
    )
        );