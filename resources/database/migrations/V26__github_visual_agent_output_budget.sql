-- Ensure the GitHub visual project intro agent has enough output budget for
-- full Markdown plus whole-document HTML preview artifacts.

SELECT set_config('app.current_tenant_id', 'default', false);

UPDATE sa_agent_version
SET model_config_json = jsonb_set(
        COALESCE(NULLIF(model_config_json, '')::jsonb, '{}'::jsonb),
        '{maxTokens}',
        '12000'::jsonb,
        true
    )::text,
    change_summary = 'Increase GitHub visual project intro output budget'
WHERE agent_id = 'github-visual-project-intro-agent'
  AND version_id = 'github-visual-project-intro-agent-v1';
