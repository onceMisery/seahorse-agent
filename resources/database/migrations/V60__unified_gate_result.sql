CREATE TABLE IF NOT EXISTS sa_gate_result (
  pk_id BIGSERIAL PRIMARY KEY,
  gate_id VARCHAR(64) NOT NULL UNIQUE,
  tenant_id VARCHAR(64) NOT NULL,
  subject_type VARCHAR(64) NOT NULL,
  subject_id VARCHAR(256) NOT NULL,
  status VARCHAR(32) NOT NULL,
  passed BOOLEAN NOT NULL,
  blocking_codes_json TEXT NOT NULL,
  items_json TEXT NOT NULL,
  checked_at TIMESTAMP NOT NULL,
  source_type VARCHAR(128),
  source_id VARCHAR(256),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sa_gate_result_subject
  ON sa_gate_result(tenant_id, subject_type, subject_id, checked_at);
