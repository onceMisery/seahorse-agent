CREATE TABLE IF NOT EXISTS sa_sandbox_runtime_capacity_reservation (
  reservation_id VARCHAR(64) PRIMARY KEY,
  node_id VARCHAR(64) NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sa_sandbox_runtime_capacity_reservation_node
  ON sa_sandbox_runtime_capacity_reservation(node_id, expires_at);

CREATE INDEX IF NOT EXISTS idx_sa_sandbox_session_runtime_node_status
  ON sa_sandbox_session(runtime_node_id, status);
