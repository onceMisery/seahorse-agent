-- =============================================================================
-- V9: Agent Marketplace (Sprint 5-6 Module 07)
-- Date: 2026-06-05
-- Scope: Agent publish review, subscriptions, ratings, popularity
-- =============================================================================

-- Phase 1: Add marketplace columns to agent definition
ALTER TABLE sa_agent_definition ADD COLUMN IF NOT EXISTS visibility VARCHAR(32) DEFAULT 'PRIVATE';
ALTER TABLE sa_agent_definition ADD COLUMN IF NOT EXISTS category VARCHAR(64);
ALTER TABLE sa_agent_definition ADD COLUMN IF NOT EXISTS tags VARCHAR(512);
ALTER TABLE sa_agent_definition ADD COLUMN IF NOT EXISTS pricing_type VARCHAR(32) DEFAULT 'FREE';
ALTER TABLE sa_agent_definition ADD COLUMN IF NOT EXISTS price DECIMAL(10,2) DEFAULT 0.00;
ALTER TABLE sa_agent_definition ADD COLUMN IF NOT EXISTS review_status VARCHAR(32) DEFAULT 'NOT_SUBMITTED';

CREATE INDEX IF NOT EXISTS idx_agent_visibility ON sa_agent_definition (visibility);
CREATE INDEX IF NOT EXISTS idx_agent_category ON sa_agent_definition (category);
CREATE INDEX IF NOT EXISTS idx_agent_review_status ON sa_agent_definition (review_status);

-- Phase 2: Agent publish review
CREATE TABLE IF NOT EXISTS sa_agent_publish_review (
    id              BIGINT         NOT NULL PRIMARY KEY,
    agent_id        VARCHAR(64)    NOT NULL,
    tenant_id       VARCHAR(64)    NOT NULL,
    submitted_by    VARCHAR(128)   NOT NULL,
    status          VARCHAR(32)    NOT NULL DEFAULT 'PENDING',
    review_comment  VARCHAR(1024),
    reviewed_by     VARCHAR(128),
    submitted_at    TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    reviewed_at     TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_review_agent ON sa_agent_publish_review (agent_id);
CREATE INDEX IF NOT EXISTS idx_review_tenant ON sa_agent_publish_review (tenant_id);
CREATE INDEX IF NOT EXISTS idx_review_status ON sa_agent_publish_review (status);
CREATE INDEX IF NOT EXISTS idx_review_submitted ON sa_agent_publish_review (submitted_at);

-- Phase 3: Agent subscription
CREATE TABLE IF NOT EXISTS sa_agent_subscription (
    id              BIGINT         NOT NULL PRIMARY KEY,
    agent_id        VARCHAR(64)    NOT NULL,
    user_id         BIGINT         NOT NULL,
    tenant_id       VARCHAR(64)    NOT NULL,
    subscribed_at   TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    active          BOOLEAN        NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_subscription_agent ON sa_agent_subscription (agent_id);
CREATE INDEX IF NOT EXISTS idx_agent_subscription_user ON sa_agent_subscription (user_id);
CREATE INDEX IF NOT EXISTS idx_agent_subscription_tenant ON sa_agent_subscription (tenant_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_subscription_unique ON sa_agent_subscription (agent_id, user_id);
CREATE INDEX IF NOT EXISTS idx_agent_subscription_active ON sa_agent_subscription (active);

-- Phase 4: Agent rating
CREATE TABLE IF NOT EXISTS sa_agent_rating (
    id          BIGINT         NOT NULL PRIMARY KEY,
    agent_id    VARCHAR(64)    NOT NULL,
    user_id     BIGINT         NOT NULL,
    rating      INTEGER        NOT NULL,
    comment     VARCHAR(1024),
    created_at  TIMESTAMP      DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_rating_agent ON sa_agent_rating (agent_id);
CREATE INDEX IF NOT EXISTS idx_rating_user ON sa_agent_rating (user_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_rating_unique ON sa_agent_rating (agent_id, user_id);
CREATE INDEX IF NOT EXISTS idx_rating_value ON sa_agent_rating (rating);

-- Phase 5: Agent rating summary (cached aggregation)
CREATE TABLE IF NOT EXISTS sa_agent_rating_summary (
    agent_id        VARCHAR(64)    NOT NULL PRIMARY KEY,
    average_rating  DECIMAL(3,2)   NOT NULL DEFAULT 0.00,
    rating_count    INTEGER        NOT NULL DEFAULT 0,
    rating_1_count  INTEGER        NOT NULL DEFAULT 0,
    rating_2_count  INTEGER        NOT NULL DEFAULT 0,
    rating_3_count  INTEGER        NOT NULL DEFAULT 0,
    rating_4_count  INTEGER        NOT NULL DEFAULT 0,
    rating_5_count  INTEGER        NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP      DEFAULT CURRENT_TIMESTAMP
);

-- Phase 6: Agent popularity score
CREATE TABLE IF NOT EXISTS sa_agent_popularity (
    agent_id            VARCHAR(64)    NOT NULL PRIMARY KEY,
    subscription_count  BIGINT         NOT NULL DEFAULT 0,
    average_rating      DECIMAL(3,2)   NOT NULL DEFAULT 0.00,
    rating_count        INTEGER        NOT NULL DEFAULT 0,
    activity_score      DECIMAL(10,2)  NOT NULL DEFAULT 0.00,
    popularity_score    DECIMAL(10,2)  NOT NULL DEFAULT 0.00,
    rank_position       INTEGER,
    updated_at          TIMESTAMP      DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_popularity_score ON sa_agent_popularity (popularity_score DESC);
CREATE INDEX IF NOT EXISTS idx_popularity_rank ON sa_agent_popularity (rank_position);
