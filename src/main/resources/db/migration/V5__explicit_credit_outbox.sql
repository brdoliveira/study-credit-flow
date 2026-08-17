DROP TRIGGER IF EXISTS trg_credit_evaluation_outbox ON credit_evaluation;
DROP FUNCTION IF EXISTS enqueue_credit_evaluation_completed();
