-- MedKernel v1.0 GA · GA-ENG-API-10 包发布 API（H2 baseline，MODE=PostgreSQL 兼容）

CREATE TABLE IF NOT EXISTS knowledge_package (
    id                BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    package_id        VARCHAR(64)   NOT NULL,
    tenant_id         VARCHAR(64)   NOT NULL,
    package_code      VARCHAR(128)  NOT NULL,
    package_version   VARCHAR(64)   NOT NULL,
    name              VARCHAR(256)  NOT NULL,
    description       CLOB          NULL,
    status            VARCHAR(32)   NOT NULL DEFAULT 'DRAFT',
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64)   NOT NULL DEFAULT 'system',
    trace_id          VARCHAR(128)  NULL,
    CONSTRAINT uk_knowledge_package_id UNIQUE (tenant_id, package_id),
    CONSTRAINT uk_knowledge_package_tenant_version UNIQUE (tenant_id, package_code, package_version),
    CONSTRAINT ck_knowledge_package_status CHECK (status IN (
        'DRAFT','PENDING_REVIEW','PUBLISHED','ACTIVE','OFFLINE','ARCHIVED'
    ))
);

CREATE INDEX IF NOT EXISTS idx_knowledge_pkg_tenant_status ON knowledge_package (tenant_id, status, updated_at);

CREATE TABLE IF NOT EXISTS package_item (
    id                BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_id           VARCHAR(64)   NOT NULL,
    tenant_id         VARCHAR(64)   NOT NULL,
    package_id        VARCHAR(64)   NOT NULL,
    asset_type        VARCHAR(32)   NOT NULL,
    asset_id          VARCHAR(64)   NOT NULL,
    asset_version     VARCHAR(64)   NOT NULL,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64)   NOT NULL DEFAULT 'system',
    trace_id          VARCHAR(128)  NULL,
    CONSTRAINT uk_package_item_id UNIQUE (item_id),
    CONSTRAINT uk_package_item_tenant_asset UNIQUE (tenant_id, package_id, asset_type, asset_id),
    CONSTRAINT ck_package_item_asset_type CHECK (asset_type IN (
        'KNOWLEDGE','TERMINOLOGY','RULE','PATHWAY','EVALUATION','FOLLOWUP'
    ))
);

CREATE INDEX IF NOT EXISTS idx_package_item_pkg ON package_item (tenant_id, package_id, asset_type);

CREATE TABLE IF NOT EXISTS release_plan (
    id                BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plan_id           VARCHAR(64)   NOT NULL,
    tenant_id         VARCHAR(64)   NOT NULL,
    package_id        VARCHAR(64)   NOT NULL,
    target_org_unit_id VARCHAR(64)  NOT NULL,
    strategy          VARCHAR(32)   NOT NULL,
    scope_type        VARCHAR(32)   NOT NULL,
    scope_value       VARCHAR(256)  NULL,
    status            VARCHAR(32)   NOT NULL DEFAULT 'DRAFT',
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64)   NOT NULL DEFAULT 'system',
    trace_id          VARCHAR(128)  NULL,
    CONSTRAINT uk_release_plan_id UNIQUE (plan_id),
    CONSTRAINT ck_release_plan_strategy CHECK (strategy IN ('GRAYSCALE','FULL')),
    CONSTRAINT ck_release_plan_scope_type CHECK (scope_type IN ('ALL','GROUP','HOSPITAL','CAMPUS','SITE','DEPARTMENT')),
    CONSTRAINT ck_release_plan_status CHECK (status IN ('DRAFT','EXECUTING','SUCCESS','FAILED','ROLLBACKED'))
);

CREATE INDEX IF NOT EXISTS idx_release_plan_pkg ON release_plan (tenant_id, package_id, status);

CREATE TABLE IF NOT EXISTS sync_log (
    id                BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    log_id            VARCHAR(64)   NOT NULL,
    tenant_id         VARCHAR(64)   NOT NULL,
    plan_id           VARCHAR(64)   NOT NULL,
    adapter_id         VARCHAR(64)   NOT NULL,
    status            VARCHAR(32)   NOT NULL DEFAULT 'RUNNING',
    error_code        VARCHAR(64)   NULL,
    error_message     CLOB          NULL,
    retry_count       INT           NOT NULL DEFAULT 0,
    sync_evidence     CLOB          NULL,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64)   NOT NULL DEFAULT 'system',
    trace_id          VARCHAR(128)  NULL,
    CONSTRAINT uk_sync_log_id UNIQUE (log_id),
    CONSTRAINT ck_sync_log_status CHECK (status IN ('RUNNING','SUCCESS','FAILED','RETRYING'))
);

CREATE INDEX IF NOT EXISTS idx_sync_log_plan ON sync_log (tenant_id, plan_id, status);
