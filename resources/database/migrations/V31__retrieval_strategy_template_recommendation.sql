ALTER TABLE IF EXISTS t_retrieval_strategy_template
    ADD COLUMN IF NOT EXISTS recommended SMALLINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_retrieval_strategy_template_recommendation_scope
ON t_retrieval_strategy_template (kb_id, enabled, deleted, recommended, sort_order);

CREATE UNIQUE INDEX IF NOT EXISTS uk_retrieval_strategy_template_recommended
ON t_retrieval_strategy_template (COALESCE(kb_id, ''))
WHERE deleted = 0 AND recommended = 1;
