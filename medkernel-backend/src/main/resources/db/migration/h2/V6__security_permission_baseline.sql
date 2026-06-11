-- MedKernel v1.0 GA · GA-ENG-BASE-02 · 身份权限闭环（H2 2.2）

CREATE TABLE IF NOT EXISTS sys_role (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    role_code       VARCHAR(64)  NOT NULL,
    display_name    VARCHAR(128) NOT NULL,
    description     VARCHAR(512),
    built_in_flag   CHAR(1)      NOT NULL DEFAULT 'Y',
    active_flag     CHAR(1)      NOT NULL DEFAULT 'Y',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64)  NOT NULL DEFAULT 'system',
    CONSTRAINT uk_sys_role_tenant_code UNIQUE (tenant_id, role_code),
    CONSTRAINT ck_sys_role_builtin CHECK (built_in_flag IN ('Y','N')),
    CONSTRAINT ck_sys_role_active CHECK (active_flag IN ('Y','N'))
);

CREATE INDEX IF NOT EXISTS idx_sys_role_tenant_active
    ON sys_role (tenant_id, active_flag, role_code);

CREATE TABLE IF NOT EXISTS sys_permission (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    permission_code VARCHAR(128) NOT NULL,
    dimension       VARCHAR(32)  NOT NULL,
    target          VARCHAR(128) NOT NULL,
    display_name    VARCHAR(128) NOT NULL,
    risk_level      VARCHAR(16)  NOT NULL,
    active_flag     CHAR(1)      NOT NULL DEFAULT 'Y',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64)  NOT NULL DEFAULT 'system',
    CONSTRAINT uk_sys_permission_code UNIQUE (permission_code),
    CONSTRAINT ck_sys_permission_dimension CHECK (dimension IN ('MENU','ACTION','DATA','ASSET','ENVIRONMENT')),
    CONSTRAINT ck_sys_permission_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT ck_sys_permission_active CHECK (active_flag IN ('Y','N'))
);

CREATE INDEX IF NOT EXISTS idx_sys_permission_dimension
    ON sys_permission (dimension, target);

