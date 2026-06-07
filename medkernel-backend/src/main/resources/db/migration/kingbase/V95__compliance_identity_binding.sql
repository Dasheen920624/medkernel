-- MedKernel v1.0 GA · SVC-COMPLIANCE-01 外部身份绑定（人大金仓）
-- ROLLBACK：确认没有身份绑定依赖后，删除 mk_compliance_identity_binding 表。

CREATE TABLE IF NOT EXISTS mk_compliance_identity_binding (
    id                      BIGSERIAL PRIMARY KEY,
    binding_id              VARCHAR(64)  NOT NULL,
    tenant_id               VARCHAR(64)  NOT NULL,
    user_id                 VARCHAR(128) NOT NULL,
    provider_type           VARCHAR(32)  NOT NULL,
    external_subject_digest VARCHAR(72)  NOT NULL,
    subject_hint            VARCHAR(128) NOT NULL,
    status                  VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    version                 BIGINT       NOT NULL DEFAULT 1,
    unbound_reason          VARCHAR(512) NULL,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by              VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id                VARCHAR(128) NULL,
    CONSTRAINT uk_compliance_identity_binding_id UNIQUE (binding_id),
    CONSTRAINT uk_compliance_identity_subject UNIQUE (tenant_id, provider_type, external_subject_digest),
    CONSTRAINT ck_compliance_identity_provider CHECK (provider_type IN ('OIDC','CAS','SAML','EMPLOYEE_NO','SM_CA')),
    CONSTRAINT ck_compliance_identity_status CHECK (status IN ('ACTIVE','UNBOUND')),
    CONSTRAINT ck_compliance_identity_version CHECK (version >= 1)
);

CREATE INDEX IF NOT EXISTS idx_compliance_identity_binding_user
    ON mk_compliance_identity_binding (tenant_id, user_id, provider_type, status);
CREATE INDEX IF NOT EXISTS idx_compliance_identity_binding_status
    ON mk_compliance_identity_binding (tenant_id, status, updated_at);

COMMENT ON TABLE mk_compliance_identity_binding IS 'SVC-COMPLIANCE-01 外部身份绑定表，身份原文不落库，仅保存 SM3 摘要和脱敏提示';
COMMENT ON COLUMN mk_compliance_identity_binding.external_subject_digest IS '外部身份主体规范化后的 SM3 摘要';
COMMENT ON COLUMN mk_compliance_identity_binding.subject_hint IS '供管理员辨认的脱敏身份提示';
COMMENT ON COLUMN mk_compliance_identity_binding.unbound_reason IS '最近一次解绑原因，解绑操作同时写统一审计链';
