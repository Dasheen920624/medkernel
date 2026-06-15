-- MedKernel 第二阶段 P2-B · AIK-STD-13 PR2 知识候选生产血缘（PostgreSQL）
-- 每条提交候选记一行回溯元数据（归属 job + 资产身份 + 内容指纹 + 候选引用 + 时点）；非资产存储。
-- ROLLBACK：确认无引用后 DROP TABLE mk_knowledge_production_candidate。

CREATE TABLE IF NOT EXISTS mk_knowledge_production_candidate (
    id               BIGSERIAL     PRIMARY KEY,
    tenant_id        VARCHAR(64)   NOT NULL,
    job_code         VARCHAR(64)   NOT NULL,
    asset_identity   VARCHAR(256)  NOT NULL,
    content_hash     VARCHAR(64)   NOT NULL,
    candidate_ref    VARCHAR(256)  NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(64)   NULL
);

CREATE INDEX idx_mk_knowledge_production_candidate_job ON mk_knowledge_production_candidate (tenant_id, job_code);

COMMENT ON TABLE mk_knowledge_production_candidate IS '知识候选生产血缘：每条提交候选记一行回溯元数据，含归属job与资产身份与内容指纹与候选引用与时点，非资产存储仅留生产血缘轨迹';
