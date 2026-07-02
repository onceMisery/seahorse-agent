ALTER TABLE sa_sandbox_session
  ADD COLUMN IF NOT EXISTS profile_id VARCHAR(64);

ALTER TABLE sa_sandbox_session
  ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;

UPDATE sa_sandbox_session
SET profile_id = CASE runtime_type
  WHEN 'CODE_INTERPRETER' THEN 'python-small'
  WHEN 'BROWSER_AUTOMATION' THEN 'browser-readonly'
  WHEN 'FILE_CONVERSION' THEN 'file-conversion'
  WHEN 'SHELL' THEN 'shell-restricted'
  ELSE lower(replace(runtime_type, '_', '-'))
END
WHERE profile_id IS NULL OR btrim(profile_id) = '';

UPDATE sa_sandbox_session
SET expires_at = created_at + INTERVAL '1 hour'
WHERE expires_at IS NULL;

ALTER TABLE sa_sandbox_session
  ALTER COLUMN profile_id SET NOT NULL;

ALTER TABLE sa_sandbox_session
  ALTER COLUMN expires_at SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_sa_sandbox_session_expires
  ON sa_sandbox_session(tenant_id, expires_at);
