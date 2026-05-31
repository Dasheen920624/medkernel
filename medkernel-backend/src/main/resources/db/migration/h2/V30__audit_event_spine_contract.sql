-- MedKernel v1.0 GA · BASE-04 审计脊柱完整字段（H2 2.2）
ALTER TABLE audit_event ADD COLUMN IF NOT EXISTS actor_roles     VARCHAR(512)  NULL;
ALTER TABLE audit_event ADD COLUMN IF NOT EXISTS org_path        VARCHAR(1024) NULL;
ALTER TABLE audit_event ADD COLUMN IF NOT EXISTS environment_key VARCHAR(64)   NULL;
ALTER TABLE audit_event ADD COLUMN IF NOT EXISTS before_snapshot CLOB          NULL;
ALTER TABLE audit_event ADD COLUMN IF NOT EXISTS after_snapshot  CLOB          NULL;
ALTER TABLE audit_event ADD COLUMN IF NOT EXISTS dedupe_key      VARCHAR(128)  NULL;

ALTER TABLE audit_event ADD CONSTRAINT uk_audit_event_dedupe UNIQUE (tenant_id, dedupe_key);

CREATE INDEX IF NOT EXISTS idx_audit_event_org_path
    ON audit_event (tenant_id, org_path, occurred_at);

CREATE INDEX IF NOT EXISTS idx_audit_event_env
    ON audit_event (tenant_id, environment_key, occurred_at);

COMMENT ON COLUMN audit_event.actor_roles IS '触发审计事件时用户拥有的角色集合';
COMMENT ON COLUMN audit_event.org_path IS '触发审计事件时的组织路径快照';
COMMENT ON COLUMN audit_event.environment_key IS '触发审计事件的环境标识';
COMMENT ON COLUMN audit_event.before_snapshot IS '变更前快照，敏感字段已脱敏';
COMMENT ON COLUMN audit_event.after_snapshot IS '变更后快照，敏感字段已脱敏';
COMMENT ON COLUMN audit_event.dedupe_key IS '审计幂等键：tenant + traceId + action + target 的 SM3 摘要';
