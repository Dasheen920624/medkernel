-- MedKernel v1.0 GA · 插件安全边界（PostgreSQL）
-- ROLLBACK：确认插件授权审计已归档后，先删除 mk_plugin_grant，再删除 mk_plugin_registry。

CREATE TABLE IF NOT EXISTS mk_plugin_registry (
    id                 BIGSERIAL    PRIMARY KEY,
    plugin_id          VARCHAR(64)  NOT NULL,
    tenant_id          VARCHAR(64)  NOT NULL,
    plugin_code        VARCHAR(128) NOT NULL,
    display_name       VARCHAR(128) NOT NULL,
    status             VARCHAR(32)  NOT NULL DEFAULT 'PENDING_REVIEW',
    authority_boundary VARCHAR(32)  NOT NULL DEFAULT 'READ_ONLY',
    capabilities_json  TEXT         NOT NULL,
    version            BIGINT       NOT NULL DEFAULT 1,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by         VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by         VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id           VARCHAR(128) NULL,
    CONSTRAINT uk_plugin_registry_id UNIQUE (plugin_id),
    CONSTRAINT uk_plugin_registry_tenant_code UNIQUE (tenant_id, plugin_code),
    CONSTRAINT ck_plugin_registry_status CHECK (status IN ('PENDING_REVIEW','AUTHORIZED','DISABLED')),
    CONSTRAINT ck_plugin_registry_boundary CHECK (authority_boundary IN ('READ_ONLY','CONTROLLED_WRITE')),
    CONSTRAINT ck_plugin_registry_version CHECK (version >= 1)
);

CREATE INDEX IF NOT EXISTS idx_plugin_registry_tenant
    ON mk_plugin_registry (tenant_id, status, plugin_code);

CREATE TABLE IF NOT EXISTS mk_plugin_grant (
    id                         BIGSERIAL    PRIMARY KEY,
    grant_id                   VARCHAR(64)  NOT NULL,
    plugin_id                  VARCHAR(64)  NOT NULL,
    tenant_id                  VARCHAR(64)  NOT NULL,
    capability_key             VARCHAR(128) NOT NULL,
    capability_type            VARCHAR(32)  NOT NULL,
    service_contract_id        VARCHAR(128) NOT NULL,
    status                     VARCHAR(32)  NOT NULL DEFAULT 'AUTHORIZED',
    approval_reason            VARCHAR(500) NULL,
    clinical_safety_confirmed  CHAR(1)      NOT NULL DEFAULT 'N',
    version                    BIGINT       NOT NULL DEFAULT 1,
    granted_at                 TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    granted_by                 VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by                 VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id                   VARCHAR(128) NULL,
    CONSTRAINT uk_mk_plugin_grant_id UNIQUE (grant_id),
    CONSTRAINT uk_mk_plugin_grant_capability UNIQUE (tenant_id, plugin_id, capability_key),
    CONSTRAINT fk_plugin_grant_registry FOREIGN KEY (plugin_id) REFERENCES mk_plugin_registry(plugin_id),
    CONSTRAINT ck_mk_plugin_grant_type CHECK (capability_type IN ('READ','EXECUTE','WRITE')),
    CONSTRAINT ck_mk_plugin_grant_status CHECK (status IN ('AUTHORIZED','REVOKED')),
    CONSTRAINT ck_mk_plugin_grant_clinical CHECK (clinical_safety_confirmed IN ('Y','N')),
    CONSTRAINT ck_mk_plugin_grant_version CHECK (version >= 1)
);

CREATE INDEX IF NOT EXISTS idx_mk_plugin_grant_tenant_status
    ON mk_plugin_grant (tenant_id, plugin_id, status);

COMMENT ON TABLE mk_plugin_registry IS '插件安全边界声明表，记录租户插件、能力边界和审核状态';
COMMENT ON COLUMN mk_plugin_registry.plugin_id IS '插件实例稳定标识';
COMMENT ON COLUMN mk_plugin_registry.authority_boundary IS '插件最高权限边界，READ_ONLY 或 CONTROLLED_WRITE';
COMMENT ON COLUMN mk_plugin_registry.capabilities_json IS '插件声明的服务契约能力清单，不保存密钥或凭证';
COMMENT ON TABLE mk_plugin_grant IS '插件能力授权表，记录受控写入审批和临床安全确认';
COMMENT ON COLUMN mk_plugin_grant.service_contract_id IS '插件能力绑定的服务契约 ID，禁止直接绑定数据库表';
COMMENT ON COLUMN mk_plugin_grant.clinical_safety_confirmed IS '临床数据写能力是否已完成安全确认，Y 或 N';
