-- MedKernel v1.0 GA · GA-ENG-API-10 包发布 API（达梦）

CREATE TABLE knowledge_package (
    id                NUMBER(19) IDENTITY PRIMARY KEY,
    package_id        VARCHAR2(64)   NOT NULL,
    tenant_id         VARCHAR2(64)   NOT NULL,
    package_code      VARCHAR2(128)  NOT NULL,
    package_version   VARCHAR2(64)   NOT NULL,
    name              VARCHAR2(256)  NOT NULL,
    description       CLOB,
    status            VARCHAR2(32)   DEFAULT 'DRAFT' NOT NULL,
    created_at        TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by        VARCHAR2(64)   DEFAULT 'system' NOT NULL,
    updated_at        TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by        VARCHAR2(64)   DEFAULT 'system' NOT NULL,
    trace_id          VARCHAR2(128),
    CONSTRAINT uk_knowledge_package_id UNIQUE (tenant_id, package_id),
    CONSTRAINT uk_knowledge_package_tenant_version UNIQUE (tenant_id, package_code, package_version),
    CONSTRAINT ck_knowledge_package_status CHECK (status IN (
        'DRAFT','PENDING_REVIEW','PUBLISHED','ACTIVE','OFFLINE','ARCHIVED'
    ))
);

CREATE INDEX idx_knowledge_pkg_tenant_status ON knowledge_package (tenant_id, status, updated_at);

CREATE TABLE package_item (
    id                NUMBER(19) IDENTITY PRIMARY KEY,
    item_id           VARCHAR2(64)   NOT NULL,
    tenant_id         VARCHAR2(64)   NOT NULL,
    package_id        VARCHAR2(64)   NOT NULL,
    asset_type        VARCHAR2(32)   NOT NULL,
    asset_id          VARCHAR2(64)   NOT NULL,
    asset_version     VARCHAR2(64)   NOT NULL,
    created_at        TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by        VARCHAR2(64)   DEFAULT 'system' NOT NULL,
    updated_at        TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by        VARCHAR2(64)   DEFAULT 'system' NOT NULL,
    trace_id          VARCHAR2(128),
    CONSTRAINT uk_package_item_id UNIQUE (tenant_id, item_id),
    CONSTRAINT uk_package_item_tenant_asset UNIQUE (tenant_id, package_id, asset_type, asset_id),
    CONSTRAINT ck_package_item_asset_type CHECK (asset_type IN (
        'KNOWLEDGE','TERMINOLOGY','RULE','PATHWAY','EVALUATION','FOLLOWUP'
    ))
);

CREATE INDEX idx_package_item_pkg ON package_item (tenant_id, package_id, asset_type);

CREATE TABLE release_plan (
    id                NUMBER(19) IDENTITY PRIMARY KEY,
    plan_id           VARCHAR2(64)   NOT NULL,
    tenant_id         VARCHAR2(64)   NOT NULL,
    package_id        VARCHAR2(64)   NOT NULL,
    target_org_unit_id VARCHAR2(64)  NOT NULL,
    strategy          VARCHAR2(32)   NOT NULL,
    scope_type        VARCHAR2(32)   NOT NULL,
    scope_value       VARCHAR2(256),
    status            VARCHAR2(32)   DEFAULT 'DRAFT' NOT NULL,
    created_at        TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by        VARCHAR2(64)   DEFAULT 'system' NOT NULL,
    updated_at        TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by        VARCHAR2(64)   DEFAULT 'system' NOT NULL,
    trace_id          VARCHAR2(128),
    CONSTRAINT uk_release_plan_id UNIQUE (tenant_id, plan_id),
    CONSTRAINT ck_release_plan_strategy CHECK (strategy IN ('GRAYSCALE','FULL')),
    CONSTRAINT ck_release_plan_scope_type CHECK (scope_type IN ('ALL','GROUP','HOSPITAL','CAMPUS','SITE','DEPARTMENT','SPECIALTY')),
    CONSTRAINT ck_release_plan_status CHECK (status IN ('DRAFT','EXECUTING','SUCCESS','FAILED','ROLLBACKED'))
);

CREATE INDEX idx_release_plan_pkg ON release_plan (tenant_id, package_id, status);

CREATE TABLE sync_target (
    id                NUMBER(19) IDENTITY PRIMARY KEY,
    target_id         VARCHAR2(64)   NOT NULL,
    tenant_id         VARCHAR2(64)   NOT NULL,
    target_name       VARCHAR2(128)  NOT NULL,
    target_type       VARCHAR2(32)   NOT NULL,
    connection_config CLOB,
    status            VARCHAR2(32)   DEFAULT 'ACTIVE' NOT NULL,
    created_at        TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by        VARCHAR2(64)   DEFAULT 'system' NOT NULL,
    updated_at        TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by        VARCHAR2(64)   DEFAULT 'system' NOT NULL,
    trace_id          VARCHAR2(128),
    CONSTRAINT uk_sync_target_id UNIQUE (tenant_id, target_id),
    CONSTRAINT ck_sync_target_type CHECK (target_type IN ('CLINICAL_DB','DIFY','GRAPH_DB','REDIS')),
    CONSTRAINT ck_sync_target_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE INDEX idx_sync_target_tenant ON sync_target (tenant_id, status);

CREATE TABLE sync_log (
    id                NUMBER(19) IDENTITY PRIMARY KEY,
    log_id            VARCHAR2(64)   NOT NULL,
    tenant_id         VARCHAR2(64)   NOT NULL,
    plan_id           VARCHAR2(64)   NOT NULL,
    target_id         VARCHAR2(64)   NOT NULL,
    status            VARCHAR2(32)   DEFAULT 'RUNNING' NOT NULL,
    error_code        VARCHAR2(64),
    error_message     CLOB,
    retry_count       NUMBER(10)     DEFAULT 0 NOT NULL,
    sync_evidence     CLOB,
    created_at        TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by        VARCHAR2(64)   DEFAULT 'system' NOT NULL,
    updated_at        TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by        VARCHAR2(64)   DEFAULT 'system' NOT NULL,
    trace_id          VARCHAR2(128),
    CONSTRAINT uk_sync_log_id UNIQUE (tenant_id, log_id),
    CONSTRAINT ck_sync_log_status CHECK (status IN ('RUNNING','SUCCESS','FAILED','RETRYING'))
);

CREATE INDEX idx_sync_log_plan ON sync_log (tenant_id, plan_id, status);
