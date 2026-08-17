CREATE TABLE credit_idempotency (
    idempotency_key UUID PRIMARY KEY,
    request_hash CHAR(64) NOT NULL,
    response_body TEXT,
    completed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_credit_idempotency_completion
        CHECK ((response_body IS NULL AND completed_at IS NULL) OR (response_body IS NOT NULL AND completed_at IS NOT NULL))
);

CREATE INDEX ix_credit_idempotency_expires_at
    ON credit_idempotency (expires_at);
