-- MedKernel 第二阶段 P2-C · AIK-STD-14 公域资料获取（H2）
-- 新项目基线：只建立白名单与运行账本，不做旧来源兼容回填。

CREATE TABLE IF NOT EXISTS mk_knowledge_acquisition_source (
    id               BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id        VARCHAR(64)   NOT NULL,
    source_code      VARCHAR(128)  NOT NULL,
    domain           VARCHAR(255)  NOT NULL,
    base_url         VARCHAR(512)  NOT NULL,
    source_type      VARCHAR(32)   NOT NULL,
    authority_level  VARCHAR(32)   NOT NULL,
    authority_basis  VARCHAR(512)  NOT NULL,
    title            VARCHAR(512)  NOT NULL,
    publisher        VARCHAR(256)  NOT NULL,
    license          VARCHAR(512)  NOT NULL,
    license_policy   VARCHAR(24)   NOT NULL,
    robots_policy    VARCHAR(24)   NOT NULL,
    enabled_flag     CHAR(1)       NOT NULL DEFAULT 'N',
    approved_by      VARCHAR(64)   NULL,
    approved_at      TIMESTAMP     NULL,
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by       VARCHAR(64)   NULL,
    updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       VARCHAR(64)   NULL,
    CONSTRAINT uk_mk_knowledge_acquisition_source_code UNIQUE (tenant_id, source_code),
    CONSTRAINT ck_mk_knowledge_acquisition_source_type CHECK (source_type IN ('GUIDELINE','DRUG_LABEL','STANDARD','POLICY','HOSPITAL_PROTOCOL','TCM_CLASSIC','LITERATURE','CONSENSUS','OTHER')),
    CONSTRAINT ck_mk_knowledge_acquisition_source_authority CHECK (authority_level IN ('A_REGULATION','B_GUIDELINE','C_CONSENSUS_LITERATURE','D_HOSPITAL','E_FEEDBACK')),
    CONSTRAINT ck_mk_knowledge_acquisition_license_policy CHECK (license_policy IN ('PERMITTED','RESTRICTED','FORBIDDEN')),
    CONSTRAINT ck_mk_knowledge_acquisition_robots_policy CHECK (robots_policy IN ('ALLOW_FETCH','MANUAL_APPROVED','DISALLOW_FETCH')),
    CONSTRAINT ck_mk_knowledge_acquisition_source_enabled CHECK (enabled_flag IN ('Y','N'))
);

CREATE TABLE IF NOT EXISTS mk_knowledge_acquisition_run (
    id                 BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          VARCHAR(64)   NOT NULL,
    run_code           VARCHAR(64)   NOT NULL,
    source_id          BIGINT        NULL,
    source_code        VARCHAR(128)  NOT NULL,
    url                VARCHAR(1024) NOT NULL,
    domain             VARCHAR(255)  NOT NULL,
    trigger_type       VARCHAR(24)   NOT NULL,
    status             VARCHAR(24)   NOT NULL,
    fetched_at         TIMESTAMP     NULL,
    source_hash        VARCHAR(64)   NULL,
    byte_size          BIGINT        NULL,
    content_type       VARCHAR(255)  NULL,
    license            VARCHAR(512)  NULL,
    license_policy     VARCHAR(24)   NULL,
    robots_policy      VARCHAR(24)   NULL,
    material_file_uri  VARCHAR(1024) NULL,
    source_document_id BIGINT        NULL,
    source_version_id  BIGINT        NULL,
    parse_job_code     VARCHAR(64)   NULL,
    failure_reason     VARCHAR(1024) NULL,
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by         VARCHAR(64)   NULL,
    updated_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         VARCHAR(64)   NULL,
    CONSTRAINT uk_mk_knowledge_acquisition_run_code UNIQUE (tenant_id, run_code),
    CONSTRAINT fk_mk_knowledge_acquisition_run_source
        FOREIGN KEY (source_id) REFERENCES mk_knowledge_acquisition_source (id),
    CONSTRAINT ck_mk_knowledge_acquisition_run_trigger CHECK (trigger_type IN ('MANUAL','SCHEDULED','AGENT_TOOL')),
    CONSTRAINT ck_mk_knowledge_acquisition_run_status CHECK (status IN ('SUCCEEDED','DUPLICATE','BLOCKED','FAILED'))
);

CREATE INDEX idx_mk_knowledge_acquisition_source_domain
    ON mk_knowledge_acquisition_source (tenant_id, domain);
CREATE INDEX idx_mk_knowledge_acquisition_source_status
    ON mk_knowledge_acquisition_source (tenant_id, enabled_flag, license_policy, robots_policy);
CREATE INDEX idx_mk_knowledge_acquisition_run_status
    ON mk_knowledge_acquisition_run (tenant_id, status, created_at);
CREATE INDEX idx_mk_knowledge_acquisition_run_source
    ON mk_knowledge_acquisition_run (tenant_id, source_code, created_at);

COMMENT ON TABLE mk_knowledge_acquisition_source IS 'AIK-STD-14 公域资料来源白名单：记录域名、许可、robots 策略和审批信息';
COMMENT ON TABLE mk_knowledge_acquisition_run IS 'AIK-STD-14 公域资料获取运行账本：记录真实 URL、抓取时点、原文指纹、资料 URI 和解析结果';
COMMENT ON COLUMN mk_knowledge_acquisition_source.license_policy IS '许可裁决：允许、受限或禁止进入知识生产';
COMMENT ON COLUMN mk_knowledge_acquisition_source.robots_policy IS 'robots / ToS 抓取策略裁决';
COMMENT ON COLUMN mk_knowledge_acquisition_run.source_hash IS '公域资料原文字节 SHA-256 指纹';
COMMENT ON COLUMN mk_knowledge_acquisition_run.material_file_uri IS '受管资料库 URI，可为 file:// 本地磁盘、对象存储或 HTTPS 网关';
