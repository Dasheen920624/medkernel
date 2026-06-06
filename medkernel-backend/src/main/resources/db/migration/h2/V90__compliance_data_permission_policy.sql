-- MedKernel v1.0 GA · SYS-06 行列数据权限策略（H2）
-- ROLLBACK：确认没有服务依赖 SYS-06 行列门禁后，删除 mk_compliance_data_permission 表。

CREATE TABLE IF NOT EXISTS mk_compliance_data_permission (
    id                   BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    policy_id            VARCHAR(128) NOT NULL,
    tenant_id            VARCHAR(64)  NOT NULL,
    resource_type        VARCHAR(128) NOT NULL,
    action               VARCHAR(32)  NOT NULL,
    min_data_level       VARCHAR(32)  NOT NULL,
    allowed_columns_json CLOB         NOT NULL,
    group_id             VARCHAR(64)  NULL,
    hospital_id          VARCHAR(64)  NULL,
    campus_id            VARCHAR(64)  NULL,
    site_id              VARCHAR(64)  NULL,
    department_id        VARCHAR(64)  NULL,
    specialty_id         VARCHAR(64)  NULL,
    status               VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    version              BIGINT       NOT NULL DEFAULT 1,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by           VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by           VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id             VARCHAR(128) NULL,
    CONSTRAINT uk_compliance_data_permission_policy UNIQUE (tenant_id, resource_type, action),
    CONSTRAINT ck_compliance_data_permission_action CHECK (action IN ('READ','EXPORT')),
    CONSTRAINT ck_compliance_data_permission_level CHECK (min_data_level IN ('DEPARTMENT','HOSPITAL','GROUP')),
    CONSTRAINT ck_compliance_data_permission_status CHECK (status IN ('ACTIVE','DISABLED')),
    CONSTRAINT ck_compliance_data_permission_version CHECK (version >= 1)
);

CREATE INDEX IF NOT EXISTS idx_compliance_data_permission_scope
    ON mk_compliance_data_permission (tenant_id, group_id, hospital_id, department_id, status);
CREATE INDEX IF NOT EXISTS idx_compliance_data_permission_resource
    ON mk_compliance_data_permission (tenant_id, resource_type, action, status);

COMMENT ON TABLE mk_compliance_data_permission IS 'SYS-06 行列数据权限策略表，按租户和资源动作配置最小数据级别与列白名单';
COMMENT ON COLUMN mk_compliance_data_permission.policy_id IS '策略 ID，由资源类型和动作确定，租户内稳定';
COMMENT ON COLUMN mk_compliance_data_permission.resource_type IS '受控业务资源类型，如 clinical_case 或 evidence_snapshot';
COMMENT ON COLUMN mk_compliance_data_permission.action IS '策略动作，READ 为读取，EXPORT 为导出';
COMMENT ON COLUMN mk_compliance_data_permission.min_data_level IS '访问该资源所需的最低数据范围级别';
COMMENT ON COLUMN mk_compliance_data_permission.allowed_columns_json IS '允许读取或导出的字段白名单 JSON 数组';
COMMENT ON COLUMN mk_compliance_data_permission.trace_id IS '最近一次策略变更的链路追踪 ID';
