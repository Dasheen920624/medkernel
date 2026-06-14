-- MedKernel 第二阶段 P2-A · LLM-03 出域数据最小化与外调安全治理（H2）
-- ROLLBACK：确认无引用后依次 DROP TABLE model_egress_evidence / model_egress_approval / model_egress_whitelist。

CREATE TABLE IF NOT EXISTS model_egress_whitelist (
    id                BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id         VARCHAR(64)   NOT NULL,
    capability_code   VARCHAR(64)   NOT NULL,
    allowed_fields    VARCHAR(1024) NOT NULL,
    sensitivity_level VARCHAR(16)   NOT NULL DEFAULT 'LOW',
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64)   NOT NULL DEFAULT 'system',
    CONSTRAINT uk_model_egress_whitelist UNIQUE (tenant_id, capability_code),
    CONSTRAINT ck_model_egress_whitelist_sensitivity CHECK (sensitivity_level IN ('LOW', 'MEDIUM', 'HIGH'))
);

CREATE TABLE IF NOT EXISTS model_egress_approval (
    id                BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id         VARCHAR(64)   NOT NULL,
    capability_code   VARCHAR(64)   NOT NULL,
    payload_hash      VARCHAR(64)   NOT NULL,
    status            VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    approver          VARCHAR(64)   NULL,
    decided_at        TIMESTAMP     NULL,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64)   NOT NULL DEFAULT 'system',
    CONSTRAINT ck_model_egress_approval_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_model_egress_approval_lookup
    ON model_egress_approval (tenant_id, capability_code, payload_hash, status);

CREATE TABLE IF NOT EXISTS model_egress_evidence (
    id                BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id         VARCHAR(64)   NOT NULL,
    capability_code   VARCHAR(64)   NOT NULL,
    task_id           VARCHAR(64)   NOT NULL,
    egress_fields     VARCHAR(1024) NOT NULL,
    desensitized_hash VARCHAR(64)   NOT NULL,
    approval_id       BIGINT        NULL,
    provider_code     VARCHAR(64)   NULL,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64)   NOT NULL DEFAULT 'system'
);

CREATE INDEX idx_model_egress_evidence_tenant
    ON model_egress_evidence (tenant_id, capability_code);
