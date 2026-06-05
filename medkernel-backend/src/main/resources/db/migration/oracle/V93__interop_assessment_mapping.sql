-- MedKernel v1.0 GA · OPT-05 互联互通测评映射（Oracle）
-- ROLLBACK：确认没有服务依赖 OPT-05 测评映射后，先导出测评项和证据映射，再删除两张 mk_compliance_interop_* 表。

CREATE TABLE mk_compliance_interop_assessment_item (
    id                    NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_id               VARCHAR2(128) NOT NULL,
    tenant_id             VARCHAR2(64)  NOT NULL,
    standard_version      VARCHAR2(64)  NOT NULL,
    dimension             VARCHAR2(64)  NOT NULL,
    item_code             VARCHAR2(128) NOT NULL,
    item_name             VARCHAR2(256) NOT NULL,
    requirement_summary   CLOB          NOT NULL,
    owner_department_id   VARCHAR2(64)  NULL,
    effective_from        TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    effective_to          TIMESTAMP WITH TIME ZONE NULL,
    status                VARCHAR2(32)  DEFAULT 'ACTIVE' NOT NULL,
    version               NUMBER(19)    DEFAULT 1 NOT NULL,
    created_at            TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by            VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by            VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    trace_id              VARCHAR2(128) NULL,
    CONSTRAINT uk_compliance_interop_item_id UNIQUE (tenant_id, item_id),
    CONSTRAINT uk_compliance_interop_item_code UNIQUE (tenant_id, standard_version, item_code),
    CONSTRAINT ck_compliance_interop_item_dimension CHECK (
        dimension IN ('DATA_RESOURCE','STANDARDIZATION','INFRASTRUCTURE','APPLICATION_EFFECT')
    ),
    CONSTRAINT ck_compliance_interop_item_status CHECK (status IN ('ACTIVE','RETIRED')),
    CONSTRAINT ck_compliance_interop_item_version CHECK (version >= 1)
);

CREATE INDEX idx_compliance_interop_item_status
    ON mk_compliance_interop_assessment_item (tenant_id, standard_version, status);
CREATE INDEX idx_compliance_interop_item_dimension
    ON mk_compliance_interop_assessment_item (tenant_id, standard_version, dimension);

CREATE TABLE mk_compliance_interop_evidence_map (
    id                   NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    map_id               VARCHAR2(128) NOT NULL,
    tenant_id            VARCHAR2(64)  NOT NULL,
    item_id              VARCHAR2(128) NOT NULL,
    evidence_source_type VARCHAR2(64)  NOT NULL,
    source_id            VARCHAR2(128) NOT NULL,
    evidence_ref         VARCHAR2(256) NOT NULL,
    evidence_summary     VARCHAR2(512) NOT NULL,
    status               VARCHAR2(32)  DEFAULT 'ACTIVE' NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by           VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at           TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by           VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    trace_id             VARCHAR2(128) NULL,
    CONSTRAINT uk_compliance_interop_evidence_map UNIQUE (tenant_id, map_id),
    CONSTRAINT uk_compliance_interop_evidence_source UNIQUE (tenant_id, item_id, evidence_source_type, source_id),
    CONSTRAINT ck_compliance_interop_evidence_source CHECK (
        evidence_source_type IN ('EVIDENCE_SNAPSHOT','EMR_LEVEL_EVIDENCE_PACKAGE')
    ),
    CONSTRAINT ck_compliance_interop_evidence_status CHECK (status IN ('ACTIVE','REVOKED'))
);

CREATE INDEX idx_compliance_interop_evidence_item
    ON mk_compliance_interop_evidence_map (tenant_id, item_id, status);
CREATE INDEX idx_compliance_interop_evidence_source
    ON mk_compliance_interop_evidence_map (tenant_id, evidence_source_type, source_id);
CREATE INDEX idx_compliance_interop_evidence_status
    ON mk_compliance_interop_evidence_map (tenant_id, status, created_at);

COMMENT ON TABLE mk_compliance_interop_assessment_item IS 'OPT-05 互联互通测评四维指标项表，保存受控版本化测评要求';
COMMENT ON TABLE mk_compliance_interop_evidence_map IS 'OPT-05 互联互通测评证据映射表，引用真实 EVID-01 或 EMR-LEVEL-02 证据';
COMMENT ON COLUMN mk_compliance_interop_assessment_item.standard_version IS '互联互通测评标准版本';
COMMENT ON COLUMN mk_compliance_interop_assessment_item.dimension IS '测评四维：数据资源、标准化、基础设施、应用效果';
COMMENT ON COLUMN mk_compliance_interop_assessment_item.requirement_summary IS '测评项要求摘要，不替代正式标准文本';
COMMENT ON COLUMN mk_compliance_interop_evidence_map.evidence_source_type IS '真实证据来源类型：EVIDENCE_SNAPSHOT 或 EMR_LEVEL_EVIDENCE_PACKAGE';
COMMENT ON COLUMN mk_compliance_interop_evidence_map.source_id IS '证据源业务 ID，必须能在对应真实表中查到';
COMMENT ON COLUMN mk_compliance_interop_evidence_map.evidence_ref IS '面向评审下钻的证据引用，如 evidence_snapshot:<id>';
COMMENT ON COLUMN mk_compliance_interop_evidence_map.trace_id IS '最近一次证据映射变更链路追踪 ID';
