CREATE INDEX IF NOT EXISTS idx_sa_context_item_expiry
  ON sa_context_item(context_pack_id, expires_at);
