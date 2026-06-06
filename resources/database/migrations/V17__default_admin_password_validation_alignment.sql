-- Align the local seed admin password with AuthLoginRequest validation.
-- Existing development databases created from older init SQL used "admin",
-- which is rejected by the current 6-128 character password rule.
UPDATE t_user
SET password = 'admin123',
    update_time = CURRENT_TIMESTAMP
WHERE username = 'admin'
  AND password = 'admin';
