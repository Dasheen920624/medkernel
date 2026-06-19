-- MedKernel 生产知识初始化发行批次（H2）
-- 初始化只固定来源、候选和审核结果，不绕过既有知识版本审核与发布状态机。

CREATE TABLE mk_knowledge_source_version_approval (
    id                    BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id             VARCHAR(64)  NOT NULL,
    source_version_id     BIGINT       NOT NULL,
    source_hash           VARCHAR(64)  NOT NULL,
    status                VARCHAR(16)  NOT NULL,
    approved_by           VARCHAR(64)  NOT NULL,
    approved_at           TIMESTAMP    NOT NULL,
    reason                VARCHAR(500) NOT NULL,
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by            VARCHAR(64)  NOT NULL,
    CONSTRAINT uk_mk_knowledge_source_approval UNIQUE (tenant_id, source_version_id),
    CONSTRAINT ck_mk_knowledge_source_approval_status CHECK (status IN ('APPROVED','REVOKED')),
    CONSTRAINT fk_mk_knowledge_source_approval_version
        FOREIGN KEY (source_version_id) REFERENCES source_version (id)
);

CREATE TABLE mk_knowledge_initialization_batch (
    id                         BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id                  VARCHAR(64)   NOT NULL,
    batch_code                 VARCHAR(64)   NOT NULL,
    release_type               VARCHAR(32)   NOT NULL,
    release_version            VARCHAR(32)   NOT NULL,
    foundation_release_version VARCHAR(32)   NULL,
    phase_code                 VARCHAR(8)    NOT NULL,
    status                     VARCHAR(16)   NOT NULL,
    source_manifest_hash       VARCHAR(64)   NOT NULL,
    candidate_manifest_hash    VARCHAR(64)   NOT NULL,
    overall_hash               VARCHAR(64)   NOT NULL,
    source_count               INTEGER       NOT NULL,
    candidate_count            INTEGER       NOT NULL,
    low_count                  INTEGER       NOT NULL,
    medium_count               INTEGER       NOT NULL,
    high_count                 INTEGER       NOT NULL,
    coverage_json              CLOB          NOT NULL,
    template_version           VARCHAR(64)   NOT NULL,
    model_version              VARCHAR(128)  NULL,
    summary                    VARCHAR(1000) NOT NULL,
    idempotency_key            VARCHAR(128)  NOT NULL,
    last_bulk_idempotency_key  VARCHAR(128)  NULL,
    last_bulk_at               TIMESTAMP     NULL,
    created_at                 TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                 VARCHAR(64)   NOT NULL,
    updated_at                 TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                 VARCHAR(64)   NOT NULL,
    CONSTRAINT uk_mk_knowledge_init_batch_code UNIQUE (tenant_id, batch_code),
    CONSTRAINT uk_mk_knowledge_init_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT ck_mk_knowledge_init_release_type
        CHECK (release_type IN ('FOUNDATION','CLINICAL_CONTENT','COMPOSITE')),
    CONSTRAINT ck_mk_knowledge_init_phase
        CHECK (phase_code IN ('F0','F1','F2','F3','F4','F5','F6','F7','F8')),
    CONSTRAINT ck_mk_knowledge_init_batch_status
        CHECK (status IN ('VALIDATED','IN_REVIEW','COMPLETE','BLOCKED')),
    CONSTRAINT ck_mk_knowledge_init_batch_counts CHECK (
        source_count > 0 AND candidate_count > 0
        AND low_count >= 0 AND medium_count >= 0 AND high_count >= 0
        AND low_count + medium_count + high_count = candidate_count
    ),
    CONSTRAINT ck_mk_knowledge_init_foundation_ref CHECK (
        (release_type = 'FOUNDATION' AND foundation_release_version IS NULL)
        OR (release_type <> 'FOUNDATION' AND foundation_release_version IS NOT NULL)
    )
);

