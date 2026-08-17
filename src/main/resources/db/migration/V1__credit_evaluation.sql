CREATE TABLE credit_evaluation (
    evaluation_id UUID PRIMARY KEY,
    cpf_masked VARCHAR(14) NOT NULL,
    decision VARCHAR(16) NOT NULL,
    approved_amount NUMERIC(19, 2) NOT NULL,
    rule_version VARCHAR(128) NOT NULL,
    rule_results JSONB NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL,
    duration_millis BIGINT NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    CONSTRAINT ck_credit_evaluation_cpf_masked
        CHECK (cpf_masked ~ '^\*\*\*\.\*\*\*\.\*\*\*-[0-9]{2}$'),
    CONSTRAINT ck_credit_evaluation_approved_amount
        CHECK (approved_amount >= 0),
    CONSTRAINT ck_credit_evaluation_duration
        CHECK (duration_millis >= 0)
);

CREATE INDEX ix_credit_evaluation_decision_evaluated_at
    ON credit_evaluation (decision, evaluated_at DESC);

CREATE INDEX ix_credit_evaluation_evaluated_at
    ON credit_evaluation (evaluated_at DESC);