INSERT INTO sys_role (tenant_id, role_code, display_name, description, built_in_flag, active_flag, created_by, updated_by) VALUES ('SYSTEM', 'platform-governance-admin', '平台治理管理员', '平台空间、客户空间、全局授权与平台运行治理', 'Y', 'Y', 'migration-v6', 'migration-v6');
INSERT INTO sys_role (tenant_id, role_code, display_name, description, built_in_flag, active_flag, created_by, updated_by) VALUES ('SYSTEM', 'platform-knowledge-governor', '平台知识治理员', '唯一平台医疗知识主源、标准包与发布治理', 'Y', 'Y', 'migration-v6', 'migration-v6');
INSERT INTO sys_role (tenant_id, role_code, display_name, description, built_in_flag, active_flag, created_by, updated_by) VALUES ('SYSTEM', 'organization-admin', '机构管理员', '按授权组织范围管理集团、医院、分院或基层机构', 'Y', 'Y', 'migration-v6', 'migration-v6');
INSERT INTO sys_role (tenant_id, role_code, display_name, description, built_in_flag, active_flag, created_by, updated_by) VALUES ('SYSTEM', 'identity-access-admin', '人员与访问管理员', '人员、任职、账号、身份来源与职责授权', 'Y', 'Y', 'migration-v6', 'migration-v6');
INSERT INTO sys_role (tenant_id, role_code, display_name, description, built_in_flag, active_flag, created_by, updated_by) VALUES ('SYSTEM', 'knowledge-governor', '机构知识治理员', '机构知识派生、审核、发布与恢复平台标准', 'Y', 'Y', 'migration-v6', 'migration-v6');
INSERT INTO sys_role (tenant_id, role_code, display_name, description, built_in_flag, active_flag, created_by, updated_by) VALUES ('SYSTEM', 'clinical-governor', '临床治理负责人', '规则、路径、临床运行与高风险审核', 'Y', 'Y', 'migration-v6', 'migration-v6');
INSERT INTO sys_role (tenant_id, role_code, display_name, description, built_in_flag, active_flag, created_by, updated_by) VALUES ('SYSTEM', 'clinical-decision-user', '临床决策使用者', '处理临床提醒、路径、待办与人工确认', 'Y', 'Y', 'migration-v6', 'migration-v6');
INSERT INTO sys_role (tenant_id, role_code, display_name, description, built_in_flag, active_flag, created_by, updated_by) VALUES ('SYSTEM', 'nursing-collaborator', '护理协同人员', '护理协同、路径任务、随访与通知', 'Y', 'Y', 'migration-v6', 'migration-v6');
INSERT INTO sys_role (tenant_id, role_code, display_name, description, built_in_flag, active_flag, created_by, updated_by) VALUES ('SYSTEM', 'medication-safety-user', '药事安全人员', '用药规则、药学知识与用药风险复核', 'Y', 'Y', 'migration-v6', 'migration-v6');
INSERT INTO sys_role (tenant_id, role_code, display_name, description, built_in_flag, active_flag, created_by, updated_by) VALUES ('SYSTEM', 'diagnostic-service-user', '医技协同人员', '检查检验结果接入与术语映射协同', 'Y', 'Y', 'migration-v6', 'migration-v6');
INSERT INTO sys_role (tenant_id, role_code, display_name, description, built_in_flag, active_flag, created_by, updated_by) VALUES ('SYSTEM', 'quality-governor', '质量与医保治理员', '质控、病案、医保审核、整改与评价', 'Y', 'Y', 'migration-v6', 'migration-v6');
INSERT INTO sys_role (tenant_id, role_code, display_name, description, built_in_flag, active_flag, created_by, updated_by) VALUES ('SYSTEM', 'compliance-auditor', '合规审计员', '独立审计、证据链与受控导出', 'Y', 'Y', 'migration-v6', 'migration-v6');
INSERT INTO sys_role (tenant_id, role_code, display_name, description, built_in_flag, active_flag, created_by, updated_by) VALUES ('SYSTEM', 'integration-operator', '集成运维员', '统一身份、适配器、运行状态与国产化运维', 'Y', 'Y', 'migration-v6', 'migration-v6');
INSERT INTO sys_role (tenant_id, role_code, display_name, description, built_in_flag, active_flag, created_by, updated_by) VALUES ('SYSTEM', 'implementation-operator', '实施运维员', '客户开通、初始化、批量导入与联调验收', 'Y', 'Y', 'migration-v6', 'migration-v6');

