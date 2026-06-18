-- MedKernel v1.0 GA · GA-ENG-API-03 知识资产 baseline (Oracle 23ai)
-- VARCHAR2 + NUMBER + IDENTITY；CHECK 内不带 NULL 比较

CREATE TABLE source_document (
    id                NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id         VARCHAR2(64)  NOT NULL,
    source_code       VARCHAR2(128) NOT NULL,
    source_type       VARCHAR2(32)  NOT NULL,
    authority_level   VARCHAR2(32)  NOT NULL,
    title             VARCHAR2(512) NOT NULL,
    publisher         VARCHAR2(256) NULL,
    license           VARCHAR2(128) NULL,
    language          VARCHAR2(16)  DEFAULT 'zh-CN' NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by        VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by        VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    CONSTRAINT uk_source_document_tenant_code UNIQUE (tenant_id, source_code),
    CONSTRAINT ck_source_document_type CHECK (source_type IN
        ('GUIDELINE','DRUG_LABEL','STANDARD','POLICY','HOSPITAL_PROTOCOL','TCM_CLASSIC','LITERATURE','CONSENSUS','OTHER')),
    CONSTRAINT ck_source_document_authority CHECK (authority_level IN
        ('CHINA_NATIONAL','INTERNATIONAL','SOCIETY','HOSPITAL','OTHER'))
);

CREATE INDEX idx_source_document_tenant_type ON source_document (tenant_id, source_type);
CREATE INDEX idx_source_document_tenant_auth ON source_document (tenant_id, authority_level);

COMMENT ON TABLE source_document IS '权威来源登记：指南/说明书/政策/院内制度/中医典籍';

CREATE TABLE source_version (
    id                  NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id           VARCHAR2(64)  NOT NULL,
    source_document_id  NUMBER(19)    NOT NULL,
    version_no          VARCHAR2(64)  NOT NULL,
    published_at        TIMESTAMP WITH TIME ZONE NULL,
    content_hash        VARCHAR2(128) NOT NULL,
    file_uri            VARCHAR2(512) NULL,
    language            VARCHAR2(16)  DEFAULT 'zh-CN' NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by          VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    CONSTRAINT uk_source_version_doc_no UNIQUE (source_document_id, version_no)
);

CREATE INDEX idx_source_version_tenant_doc ON source_version (tenant_id, source_document_id);
COMMENT ON TABLE source_version IS '权威来源的受控版本';

CREATE TABLE mk_knowledge_material_object (
    id              NUMBER(19)     GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       VARCHAR2(64)   NOT NULL,
    scope_key       VARCHAR2(128)  NOT NULL,
    file_uri        VARCHAR2(1024) NOT NULL,
    sha256          VARCHAR2(64)   NOT NULL,
    content_type    VARCHAR2(128)  NOT NULL,
    byte_size       NUMBER(19)     NOT NULL,
    storage_backend VARCHAR2(32)   NOT NULL,
    source_channel  VARCHAR2(64)   NOT NULL,
    stored_at       TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    stored_by       VARCHAR2(64)   NULL,
    CONSTRAINT uk_mk_knowledge_material_object_scope_hash UNIQUE (tenant_id, scope_key, sha256),
    CONSTRAINT ck_mk_knowledge_material_object_backend CHECK (storage_backend IN ('LOCAL_FILE','REMOTE_URI'))
);

-- Oracle 已为同列唯一约束建立索引，不重复创建等价普通索引。
COMMENT ON TABLE mk_knowledge_material_object IS '知识文档原件资料库对象账本：记录受管 URI、真实 SHA-256、后端类型和入库审计，不保存原文字节';
COMMENT ON COLUMN mk_knowledge_material_object.file_uri IS '受管资料库 URI，可为现场 file、本地网关或对象存储 URI，禁止伪造临时路径';
COMMENT ON COLUMN mk_knowledge_material_object.sha256 IS '原件字节 SHA-256 指纹，用于取回校验和重复入库幂等';

