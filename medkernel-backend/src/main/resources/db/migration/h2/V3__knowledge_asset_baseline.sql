-- MedKernel v1.0 GA · GA-ENG-API-03 知识资产 baseline (H2 2.2, MODE=PostgreSQL)
-- 7 表：来源 / 来源版本 / 来源片段 / 知识身份 / 知识资产版本 / 引用 / 替代关系
-- + 1 表：异步导出作业
--
-- 设计约束（与详细规范 §1286 / §8.3-§8.4 / §1797-§1806 对齐）：
--   1. 所有表必须带 tenant_id NOT NULL；查询索引前缀都是 tenant_id
--   2. 唯一权威版本（每个 identity 同时刻 ≤ 1 个 ACTIVE）由 Service 层事务 + SELECT FOR UPDATE 保证；
--      不使用 partial unique index 以保证 5 方言兼容
--   3. 状态 / 风险 / 类型 字段统一用 VARCHAR + CHECK，避免数据库枚举类型
--   4. content_hash / payload_digest 用 VARCHAR(128)，足够 SHA-512 hex
--   5. anchors / filter_json 短期内用 VARCHAR(2048) 存 JSON 字符串；超长锚点树后续迁 TEXT
--   6. 4 列审计基线：created_at / created_by / updated_at / updated_by

-- ─── 来源域 ────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS source_document (
    id                BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id         VARCHAR(64)  NOT NULL,
    source_code       VARCHAR(128) NOT NULL,
    source_type       VARCHAR(32)  NOT NULL,
    authority_level   VARCHAR(32)  NOT NULL,
    title             VARCHAR(512) NOT NULL,
    publisher         VARCHAR(256) NULL,
    license           VARCHAR(128) NULL,
    language          VARCHAR(16)  NOT NULL DEFAULT 'zh-CN',
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64)  NOT NULL DEFAULT 'system',
    CONSTRAINT uk_source_document_tenant_code UNIQUE (tenant_id, source_code),
    CONSTRAINT ck_source_document_type CHECK (source_type IN
        ('GUIDELINE','DRUG_LABEL','STANDARD','POLICY','HOSPITAL_PROTOCOL','TCM_CLASSIC','LITERATURE','CONSENSUS','OTHER')),
    CONSTRAINT ck_source_document_authority CHECK (authority_level IN
        ('CHINA_NATIONAL','INTERNATIONAL','SOCIETY','HOSPITAL','OTHER'))
);

CREATE INDEX IF NOT EXISTS idx_source_document_tenant_type ON source_document (tenant_id, source_type);
CREATE INDEX IF NOT EXISTS idx_source_document_tenant_auth ON source_document (tenant_id, authority_level);

CREATE TABLE IF NOT EXISTS source_version (
    id                  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id           VARCHAR(64)  NOT NULL,
    source_document_id  BIGINT       NOT NULL,
    version_no          VARCHAR(64)  NOT NULL,
    published_at        TIMESTAMP    NULL,
    content_hash        VARCHAR(128) NOT NULL,
    file_uri            VARCHAR(512) NULL,
    language            VARCHAR(16)  NOT NULL DEFAULT 'zh-CN',
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(64)  NOT NULL DEFAULT 'system',
    CONSTRAINT uk_source_version_doc_no UNIQUE (source_document_id, version_no)
);

CREATE INDEX IF NOT EXISTS idx_source_version_tenant_doc ON source_version (tenant_id, source_document_id);

CREATE TABLE IF NOT EXISTS mk_knowledge_material_object (
    id              BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       VARCHAR(64)   NOT NULL,
    scope_key       VARCHAR(128)  NOT NULL,
    file_uri        VARCHAR(1024) NOT NULL,
    sha256          VARCHAR(64)   NOT NULL,
    content_type    VARCHAR(128)  NOT NULL,
    byte_size       BIGINT        NOT NULL,
    storage_backend VARCHAR(32)   NOT NULL,
    source_channel  VARCHAR(64)   NOT NULL,
    stored_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    stored_by       VARCHAR(64)   NULL,
    CONSTRAINT uk_mk_knowledge_material_object_scope_hash UNIQUE (tenant_id, scope_key, sha256),
    CONSTRAINT ck_mk_knowledge_material_object_backend CHECK (storage_backend IN ('LOCAL_FILE','REMOTE_URI'))
);

