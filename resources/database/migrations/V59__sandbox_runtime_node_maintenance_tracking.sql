CREATE TABLE IF NOT EXISTS sa_sandbox_runtime_node_maintenance_capability (
  node_id VARCHAR(64) PRIMARY KEY,
  owner_id VARCHAR(64) NOT NULL,
  create_operation_tracking BOOLEAN NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS sa_sandbox_runtime_node_create_operation (
  operation_id VARCHAR(128) PRIMARY KEY,
  node_id VARCHAR(64) NOT NULL,
  owner_id VARCHAR(64) NOT NULL,
  started_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sa_sandbox_runtime_node_create_operation_owner
  ON sa_sandbox_runtime_node_create_operation(node_id, owner_id);
