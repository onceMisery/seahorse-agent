CREATE TABLE IF NOT EXISTS sa_sandbox_runtime_node_admission_override (
  node_id VARCHAR(64) PRIMARY KEY,
  draining BOOLEAN NOT NULL,
  operator_id VARCHAR(128) NOT NULL,
  updated_at TIMESTAMP NOT NULL
);