CREATE INDEX IF NOT EXISTS idx_mk_knowledge_material_object_lookup
    ON mk_knowledge_material_object (tenant_id, scope_key, sha256);
COMMENT ON TABLE mk_knowledge_material_object IS '知识文档原件资料库对象账本：记录受管 URI、真实 SHA-256、后端类型和入库审计，不保存原文字节';
COMMENT ON COLUMN mk_knowledge_material_object.file_uri IS '受管资料库 URI，可为现场 file、本地网关或对象存储 URI，禁止伪造临时路径';
COMMENT ON COLUMN mk_knowledge_material_object.sha256 IS '原件字节 SHA-256 指纹，用于取回校验和重复入库幂等';

CREATE TABLE IF NOT EXISTS source_fragment (
    id                  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id           VARCHAR(64)  NOT NULL,
    source_version_id   BIGINT       NOT NULL,
    anchor_path         VARCHAR(256) NOT NULL,
    anchor_label        VARCHAR(256) NULL,
    text_excerpt        VARCHAR(2048) NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_source_fragment_version_anchor UNIQUE (source_version_id, anchor_path)
);

CREATE INDEX IF NOT EXISTS idx_source_fragment_tenant_ver ON source_fragment (tenant_id, source_version_id);

-- ─── 知识身份 + 版本 ─────────────────────────────────────

CREATE TABLE IF NOT EXISTS knowledge_identity (
    id                    BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id             VARCHAR(64)  NOT NULL,
    identity_code         VARCHAR(128) NOT NULL,
    domain                VARCHAR(32)  NOT NULL,
    subject               VARCHAR(512) NOT NULL,
    specialty_id          VARCHAR(64)  NULL,
    description           VARCHAR(2048) NULL,
    status                VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    current_version_id    BIGINT       NULL,
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by            VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by            VARCHAR(64)  NOT NULL DEFAULT 'system',
    CONSTRAINT uk_knowledge_identity_tenant_code UNIQUE (tenant_id, identity_code),
    CONSTRAINT ck_knowledge_identity_domain CHECK (domain IN
        ('GUIDELINE','DRUG','PATHWAY_KNOWLEDGE','NURSING','REPORT','TCM','PROTOCOL','POLICY','LITERATURE','OTHER')),
    CONSTRAINT ck_knowledge_identity_status CHECK (status IN ('ACTIVE','WITHDRAWN','ARCHIVED'))
);

