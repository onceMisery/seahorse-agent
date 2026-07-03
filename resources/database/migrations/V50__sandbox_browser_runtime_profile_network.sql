ALTER TABLE sa_sandbox_runtime_profile_policy
  DROP CONSTRAINT IF EXISTS chk_sa_sandbox_runtime_profile_policy_network;

ALTER TABLE sa_sandbox_runtime_profile_policy
  ADD CONSTRAINT chk_sa_sandbox_runtime_profile_policy_network
  CHECK (network_allowed = FALSE OR runtime_type = 'BROWSER_AUTOMATION');
