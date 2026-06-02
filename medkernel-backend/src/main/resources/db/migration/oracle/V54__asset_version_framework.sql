-- MedKernel v1.0 GA · SYS-04 不可变资产版本框架（Oracle）
-- ROLLBACK：确认无资产引擎接入 mk_version_asset_version 后，删除该表及索引。

CREATE TABLE mk_version_asset_version (
    id                 NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version_id         VARCHAR2(64)  NOT NULL,
    tenant_id          VARCHAR2(64)  NOT NULL,
    asset_type         VARCHAR2(32)  NOT NULL,
    asset_identity     VARCHAR2(128) NOT NULL,
    version_no         VARCHAR2(64)  NOT NULL,
    org_path           VARCHAR2(256) NOT NULL,
    applicable_scope   VARCHAR2(256) NOT NULL,
    content_hash       VARCHAR2(64)  NOT NULL,
    status             VARCHAR2(32)  NOT NULL,
    active_scope_key   VARCHAR2(512) NOT NULL,
    source_ref         VARCHAR2(512) NULL,
    effective_from     TIMESTAMP WITH TIME ZONE NULL,
    effective_to       TIMESTAMP WITH TIME ZONE NULL,
    created_at         TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by         VARCHAR2(64)  NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by         VARCHAR2(64)  NOT NULL,
    trace_id           VARCHAR2(128) NULL,
    CONSTRAINT uk_mk_version_asset_version_id UNIQUE (version_id),
    CONSTRAINT uk_mk_version_asset_version_no UNIQUE (tenant_id, asset_type, asset_identity, version_no),
    CONSTRAINT uk_mk_version_asset_version_active UNIQUE (tenant_id, asset_type, active_scope_key),
    CONSTRAINT ck_mk_version_asset_version_type CHECK (asset_type IN
        ('KNOWLEDGE','TERMINOLOGY','RULE','PATHWAY','PACKAGE','EVALUATION')),
    CONSTRAINT ck_mk_version_asset_version_status CHECK (status IN
        ('DRAFT','PENDING_REVIEW','PUBLISHED','ACTIVE','OFFLINE','ARCHIVED')),
    CONSTRAINT ck_mk_version_asset_version_hash CHECK (REGEXP_LIKE(content_hash, '^[0-9a-f]{64}$'))
);

CREATE INDEX idx_mk_version_asset_version_tenant_status
    ON mk_version_asset_version (tenant_id, status, updated_at);
CREATE INDEX idx_mk_version_asset_version_identity
    ON mk_version_asset_version (tenant_id, asset_type, asset_identity);
CREATE INDEX idx_mk_version_asset_version_active_scope
    ON mk_version_asset_version (tenant_id, asset_type, active_scope_key, status);

COMMENT ON TABLE mk_version_asset_version IS '通用配置资产版本：登记不可变版本、内容指纹和生效域唯一键';
COMMENT ON COLUMN mk_version_asset_version.version_id IS '版本业务 ID，跨方言唯一';
COMMENT ON COLUMN mk_version_asset_version.tenant_id IS '租户 ID';
COMMENT ON COLUMN mk_version_asset_version.asset_type IS '资产类型：知识、术语、规则、路径、包或评估指标';
COMMENT ON COLUMN mk_version_asset_version.asset_identity IS '资产身份编码，同一身份下版本号单调演进';
COMMENT ON COLUMN mk_version_asset_version.version_no IS '资产版本号，租户与资产身份内唯一';
COMMENT ON COLUMN mk_version_asset_version.org_path IS '组织生效域，记录七层组织继承中的发布范围';
COMMENT ON COLUMN mk_version_asset_version.applicable_scope IS '适用人群或上下文范围，参与唯一生效域判定';
COMMENT ON COLUMN mk_version_asset_version.content_hash IS '资产内容 SHA-256 十六进制指纹，禁止版本号或时间戳伪造';
COMMENT ON COLUMN mk_version_asset_version.status IS '版本状态：DRAFT 草稿 / PENDING_REVIEW 待审核 / PUBLISHED 已发布 / ACTIVE 生效中 / OFFLINE 已下线 / ARCHIVED 已归档';
COMMENT ON COLUMN mk_version_asset_version.active_scope_key IS 'ACTIVE 时为资产身份、组织域和适用域拼接键；非 ACTIVE 使用 version:<version_id> 保持唯一';
COMMENT ON COLUMN mk_version_asset_version.source_ref IS '来源引用，例如知识来源、规则定义或包条目引用';
COMMENT ON COLUMN mk_version_asset_version.effective_from IS '生效开始时间，PR1 可为空，后续发布流写入';
COMMENT ON COLUMN mk_version_asset_version.effective_to IS '生效结束时间，PR1 可为空，后续下线或回滚写入';
COMMENT ON COLUMN mk_version_asset_version.created_at IS '创建时间';
COMMENT ON COLUMN mk_version_asset_version.created_by IS '创建人';
COMMENT ON COLUMN mk_version_asset_version.updated_at IS '更新时间';
COMMENT ON COLUMN mk_version_asset_version.updated_by IS '更新人';
COMMENT ON COLUMN mk_version_asset_version.trace_id IS '链路追踪 ID';
