-- =============================================================================
-- V5: Billing System (SaaS MVP Tasks 3.1-3.3)
-- Date: 2026-06-05
-- Scope: Subscription plans, subscriptions, payment orders, callback logs,
--        usage rollups, bills, and bill line items
-- =============================================================================

-- Phase 1: Subscription plan definitions
CREATE TABLE IF NOT EXISTS sa_subscription_plan (
    id                  BIGINT         NOT NULL PRIMARY KEY,
    code                VARCHAR(32)    NOT NULL,
    name                VARCHAR(128)   NOT NULL,
    description         VARCHAR(512),
    monthly_price       DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    yearly_price        DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    token_limit         BIGINT         NOT NULL DEFAULT 0,
    storage_limit_bytes BIGINT         NOT NULL DEFAULT 0,
    concurrency_limit   INTEGER        NOT NULL DEFAULT 1,
    active              BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP      DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_plan_code ON sa_subscription_plan (code);

-- Seed default plans
INSERT INTO sa_subscription_plan (id, code, name, description, monthly_price, yearly_price, token_limit, storage_limit_bytes, concurrency_limit, active)
VALUES
    (1, 'FREE_TRIAL', 'Free Trial', 'Free trial with limited quota', 0.00, 0.00, 100000, 1073741824, 1, TRUE),
    (2, 'BASIC', 'Basic', 'Basic plan for small teams', 29.99, 299.99, 1000000, 10737418240, 5, TRUE),
    (3, 'PRO', 'Pro', 'Professional plan for growing teams', 99.99, 999.99, 10000000, 107374182400, 20, TRUE),
    (4, 'ENTERPRISE', 'Enterprise', 'Enterprise plan with unlimited quota', 499.99, 4999.99, 100000000, 1099511627776, 100, TRUE)
ON CONFLICT DO NOTHING;

-- Phase 2: Tenant subscriptions
CREATE TABLE IF NOT EXISTS sa_subscription (
    id                  BIGINT         NOT NULL PRIMARY KEY,
    tenant_id           VARCHAR(64)    NOT NULL,
    plan_code           VARCHAR(32)    NOT NULL,
    status              VARCHAR(32)    NOT NULL DEFAULT 'ACTIVE',
    started_at          TIMESTAMP,
    expires_at          TIMESTAMP,
    token_limit         BIGINT         NOT NULL DEFAULT 0,
    storage_limit_bytes BIGINT         NOT NULL DEFAULT 0,
    concurrency_limit   INTEGER        NOT NULL DEFAULT 1,
    created_at          TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP      DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_subscription_tenant ON sa_subscription (tenant_id);
CREATE INDEX IF NOT EXISTS idx_subscription_status ON sa_subscription (status);
CREATE INDEX IF NOT EXISTS idx_subscription_tenant_status ON sa_subscription (tenant_id, status);

-- Phase 3: Payment orders
CREATE TABLE IF NOT EXISTS sa_payment_order (
    id                BIGINT         NOT NULL PRIMARY KEY,
    order_no          VARCHAR(64)    NOT NULL,
    tenant_id         VARCHAR(64)    NOT NULL,
    plan_code         VARCHAR(32)    NOT NULL,
    payment_channel   VARCHAR(32)    NOT NULL,
    status            VARCHAR(32)    NOT NULL DEFAULT 'PENDING',
    amount            DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    channel_trade_no  VARCHAR(128),
    created_at        TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    paid_at           TIMESTAMP,
    updated_at        TIMESTAMP      DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_payment_order_no ON sa_payment_order (order_no);
CREATE INDEX IF NOT EXISTS idx_payment_order_tenant ON sa_payment_order (tenant_id);
CREATE INDEX IF NOT EXISTS idx_payment_order_status ON sa_payment_order (status);
CREATE INDEX IF NOT EXISTS idx_payment_order_channel_trade ON sa_payment_order (channel_trade_no) WHERE channel_trade_no IS NOT NULL;

-- Phase 4: Payment callback log (idempotency)
CREATE TABLE IF NOT EXISTS sa_payment_callback_log (
    id                BIGINT         NOT NULL PRIMARY KEY,
    channel           VARCHAR(32)    NOT NULL,
    channel_trade_no  VARCHAR(128)   NOT NULL,
    order_no          VARCHAR(64)    NOT NULL,
    created_at        TIMESTAMP      DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_callback_channel_trade ON sa_payment_callback_log (channel, channel_trade_no);
CREATE INDEX IF NOT EXISTS idx_callback_order_no ON sa_payment_callback_log (order_no);

-- Phase 5: Usage rollup (aggregated usage per tenant per period)
CREATE TABLE IF NOT EXISTS sa_usage_rollup (
    id                BIGINT         NOT NULL PRIMARY KEY,
    tenant_id         VARCHAR(64)    NOT NULL,
    period            VARCHAR(7)     NOT NULL,
    token_used        BIGINT         NOT NULL DEFAULT 0,
    call_count        BIGINT         NOT NULL DEFAULT 0,
    storage_bytes     BIGINT         NOT NULL DEFAULT 0,
    cost_amount       DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    created_at        TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP      DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_usage_rollup_tenant_period ON sa_usage_rollup (tenant_id, period);
CREATE INDEX IF NOT EXISTS idx_usage_rollup_period ON sa_usage_rollup (period);

-- Phase 6: Bills
CREATE TABLE IF NOT EXISTS sa_bill (
    id                BIGINT         NOT NULL PRIMARY KEY,
    bill_no           VARCHAR(64)    NOT NULL,
    tenant_id         VARCHAR(64)    NOT NULL,
    bill_period       VARCHAR(7)     NOT NULL,
    total_amount      DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    status            VARCHAR(32)    NOT NULL DEFAULT 'GENERATED',
    generated_at      TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    due_at            TIMESTAMP,
    updated_at        TIMESTAMP      DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_bill_no ON sa_bill (bill_no);
CREATE INDEX IF NOT EXISTS idx_bill_tenant ON sa_bill (tenant_id);
CREATE INDEX IF NOT EXISTS idx_bill_period ON sa_bill (bill_period);
CREATE INDEX IF NOT EXISTS idx_bill_tenant_period ON sa_bill (tenant_id, bill_period);
CREATE INDEX IF NOT EXISTS idx_bill_status ON sa_bill (status);

-- Phase 7: Bill line items
CREATE TABLE IF NOT EXISTS sa_bill_line_item (
    id                BIGINT         NOT NULL PRIMARY KEY,
    bill_id           BIGINT         NOT NULL,
    item_type         VARCHAR(32)    NOT NULL,
    description       VARCHAR(256),
    amount            DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    quantity          BIGINT         NOT NULL DEFAULT 0,
    created_at        TIMESTAMP      DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_bill_line_item_bill ON sa_bill_line_item (bill_id);
CREATE INDEX IF NOT EXISTS idx_bill_line_item_type ON sa_bill_line_item (item_type);
