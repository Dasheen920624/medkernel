-- MedKernel v1.0 GA · P12-5 条件片段库（H2）
-- ROLLBACK：如需回滚，先导出 mk_engine_condition_fragment 审计证据与引用资产影响分析，再删除本表。

CREATE TABLE IF NOT EXISTS mk_engine_condition_fragment (
    id                  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fragment_id         VARCHAR(64)  NOT NULL,
    tenant_id           VARCHAR(64)  NOT NULL,
    fragment_code       VARCHAR(64)  NOT NULL,
    name                VARCHAR(200) NOT NULL,
    category            VARCHAR(64)  NULL,
    body_json           CLOB         NOT NULL,
    version_no          INTEGER      NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    package_version     VARCHAR(40)  NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id            VARCHAR(128) NULL,
    CONSTRAINT uk_mk_engine_condition_fragment_id UNIQUE (tenant_id, fragment_id),
    CONSTRAINT uk_mk_engine_condition_fragment_version UNIQUE (tenant_id, fragment_code, version_no)
);

CREATE INDEX IF NOT EXISTS idx_mk_engine_condition_fragment_code
    ON mk_engine_condition_fragment (tenant_id, fragment_code);

CREATE INDEX IF NOT EXISTS idx_mk_engine_condition_fragment_package
    ON mk_engine_condition_fragment (tenant_id, package_version);

CREATE INDEX IF NOT EXISTS idx_mk_engine_condition_fragment_status
    ON mk_engine_condition_fragment (tenant_id, status);

COMMENT ON TABLE mk_engine_condition_fragment IS '条件片段库，用于规则 when 与路径守卫复用命名条件组';
COMMENT ON COLUMN mk_engine_condition_fragment.fragment_id IS '条件片段业务 ID';
COMMENT ON COLUMN mk_engine_condition_fragment.tenant_id IS '租户 ID';
COMMENT ON COLUMN mk_engine_condition_fragment.fragment_code IS '条件片段编码';
COMMENT ON COLUMN mk_engine_condition_fragment.name IS '条件片段名称';
COMMENT ON COLUMN mk_engine_condition_fragment.category IS '条件片段分类';
COMMENT ON COLUMN mk_engine_condition_fragment.body_json IS '条件片段正文 JSON';
COMMENT ON COLUMN mk_engine_condition_fragment.version_no IS '条件片段版本号';
COMMENT ON COLUMN mk_engine_condition_fragment.status IS '条件片段状态';
COMMENT ON COLUMN mk_engine_condition_fragment.package_version IS '条件片段所属知识包版本';
COMMENT ON COLUMN mk_engine_condition_fragment.created_at IS '条件片段创建时间';
COMMENT ON COLUMN mk_engine_condition_fragment.created_by IS '条件片段创建人';
COMMENT ON COLUMN mk_engine_condition_fragment.updated_at IS '条件片段更新时间';
COMMENT ON COLUMN mk_engine_condition_fragment.updated_by IS '条件片段更新人';
COMMENT ON COLUMN mk_engine_condition_fragment.trace_id IS '请求链路追踪 ID';
