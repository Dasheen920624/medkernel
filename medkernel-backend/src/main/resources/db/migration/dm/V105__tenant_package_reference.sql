-- MedKernel v1.0 GA · P5-6.1 租户开通平台包引用制（达梦 DM）
-- 正向迁移：新增租户对平台知识包的引用表，开通只写引用，不复制 knowledge_package/package_item。
-- ROLLBACK: 回退需先确认无租户仍依赖平台包引用开通结果，再删除 mk_pkg_tenant_package_reference。

CREATE TABLE mk_pkg_tenant_package_reference (
    id                  NUMBER(19)    IDENTITY PRIMARY KEY,
    reference_id        VARCHAR2(64)  NOT NULL,
    tenant_id           VARCHAR2(64)  NOT NULL,
    platform_tenant_id  VARCHAR2(64)  NOT NULL,
    platform_package_id VARCHAR2(64)  NOT NULL,
    package_code        VARCHAR2(128) NOT NULL,
    package_version     VARCHAR2(64)  NOT NULL,
    target_org_unit_id  VARCHAR2(64)  NOT NULL,
    source_template_code VARCHAR2(128) NOT NULL,
    status              VARCHAR2(32)  NOT NULL,
    created_at          TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by          VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at          TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by          VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    trace_id            VARCHAR2(128) NULL,
    CONSTRAINT uk_pkg_tpref_id UNIQUE (reference_id),
    CONSTRAINT uk_pkg_tpref_scope UNIQUE (tenant_id, package_code, package_version, target_org_unit_id),
    CONSTRAINT fk_pkg_tpref_platform_package FOREIGN KEY (platform_tenant_id, platform_package_id)
        REFERENCES knowledge_package (tenant_id, package_id),
    CONSTRAINT ck_pkg_tpref_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE INDEX idx_pkg_tpref_tenant_status
    ON mk_pkg_tenant_package_reference (tenant_id, status, updated_at);
CREATE INDEX idx_pkg_tpref_package
    ON mk_pkg_tenant_package_reference (package_code, package_version);

COMMENT ON TABLE mk_pkg_tenant_package_reference IS '租户平台包引用：记录租户开通时引用的平台知识包，不复制平台包资产';
COMMENT ON COLUMN mk_pkg_tenant_package_reference.reference_id IS '引用业务 ID';
COMMENT ON COLUMN mk_pkg_tenant_package_reference.tenant_id IS '引用归属租户 ID';
COMMENT ON COLUMN mk_pkg_tenant_package_reference.platform_tenant_id IS '被引用平台知识包归属租户 ID';
COMMENT ON COLUMN mk_pkg_tenant_package_reference.platform_package_id IS '被引用的平台知识包业务 ID';
COMMENT ON COLUMN mk_pkg_tenant_package_reference.package_code IS '平台知识包稳定编码';
COMMENT ON COLUMN mk_pkg_tenant_package_reference.package_version IS '平台知识包版本';
COMMENT ON COLUMN mk_pkg_tenant_package_reference.target_org_unit_id IS '引用生效目标组织 ID';
COMMENT ON COLUMN mk_pkg_tenant_package_reference.source_template_code IS '来源首发模板编码';
COMMENT ON COLUMN mk_pkg_tenant_package_reference.status IS '引用状态：ACTIVE 启用、INACTIVE 停用';
COMMENT ON COLUMN mk_pkg_tenant_package_reference.created_at IS '创建时间';
COMMENT ON COLUMN mk_pkg_tenant_package_reference.created_by IS '创建人';
COMMENT ON COLUMN mk_pkg_tenant_package_reference.updated_at IS '更新时间';
COMMENT ON COLUMN mk_pkg_tenant_package_reference.updated_by IS '更新人';
COMMENT ON COLUMN mk_pkg_tenant_package_reference.trace_id IS '链路追踪 ID';
