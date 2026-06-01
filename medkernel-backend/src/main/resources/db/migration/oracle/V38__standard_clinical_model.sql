-- MedKernel SYS-01 · 标准临床模型 12 对象关系库权威表（Oracle）
-- ROLLBACK: 若需回滚本迁移，先确认无 SYS-01 新表业务数据，再删除 mk_clinical_* 表，并将 canonical_resource 类型约束恢复为包含 SYMPTOM 的旧集合。

ALTER TABLE canonical_resource DROP CONSTRAINT ck_canonical_resource_type;
ALTER TABLE canonical_resource ADD CONSTRAINT ck_canonical_resource_type CHECK (resource_type IN (
    'PATIENT','ENCOUNTER','CONDITION','NURSING_ASSESSMENT','OBSERVATION',
    'DIAGNOSTIC_REPORT','MEDICATION','PROCEDURE','DOCUMENT','CARE_PLAN','FOLLOW_UP','CLAIM'
));

CREATE TABLE mk_clinical_patient (
    patient_id          VARCHAR2(64)  PRIMARY KEY,
    tenant_id           VARCHAR2(64)  NOT NULL,
    org_path            VARCHAR2(512) NOT NULL,
    source_system       VARCHAR2(64)  NOT NULL,
    source_id           VARCHAR2(128) NOT NULL,
    fhir_resource_id    VARCHAR2(128),
    name_cipher         VARCHAR2(1024) NOT NULL,
    name_mask           VARCHAR2(128) NOT NULL,
    identity_no_cipher  VARCHAR2(1024),
    identity_no_mask    VARCHAR2(64),
    phone_cipher        VARCHAR2(1024),
    phone_mask          VARCHAR2(64),
    birth_date          DATE,
    gender_code         VARCHAR2(16),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by          VARCHAR2(64) NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by          VARCHAR2(64) NOT NULL,
    trace_id            VARCHAR2(128),
    CONSTRAINT uk_mk_clinical_patient_source UNIQUE (tenant_id, source_system, source_id)
);

CREATE INDEX idx_mk_clinical_patient_org_path ON mk_clinical_patient (tenant_id, org_path);

CREATE TABLE mk_clinical_encounter (
    encounter_id        VARCHAR2(64) PRIMARY KEY,
    tenant_id           VARCHAR2(64) NOT NULL,
    org_path            VARCHAR2(512) NOT NULL,
    source_system       VARCHAR2(64) NOT NULL,
    source_id           VARCHAR2(128) NOT NULL,
    fhir_resource_id    VARCHAR2(128),
    patient_id          VARCHAR2(64) NOT NULL,
    encounter_class     VARCHAR2(32) NOT NULL,
    status              VARCHAR2(32) NOT NULL,
    started_at          TIMESTAMP WITH TIME ZONE,
    ended_at            TIMESTAMP WITH TIME ZONE,
    org_unit_id         VARCHAR2(64),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by          VARCHAR2(64) NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by          VARCHAR2(64) NOT NULL,
    trace_id            VARCHAR2(128),
    CONSTRAINT uk_mk_clinical_encounter_source UNIQUE (tenant_id, source_system, source_id)
);

CREATE INDEX idx_mk_clinical_encounter_patient ON mk_clinical_encounter (tenant_id, patient_id);
CREATE INDEX idx_mk_clinical_encounter_org_path ON mk_clinical_encounter (tenant_id, org_path);

CREATE TABLE mk_clinical_condition (
    condition_id        VARCHAR2(64) PRIMARY KEY,
    tenant_id           VARCHAR2(64) NOT NULL,
    org_path            VARCHAR2(512) NOT NULL,
    source_system       VARCHAR2(64) NOT NULL,
    source_id           VARCHAR2(128) NOT NULL,
    fhir_resource_id    VARCHAR2(128),
    patient_id          VARCHAR2(64) NOT NULL,
    encounter_id        VARCHAR2(64),
    code                VARCHAR2(128) NOT NULL,
    code_system         VARCHAR2(64) NOT NULL,
    display_name        VARCHAR2(256) NOT NULL,
    clinical_status     VARCHAR2(32),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by          VARCHAR2(64) NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by          VARCHAR2(64) NOT NULL,
    trace_id            VARCHAR2(128),
    CONSTRAINT uk_mk_clinical_condition_source UNIQUE (tenant_id, source_system, source_id)
);

