-- Notification center tables

-- Notifications (in-app messages)
CREATE TABLE IF NOT EXISTS sa_notification (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT,
    type VARCHAR(32) NOT NULL DEFAULT 'SYSTEM',  -- SYSTEM, ALERT, TASK, BILLING
    priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL',  -- LOW, NORMAL, HIGH, URGENT
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP,
    link VARCHAR(512),                              -- optional deep link
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP                            -- auto-expire old notifications
);
CREATE INDEX IF NOT EXISTS idx_notification_user ON sa_notification (tenant_id, user_id, is_read, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notification_expires ON sa_notification (expires_at) WHERE expires_at IS NOT NULL;

-- Notification templates
CREATE TABLE IF NOT EXISTS sa_notification_template (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    template_code VARCHAR(64) NOT NULL,
    channel VARCHAR(16) NOT NULL DEFAULT 'IN_APP',  -- IN_APP, EMAIL, WEBHOOK
    title_template VARCHAR(255) NOT NULL,
    body_template TEXT NOT NULL,
    locale VARCHAR(10) NOT NULL DEFAULT 'zh_CN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_notification_template UNIQUE (template_code, channel, locale)
);

-- Notification preferences
CREATE TABLE IF NOT EXISTS sa_notification_preference (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    user_id BIGINT NOT NULL,
    channel VARCHAR(16) NOT NULL,       -- IN_APP, EMAIL, WEBHOOK
    notification_type VARCHAR(32) NOT NULL,  -- SYSTEM, ALERT, TASK, BILLING
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_notification_pref UNIQUE (user_id, channel, notification_type)
);

-- Webhooks
CREATE TABLE IF NOT EXISTS sa_webhook (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    name VARCHAR(128) NOT NULL,
    url VARCHAR(512) NOT NULL,
    secret VARCHAR(256) NOT NULL,                   -- for HMAC-SHA256 signature
    events TEXT NOT NULL,                           -- comma-separated event types
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_webhook_tenant ON sa_webhook (tenant_id, is_active);

-- Webhook delivery log
CREATE TABLE IF NOT EXISTS sa_webhook_log (
    id BIGSERIAL PRIMARY KEY,
    webhook_id BIGINT NOT NULL REFERENCES sa_webhook(id),
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    response_status INT,
    response_body TEXT,
    attempt INT NOT NULL DEFAULT 1,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',  -- PENDING, SUCCESS, FAILED
    next_retry_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_webhook_log_status ON sa_webhook_log (status, next_retry_at);
