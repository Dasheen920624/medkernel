-- MedKernel 第二阶段 P2-C · AIK-STD-06 生成期影子评测运行记录（PostgreSQL）
-- 候选进入人工审核前记录影子评测指标、退化和达标裁决；无基准集不得自动认证。
-- ROLLBACK：确认无引用后 DROP TABLE mk_knowledge_shadow_run。

CREATE TABLE IF NOT EXISTS mk_knowledge_shadow_run (
    id                   BIGSERIAL    PRIMARY KEY,
    tenant_id            VARCHAR(64)  NOT NULL,
    job_code             VARCHAR(64)  NOT NULL,
    asset_type           VARCHAR(32)  NOT NULL,
    target_identity_id   BIGINT       NULL,
    content_hash         VARCHAR(64)  NOT NULL,
    capability_code      VARCHAR(96)  NOT NULL,
    status               VARCHAR(24)  NOT NULL,
    total_cases          INTEGER      NOT NULL DEFAULT 0,
    hit_count            INTEGER      NOT NULL DEFAULT 0,
    false_positive_count INTEGER      NOT NULL DEFAULT 0,
    miss_count           INTEGER      NOT NULL DEFAULT 0,
    degradation_detected BOOLEAN      NOT NULL DEFAULT FALSE,
    ready_for_review     BOOLEAN      NOT NULL DEFAULT FALSE,
    basis                VARCHAR(512) NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by           VARCHAR(64)  NULL,
    CONSTRAINT ck_mk_knowledge_shadow_run_status CHECK (status IN ('NOT_READY','FAILED','PASSED','PENDING_REVIEW')),
    CONSTRAINT ck_mk_knowledge_shadow_run_counts CHECK (
        total_cases >= 0 AND hit_count >= 0 AND false_positive_count >= 0 AND miss_count >= 0
    )
);

CREATE INDEX idx_mk_knowledge_shadow_run_job ON mk_knowledge_shadow_run (tenant_id, job_code);
CREATE INDEX idx_mk_knowledge_shadow_run_status ON mk_knowledge_shadow_run (tenant_id, status, created_at);

COMMENT ON TABLE mk_knowledge_shadow_run IS '知识候选生成期影子评测运行记录：记录回归用例指标、质量退化和是否允许进入人工审核';