CREATE INDEX idx_mk_clinical_condition_patient ON mk_clinical_condition (tenant_id, patient_id);
CREATE INDEX idx_mk_clinical_condition_org_path ON mk_clinical_condition (tenant_id, org_path);
CREATE INDEX idx_mk_clinical_condition_code ON mk_clinical_condition (tenant_id, code_system, code);

CREATE TABLE mk_clinical_observation (
    observation_id      VARCHAR2(64) PRIMARY KEY,
    tenant_id           VARCHAR2(64) NOT NULL,
    org_path            VARCHAR2(512) NOT NULL,
    source_system       VARCHAR2(64) NOT NULL,
    source_id           VARCHAR2(128) NOT NULL,
    fhir_resource_id    VARCHAR2(128),
    patient_id          VARCHAR2(64) NOT NULL,
    encounter_id        VARCHAR2(64),
    code                VARCHAR2(128) NOT NULL,
    code_system         VARCHAR2(64) NOT NULL,
    display_name        VARCHAR2(256) NOT NULL,
    value_numeric       NUMBER(18,6),
    unit                VARCHAR2(64),
    critical_flag       VARCHAR2(32),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by          VARCHAR2(64) NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by          VARCHAR2(64) NOT NULL,
    trace_id            VARCHAR2(128),
    CONSTRAINT uk_mk_clinical_observation_source UNIQUE (tenant_id, source_system, source_id)
);

CREATE INDEX idx_mk_clinical_observation_patient ON mk_clinical_observation (tenant_id, patient_id);
CREATE INDEX idx_mk_clinical_observation_org_path ON mk_clinical_observation (tenant_id, org_path);
CREATE INDEX idx_mk_clinical_observation_code ON mk_clinical_observation (tenant_id, code_system, code);

CREATE TABLE mk_clinical_medication (
    medication_id       VARCHAR2(64) PRIMARY KEY,
    tenant_id           VARCHAR2(64) NOT NULL,
    org_path            VARCHAR2(512) NOT NULL,
    source_system       VARCHAR2(64) NOT NULL,
    source_id           VARCHAR2(128) NOT NULL,
    fhir_resource_id    VARCHAR2(128),
    patient_id          VARCHAR2(64) NOT NULL,
    encounter_id        VARCHAR2(64),
    code                VARCHAR2(128) NOT NULL,
    code_system         VARCHAR2(64) NOT NULL,
    display_name        VARCHAR2(256) NOT NULL,
    dose                NUMBER(18,6),
    dose_unit           VARCHAR2(64),
    route               VARCHAR2(64),
    frequency           VARCHAR2(64),
    status              VARCHAR2(32),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by          VARCHAR2(64) NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by          VARCHAR2(64) NOT NULL,
    trace_id            VARCHAR2(128),
    CONSTRAINT uk_mk_clinical_medication_source UNIQUE (tenant_id, source_system, source_id)
);

CREATE INDEX idx_mk_clinical_medication_patient ON mk_clinical_medication (tenant_id, patient_id);
CREATE INDEX idx_mk_clinical_medication_org_path ON mk_clinical_medication (tenant_id, org_path);
CREATE INDEX idx_mk_clinical_medication_code ON mk_clinical_medication (tenant_id, code_system, code);

CREATE TABLE mk_clinical_procedure (
    procedure_id        VARCHAR2(64) PRIMARY KEY,
    tenant_id           VARCHAR2(64) NOT NULL,
    org_path            VARCHAR2(512) NOT NULL,
    source_system       VARCHAR2(64) NOT NULL,
    source_id           VARCHAR2(128) NOT NULL,
    fhir_resource_id    VARCHAR2(128),
    patient_id          VARCHAR2(64) NOT NULL,
    encounter_id        VARCHAR2(64),
    code                VARCHAR2(128) NOT NULL,
    code_system         VARCHAR2(64) NOT NULL,
    display_name        VARCHAR2(256) NOT NULL,
    status              VARCHAR2(32),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by          VARCHAR2(64) NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by          VARCHAR2(64) NOT NULL,
    trace_id            VARCHAR2(128),
    CONSTRAINT uk_mk_clinical_procedure_source UNIQUE (tenant_id, source_system, source_id)
);

