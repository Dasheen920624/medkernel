-- MedKernel 全真沙盘 · 历史原样重放清单（金仓）
-- 仅保存 D4 脱敏上下文和精确资产快照，不连接现场租户，也不写回正式业务链路。

CREATE TABLE mk_sandbox_replay_case (
    id                         BIGSERIAL PRIMARY KEY,
    replay_case_id             VARCHAR(64)   NOT NULL,
    sandbox_tenant_id          VARCHAR(64)   NOT NULL,
    source_tenant_ref          VARCHAR(71)   NOT NULL,
    source_event_ref           VARCHAR(71)   NOT NULL,
    source_trace_ref           VARCHAR(71)   NOT NULL,
    source_context_ref         VARCHAR(71)   NOT NULL,
    context_snapshot_json      TEXT          NOT NULL,
    context_snapshot_hash      VARCHAR(64)   NOT NULL,
    package_code               VARCHAR(128)  NOT NULL,
    package_version            VARCHAR(64)   NOT NULL,
    occurred_at                TIMESTAMP     NOT NULL,
    manifest_hash              VARCHAR(64)   NOT NULL,
    deidentification_profile   VARCHAR(64)   NOT NULL,
    status                     VARCHAR(16)   NOT NULL,
    imported_at                TIMESTAMP     NOT NULL,
    imported_by                VARCHAR(64)   NOT NULL,
    revoked_at                 TIMESTAMP     NULL,
    revoked_by                 VARCHAR(64)   NULL,
    revoke_reason              VARCHAR(512)  NULL,
    created_at                 TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                 TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    trace_id                   VARCHAR(128)  NOT NULL,
    CONSTRAINT uk_mk_sandbox_replay_case UNIQUE (sandbox_tenant_id, replay_case_id),
    CONSTRAINT uk_mk_sandbox_replay_manifest UNIQUE (sandbox_tenant_id, manifest_hash),
    CONSTRAINT ck_mk_sandbox_replay_case_status CHECK (status IN ('IMPORTED','REVOKED')),
    CONSTRAINT ck_mk_sandbox_replay_case_revoke CHECK (
        (status = 'IMPORTED' AND revoked_at IS NULL AND revoked_by IS NULL AND revoke_reason IS NULL)
        OR (status = 'REVOKED' AND revoked_at IS NOT NULL AND revoked_by IS NOT NULL AND revoke_reason IS NOT NULL)
    )
);

CREATE TABLE mk_sandbox_replay_asset_binding (
    id                         BIGSERIAL PRIMARY KEY,
    binding_id                 VARCHAR(64)   NOT NULL,
    sandbox_tenant_id          VARCHAR(64)   NOT NULL,
    replay_case_id             VARCHAR(64)   NOT NULL,
    asset_type                 VARCHAR(32)   NOT NULL,
    asset_identity             VARCHAR(128)  NOT NULL,
    version_id                 VARCHAR(128)  NOT NULL,
    asset_version              VARCHAR(64)   NOT NULL,
    source_tier                VARCHAR(16)   NOT NULL,
    source_org_ref             VARCHAR(71)   NOT NULL,
    content_json               TEXT          NOT NULL,
    content_hash               VARCHAR(64)   NOT NULL,
    historical_status          VARCHAR(16)   NOT NULL,
    created_at                 TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                 VARCHAR(64)   NOT NULL,
    trace_id                   VARCHAR(128)  NOT NULL,
    CONSTRAINT uk_mk_sandbox_replay_asset_id UNIQUE (sandbox_tenant_id, binding_id),
    CONSTRAINT uk_mk_sandbox_replay_asset_version UNIQUE (
        sandbox_tenant_id, replay_case_id, asset_type, asset_identity, version_id
    ),
    CONSTRAINT fk_mk_sandbox_replay_asset_case
        FOREIGN KEY (sandbox_tenant_id, replay_case_id)
        REFERENCES mk_sandbox_replay_case (sandbox_tenant_id, replay_case_id),
    CONSTRAINT ck_mk_sandbox_replay_asset_source CHECK (source_tier IN ('PLATFORM','ORG')),
    CONSTRAINT ck_mk_sandbox_replay_asset_status CHECK (
        historical_status IN ('PUBLISHED','DEPRECATED','RETIRED')
    )
);

