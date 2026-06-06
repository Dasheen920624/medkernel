-- MedKernel v1.0 GA · SYS-06 行列数据权限策略（达梦）
-- ROLLBACK：确认没有服务依赖 SYS-06 行列门禁后，删除 mk_compliance_data_permission 表。

CREATE TABLE mk_compliance_data_permission (
    id                   NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    policy_id            VARCHAR2(128) NOT NULL,
    tenant_id            VARCHAR2(64)  NOT NULL,
    resource_type        VARCHAR2(128) NOT NULL,
    action               VARCHAR2(32)  NOT NULL,
    min_data_level       VARCHAR2(32)  NOT NULL,
    allowed_columns_json CLOB          NOT NULL,
    group_id             VARCHAR2(64)  NULL,
    hospital_id          VARCHAR2(64)  NULL,
    campus_id            VARCHAR2(64)  NULL,
    site_id              VARCHAR2(64)  NULL,
    department_id        VARCHAR2(64)  NULL,
    specialty_id         VARCHAR2(64)  NULL,
    status               VARCHAR2(32)  DEFAULT 'ACTIVE' NOT NULL,
    version              NUMBER(19)    DEFAULT 1 NOT NULL,
    created_at           TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by           VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at           TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by           VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    trace_id             VARCHAR2(128) NULL,
    CONSTRAINT uk_compliance_data_permission_policy UNIQUE (tenant_id, resource_type, action),
    CONSTRAINT ck_compliance_data_permission_action CHECK (action IN ('READ','EXPORT')),
    CONSTRAINT ck_compliance_data_permission_level CHECK (min_data_level IN ('DEPARTMENT','HOSPITAL','GROUP')),
    CONSTRAINT ck_compliance_data_permission_status CHECK (status IN ('ACTIVE','DISABLED')),
    CONSTRAINT ck_compliance_data_permission_version CHECK (version >= 1)
);

CREATE INDEX idx_compliance_data_permission_scope
    ON mk_compliance_data_permission (tenant_id, group_id, hospital_id, department_id, status);
CREATE INDEX idx_compliance_data_permission_resource
    ON mk_compliance_data_permission (tenant_id, resource_type, action, status);

COMMENT ON TABLE mk_compliance_data_permission IS 'SYS-06 行列数据权限策略表，按租户和资源动作配置最小数据级别与列白名单';
COMMENT ON COLUMN mk_compliance_data_permission.policy_id IS '策略 ID，由资源类型和动作确定，租户内稳定';
COMMENT ON COLUMN mk_compliance_data_permission.resource_type IS '受控业务资源类型，如 clinical_case 或 evidence_snapshot';
COMMENT ON COLUMN mk_compliance_data_permission.action IS '策略动作，READ 为读取，EXPORT 为导出';
COMMENT ON COLUMN mk_compliance_data_permission.min_data_level IS '访问该资源所需的最低数据范围级别';
COMMENT ON COLUMN mk_compliance_data_permission.allowed_columns_json IS '允许读取或导出的字段白名单 JSON 数组';
COMMENT ON COLUMN mk_compliance_data_permission.trace_id IS '最近一次策略变更的链路追踪 ID';
