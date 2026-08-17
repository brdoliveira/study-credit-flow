ALTER TABLE credit_outbox
    ADD COLUMN failed_at TIMESTAMPTZ;

ALTER TABLE credit_outbox
    DROP CONSTRAINT ck_credit_outbox_status;

ALTER TABLE credit_outbox
    ADD CONSTRAINT ck_credit_outbox_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED'));

CREATE INDEX ix_credit_outbox_failed
    ON credit_outbox (created_at)
    WHERE status = 'FAILED';
