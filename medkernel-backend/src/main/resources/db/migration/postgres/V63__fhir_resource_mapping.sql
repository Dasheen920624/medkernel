-- MedKernel v1.0 GA · OPT-01 FHIR 资源映射与规则表（PostgreSQL）
-- ROLLBACK：确认无 FHIR 资源映射证据与字段映射规则依赖后，删除 mk_fhir_resource_mapping 与 mk_fhir_mapping_rule。

CREATE TABLE IF NOT EXISTS mk_fhir_resource_mapping (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_id               VARCHAR(64)  NOT NULL,
    org_path                VARCHAR(512) NOT NULL,
    fhir_version            VARCHAR(8)   NOT NULL,
    fhir_resource_type      VARCHAR(64)  NOT NULL,
    fhir_id                 VARCHAR(128) NOT NULL,
    canonical_resource_id   BIGINT       NOT NULL,
    canonical_resource_type VARCHAR(64)  NOT NULL,
    field_mapping_rate      NUMERIC(7,4) NOT NULL,
    missing_field_count     INTEGER      NOT NULL DEFAULT 0,
    mapping_status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    trace_id                VARCHAR(128) NULL,
    created_at              TIMESTAMPTZ  NOT NULL,
    created_by              VARCHAR(64)  NOT NULL,
    updated_at              TIMESTAMPTZ  NOT NULL,
    updated_by              VARCHAR(64)  NOT NULL,
    CONSTRAINT uk_mk_fhir_res_map_fhir UNIQUE (tenant_id, fhir_version, fhir_resource_type, fhir_id),
    CONSTRAINT uk_mk_fhir_res_map_canon UNIQUE (tenant_id, canonical_resource_id, fhir_version),
    CONSTRAINT ck_mk_fhir_res_map_ver CHECK (fhir_version IN ('R4','R5')),
    CONSTRAINT ck_mk_fhir_res_map_status CHECK (mapping_status IN ('ACTIVE','DEPRECATED','UNMAPPED_WARNING')),
    CONSTRAINT ck_mk_fhir_res_map_rate CHECK (field_mapping_rate >= 0 AND field_mapping_rate <= 1),
    CONSTRAINT ck_mk_fhir_res_map_missing CHECK (missing_field_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_mk_fhir_res_map_tenant
    ON mk_fhir_resource_mapping (tenant_id, fhir_resource_type);
CREATE INDEX IF NOT EXISTS idx_mk_fhir_res_map_canon
    ON mk_fhir_resource_mapping (tenant_id, canonical_resource_id);

CREATE TABLE IF NOT EXISTS mk_fhir_mapping_rule (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_id               VARCHAR(64)  NOT NULL,
    rule_code               VARCHAR(128) NOT NULL,
    fhir_version            VARCHAR(8)   NOT NULL,
    fhir_resource_type      VARCHAR(64)  NOT NULL,
    canonical_resource_type VARCHAR(64)  NOT NULL,
    fhir_path               VARCHAR(256) NOT NULL,
    canonical_path          VARCHAR(256) NOT NULL,
    required_field          BOOLEAN      NOT NULL DEFAULT FALSE,
    transform_type          VARCHAR(64)  NOT NULL,
    rule_version            INTEGER      NOT NULL,
    status                  VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    trace_id                VARCHAR(128) NULL,
    created_at              TIMESTAMPTZ  NOT NULL,
    created_by              VARCHAR(64)  NOT NULL,
    updated_at              TIMESTAMPTZ  NOT NULL,
    updated_by              VARCHAR(64)  NOT NULL,
    CONSTRAINT uk_mk_fhir_rule_code UNIQUE (tenant_id, rule_code, fhir_version, rule_version),
    CONSTRAINT ck_mk_fhir_rule_ver CHECK (fhir_version IN ('R4','R5')),
    CONSTRAINT ck_mk_fhir_rule_status CHECK (status IN ('ACTIVE','DEPRECATED','UNMAPPED_WARNING')),
    CONSTRAINT ck_mk_fhir_rule_version CHECK (rule_version > 0)
);

CREATE INDEX IF NOT EXISTS idx_mk_fhir_rule_tenant
    ON mk_fhir_mapping_rule (tenant_id, fhir_version, fhir_resource_type, status);

COMMENT ON TABLE mk_fhir_resource_mapping IS 'FHIR 资源与标准临床资源的一一映射证据';
COMMENT ON COLUMN mk_fhir_resource_mapping.tenant_id IS '租户 ID';
COMMENT ON COLUMN mk_fhir_resource_mapping.org_path IS '组织路径快照';
COMMENT ON COLUMN mk_fhir_resource_mapping.fhir_version IS 'FHIR 版本：R4 或 R5';
COMMENT ON COLUMN mk_fhir_resource_mapping.fhir_resource_type IS 'FHIR 资源类型';
COMMENT ON COLUMN mk_fhir_resource_mapping.fhir_id IS 'FHIR 资源 ID';
COMMENT ON COLUMN mk_fhir_resource_mapping.canonical_resource_id IS '标准临床资源表主键';
COMMENT ON COLUMN mk_fhir_resource_mapping.canonical_resource_type IS '标准临床资源类型';
COMMENT ON COLUMN mk_fhir_resource_mapping.field_mapping_rate IS '字段映射完成率，0 到 1';
COMMENT ON COLUMN mk_fhir_resource_mapping.missing_field_count IS '缺失字段数量';
COMMENT ON COLUMN mk_fhir_resource_mapping.mapping_status IS '映射状态';
COMMENT ON COLUMN mk_fhir_resource_mapping.trace_id IS '链路追踪 ID';
COMMENT ON COLUMN mk_fhir_resource_mapping.created_at IS '创建时间';
COMMENT ON COLUMN mk_fhir_resource_mapping.created_by IS '创建人';
COMMENT ON COLUMN mk_fhir_resource_mapping.updated_at IS '更新时间';
COMMENT ON COLUMN mk_fhir_resource_mapping.updated_by IS '更新人';

COMMENT ON TABLE mk_fhir_mapping_rule IS 'FHIR 字段到标准临床字段的版本化映射规则';
COMMENT ON COLUMN mk_fhir_mapping_rule.tenant_id IS '租户 ID';
COMMENT ON COLUMN mk_fhir_mapping_rule.rule_code IS '规则编码';
COMMENT ON COLUMN mk_fhir_mapping_rule.fhir_version IS 'FHIR 版本：R4 或 R5';
COMMENT ON COLUMN mk_fhir_mapping_rule.fhir_resource_type IS 'FHIR 资源类型';
COMMENT ON COLUMN mk_fhir_mapping_rule.canonical_resource_type IS '标准临床资源类型';
COMMENT ON COLUMN mk_fhir_mapping_rule.fhir_path IS 'FHIR 字段路径';
COMMENT ON COLUMN mk_fhir_mapping_rule.canonical_path IS '标准临床字段路径';
COMMENT ON COLUMN mk_fhir_mapping_rule.required_field IS '是否必填字段';
COMMENT ON COLUMN mk_fhir_mapping_rule.transform_type IS '字段转换类型';
COMMENT ON COLUMN mk_fhir_mapping_rule.rule_version IS '规则版本号';
COMMENT ON COLUMN mk_fhir_mapping_rule.status IS '规则状态';
COMMENT ON COLUMN mk_fhir_mapping_rule.trace_id IS '链路追踪 ID';
COMMENT ON COLUMN mk_fhir_mapping_rule.created_at IS '创建时间';
COMMENT ON COLUMN mk_fhir_mapping_rule.created_by IS '创建人';
COMMENT ON COLUMN mk_fhir_mapping_rule.updated_at IS '更新时间';
COMMENT ON COLUMN mk_fhir_mapping_rule.updated_by IS '更新人';
