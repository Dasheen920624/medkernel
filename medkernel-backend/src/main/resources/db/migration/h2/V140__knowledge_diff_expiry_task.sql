-- MedKernel 第二阶段 P2-C · AIK-STD-08 最新知识差异检测与过期治理（H2）
-- 新项目基线：只保留当前差异台账与过期复核任务，不做旧数据兼容回填。

CREATE TABLE IF NOT EXISTS knowledge_diff (
    id                     BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id              VARCHAR(64)  NOT NULL,
    run_code               VARCHAR(64)  NOT NULL,
    target_identity_id     BIGINT       NULL,
    current_version_id     BIGINT       NULL,
    asset_identity         VARCHAR(160) NOT NULL,
    current_content_hash   VARCHAR(64)  NULL,
    candidate_content_hash VARCHAR(64)  NOT NULL,
    diff_type              VARCHAR(24)  NOT NULL,
    basis                  CLOB         NOT NULL,
    source_ref             VARCHAR(512) NOT NULL,
    detected_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by             VARCHAR(64)  NULL,
    trace_id               VARCHAR(128) NULL,
    CONSTRAINT uk_knowledge_diff_run_asset_hash
        UNIQUE (tenant_id, run_code, asset_identity, candidate_content_hash, diff_type),
    CONSTRAINT ck_knowledge_diff_type CHECK (diff_type IN ('NEW','REVISED','DEPRECATED'))
);

CREATE INDEX idx_knowledge_diff_target
    ON knowledge_diff (tenant_id, target_identity_id, detected_at);
CREATE INDEX idx_knowledge_diff_run
    ON knowledge_diff (tenant_id, run_code);

CREATE TABLE IF NOT EXISTS expiry_task (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    task_key        VARCHAR(240) NOT NULL,
    diff_id         BIGINT       NULL,
    identity_id     BIGINT       NOT NULL,
    version_id      BIGINT       NOT NULL,
    task_type       VARCHAR(32)  NOT NULL,
    status          VARCHAR(24)  NOT NULL,
    risk_level      VARCHAR(16)  NOT NULL,
    reason          CLOB         NOT NULL,
    review_due_at   TIMESTAMP    NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(64)  NULL,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64)  NULL,
    trace_id        VARCHAR(128) NULL,
    CONSTRAINT uk_expiry_task_key UNIQUE (tenant_id, task_key),
    CONSTRAINT ck_expiry_task_type CHECK (task_type IN ('SOURCE_DEPRECATED','REVIEW_OVERDUE')),
    CONSTRAINT ck_expiry_task_status CHECK (status IN ('OPEN','IN_REVIEW','RESOLVED','CANCELLED')),
    CONSTRAINT ck_expiry_task_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH'))
);

CREATE INDEX idx_expiry_task_status
    ON expiry_task (tenant_id, status, review_due_at);
CREATE INDEX idx_expiry_task_version
    ON expiry_task (tenant_id, version_id, status);

COMMENT ON TABLE knowledge_diff IS 'AIK-STD-08 最新知识探索差异台账：记录新增、修订、废止差异，不自动替换权威版本';
COMMENT ON COLUMN knowledge_diff.run_code IS '探索运行编码，关联当时看到什么';
COMMENT ON COLUMN knowledge_diff.target_identity_id IS '待对照的现行知识身份 ID；为空表示全新知识候选';
COMMENT ON COLUMN knowledge_diff.current_version_id IS '现行权威版本 ID；新增或缺基线时为空';
COMMENT ON COLUMN knowledge_diff.candidate_content_hash IS '探索候选内容 SHA-256 指纹';
COMMENT ON COLUMN knowledge_diff.diff_type IS '差异类型：新增、修订、废止';
COMMENT ON COLUMN knowledge_diff.basis IS '差异检测依据和人读说明';
COMMENT ON TABLE expiry_task IS 'AIK-STD-08 过期知识复核任务：来源废止或复审超期只触发复核，不自动撤回';
COMMENT ON COLUMN expiry_task.task_key IS '过期任务幂等键';
COMMENT ON COLUMN expiry_task.diff_id IS '触发该任务的差异台账 ID';
COMMENT ON COLUMN expiry_task.task_type IS '过期触发类型：来源废止或复审超期';
COMMENT ON COLUMN expiry_task.status IS '过期复核任务状态';
COMMENT ON COLUMN expiry_task.reason IS '过期复核原因和来源依据';