INSERT INTO sys_permission (permission_code, dimension, target, display_name, risk_level, created_by, updated_by) VALUES ('org.read', 'ACTION', 'org', '查看组织树', 'LOW', 'migration-v6', 'migration-v6');
INSERT INTO sys_permission (permission_code, dimension, target, display_name, risk_level, created_by, updated_by) VALUES ('menu.workbench', 'MENU', 'workbench', '查看工作台入口', 'LOW', 'migration-v6', 'migration-v6');
INSERT INTO sys_permission (permission_code, dimension, target, display_name, risk_level, created_by, updated_by) VALUES ('data.department', 'DATA', 'department', '访问本科室数据', 'LOW', 'migration-v6', 'migration-v6');
INSERT INTO sys_permission (permission_code, dimension, target, display_name, risk_level, created_by, updated_by) VALUES ('data.hospital', 'DATA', 'hospital', '访问全院数据', 'MEDIUM', 'migration-v6', 'migration-v6');
INSERT INTO sys_permission (permission_code, dimension, target, display_name, risk_level, created_by, updated_by) VALUES ('data.group', 'DATA', 'group', '访问集团跨院数据', 'HIGH', 'migration-v6', 'migration-v6');
INSERT INTO sys_permission (permission_code, dimension, target, display_name, risk_level, created_by, updated_by) VALUES ('data.desensitized', 'DATA', 'desensitized', '访问脱敏数据', 'LOW', 'migration-v6', 'migration-v6');
INSERT INTO sys_permission (permission_code, dimension, target, display_name, risk_level, created_by, updated_by) VALUES ('asset.config-package', 'ASSET', 'config-package', '访问配置包资产', 'MEDIUM', 'migration-v6', 'migration-v6');
INSERT INTO sys_permission (permission_code, dimension, target, display_name, risk_level, created_by, updated_by) VALUES ('asset.dictionary', 'ASSET', 'dictionary', '访问字典映射资产', 'MEDIUM', 'migration-v6', 'migration-v6');
INSERT INTO sys_permission (permission_code, dimension, target, display_name, risk_level, created_by, updated_by) VALUES ('asset.knowledge-package', 'ASSET', 'knowledge-package', '访问知识包资产', 'MEDIUM', 'migration-v6', 'migration-v6');
INSERT INTO sys_permission (permission_code, dimension, target, display_name, risk_level, created_by, updated_by) VALUES ('asset.rule', 'ASSET', 'rule', '访问规则资产', 'MEDIUM', 'migration-v6', 'migration-v6');
INSERT INTO sys_permission (permission_code, dimension, target, display_name, risk_level, created_by, updated_by) VALUES ('asset.pathway', 'ASSET', 'pathway', '访问路径资产', 'MEDIUM', 'migration-v6', 'migration-v6');
INSERT INTO sys_permission (permission_code, dimension, target, display_name, risk_level, created_by, updated_by) VALUES ('env.test', 'ENVIRONMENT', 'test', '访问测试环境', 'LOW', 'migration-v6', 'migration-v6');
INSERT INTO sys_permission (permission_code, dimension, target, display_name, risk_level, created_by, updated_by) VALUES ('env.trial', 'ENVIRONMENT', 'trial', '访问试运行环境', 'MEDIUM', 'migration-v6', 'migration-v6');
INSERT INTO sys_permission (permission_code, dimension, target, display_name, risk_level, created_by, updated_by) VALUES ('env.production', 'ENVIRONMENT', 'production', '访问正式环境', 'HIGH', 'migration-v6', 'migration-v6');
INSERT INTO sys_permission (permission_code, dimension, target, display_name, risk_level, created_by, updated_by) VALUES ('env.emergency', 'ENVIRONMENT', 'emergency', '访问应急环境', 'HIGH', 'migration-v6', 'migration-v6');

CREATE TABLE IF NOT EXISTS role_permission (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    role_code       VARCHAR(64)  NOT NULL,
    permission_code VARCHAR(128) NOT NULL,
    effect          VARCHAR(16)  NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64)  NOT NULL DEFAULT 'system',
    CONSTRAINT uk_role_permission UNIQUE (tenant_id, role_code, permission_code),
    CONSTRAINT ck_role_permission_effect CHECK (effect IN ('ALLOW','DENY'))
);

CREATE INDEX IF NOT EXISTS idx_role_permission_tenant_role
    ON role_permission (tenant_id, role_code);

CREATE TABLE IF NOT EXISTS user_role_assignment (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    user_id         VARCHAR(128) NOT NULL,
    role_code       VARCHAR(64)  NOT NULL,
    scope_level     VARCHAR(32)  NOT NULL DEFAULT 'TENANT',
    scope_code      VARCHAR(128) NOT NULL,
    active_flag     CHAR(1)      NOT NULL DEFAULT 'Y',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64)  NOT NULL DEFAULT 'system',
    CONSTRAINT uk_user_role_assignment UNIQUE (tenant_id, user_id, role_code, scope_level, scope_code),
    CONSTRAINT ck_user_role_assignment_active CHECK (active_flag IN ('Y','N'))
);

CREATE INDEX IF NOT EXISTS idx_user_role_assignment_user
    ON user_role_assignment (tenant_id, user_id, active_flag);