CREATE TABLE source_fragment (
    id                  NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id           VARCHAR2(64)  NOT NULL,
    source_version_id   NUMBER(19)    NOT NULL,
    anchor_path         VARCHAR2(256) NOT NULL,
    anchor_label        VARCHAR2(256) NULL,
    text_excerpt        VARCHAR2(2048) NULL,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT uk_source_fragment_version_anchor UNIQUE (source_version_id, anchor_path)
);

CREATE INDEX idx_source_fragment_tenant_ver ON source_fragment (tenant_id, source_version_id);
COMMENT ON TABLE source_fragment IS '权威来源版本的可引用内容片段';

CREATE TABLE knowledge_identity (
    id                    NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id             VARCHAR2(64)  NOT NULL,
    identity_code         VARCHAR2(128) NOT NULL,
    domain                VARCHAR2(32)  NOT NULL,
    subject               VARCHAR2(512) NOT NULL,
    specialty_id          VARCHAR2(64)  NULL,
    description           VARCHAR2(2048) NULL,
    status                VARCHAR2(32)  DEFAULT 'ACTIVE' NOT NULL,
    current_version_id    NUMBER(19)    NULL,
    created_at            TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by            VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by            VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    CONSTRAINT uk_knowledge_identity_tenant_code UNIQUE (tenant_id, identity_code),
    CONSTRAINT ck_knowledge_identity_domain CHECK (domain IN
        ('GUIDELINE','DRUG','PATHWAY_KNOWLEDGE','NURSING','REPORT','TCM','PROTOCOL','POLICY','LITERATURE','OTHER')),
    CONSTRAINT ck_knowledge_identity_status CHECK (status IN ('ACTIVE','WITHDRAWN','ARCHIVED'))
);

CREATE INDEX idx_knowledge_identity_tenant_domain ON knowledge_identity (tenant_id, domain);
CREATE INDEX idx_knowledge_identity_specialty   ON knowledge_identity (tenant_id, specialty_id);
CREATE INDEX idx_knowledge_identity_updated     ON knowledge_identity (tenant_id, updated_at);

COMMENT ON TABLE knowledge_identity IS '知识身份：跨版本的稳定主题（指南/药品/护理/路径/制度等）';

CREATE TABLE knowledge_asset_version (
    id                  NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id           VARCHAR2(64)  NOT NULL,
    identity_id         NUMBER(19)    NOT NULL,
    version_no          VARCHAR2(64)  NOT NULL,
    version_label       VARCHAR2(256) NULL,
    source_document_id  NUMBER(19)    NULL,
    source_version_id   NUMBER(19)    NULL,
    content_hash        VARCHAR2(128) NOT NULL,
    anchors             VARCHAR2(2048) NULL,
    status              VARCHAR2(32)  DEFAULT 'DRAFT' NOT NULL,
    risk_level          VARCHAR2(16)  DEFAULT 'MEDIUM' NOT NULL,
    effective_from      TIMESTAMP WITH TIME ZONE NULL,
    effective_to        TIMESTAMP WITH TIME ZONE NULL,
    reviewed_by         VARCHAR2(64)  NULL,
    reviewed_at         TIMESTAMP WITH TIME ZONE NULL,
    activated_at        TIMESTAMP WITH TIME ZONE NULL,
    superseded_at       TIMESTAMP WITH TIME ZONE NULL,
    withdrawn_at        TIMESTAMP WITH TIME ZONE NULL,
    withdrawn_reason    VARCHAR2(512) NULL,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by          VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by          VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    CONSTRAINT uk_knowledge_asset_version UNIQUE (identity_id, version_no),
    CONSTRAINT ck_knowledge_asset_version_status CHECK (status IN
        ('DRAFT','CANDIDATE','UNDER_REVIEW','ACTIVE','SUPERSEDED','WITHDRAWN','REJECTED')),
    CONSTRAINT ck_knowledge_asset_version_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH'))
);

