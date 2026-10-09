ALTER TABLE operating_cycle
DROP CONSTRAINT ck_operating_cycle_status;

ALTER TABLE operating_cycle
    ADD CONSTRAINT ck_operating_cycle_status
        CHECK (status IN ('PLANNED', 'OPEN', 'CLOSED'));