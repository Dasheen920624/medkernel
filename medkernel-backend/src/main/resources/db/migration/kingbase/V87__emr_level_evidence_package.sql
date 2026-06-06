-- MedKernel v1.0 GA · EMR-LEVEL-02 电子病历评级数据质量与证据包（人大金仓）
-- ROLLBACK：如需回滚，先导出 mk_emr_level_evidence_package 证据包 payload/hash，再删除本迁移新增表。

CREATE TABLE IF NOT EXISTS mk_emr_level_evidence_package (
    id                  BIGSERIAL PRIMARY KEY,
    package_id          VARCHAR(128) NOT NULL,
    tenant_id           VARCHAR(64)  NOT NULL,
    target_id           VARCHAR(128) NOT NULL,
    hospital_org_id     VARCHAR(64)  NOT NULL,
    standard_version    VARCHAR(64)  NOT NULL,
    idempotency_key     VARCHAR(128) NOT NULL,
    status              VARCHAR(32)  NOT NULL DEFAULT 'EXPORTED',
    evidence_line_count INT          NOT NULL DEFAULT 0,
    payload_sha256      VARCHAR(64)  NOT NULL,
    payload_ndjson      TEXT         NOT NULL,
    requested_by        VARCHAR(64)  NOT NULL DEFAULT 'system',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by          VARCHAR(64)  NOT NULL DEFAULT 'system',
    completed_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    trace_id            VARCHAR(128) NULL,
    CONSTRAINT uk_emr_level_pkg_id UNIQUE (tenant_id, package_id),
    CONSTRAINT uk_emr_level_pkg_idem UNIQUE (tenant_id, target_id, idempotency_key),
    CONSTRAINT ck_emr_level_pkg_status CHECK (status IN ('EXPORTED')),
    CONSTRAINT ck_emr_level_pkg_lines CHECK (evidence_line_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_emr_level_pkg_target
    ON mk_emr_level_evidence_package (tenant_id, target_id, created_at);
CREATE INDEX IF NOT EXISTS idx_emr_level_pkg_created
    ON mk_emr_level_evidence_package (tenant_id, created_at);

COMMENT ON TABLE mk_emr_level_evidence_package IS 'EMR-LEVEL-02 电子病历评级证据包导出表，保存真实 NDJSON 证据包和可复算 SHA-256 指纹';
COMMENT ON COLUMN mk_emr_level_evidence_package.package_id IS '证据包 ID，由租户、评级目标和幂等键确定生成';
COMMENT ON COLUMN mk_emr_level_evidence_package.idempotency_key IS '证据包导出幂等键，同一目标同一键只生成一份证据包';
COMMENT ON COLUMN mk_emr_level_evidence_package.payload_sha256 IS '证据包 NDJSON payload 的 SHA-256 十六进制指纹';
COMMENT ON COLUMN mk_emr_level_evidence_package.payload_ndjson IS '按评级标准项组织的真实证据包 NDJSON 内容';
COMMENT ON COLUMN mk_emr_level_evidence_package.trace_id IS '链路追踪 ID';