ALTER TABLE mk_sandbox_run ADD COLUMN replay_case_id VARCHAR(64) NULL;
ALTER TABLE mk_sandbox_run DROP CONSTRAINT ck_mk_sandbox_run_current_binding;
ALTER TABLE mk_sandbox_run DROP CONSTRAINT ck_mk_sandbox_run_baseline_complete;
ALTER TABLE mk_sandbox_run ADD CONSTRAINT fk_mk_sandbox_run_replay_case
    FOREIGN KEY (tenant_id, replay_case_id)
    REFERENCES mk_sandbox_replay_case (sandbox_tenant_id, replay_case_id);
ALTER TABLE mk_sandbox_run ADD CONSTRAINT ck_mk_sandbox_run_current_binding CHECK (
    mode <> 'CURRENT' OR binding_id IS NOT NULL
    OR (baseline_id IS NULL AND status IN ('PREPARING','FAILED'))
);
ALTER TABLE mk_sandbox_run ADD CONSTRAINT ck_mk_sandbox_run_replay_case CHECK (
    mode NOT IN ('HISTORICAL_EXACT','COMPARE') OR replay_case_id IS NOT NULL
    OR (baseline_id IS NULL AND status IN ('PREPARING','FAILED'))
);
ALTER TABLE mk_sandbox_run ADD CONSTRAINT ck_mk_sandbox_run_baseline_complete CHECK (
    (baseline_id IS NULL AND package_owner_tenant_id IS NULL AND package_id IS NULL
        AND package_code IS NULL AND package_version IS NULL AND resolution_source IS NULL
        AND asset_bindings_json IS NULL AND baseline_hash IS NULL AND replay_case_id IS NULL
        AND status IN ('PREPARING','FAILED'))
    OR (baseline_id IS NOT NULL AND package_code IS NOT NULL AND package_version IS NOT NULL
        AND resolution_source IS NOT NULL AND asset_bindings_json IS NOT NULL
        AND baseline_hash IS NOT NULL AND (
            (mode = 'CURRENT' AND binding_id IS NOT NULL AND package_owner_tenant_id IS NOT NULL
                AND package_id IS NOT NULL AND replay_case_id IS NULL)
            OR (mode = 'HISTORICAL_EXACT' AND binding_id IS NULL
                AND package_owner_tenant_id IS NULL AND package_id IS NULL
                AND replay_case_id IS NOT NULL AND resolution_source = 'REPLAY_MANIFEST')
            OR (mode = 'COMPARE' AND binding_id IS NOT NULL
                AND package_owner_tenant_id IS NOT NULL AND package_id IS NOT NULL
                AND replay_case_id IS NOT NULL)
        ))
);

CREATE INDEX idx_mk_sandbox_replay_case_status
    ON mk_sandbox_replay_case (sandbox_tenant_id, status, imported_at);
CREATE INDEX idx_mk_sandbox_replay_asset_case
    ON mk_sandbox_replay_asset_binding (sandbox_tenant_id, replay_case_id, asset_type);
CREATE INDEX idx_mk_sandbox_run_replay_case
    ON mk_sandbox_run (tenant_id, replay_case_id, started_at);

COMMENT ON TABLE mk_sandbox_replay_case IS '沙盘历史重放不可变清单：保存 D4 脱敏上下文、历史配置包口径和清单摘要';
COMMENT ON COLUMN mk_sandbox_replay_case.source_tenant_ref IS '现场来源租户的 SHA-256 不可逆别名，禁止保存真实租户标识';
COMMENT ON COLUMN mk_sandbox_replay_case.context_snapshot_json IS '历史重放使用的 D4 脱敏规范上下文快照';
COMMENT ON COLUMN mk_sandbox_replay_case.context_snapshot_hash IS '脱敏上下文规范 JSON 的 SHA-256 摘要';
COMMENT ON COLUMN mk_sandbox_replay_case.manifest_hash IS '上下文与全部精确资产绑定共同计算的历史重放清单摘要';
COMMENT ON TABLE mk_sandbox_replay_asset_binding IS '历史重放精确资产版本及只读内容快照，不进入当前知识资产生命周期';
COMMENT ON COLUMN mk_sandbox_replay_asset_binding.content_json IS '历史资产只读内容快照';
COMMENT ON COLUMN mk_sandbox_replay_asset_binding.content_hash IS '历史资产规范 JSON 的 SHA-256 摘要';
COMMENT ON COLUMN mk_sandbox_run.replay_case_id IS 'HISTORICAL_EXACT 或 COMPARE 使用的演练机构历史重放清单标识';

-- ROLLBACK: 如需回滚，先导出并校验历史重放清单，再删除新增关联列与沙盘重放表；不得将快照回写生产主源。
