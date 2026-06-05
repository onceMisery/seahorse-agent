-- =============================================================================
-- V7: Execution Steps (SaaS MVP Sprint 4 — Module 08: Workflow Visualization)
-- Date: 2026-06-05
-- Scope: Workflow execution step records and step-to-step edges for DAG rendering
-- =============================================================================

CREATE TABLE IF NOT EXISTS t_agent_execution_steps (
    step_id        VARCHAR(64)    NOT NULL PRIMARY KEY,
    run_id         VARCHAR(64)    NOT NULL,
    step_type      VARCHAR(32)    NOT NULL,
    status         VARCHAR(32)    NOT NULL DEFAULT 'PENDING',
    started_at     TIMESTAMP,
    completed_at   TIMESTAMP,
    duration_ms    BIGINT,
    result_data    TEXT,
    position_x     INTEGER,
    position_y     INTEGER,
    created_at     TIMESTAMP      DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_exec_steps_run ON t_agent_execution_steps (run_id);
CREATE INDEX IF NOT EXISTS idx_exec_steps_status ON t_agent_execution_steps (status);
CREATE INDEX IF NOT EXISTS idx_exec_steps_run_started ON t_agent_execution_steps (run_id, started_at);

CREATE TABLE IF NOT EXISTS t_agent_execution_step_edges (
    id               BIGSERIAL      PRIMARY KEY,
    source_step_id   VARCHAR(64)    NOT NULL,
    target_step_id   VARCHAR(64)    NOT NULL,
    edge_type        VARCHAR(32)    NOT NULL DEFAULT 'SEQUENTIAL',
    created_at       TIMESTAMP      DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_exec_edges_source ON t_agent_execution_step_edges (source_step_id);
CREATE INDEX IF NOT EXISTS idx_exec_edges_target ON t_agent_execution_step_edges (target_step_id);
CREATE INDEX IF NOT EXISTS idx_exec_edges_source_target ON t_agent_execution_step_edges (source_step_id, target_step_id);