CREATE INDEX idx_mk_clinical_procedure_patient ON mk_clinical_procedure (tenant_id, patient_id);
CREATE INDEX idx_mk_clinical_procedure_org_path ON mk_clinical_procedure (tenant_id, org_path);
CREATE INDEX idx_mk_clinical_procedure_code ON mk_clinical_procedure (tenant_id, code_system, code);

CREATE TABLE mk_clinical_diagnostic_report (
    report_id           VARCHAR2(64) PRIMARY KEY,
    tenant_id           VARCHAR2(64) NOT NULL,
    org_path            VARCHAR2(512) NOT NULL,
    source_system       VARCHAR2(64) NOT NULL,
    source_id           VARCHAR2(128) NOT NULL,
    fhir_resource_id    VARCHAR2(128),
    patient_id          VARCHAR2(64) NOT NULL,
    encounter_id        VARCHAR2(64),
    report_type         VARCHAR2(64) NOT NULL,
    status              VARCHAR2(32),
    conclusion          CLOB,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by          VARCHAR2(64) NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by          VARCHAR2(64) NOT NULL,
    trace_id            VARCHAR2(128),
    CONSTRAINT uk_mk_clinical_diagnostic_report_source UNIQUE (tenant_id, source_system, source_id)
);

CREATE INDEX idx_mk_clinical_diagnostic_report_patient ON mk_clinical_diagnostic_report (tenant_id, patient_id);
CREATE INDEX idx_mk_clinical_diagnostic_report_org_path ON mk_clinical_diagnostic_report (tenant_id, org_path);

CREATE TABLE mk_clinical_document (
    document_id         VARCHAR2(64) PRIMARY KEY,
    tenant_id           VARCHAR2(64) NOT NULL,
    org_path            VARCHAR2(512) NOT NULL,
    source_system       VARCHAR2(64) NOT NULL,
    source_id           VARCHAR2(128) NOT NULL,
    fhir_resource_id    VARCHAR2(128),
    patient_id          VARCHAR2(64) NOT NULL,
    encounter_id        VARCHAR2(64),
    document_type       VARCHAR2(64) NOT NULL,
    status              VARCHAR2(32),
    content_hash        VARCHAR2(128),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by          VARCHAR2(64) NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by          VARCHAR2(64) NOT NULL,
    trace_id            VARCHAR2(128),
    CONSTRAINT uk_mk_clinical_document_source UNIQUE (tenant_id, source_system, source_id)
);

CREATE INDEX idx_mk_clinical_document_patient ON mk_clinical_document (tenant_id, patient_id);
CREATE INDEX idx_mk_clinical_document_org_path ON mk_clinical_document (tenant_id, org_path);

CREATE TABLE mk_clinical_nursing_assessment (
    assessment_id       VARCHAR2(64) PRIMARY KEY,
    tenant_id           VARCHAR2(64) NOT NULL,
    org_path            VARCHAR2(512) NOT NULL,
    source_system       VARCHAR2(64) NOT NULL,
    source_id           VARCHAR2(128) NOT NULL,
    fhir_resource_id    VARCHAR2(128),
    patient_id          VARCHAR2(64) NOT NULL,
    encounter_id        VARCHAR2(64),
    assessment_type     VARCHAR2(64) NOT NULL,
    status              VARCHAR2(32),
    risk_level          VARCHAR2(32),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by          VARCHAR2(64) NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by          VARCHAR2(64) NOT NULL,
    trace_id            VARCHAR2(128),
    CONSTRAINT uk_mk_clinical_nursing_assessment_source UNIQUE (tenant_id, source_system, source_id)
);

CREATE INDEX idx_mk_clinical_nursing_assessment_patient ON mk_clinical_nursing_assessment (tenant_id, patient_id);
CREATE INDEX idx_mk_clinical_nursing_assessment_org_path ON mk_clinical_nursing_assessment (tenant_id, org_path);

CREATE TABLE mk_clinical_care_plan (
    care_plan_id        VARCHAR2(64) PRIMARY KEY,
    tenant_id           VARCHAR2(64) NOT NULL,
    org_path            VARCHAR2(512) NOT NULL,
    source_system       VARCHAR2(64) NOT NULL,
    source_id           VARCHAR2(128) NOT NULL,
    fhir_resource_id    VARCHAR2(128),
    patient_id          VARCHAR2(64) NOT NULL,
    encounter_id        VARCHAR2(64),
    pathway_id          VARCHAR2(64),
    status              VARCHAR2(32),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by          VARCHAR2(64) NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by          VARCHAR2(64) NOT NULL,
    trace_id            VARCHAR2(128),
    CONSTRAINT uk_mk_clinical_care_plan_source UNIQUE (tenant_id, source_system, source_id)
);

