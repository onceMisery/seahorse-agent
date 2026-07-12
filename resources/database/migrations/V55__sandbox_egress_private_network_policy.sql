ALTER TABLE sa_sandbox_egress_policy
  ADD COLUMN IF NOT EXISTS browser_private_network_allowed_hosts TEXT NOT NULL DEFAULT '';