CREATE INDEX IF NOT EXISTS idx_knowledge_identity_tenant_domain ON knowledge_identity (tenant_id, domain);
CREATE INDEX IF NOT EXISTS idx_knowledge_identity_specialty   ON knowledge_identity (tenant_id, specialty_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_identity_updated     ON knowledge_identity (tenant_id, updated_at);

CREATE TABLE IF NOT EXISTS knowledge_asset_version (
    id                  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id           VARCHAR(64)  NOT NULL,
    identity_id         BIGINT       NOT NULL,
    version_no          VARCHAR(64)  NOT NULL,
    version_label       VARCHAR(256) NULL,
    source_document_id  BIGINT       NULL,
    source_version_id   BIGINT       NULL,
    content_hash        VARCHAR(128) NOT NULL,
    anchors             VARCHAR(2048) NULL,
    status              VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    risk_level          VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM',
    effective_from      TIMESTAMP    NULL,
    effective_to        TIMESTAMP    NULL,
    reviewed_by         VARCHAR(64)  NULL,
    reviewed_at         TIMESTAMP    NULL,
    activated_at        TIMESTAMP    NULL,
    superseded_at       TIMESTAMP    NULL,
    withdrawn_at        TIMESTAMP    NULL,
    withdrawn_reason    VARCHAR(512) NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(64)  NOT NULL DEFAULT 'system',
    CONSTRAINT uk_knowledge_asset_version UNIQUE (identity_id, version_no),
    CONSTRAINT ck_knowledge_asset_version_status CHECK (status IN
        ('DRAFT','CANDIDATE','UNDER_REVIEW','ACTIVE','SUPERSEDED','WITHDRAWN','REJECTED')),
    CONSTRAINT ck_knowledge_asset_version_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH'))
);

CREATE INDEX IF NOT EXISTS idx_knowledge_av_identity_status ON knowledge_asset_version (identity_id, status);
CREATE INDEX IF NOT EXISTS idx_knowledge_av_tenant_status   ON knowledge_asset_version (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_knowledge_av_tenant_updated  ON knowledge_asset_version (tenant_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_knowledge_av_content_hash    ON knowledge_asset_version (tenant_id, content_hash);

-- ─── 引用 + 替代 ─────────────────────────────────────────

CREATE TABLE IF NOT EXISTS citation (
    id                  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id           VARCHAR(64)  NOT NULL,
    asset_version_id    BIGINT       NOT NULL,
    source_fragment_id  BIGINT       NOT NULL,
    relation            VARCHAR(32)  NOT NULL DEFAULT 'DERIVED_FROM',
    weight              INT          NOT NULL DEFAULT 100,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(64)  NOT NULL DEFAULT 'system',
    CONSTRAINT uk_citation_av_fragment UNIQUE (asset_version_id, source_fragment_id, relation),
    CONSTRAINT ck_citation_relation CHECK (relation IN
        ('DERIVED_FROM','CITES','CONTRADICTS','SUPPORTS','SUPERSEDES_OF'))
);

CREATE INDEX IF NOT EXISTS idx_citation_tenant_av ON citation (tenant_id, asset_version_id);
CREATE INDEX IF NOT EXISTS idx_citation_fragment  ON citation (source_fragment_id);

CREATE TABLE IF NOT EXISTS knowledge_supersession (
    id                  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id           VARCHAR(64)  NOT NULL,
    identity_id         BIGINT       NOT NULL,
    old_version_id      BIGINT       NULL,
    new_version_id      BIGINT       NULL,
    transition_type     VARCHAR(32)  NOT NULL,
    transition_reason   VARCHAR(512) NULL,
    transitioned_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    transitioned_by     VARCHAR(64)  NOT NULL,
    CONSTRAINT ck_knowledge_supersession_type CHECK (transition_type IN
        ('ACTIVATE','REPLACE','WITHDRAW','RESTORE','ROLLBACK'))
);

CREATE INDEX IF NOT EXISTS idx_supersession_tenant_identity ON knowledge_supersession (tenant_id, identity_id, transitioned_at);
CREATE INDEX IF NOT EXISTS idx_supersession_old ON knowledge_supersession (old_version_id);
CREATE INDEX IF NOT EXISTS idx_supersession_new ON knowledge_supersession (new_version_id);

-- ─── 异步导出作业 ────────────────────────────────────────

CREATE TABLE IF NOT EXISTS knowledge_export_job (
    id                BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id         VARCHAR(64)  NOT NULL,
    job_code          VARCHAR(64)  NOT NULL,
    requested_by      VARCHAR(64)  NOT NULL,
    export_type       VARCHAR(32)  NOT NULL,
    filter_json       VARCHAR(2048) NULL,
    status            VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    progress          INT          NOT NULL DEFAULT 0,
    result_uri        VARCHAR(512) NULL,
    item_count        BIGINT       NULL,
    error_message     VARCHAR(1024) NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at        TIMESTAMP    NULL,
    completed_at      TIMESTAMP    NULL,
    expires_at        TIMESTAMP    NULL,
    CONSTRAINT uk_knowledge_export_job_code UNIQUE (job_code),
    CONSTRAINT ck_knowledge_export_job_type CHECK (export_type IN
        ('IDENTITIES','VERSIONS','LINEAGE','CITATIONS','FULL_TENANT')),
    CONSTRAINT ck_knowledge_export_job_status CHECK (status IN
        ('PENDING','RUNNING','SUCCEEDED','FAILED','EXPIRED','CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_export_job_tenant_status  ON knowledge_export_job (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_export_job_tenant_created ON knowledge_export_job (tenant_id, created_at);
