-- 医疗知识派生血缘与人员主数据（Oracle）
-- ROLLBACK：先停止人员导入和知识派生，再按依赖逆序删除本迁移创建的六张表。

ALTER TABLE org_unit DROP CONSTRAINT ck_org_unit_facility_type;
ALTER TABLE org_unit ADD CONSTRAINT ck_org_unit_facility_type
    CHECK (
        (level_code = 'FACILITY' AND facility_type IN (
            'HOSPITAL','SPECIALTY_HOSPITAL','BRANCH_HOSPITAL',
            'COMMUNITY_HEALTH_CENTER','TOWNSHIP_CLINIC','VILLAGE_CLINIC',
            'OUTPATIENT_CLINIC','STATION','OTHER'))
        OR (level_code <> 'FACILITY' AND facility_type IS NULL)
    );

CREATE TABLE mk_knowledge_customization (
    customization_id VARCHAR2(64) PRIMARY KEY,
    tenant_id VARCHAR2(64) NOT NULL,
    platform_identity_id NUMBER(19) NOT NULL,
    platform_version_id NUMBER(19) NOT NULL,
    platform_version_no VARCHAR2(64) NOT NULL,
    local_identity_id NUMBER(19) NOT NULL,
    local_version_id NUMBER(19) NOT NULL,
    target_org_unit_id VARCHAR2(64) NOT NULL,
    target_org_path VARCHAR2(256) NOT NULL,
    applicable_scope VARCHAR2(256) NOT NULL,
    source_type VARCHAR2(32) NOT NULL,
    status VARCHAR2(32) NOT NULL,
    reason VARCHAR2(1024) NOT NULL,
    override_id VARCHAR2(64) NULL,
    version NUMBER(19) DEFAULT 1 NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by VARCHAR2(64) NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by VARCHAR2(64) NOT NULL,
    trace_id VARCHAR2(128) NULL,
    CONSTRAINT uk_knowledge_customization_scope UNIQUE (tenant_id, platform_identity_id, target_org_unit_id, applicable_scope),
    CONSTRAINT ck_knowledge_customization_source CHECK (source_type IN ('LOCAL_CUSTOMIZATION')),
    CONSTRAINT ck_knowledge_customization_status CHECK (status IN ('DRAFT','ACTIVE','RESTORED')),
    CONSTRAINT ck_knowledge_customization_version CHECK (version >= 1)
);
CREATE INDEX idx_knowledge_customization_local
    ON mk_knowledge_customization (tenant_id, local_identity_id, local_version_id);

CREATE TABLE mk_person (
    person_id VARCHAR2(64) PRIMARY KEY,
    tenant_id VARCHAR2(64) NOT NULL,
    employee_no VARCHAR2(128) NOT NULL,
    display_name VARCHAR2(128) NOT NULL,
    mobile_hint VARCHAR2(32) NULL,
    status VARCHAR2(32) NOT NULL,
    version NUMBER(19) DEFAULT 1 NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by VARCHAR2(64) NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by VARCHAR2(64) NOT NULL,
    trace_id VARCHAR2(128) NULL,
    CONSTRAINT uk_person_employee UNIQUE (tenant_id, employee_no),
    CONSTRAINT ck_person_status CHECK (status IN ('ACTIVE','INACTIVE','LEFT')),
    CONSTRAINT ck_person_version CHECK (version >= 1)
);
CREATE INDEX idx_person_directory ON mk_person (tenant_id, display_name, employee_no);

