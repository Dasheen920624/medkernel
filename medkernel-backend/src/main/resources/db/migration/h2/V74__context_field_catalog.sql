-- MedKernel v1.0 GA · P2/P5 上下文字段目录租户扩展（H2 PostgreSQL 兼容模式）
-- 系统级字段目录由代码从 canonical 派生；本表仅存租户自定义/补充字段，可前台维护。

CREATE TABLE IF NOT EXISTS mk_context_field_catalog (
    id            BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    field_id      VARCHAR(64)   NOT NULL,
    tenant_id     VARCHAR(64)   NOT NULL,
    category      VARCHAR(64)   NOT NULL,
    group_name    VARCHAR(64)   NOT NULL,
    resource_type VARCHAR(40)   NOT NULL,
    field_path    VARCHAR(200)  NOT NULL,
    display_name  VARCHAR(200)  NOT NULL,
    data_type     VARCHAR(20)   NOT NULL,
    unit          VARCHAR(40)   NULL,
    code_system   VARCHAR(64)   NULL,
    description   VARCHAR(500)  NULL,
    status        VARCHAR(20)   NOT NULL,
    created_at    TIMESTAMP     NOT NULL,
    created_by    VARCHAR(64)   NOT NULL,
    updated_at    TIMESTAMP     NOT NULL,
    updated_by    VARCHAR(64)   NOT NULL,
    trace_id      VARCHAR(128)  NULL,
    CONSTRAINT uk_mk_ctx_field_catalog_tenant_path UNIQUE (tenant_id, field_path),
    CONSTRAINT ck_mk_ctx_field_catalog_data_type
        CHECK (data_type IN ('number','string','boolean','date','code','list')),
    CONSTRAINT ck_mk_ctx_field_catalog_status CHECK (status IN ('ACTIVE','DEPRECATED'))
);

CREATE INDEX IF NOT EXISTS idx_mk_ctx_field_catalog_tenant
    ON mk_context_field_catalog (tenant_id);

COMMENT ON TABLE mk_context_field_catalog IS '上下文字段目录租户自定义扩展字段';
