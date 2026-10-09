ALTER TABLE sale_event
DROP CONSTRAINT ck_sale_event_type;

ALTER TABLE sale_event
    ADD CONSTRAINT ck_sale_event_type
        CHECK (
            event_type IN (
                           'SALE_OPENED',
                           'SALE_ITEM_ADDED',
                           'SALE_ITEM_QUANTITY_CHANGED',
                           'SALE_ITEM_REMOVED',
                           'SALE_ITEM_PRICE_CHANGED',
                           'SALE_ITEM_DISCOUNT_APPLIED',
                           'SALE_ITEM_DISCOUNT_REMOVED',
                           'SALE_ITEM_COURTESY_APPLIED',
                           'SALE_ITEM_COURTESY_REMOVED',
                           'SALE_DISCOUNT_APPLIED',
                           'SALE_DISCOUNT_REMOVED'
                )
            );