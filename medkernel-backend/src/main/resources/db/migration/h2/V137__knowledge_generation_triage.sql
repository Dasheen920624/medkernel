-- MedKernel 第二阶段 P2-C · AIK-STD-10 生成期知识候选 8 态分流（H2）
-- 生成期记录候选身份识别、去重、8 态分流与处理去向；append-only 审计轨迹。
-- ROLLBACK：确认无引用后 DROP TABLE mk_knowledge_generation_triage。

CREATE TABLE IF NOT EXISTS mk_knowledge_generation_triage (
    id                 BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          VARCHAR(64)  NOT NULL,
    job_code           VARCHAR(64)  NOT NULL,
    content_hash       VARCHAR(64)  NOT NULL,
    asset_type         VARCHAR(32)  NOT NULL,
    target_identity_id BIGINT       NULL,
    active_version_id  BIGINT       NULL,
    matched_version_id BIGINT       NULL,
    triage_state       VARCHAR(32)  NOT NULL,
    action             VARCHAR(32)  NOT NULL,
    basis              VARCHAR(512) NOT NULL,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by         VARCHAR(64)  NULL,
    CONSTRAINT ck_mk_knowledge_generation_triage_state CHECK (triage_state IN
        ('NEW_ASSET','DUPLICATE','MINOR_REVISION','MAJOR_UPGRADE','CONFLICT','DOWNGRADE','DEPRECATION','UNCERTAIN')),
    CONSTRAINT ck_mk_knowledge_generation_triage_action CHECK (action IN
        ('SUBMIT_REVIEW','SKIP_DUPLICATE','MERGE_REVIEW','UPGRADE_REVIEW','CONFLICT_REVIEW','DOWNGRADE_REVIEW','RETIREMENT_REVIEW','MANUAL_REVIEW'))
);

CREATE INDEX idx_mk_knowledge_generation_triage_job ON mk_knowledge_generation_triage (tenant_id, job_code);
CREATE INDEX idx_mk_knowledge_generation_triage_identity ON mk_knowledge_generation_triage (tenant_id, target_identity_id, created_at);
