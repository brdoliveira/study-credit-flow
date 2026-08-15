CREATE TABLE credit_outbox (
    event_id UUID PRIMARY KEY,
    evaluation_id UUID NOT NULL REFERENCES credit_evaluation (evaluation_id),
    event_type VARCHAR(128) NOT NULL,
    event_version INTEGER NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_credit_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED')),
    CONSTRAINT ck_credit_outbox_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_credit_outbox_event_version CHECK (event_version > 0)
);

CREATE INDEX ix_credit_outbox_pending
    ON credit_outbox (next_attempt_at, created_at)
    WHERE status = 'PENDING';

CREATE TABLE processed_credit_evaluation_event (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE OR REPLACE FUNCTION enqueue_credit_evaluation_completed()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO credit_outbox (
        event_id,
        evaluation_id,
        event_type,
        event_version,
        payload
    ) VALUES (
        NEW.evaluation_id,
        NEW.evaluation_id,
        'CreditEvaluationCompleted',
        1,
        jsonb_build_object(
            'eventId', NEW.evaluation_id,
            'eventVersion', 1,
            'evaluationId', NEW.evaluation_id,
            'decision', NEW.decision,
            'approvedAmount', NEW.approved_amount,
            'ruleVersion', NEW.rule_version,
            'evaluatedAt', NEW.evaluated_at,
            'correlationId', NEW.correlation_id
        )
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_credit_evaluation_outbox
AFTER INSERT ON credit_evaluation
FOR EACH ROW
EXECUTE FUNCTION enqueue_credit_evaluation_completed();
