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

INSERT INTO sys_role (tenant_id, role_code, display_name, description, built_in_flag, active_flag, created_by, updated_by) VALUES ('SYSTEM', 'platform-admin', '平台管理员', '租户开通、全局标准包、系统配置', 'Y', 'Y', 'migration-v6', 'migration-v6');
INSERT INTO sys_role (tenant_id, role_code, display_name, description, built_in_flag, active_flag, created_by, updated_by) VALUES ('SYSTEM', 'group-admin', '集团管理员', '集团组织、集团知识包、集团质控指标、跨院分析', 'Y', 'Y', 'migration-v6', 'migration-v6');
INSERT INTO sys_role (tenant_id, role_code, display_name, description, built_in_flag, active_flag, created_by, updated_by) VALUES ('SYSTEM', 'hospital-admin', '医院管理员', '院内组织、用户、适配器、院内映射、发布审批', 'Y', 'Y', 'migration-v6', 'migration-v6');
INSERT INTO sys_role (tenant_id, role_code, display_name, description, built_in_flag, active_flag, created_by, updated_by) VALUES ('SYSTEM', 'it-ops', '信息科', '接口、适配器、同步任务、国产化自检、运行监控', 'Y', 'Y', 'migration-v6', 'migration-v6');
INSERT INTO sys_role (tenant_id, role_code, display_name, description, built_in_flag, active_flag, created_by, updated_by) VALUES ('SYSTEM', 'medical-affairs', '医务处', '路径、规则、知识审核、临床运行治理', 'Y', 'Y', 'migration-v6', 'migration-v6');
INSERT INTO sys_role (tenant_id, role_code, display_name, description, built_in_flag, active_flag, created_by, updated_by) VALUES ('SYSTEM', 'qa-manager', '质控办', '评估指标、质控预警、整改闭环、证据导出', 'Y', 'Y', 'migration-v6', 'migration-v6');
INSERT INTO sys_role (tenant_id, role_code, display_name, description, built_in_flag, active_flag, created_by, updated_by) VALUES ('SYSTEM', 'insurance-manager', '医保办', 'DRG/DIP、医保审核、支付目录同步', 'Y', 'Y', 'migration-v6', 'migration-v6');
INSERT INTO sys_role (tenant_id, role_code, display_name, description, built_in_flag, active_flag, created_by, updated_by) VALUES ('SYSTEM', 'dept-head', '科主任', '科室规则覆盖、路径审核、整改确认', 'Y', 'Y', 'migration-v6', 'migration-v6');
INSERT INTO sys_role (tenant_id, role_code, display_name, description, built_in_flag, active_flag, created_by, updated_by) VALUES ('SYSTEM', 'specialist', '专科专家', '专病知识、路径、规则、随访计划审核和调整', 'Y', 'Y', 'migration-v6', 'migration-v6');
INSERT INTO sys_role (tenant_id, role_code, display_name, description, built_in_flag, active_flag, created_by, updated_by) VALUES ('SYSTEM', 'doctor', '临床医生', '查看提醒、采纳或不采纳、查看解释、提交反馈', 'Y', 'Y', 'migration-v6', 'migration-v6');
INSERT INTO sys_role (tenant_id, role_code, display_name, description, built_in_flag, active_flag, created_by, updated_by) VALUES ('SYSTEM', 'nurse', '护理人员', '护理评估、计划、复评、交班、护理质控', 'Y', 'Y', 'migration-v6', 'migration-v6');
INSERT INTO sys_role (tenant_id, role_code, display_name, description, built_in_flag, active_flag, created_by, updated_by) VALUES ('SYSTEM', 'audit-compliance', '合规审计', '审计日志、安全基线、证据包、数据出境评估', 'Y', 'Y', 'migration-v6', 'migration-v6');
INSERT INTO sys_role (tenant_id, role_code, display_name, description, built_in_flag, active_flag, created_by, updated_by) VALUES ('SYSTEM', 'implementation-engineer', '实施工程师', '试点准备、配置包导入、联调、验收材料', 'Y', 'Y', 'migration-v6', 'migration-v6');

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
