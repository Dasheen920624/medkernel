-- MedKernel v1.0 GA · SVC-INTEGRATION-01 第三方业务接口服务包（H2 PostgreSQL 兼容模式）

CREATE TABLE IF NOT EXISTS mk_integration_onboarding (
    id                  BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    onboarding_id       VARCHAR(64)   NOT NULL,
    tenant_id           VARCHAR(64)   NOT NULL,
    name                VARCHAR(256)  NOT NULL,
    access_mode         VARCHAR(16)   NOT NULL,
    adapter_id          VARCHAR(64)   NULL,
    fhir_version        VARCHAR(16)   NULL,
    source_system       VARCHAR(128)  NOT NULL,
    business_scenario   VARCHAR(256)  NOT NULL,
    org_path            VARCHAR(512)  NOT NULL,
    callback_webhook_id VARCHAR(64)   NULL,
    status              VARCHAR(32)   NOT NULL,
    evidence_text       VARCHAR(1000) NULL,
    created_at          TIMESTAMP     NOT NULL,
    created_by          VARCHAR(64)   NOT NULL,
    updated_at          TIMESTAMP     NOT NULL,
    updated_by          VARCHAR(64)   NOT NULL,
    trace_id            VARCHAR(128)  NULL,
    CONSTRAINT uk_integ_onboarding_tenant_id UNIQUE (tenant_id, onboarding_id),
    CONSTRAINT ck_integ_onboarding_mode CHECK (access_mode IN ('ADAPTER','FHIR')),
    CONSTRAINT ck_integ_onboarding_status CHECK (status IN ('REQUESTED','AUTH_CONFIGURED','MAPPING_CONFIGURED','ONLINE','OFFLINE')),
    CONSTRAINT ck_integ_onboarding_fhir CHECK (fhir_version IS NULL OR fhir_version IN ('R4','R5'))
);

CREATE INDEX IF NOT EXISTS idx_integ_onb_tenant_status
    ON mk_integration_onboarding (tenant_id, status, updated_at);
CREATE INDEX IF NOT EXISTS idx_integ_onb_adapter
    ON mk_integration_onboarding (tenant_id, adapter_id);

CREATE TABLE IF NOT EXISTS mk_integration_regional_source (
    id                       BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_id                VARCHAR(64)   NOT NULL,
    tenant_id                VARCHAR(64)   NOT NULL,
    regional_network_name    VARCHAR(256)  NOT NULL,
    source_organization_id   VARCHAR(64)   NOT NULL,
    source_organization_name VARCHAR(256)  NOT NULL,
    trust_level              VARCHAR(16)   NOT NULL,
    evidence_text            VARCHAR(1000) NOT NULL,
    adapter_id               VARCHAR(64)   NULL,
    onboarding_id            VARCHAR(64)   NULL,
    org_path                 VARCHAR(512)  NOT NULL,
    status                   VARCHAR(32)   NOT NULL,
    created_at               TIMESTAMP     NOT NULL,
    created_by               VARCHAR(64)   NOT NULL,
    updated_at               TIMESTAMP     NOT NULL,
    updated_by               VARCHAR(64)   NOT NULL,
    trace_id                 VARCHAR(128)  NULL,
    CONSTRAINT uk_integ_regional_source_id UNIQUE (tenant_id, source_id),
    CONSTRAINT ck_integ_regional_trust CHECK (trust_level IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT ck_integ_regional_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE INDEX IF NOT EXISTS idx_integ_regional_tenant_trust
    ON mk_integration_regional_source (tenant_id, trust_level, status);
CREATE INDEX IF NOT EXISTS idx_integ_regional_org
    ON mk_integration_regional_source (tenant_id, source_organization_id);

COMMENT ON TABLE mk_integration_onboarding IS '第三方业务接口接入生命周期档案';
COMMENT ON COLUMN mk_integration_onboarding.id IS '自增主键';
COMMENT ON COLUMN mk_integration_onboarding.onboarding_id IS '接入申请业务 ID';
COMMENT ON COLUMN mk_integration_onboarding.tenant_id IS '租户 ID';
COMMENT ON COLUMN mk_integration_onboarding.name IS '接入申请中文名称';
COMMENT ON COLUMN mk_integration_onboarding.access_mode IS '接入路线：适配器或 FHIR 门面';
COMMENT ON COLUMN mk_integration_onboarding.adapter_id IS '绑定适配器 ID';
COMMENT ON COLUMN mk_integration_onboarding.fhir_version IS 'FHIR 版本：R4/R5';
COMMENT ON COLUMN mk_integration_onboarding.source_system IS '第三方来源系统';
COMMENT ON COLUMN mk_integration_onboarding.business_scenario IS '业务接入场景';
COMMENT ON COLUMN mk_integration_onboarding.org_path IS '组织作用域路径';
COMMENT ON COLUMN mk_integration_onboarding.callback_webhook_id IS '回调 Webhook 配置 ID';
COMMENT ON COLUMN mk_integration_onboarding.status IS '接入阶段状态';
COMMENT ON COLUMN mk_integration_onboarding.evidence_text IS '阶段推进证据说明';
COMMENT ON COLUMN mk_integration_onboarding.created_at IS '创建时间';
COMMENT ON COLUMN mk_integration_onboarding.created_by IS '创建人';
COMMENT ON COLUMN mk_integration_onboarding.updated_at IS '更新时间';
COMMENT ON COLUMN mk_integration_onboarding.updated_by IS '更新人';
COMMENT ON COLUMN mk_integration_onboarding.trace_id IS '链路追踪 ID';

COMMENT ON TABLE mk_integration_regional_source IS '区域协同来源可信分级档案';
COMMENT ON COLUMN mk_integration_regional_source.id IS '自增主键';
COMMENT ON COLUMN mk_integration_regional_source.source_id IS '区域来源业务 ID';
COMMENT ON COLUMN mk_integration_regional_source.tenant_id IS '租户 ID';
COMMENT ON COLUMN mk_integration_regional_source.regional_network_name IS '区域协同网络名称';
COMMENT ON COLUMN mk_integration_regional_source.source_organization_id IS '来源组织 ID';
COMMENT ON COLUMN mk_integration_regional_source.source_organization_name IS '来源组织名称';
COMMENT ON COLUMN mk_integration_regional_source.trust_level IS '来源可信分级';
COMMENT ON COLUMN mk_integration_regional_source.evidence_text IS '可信分级证据说明';
COMMENT ON COLUMN mk_integration_regional_source.adapter_id IS '关联适配器 ID';
COMMENT ON COLUMN mk_integration_regional_source.onboarding_id IS '关联接入申请 ID';
COMMENT ON COLUMN mk_integration_regional_source.org_path IS '组织作用域路径';
COMMENT ON COLUMN mk_integration_regional_source.status IS '来源状态';
COMMENT ON COLUMN mk_integration_regional_source.created_at IS '创建时间';
COMMENT ON COLUMN mk_integration_regional_source.created_by IS '创建人';
COMMENT ON COLUMN mk_integration_regional_source.updated_at IS '更新时间';
COMMENT ON COLUMN mk_integration_regional_source.updated_by IS '更新人';
COMMENT ON COLUMN mk_integration_regional_source.trace_id IS '链路追踪 ID';
