ALTER TABLE credit_outbox
    DROP CONSTRAINT ck_credit_outbox_status;

ALTER TABLE credit_outbox
    ADD CONSTRAINT ck_credit_outbox_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED'));

CREATE INDEX ix_credit_outbox_claimable
    ON credit_outbox (status, next_attempt_at, created_at);