CREATE TABLE mk_knowledge_initialization_item (
    id                          BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id                   VARCHAR(64)   NOT NULL,
    batch_id                    BIGINT        NOT NULL,
    sequence_no                 INTEGER       NOT NULL,
    catalog_code                VARCHAR(16)   NOT NULL,
    asset_type                  VARCHAR(32)   NOT NULL,
    canonical_id                VARCHAR(256)  NOT NULL,
    namespace                   VARCHAR(256)  NOT NULL,
    asset_version               VARCHAR(32)   NOT NULL,
    source_version_id           BIGINT        NOT NULL,
    source_hash                 VARCHAR(64)   NOT NULL,
    candidate_ref               VARCHAR(128)  NOT NULL,
    candidate_classification_id BIGINT        NOT NULL,
    candidate_content_hash      VARCHAR(64)   NOT NULL,
    risk_level                  VARCHAR(16)   NOT NULL,
    generated_by_model_flag     CHAR(1)       NOT NULL,
    dependencies_json           CLOB          NOT NULL,
    governance_json             CLOB          NOT NULL,
    change_type                 VARCHAR(32)   NOT NULL,
    replacement_canonical_id    VARCHAR(256)  NULL,
    effective_to                TIMESTAMP     NULL,
    status                      VARCHAR(24)   NOT NULL,
    created_at                  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                  VARCHAR(64)   NOT NULL,
    updated_at                  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                  VARCHAR(64)   NOT NULL,
    CONSTRAINT uk_mk_knowledge_init_item_sequence UNIQUE (tenant_id, batch_id, sequence_no),
    CONSTRAINT uk_mk_knowledge_init_item_canonical UNIQUE (tenant_id, batch_id, canonical_id),
    CONSTRAINT uk_mk_knowledge_init_item_candidate UNIQUE (tenant_id, candidate_classification_id),
    CONSTRAINT fk_mk_knowledge_init_item_batch FOREIGN KEY (batch_id)
        REFERENCES mk_knowledge_initialization_batch (id),
    CONSTRAINT fk_mk_knowledge_init_item_source FOREIGN KEY (source_version_id)
        REFERENCES source_version (id),
    CONSTRAINT fk_mk_knowledge_init_item_classification FOREIGN KEY (candidate_classification_id)
        REFERENCES mk_knowledge_candidate_classification (id),
    CONSTRAINT ck_mk_knowledge_init_item_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT ck_mk_knowledge_init_item_model CHECK (generated_by_model_flag IN ('Y','N')),
    CONSTRAINT ck_mk_knowledge_init_item_change
        CHECK (change_type IN ('NEW','PATCH_COMPATIBLE','MINOR_COMPATIBLE','MAJOR_BREAKING','DEPRECATION')),
    CONSTRAINT ck_mk_knowledge_init_item_status
        CHECK (status IN ('PENDING_REVIEW','APPROVED','BLOCKED')),
    CONSTRAINT ck_mk_knowledge_init_item_replacement CHECK (
        (change_type <> 'DEPRECATION' AND replacement_canonical_id IS NULL AND effective_to IS NULL)
        OR (change_type = 'DEPRECATION' AND replacement_canonical_id IS NOT NULL AND effective_to IS NOT NULL)
    )
);

CREATE INDEX idx_mk_knowledge_source_approval_status
    ON mk_knowledge_source_version_approval (tenant_id, status, approved_at);
CREATE INDEX idx_mk_knowledge_init_batch_status
    ON mk_knowledge_initialization_batch (tenant_id, status, created_at);
CREATE INDEX idx_mk_knowledge_init_batch_release
    ON mk_knowledge_initialization_batch (tenant_id, release_type, release_version, status);
CREATE INDEX idx_mk_knowledge_init_item_batch
    ON mk_knowledge_initialization_item (tenant_id, batch_id, status, sequence_no);
CREATE INDEX idx_mk_knowledge_init_item_source
    ON mk_knowledge_initialization_item (tenant_id, source_version_id, source_hash);

COMMENT ON TABLE mk_knowledge_source_version_approval IS '知识来源版本独立批准记录，登记人与批准人职责分离';
COMMENT ON COLUMN mk_knowledge_source_version_approval.source_hash IS '批准时官方来源原件的 SHA-256 摘要，漂移后批准失效';
COMMENT ON TABLE mk_knowledge_initialization_batch IS '生产知识初始化发行批次，固定来源清单、候选清单、风险统计和整体摘要';
COMMENT ON COLUMN mk_knowledge_initialization_batch.overall_hash IS '初始化发行预览的稳定整体 SHA-256 摘要';
COMMENT ON COLUMN mk_knowledge_initialization_batch.foundation_release_version IS '临床内容或组合资产锁定的基础发行版本';
COMMENT ON TABLE mk_knowledge_initialization_item IS '初始化发行候选条目，复用既有候选分类与 LOW、MEDIUM、HIGH 审核链';
COMMENT ON COLUMN mk_knowledge_initialization_item.candidate_classification_id IS '固定的知识候选分类记录，禁止由前端批审时替换候选集合';
COMMENT ON COLUMN mk_knowledge_initialization_item.generated_by_model_flag IS '候选是否含模型生成内容；基础 canonical 数据禁止模型生成';

-- ROLLBACK: 如需回滚，先导出发行摘要、来源批准与审核证据，再按条目、批次、来源批准的逆序删除新增表。