CREATE TABLE mk_person_appointment (
    appointment_id VARCHAR2(64) PRIMARY KEY,
    tenant_id VARCHAR2(64) NOT NULL,
    person_id VARCHAR2(64) NOT NULL,
    organization_id VARCHAR2(64) NOT NULL,
    department_id VARCHAR2(64) NULL,
    ward_id VARCHAR2(64) NULL,
    appointment_type VARCHAR2(32) NOT NULL,
    position_title VARCHAR2(128) NULL,
    primary_flag CHAR(1) DEFAULT 'N' NOT NULL,
    effective_from TIMESTAMP NOT NULL,
    effective_to TIMESTAMP NULL,
    status VARCHAR2(32) NOT NULL,
    version NUMBER(19) DEFAULT 1 NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by VARCHAR2(64) NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by VARCHAR2(64) NOT NULL,
    trace_id VARCHAR2(128) NULL,
    CONSTRAINT fk_person_appointment_person FOREIGN KEY (person_id) REFERENCES mk_person(person_id),
    CONSTRAINT ck_person_appointment_type CHECK (appointment_type IN ('INTERNAL','GROUP_SHARED','EXTERNAL_COLLABORATOR','IMPLEMENTATION')),
    CONSTRAINT ck_person_appointment_primary CHECK (primary_flag IN ('Y','N')),
    CONSTRAINT ck_person_appointment_status CHECK (status IN ('PENDING','ACTIVE','ENDED')),
    CONSTRAINT ck_person_appointment_version CHECK (version >= 1)
);
CREATE INDEX idx_person_appointment_person
    ON mk_person_appointment (tenant_id, person_id, status, primary_flag);
CREATE INDEX idx_person_appointment_org
    ON mk_person_appointment (tenant_id, organization_id, department_id, ward_id, status);

CREATE TABLE mk_person_account_link (
    link_id VARCHAR2(64) PRIMARY KEY,
    tenant_id VARCHAR2(64) NOT NULL,
    person_id VARCHAR2(64) NOT NULL,
    user_id VARCHAR2(128) NOT NULL,
    status VARCHAR2(32) NOT NULL,
    version NUMBER(19) DEFAULT 1 NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by VARCHAR2(64) NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by VARCHAR2(64) NOT NULL,
    trace_id VARCHAR2(128) NULL,
    CONSTRAINT fk_person_account_person FOREIGN KEY (person_id) REFERENCES mk_person(person_id),
    CONSTRAINT uk_person_account_person UNIQUE (tenant_id, person_id),
    CONSTRAINT uk_person_account_user UNIQUE (tenant_id, user_id),
    CONSTRAINT ck_person_account_status CHECK (status IN ('ACTIVE','DISABLED')),
    CONSTRAINT ck_person_account_version CHECK (version >= 1)
);

CREATE TABLE mk_person_import_job (
    job_id VARCHAR2(64) PRIMARY KEY,
    tenant_id VARCHAR2(64) NOT NULL,
    file_name VARCHAR2(256) NOT NULL,
    file_digest VARCHAR2(128) NOT NULL,
    status VARCHAR2(32) NOT NULL,
    total_rows NUMBER(10) DEFAULT 0 NOT NULL,
    valid_rows NUMBER(10) DEFAULT 0 NOT NULL,
    conflict_rows NUMBER(10) DEFAULT 0 NOT NULL,
    success_rows NUMBER(10) DEFAULT 0 NOT NULL,
    failure_rows NUMBER(10) DEFAULT 0 NOT NULL,
    committed_at TIMESTAMP NULL,
    version NUMBER(19) DEFAULT 1 NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by VARCHAR2(64) NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by VARCHAR2(64) NOT NULL,
    trace_id VARCHAR2(128) NULL,
    CONSTRAINT uk_person_import_digest UNIQUE (tenant_id, file_digest),
    CONSTRAINT ck_person_import_status CHECK (status IN ('VALIDATING','HAS_ISSUES','READY','PROCESSING','PARTIAL','COMPLETED','CANCELLED')),
    CONSTRAINT ck_person_import_version CHECK (version >= 1)
);

