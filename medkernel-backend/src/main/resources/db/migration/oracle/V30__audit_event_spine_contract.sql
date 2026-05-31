-- MedKernel v1.0 GA · BASE-04 审计脊柱完整字段（Oracle 19c+）
ALTER TABLE audit_event ADD (
    actor_roles     VARCHAR2(512)  NULL,
    org_path        VARCHAR2(1024) NULL,
    environment_key VARCHAR2(64)   NULL,
    before_snapshot CLOB           NULL,
    after_snapshot  CLOB           NULL,
    dedupe_key      VARCHAR2(128)  NULL
);

ALTER TABLE audit_event ADD CONSTRAINT uk_audit_event_dedupe UNIQUE (tenant_id, dedupe_key);

CREATE INDEX idx_audit_event_org_path
    ON audit_event (tenant_id, org_path, occurred_at);

CREATE INDEX idx_audit_event_env
    ON audit_event (tenant_id, environment_key, occurred_at);

COMMENT ON COLUMN audit_event.actor_roles IS '触发审计事件时用户拥有的角色集合';
COMMENT ON COLUMN audit_event.org_path IS '触发审计事件时的组织路径快照';
COMMENT ON COLUMN audit_event.environment_key IS '触发审计事件的环境标识';
COMMENT ON COLUMN audit_event.before_snapshot IS '变更前快照，敏感字段已脱敏';
COMMENT ON COLUMN audit_event.after_snapshot IS '变更后快照，敏感字段已脱敏';
COMMENT ON COLUMN audit_event.dedupe_key IS '审计幂等键：tenant + traceId + action + target 的 SM3 摘要';
