CREATE TABLE IF NOT EXISTS sa_eval_candidate (
  candidate_id VARCHAR(64) PRIMARY KEY,
  run_id VARCHAR(64) NOT NULL,
  message_id VARCHAR(64),
  user_query TEXT NOT NULL,
  assistant_response TEXT NOT NULL,
  feedback_reason VARCHAR(1000),
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  reviewer_note VARCHAR(1000),
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  decided_at TIMESTAMP,
  CONSTRAINT chk_sa_eval_candidate_status
    CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_sa_eval_candidate_status
  ON sa_eval_candidate(status, created_at);

CREATE TABLE IF NOT EXISTS sa_eval_sample (
  sample_id VARCHAR(64) PRIMARY KEY,
  dataset_id VARCHAR(64) NOT NULL,
  user_query TEXT NOT NULL,
  expected_response TEXT NOT NULL,
  feedback_reason VARCHAR(1000),
  source_run_id VARCHAR(64),
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sa_eval_sample_dataset
  ON sa_eval_sample(dataset_id, created_at);