CREATE INDEX idx_mk_clinical_care_plan_patient ON mk_clinical_care_plan (tenant_id, patient_id);
CREATE INDEX idx_mk_clinical_care_plan_org_path ON mk_clinical_care_plan (tenant_id, org_path);

CREATE TABLE mk_clinical_follow_up (
    follow_up_id        VARCHAR2(64) PRIMARY KEY,
    tenant_id           VARCHAR2(64) NOT NULL,
    org_path            VARCHAR2(512) NOT NULL,
    source_system       VARCHAR2(64) NOT NULL,
    source_id           VARCHAR2(128) NOT NULL,
    fhir_resource_id    VARCHAR2(128),
    patient_id          VARCHAR2(64) NOT NULL,
    encounter_id        VARCHAR2(64),
    plan_type           VARCHAR2(64) NOT NULL,
    status              VARCHAR2(32),
    planned_at          TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by          VARCHAR2(64) NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by          VARCHAR2(64) NOT NULL,
    trace_id            VARCHAR2(128),
    CONSTRAINT uk_mk_clinical_follow_up_source UNIQUE (tenant_id, source_system, source_id)
);

CREATE INDEX idx_mk_clinical_follow_up_patient ON mk_clinical_follow_up (tenant_id, patient_id);
CREATE INDEX idx_mk_clinical_follow_up_org_path ON mk_clinical_follow_up (tenant_id, org_path);

CREATE TABLE mk_clinical_claim (
    claim_id            VARCHAR2(64) PRIMARY KEY,
    tenant_id           VARCHAR2(64) NOT NULL,
    org_path            VARCHAR2(512) NOT NULL,
    source_system       VARCHAR2(64) NOT NULL,
    source_id           VARCHAR2(128) NOT NULL,
    fhir_resource_id    VARCHAR2(128),
    patient_id          VARCHAR2(64) NOT NULL,
    encounter_id        VARCHAR2(64),
    claim_type          VARCHAR2(64),
    status              VARCHAR2(32),
    total_amount        NUMBER(18,2),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by          VARCHAR2(64) NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by          VARCHAR2(64) NOT NULL,
    trace_id            VARCHAR2(128),
    CONSTRAINT uk_mk_clinical_claim_source UNIQUE (tenant_id, source_system, source_id)
);

CREATE INDEX idx_mk_clinical_claim_patient ON mk_clinical_claim (tenant_id, patient_id);
CREATE INDEX idx_mk_clinical_claim_org_path ON mk_clinical_claim (tenant_id, org_path);

COMMENT ON TABLE mk_clinical_patient IS 'SYS-01 标准患者对象权威表，敏感字段只保存密文与掩码';
COMMENT ON TABLE mk_clinical_encounter IS 'SYS-01 标准就诊对象权威表';
COMMENT ON TABLE mk_clinical_condition IS 'SYS-01 标准诊断对象权威表';
COMMENT ON TABLE mk_clinical_observation IS 'SYS-01 标准观察对象权威表';
COMMENT ON TABLE mk_clinical_medication IS 'SYS-01 标准用药对象权威表';
COMMENT ON TABLE mk_clinical_procedure IS 'SYS-01 标准手术与操作对象权威表';
COMMENT ON TABLE mk_clinical_diagnostic_report IS 'SYS-01 标准诊断报告对象权威表';
COMMENT ON TABLE mk_clinical_document IS 'SYS-01 标准临床文书对象权威表';
COMMENT ON TABLE mk_clinical_nursing_assessment IS 'SYS-01 标准护理评估对象权威表';
COMMENT ON TABLE mk_clinical_care_plan IS 'SYS-01 标准照护计划对象权威表';
COMMENT ON TABLE mk_clinical_follow_up IS 'SYS-01 标准随访对象权威表';
COMMENT ON TABLE mk_clinical_claim IS 'SYS-01 标准医保结算对象权威表';
