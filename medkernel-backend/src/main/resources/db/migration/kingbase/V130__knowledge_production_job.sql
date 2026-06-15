-- MedKernel 第二阶段 P2-B · AIK-STD-13 知识生产编排 job（KingbaseES）
-- 统一编排层生产任务：来源范围 + 资产类型 + 生产器 + 目标管道 + 模型策略；双形态物理隔离 + 血缘/审计。
-- ROLLBACK：确认无引用后 DROP TABLE mk_knowledge_production_job。

CREATE TABLE IF NOT EXISTS mk_knowledge_production_job (
    id               BIGSERIAL     PRIMARY KEY,
    tenant_id        VARCHAR(64)   NOT NULL,
    job_code         VARCHAR(64)   NOT NULL,
    source_scope     VARCHAR(1024) NOT NULL,
    asset_type       VARCHAR(32)   NOT NULL,
    producer         VARCHAR(16)   NOT NULL,
    target_pipeline  VARCHAR(16)   NOT NULL,
    domain           VARCHAR(24)   NOT NULL,
    model_strategy   VARCHAR(256)  NULL,
    status           VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    candidate_count  INTEGER       NOT NULL DEFAULT 0,
    lineage          VARCHAR(2048) NULL,
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by       VARCHAR(64)   NULL,
    updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       VARCHAR(64)   NULL,
    trace_id         VARCHAR(128)  NULL,
    CONSTRAINT uk_mk_knowledge_production_job_code UNIQUE (job_code),
    CONSTRAINT ck_mk_knowledge_production_job_producer CHECK (producer IN ('API_MODEL', 'AGENT_TOOL', 'LOCAL_MODEL', 'MANUAL')),
    CONSTRAINT ck_mk_knowledge_production_job_pipeline CHECK (target_pipeline IN ('PLATFORM_SOURCE', 'TENANT_OVERLAY')),
    CONSTRAINT ck_mk_knowledge_production_job_status CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_mk_knowledge_production_job_domain CHECK (domain IN ('CLINICAL', 'PHARMACY', 'TERMINOLOGY_REPORT', 'EVALUATION_INSURANCE', 'GENERAL'))
);

CREATE INDEX idx_mk_knowledge_production_job_lookup ON mk_knowledge_production_job (tenant_id, target_pipeline, status);

COMMENT ON TABLE mk_knowledge_production_job IS '知识生产编排job：统一编排层生产任务，记来源范围与资产类型与生产器与目标管道与模型策略，落双形态物理隔离与血缘审计';
