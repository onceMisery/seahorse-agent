ALTER TABLE sa_agent_handoff
    ADD COLUMN IF NOT EXISTS context_pack_id VARCHAR(64);
