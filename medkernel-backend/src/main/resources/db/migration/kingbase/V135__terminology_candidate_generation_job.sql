-- B0 第一阶段完美化 · 术语候选生成异步任务（KingbaseES）
-- 10 万级院内字典候选生成只返回任务与分页入口，不同步返回候选明细。
-- ROLLBACK：确认无引用后 DROP TABLE mk_term_candidate_generation_job，并删除 mapping_candidate.generation_job_code。

CREATE TABLE IF NOT EXISTS mk_term_candidate_generation_job (
    id                       BIGSERIAL     PRIMARY KEY,
    tenant_id                VARCHAR(64)   NOT NULL,
    job_code                 VARCHAR(64)   NOT NULL,
    source_system            VARCHAR(64)   NOT NULL,
    minimum_score            DOUBLE PRECISION NULL,
    semantic_assist_enabled  BOOLEAN       NOT NULL DEFAULT TRUE,
    package_version          VARCHAR(64)   NOT NULL,
    requested_by             VARCHAR(64)   NOT NULL,
    status                   VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    progress                 INTEGER       NOT NULL DEFAULT 0,
    generated_count          INTEGER       NOT NULL DEFAULT 0,
    candidate_page_uri       VARCHAR(512)  NULL,
    error_message            VARCHAR(1024) NULL,
    created_at               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at               TIMESTAMP     NULL,
    completed_at             TIMESTAMP     NULL,
    CONSTRAINT uk_mk_term_candidate_generation_job_code UNIQUE (job_code),
    CONSTRAINT ck_mk_term_candidate_generation_job_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED'))
);

ALTER TABLE mapping_candidate ADD COLUMN IF NOT EXISTS generation_job_code VARCHAR(64) NULL;

CREATE INDEX IF NOT EXISTS idx_mk_term_candidate_generation_job_tenant
    ON mk_term_candidate_generation_job (tenant_id, created_at);
CREATE INDEX IF NOT EXISTS idx_mapping_candidate_generation_job
    ON mapping_candidate (tenant_id, generation_job_code, status);

COMMENT ON TABLE mk_term_candidate_generation_job IS '术语候选生成异步任务：保存来源系统、阈值、生成状态、候选数量和分页入口，避免同步返回大批量候选明细';
COMMENT ON COLUMN mapping_candidate.generation_job_code IS '产生该候选的术语候选生成任务编码，用于按任务分页追溯';