CREATE TABLE mk_person_import_row (
    row_id VARCHAR2(64) PRIMARY KEY,
    job_id VARCHAR2(64) NOT NULL,
    tenant_id VARCHAR2(64) NOT NULL,
    row_no NUMBER(10) NOT NULL,
    employee_no VARCHAR2(128) NULL,
    display_name VARCHAR2(128) NULL,
    organization_code VARCHAR2(128) NULL,
    department_code VARCHAR2(128) NULL,
    ward_code VARCHAR2(128) NULL,
    appointment_type VARCHAR2(32) NULL,
    position_title VARCHAR2(128) NULL,
    login_name VARCHAR2(128) NULL,
    role_code VARCHAR2(64) NULL,
    identity_provider VARCHAR2(32) NULL,
    external_subject_digest VARCHAR2(128) NULL,
    external_subject_hint VARCHAR2(32) NULL,
    action VARCHAR2(32) NOT NULL,
    status VARCHAR2(32) NOT NULL,
    error_message VARCHAR2(1024) NULL,
    result_person_id VARCHAR2(64) NULL,
    result_user_id VARCHAR2(128) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_person_import_row_job FOREIGN KEY (job_id) REFERENCES mk_person_import_job(job_id),
    CONSTRAINT uk_person_import_row_no UNIQUE (job_id, row_no),
    CONSTRAINT ck_person_import_row_action CHECK (action IN ('CREATE','UPDATE','SKIP','CONFLICT')),
    CONSTRAINT ck_person_import_row_status CHECK (status IN ('VALID','INVALID','SUCCESS','FAILED','SKIPPED'))
);
CREATE INDEX idx_person_import_row_job ON mk_person_import_row (job_id, row_no);

ALTER TABLE mk_compliance_data_permission
    ADD (ward_id VARCHAR2(64) NULL);
CREATE INDEX idx_compliance_data_permission_ward
    ON mk_compliance_data_permission (tenant_id, ward_id, status);

COMMENT ON TABLE mk_knowledge_customization IS '客户机构从平台权威知识按需派生的血缘与覆盖生命周期';
COMMENT ON COLUMN mk_compliance_data_permission.ward_id IS '数据权限策略限定的病区组织节点';
COMMENT ON COLUMN org_unit.facility_type IS '医疗服务机构类型：医院、专科医院、独立分院、社区卫生服务中心、乡镇卫生院、村卫生室、门诊部、服务站或其他';
COMMENT ON COLUMN mk_knowledge_customization.platform_identity_id IS '平台知识身份主键，只允许来自唯一平台主租户';
COMMENT ON COLUMN mk_knowledge_customization.platform_version_id IS '派生时冻结的平台基线版本主键';
COMMENT ON COLUMN mk_knowledge_customization.local_identity_id IS '客户租户内保持同一业务键的知识身份主键';
COMMENT ON COLUMN mk_knowledge_customization.local_version_id IS '客户租户内派生版本主键';
COMMENT ON COLUMN mk_knowledge_customization.target_org_unit_id IS '本地定制生效的组织节点';
COMMENT ON COLUMN mk_knowledge_customization.override_id IS '发布后登记的组织继承覆盖业务 ID';
COMMENT ON TABLE mk_person IS '租户内自然人主数据，独立于登录账号和外部认证身份';
COMMENT ON COLUMN mk_person.employee_no IS '租户内稳定人员编号，用于批量导入幂等匹配';
COMMENT ON TABLE mk_person_appointment IS '人员在医疗机构、科室、病区中的任职关系和有效期';
COMMENT ON COLUMN mk_person_appointment.ward_id IS '任职所在病区组织节点';
COMMENT ON COLUMN mk_person_appointment.primary_flag IS '是否为当前主任职，Y 是、N 否';
COMMENT ON TABLE mk_person_account_link IS '自然人与稳定系统用户主体之间的一对一关联';
COMMENT ON TABLE mk_person_import_job IS '人员批量导入预检、提交和结果汇总任务';
COMMENT ON TABLE mk_person_import_row IS '人员批量导入逐行校验、冲突和处理结果';
COMMENT ON COLUMN mk_person_import_row.ward_code IS '人员导入时用于组织树匹配的病区编码';
COMMENT ON COLUMN mk_person_import_row.external_subject_digest IS '外部身份国密摘要，禁止保存身份原文';
