-- MedKernel 第二阶段 P2-A · LLM-03 出域数据最小化与外调安全治理（PostgreSQL）
-- ROLLBACK：确认无引用后依次 DROP TABLE mk_llm_egress_evidence / mk_llm_egress_approval / mk_llm_egress_whitelist。

CREATE TABLE IF NOT EXISTS mk_llm_egress_whitelist (
    id                BIGSERIAL     PRIMARY KEY,
    tenant_id         VARCHAR(64)   NOT NULL,
    capability_code   VARCHAR(64)   NOT NULL,
    allowed_fields    VARCHAR(1024) NOT NULL,
    sensitivity_level VARCHAR(16)   NOT NULL DEFAULT 'LOW',
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by        VARCHAR(64)   NOT NULL DEFAULT 'system',
    CONSTRAINT uk_mk_llm_egress_whitelist UNIQUE (tenant_id, capability_code),
    CONSTRAINT ck_mk_llm_egress_whitelist_sensitivity CHECK (sensitivity_level IN ('LOW', 'MEDIUM', 'HIGH'))
);

CREATE TABLE IF NOT EXISTS mk_llm_egress_approval (
    id                BIGSERIAL     PRIMARY KEY,
    tenant_id         VARCHAR(64)   NOT NULL,
    capability_code   VARCHAR(64)   NOT NULL,
    payload_hash      VARCHAR(64)   NOT NULL,
    status            VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    approver          VARCHAR(64)   NULL,
    decided_at        TIMESTAMPTZ   NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by        VARCHAR(64)   NOT NULL DEFAULT 'system',
    CONSTRAINT ck_mk_llm_egress_approval_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_mk_llm_egress_approval_lookup
    ON mk_llm_egress_approval (tenant_id, capability_code, payload_hash, status);

CREATE TABLE IF NOT EXISTS mk_llm_egress_evidence (
    id                BIGSERIAL     PRIMARY KEY,
    tenant_id         VARCHAR(64)   NOT NULL,
    capability_code   VARCHAR(64)   NOT NULL,
    task_id           VARCHAR(64)   NOT NULL,
    egress_fields     VARCHAR(1024) NOT NULL,
    desensitized_hash VARCHAR(64)   NOT NULL,
    approval_id       BIGINT        NULL,
    provider_code     VARCHAR(64)   NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by        VARCHAR(64)   NOT NULL DEFAULT 'system'
);

CREATE INDEX idx_mk_llm_egress_evidence_tenant
    ON mk_llm_egress_evidence (tenant_id, capability_code);

COMMENT ON TABLE mk_llm_egress_whitelist IS '模型出域字段白名单：按能力码声明允许出域的最小字段集与敏感级';
COMMENT ON TABLE mk_llm_egress_approval IS '模型高敏出域审批记录：留痕审批裁定，不存原始患者明文数据';
COMMENT ON TABLE mk_llm_egress_evidence IS '模型出域证据：留存出域字段清单与脱敏后内容哈希及审批引用';
