CREATE TABLE IF NOT EXISTS sa_role_card (
    id          BIGINT       NOT NULL PRIMARY KEY,
    tenant_id   VARCHAR(64)  NOT NULL DEFAULT 'default',
    user_id     VARCHAR(64)  NOT NULL,
    name        VARCHAR(128) NOT NULL,
    definition  TEXT         NOT NULL,
    avatar_ref  VARCHAR(512),
    higher_perm SMALLINT     NOT NULL DEFAULT 0,
    enabled     SMALLINT     NOT NULL DEFAULT 0,
    create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT     NOT NULL DEFAULT 0
);

COMMENT ON TABLE sa_role_card IS '角色卡表，保存用户可复用的运行时角色定义、头像引用、高权限标记和默认启用状态';
COMMENT ON COLUMN sa_role_card.id IS '主键 ID，雪花 ID';
COMMENT ON COLUMN sa_role_card.tenant_id IS '租户 ID，用于多租户隔离';
COMMENT ON COLUMN sa_role_card.user_id IS '角色卡归属用户 ID';
COMMENT ON COLUMN sa_role_card.name IS '角色卡名称';
COMMENT ON COLUMN sa_role_card.definition IS '角色卡定义内容，会作为运行时角色指令注入到 Agent 上下文';
COMMENT ON COLUMN sa_role_card.avatar_ref IS '角色卡头像资源引用，可为空';
COMMENT ON COLUMN sa_role_card.higher_perm IS '高权限角色标记，0 表示普通角色，1 表示需要更严格校验或审批';
COMMENT ON COLUMN sa_role_card.enabled IS '是否为当前用户默认启用角色卡，0 表示否，1 表示是';
COMMENT ON COLUMN sa_role_card.create_time IS '创建时间';
COMMENT ON COLUMN sa_role_card.update_time IS '更新时间';
COMMENT ON COLUMN sa_role_card.deleted IS '软删除标记，0 表示有效，1 表示已删除';

CREATE INDEX IF NOT EXISTS idx_sa_role_card_user
    ON sa_role_card (tenant_id, user_id, enabled, deleted);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sa_role_card_user_enabled
    ON sa_role_card (tenant_id, user_id)
    WHERE enabled = 1 AND deleted = 0;

ALTER TABLE sa_role_card ENABLE ROW LEVEL SECURITY;
ALTER TABLE sa_role_card FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS rls_tenant_isolation ON sa_role_card;
CREATE POLICY rls_tenant_isolation ON sa_role_card
    USING (tenant_id = current_setting('app.current_tenant_id', true));