CREATE INDEX idx_knowledge_av_identity_status ON knowledge_asset_version (identity_id, status);
CREATE INDEX idx_knowledge_av_tenant_status   ON knowledge_asset_version (tenant_id, status);
CREATE INDEX idx_knowledge_av_tenant_updated  ON knowledge_asset_version (tenant_id, updated_at);
CREATE INDEX idx_knowledge_av_content_hash    ON knowledge_asset_version (tenant_id, content_hash);

COMMENT ON TABLE knowledge_asset_version IS '知识资产版本：唯一权威版本由 Service 层事务保证';

CREATE TABLE citation (
    id                  NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id           VARCHAR2(64)  NOT NULL,
    asset_version_id    NUMBER(19)    NOT NULL,
    source_fragment_id  NUMBER(19)    NOT NULL,
    relation            VARCHAR2(32)  DEFAULT 'DERIVED_FROM' NOT NULL,
    weight              NUMBER(10)    DEFAULT 100 NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by          VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    CONSTRAINT uk_citation_av_fragment UNIQUE (asset_version_id, source_fragment_id, relation),
    CONSTRAINT ck_citation_relation CHECK (relation IN
        ('DERIVED_FROM','CITES','CONTRADICTS','SUPPORTS','SUPERSEDES_OF'))
);

CREATE INDEX idx_citation_tenant_av ON citation (tenant_id, asset_version_id);
CREATE INDEX idx_citation_fragment  ON citation (source_fragment_id);
COMMENT ON TABLE citation IS '知识资产版本与来源片段的引用关系';

CREATE TABLE knowledge_supersession (
    id                  NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id           VARCHAR2(64)  NOT NULL,
    identity_id         NUMBER(19)    NOT NULL,
    old_version_id      NUMBER(19)    NULL,
    new_version_id      NUMBER(19)    NULL,
    transition_type     VARCHAR2(32)  NOT NULL,
    transition_reason   VARCHAR2(512) NULL,
    transitioned_at     TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    transitioned_by     VARCHAR2(64)  NOT NULL,
    CONSTRAINT ck_knowledge_supersession_type CHECK (transition_type IN
        ('ACTIVATE','REPLACE','WITHDRAW','RESTORE','ROLLBACK'))
);

CREATE INDEX idx_supersession_tenant_identity ON knowledge_supersession (tenant_id, identity_id, transitioned_at);
CREATE INDEX idx_supersession_old ON knowledge_supersession (old_version_id);
CREATE INDEX idx_supersession_new ON knowledge_supersession (new_version_id);

COMMENT ON TABLE knowledge_supersession IS '版本替代/撤回历史链：用于历史重放和 lineage';

CREATE TABLE knowledge_export_job (
    id                NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id         VARCHAR2(64)  NOT NULL,
    job_code          VARCHAR2(64)  NOT NULL,
    requested_by      VARCHAR2(64)  NOT NULL,
    export_type       VARCHAR2(32)  NOT NULL,
    filter_json       VARCHAR2(2048) NULL,
    status            VARCHAR2(32)  DEFAULT 'PENDING' NOT NULL,
    progress          NUMBER(10)    DEFAULT 0 NOT NULL,
    result_uri        VARCHAR2(512) NULL,
    item_count        NUMBER(19)    NULL,
    error_message     VARCHAR2(1024) NULL,
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    started_at        TIMESTAMP WITH TIME ZONE NULL,
    completed_at      TIMESTAMP WITH TIME ZONE NULL,
    expires_at        TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT uk_knowledge_export_job_code UNIQUE (job_code),
    CONSTRAINT ck_knowledge_export_job_type CHECK (export_type IN
        ('IDENTITIES','VERSIONS','LINEAGE','CITATIONS','FULL_TENANT')),
    CONSTRAINT ck_knowledge_export_job_status CHECK (status IN
        ('PENDING','RUNNING','SUCCEEDED','FAILED','EXPIRED','CANCELLED'))
);

CREATE INDEX idx_export_job_tenant_status  ON knowledge_export_job (tenant_id, status);
CREATE INDEX idx_export_job_tenant_created ON knowledge_export_job (tenant_id, created_at);
COMMENT ON TABLE knowledge_export_job IS '知识资产异步导出任务';
