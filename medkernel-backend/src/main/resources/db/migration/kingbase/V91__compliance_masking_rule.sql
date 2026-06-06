-- MedKernel v1.0 GA · SYS-06 后端脱敏规则（人大金仓）
-- ROLLBACK：确认没有服务依赖 SYS-06 脱敏框架后，删除 mk_compliance_masking_rule 表。

CREATE TABLE IF NOT EXISTS mk_compliance_masking_rule (
    id            BIGSERIAL PRIMARY KEY,
    rule_id       VARCHAR(128) NOT NULL,
    tenant_id     VARCHAR(64)  NOT NULL,
    resource_type VARCHAR(128) NOT NULL,
    field_name    VARCHAR(64)  NOT NULL,
    scenario_code VARCHAR(64)  NOT NULL DEFAULT 'DEFAULT',
    strategy      VARCHAR(32)  NOT NULL,
    mask_char     VARCHAR(4)   NOT NULL DEFAULT '*',
    prefix_keep   INTEGER      NOT NULL DEFAULT 0,
    suffix_keep   INTEGER      NOT NULL DEFAULT 0,
    status        VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    version       BIGINT       NOT NULL DEFAULT 1,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by    VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by    VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id      VARCHAR(128) NULL,
    CONSTRAINT uk_compliance_masking_rule UNIQUE (tenant_id, resource_type, field_name, scenario_code),
    CONSTRAINT ck_compliance_masking_rule_strategy CHECK (strategy IN ('REDACT','KEEP_LAST','KEEP_FIRST_LAST','EMAIL','FIXED')),
    CONSTRAINT ck_compliance_masking_rule_status CHECK (status IN ('ACTIVE','DISABLED')),
    CONSTRAINT ck_compliance_masking_rule_keep CHECK (prefix_keep >= 0 AND prefix_keep <= 32 AND suffix_keep >= 0 AND suffix_keep <= 32),
    CONSTRAINT ck_compliance_masking_rule_version CHECK (version >= 1)
);

CREATE INDEX IF NOT EXISTS idx_compliance_masking_rule_resource
    ON mk_compliance_masking_rule (tenant_id, resource_type, field_name, scenario_code, status);
CREATE INDEX IF NOT EXISTS idx_compliance_masking_rule_status
    ON mk_compliance_masking_rule (tenant_id, status, updated_at);

COMMENT ON TABLE mk_compliance_masking_rule IS 'SYS-06 后端脱敏规则表，按租户、资源、字段和场景配置脱敏策略';
COMMENT ON COLUMN mk_compliance_masking_rule.rule_id IS '脱敏规则 ID，由资源、字段和场景确定，租户内稳定';
COMMENT ON COLUMN mk_compliance_masking_rule.resource_type IS '受控业务资源类型，如 clinical_case 或 evidence_snapshot';
COMMENT ON COLUMN mk_compliance_masking_rule.field_name IS '敏感字段名，后端调用脱敏服务时按该字段匹配规则';
COMMENT ON COLUMN mk_compliance_masking_rule.scenario_code IS '脱敏场景编码，DEFAULT 为默认场景';
COMMENT ON COLUMN mk_compliance_masking_rule.strategy IS '脱敏策略，如 KEEP_LAST、KEEP_FIRST_LAST、EMAIL、FIXED';
COMMENT ON COLUMN mk_compliance_masking_rule.mask_char IS '脱敏替换字符，必须为单个字符';
COMMENT ON COLUMN mk_compliance_masking_rule.trace_id IS '最近一次脱敏规则变更的链路追踪 ID';
