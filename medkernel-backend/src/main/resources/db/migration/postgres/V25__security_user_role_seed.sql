-- MedKernel v1.0 GA · 安全初始化数据：用户角色分配基线（PostgreSQL 15+）
-- 本迁移只写用户与角色、数据范围绑定，不写账号密码；真实身份认证由院方统一身份源提供。
-- role_permission 是租户级权限覆盖表，默认角色权限仍由 DefaultPermissionPolicy 维护。

INSERT INTO user_role_assignment
    (tenant_id, user_id, role_code, scope_level, scope_code, active_flag, created_by, updated_by)
VALUES ('t-1', 'platform-admin-1', 'platform-admin', 'TENANT', 't-1', 'Y', 'migration-v25', 'migration-v25');

INSERT INTO user_role_assignment
    (tenant_id, user_id, role_code, scope_level, scope_code, active_flag, created_by, updated_by)
VALUES ('t-1', 'group-admin-1', 'group-admin', 'TENANT', 't-1', 'Y', 'migration-v25', 'migration-v25');

INSERT INTO user_role_assignment
    (tenant_id, user_id, role_code, scope_level, scope_code, active_flag, created_by, updated_by)
VALUES ('t-1', 'admin-1', 'hospital-admin', 'TENANT', 't-1', 'Y', 'migration-v25', 'migration-v25');

INSERT INTO user_role_assignment
    (tenant_id, user_id, role_code, scope_level, scope_code, active_flag, created_by, updated_by)
VALUES ('t-1', 'it-ops-1', 'it-ops', 'TENANT', 't-1', 'Y', 'migration-v25', 'migration-v25');

INSERT INTO user_role_assignment
    (tenant_id, user_id, role_code, scope_level, scope_code, active_flag, created_by, updated_by)
VALUES ('t-1', 'implementation-1', 'implementation-engineer', 'TENANT', 't-1', 'Y', 'migration-v25', 'migration-v25');

INSERT INTO user_role_assignment
    (tenant_id, user_id, role_code, scope_level, scope_code, active_flag, created_by, updated_by)
VALUES ('t-1', 'medical-affairs-1', 'medical-affairs', 'TENANT', 't-1', 'Y', 'migration-v25', 'migration-v25');

INSERT INTO user_role_assignment
    (tenant_id, user_id, role_code, scope_level, scope_code, active_flag, created_by, updated_by)
VALUES ('t-1', 'qa-manager-1', 'qa-manager', 'TENANT', 't-1', 'Y', 'migration-v25', 'migration-v25');

INSERT INTO user_role_assignment
    (tenant_id, user_id, role_code, scope_level, scope_code, active_flag, created_by, updated_by)
VALUES ('t-1', 'insurance-manager-1', 'insurance-manager', 'TENANT', 't-1', 'Y', 'migration-v25', 'migration-v25');

INSERT INTO user_role_assignment
    (tenant_id, user_id, role_code, scope_level, scope_code, active_flag, created_by, updated_by)
VALUES ('t-1', 'dept-head-1', 'dept-head', 'TENANT', 't-1', 'Y', 'migration-v25', 'migration-v25');

INSERT INTO user_role_assignment
    (tenant_id, user_id, role_code, scope_level, scope_code, active_flag, created_by, updated_by)
VALUES ('t-1', 'specialist-1', 'specialist', 'TENANT', 't-1', 'Y', 'migration-v25', 'migration-v25');

INSERT INTO user_role_assignment
    (tenant_id, user_id, role_code, scope_level, scope_code, active_flag, created_by, updated_by)
VALUES ('t-1', 'doctor-1', 'doctor', 'TENANT', 't-1', 'Y', 'migration-v25', 'migration-v25');

INSERT INTO user_role_assignment
    (tenant_id, user_id, role_code, scope_level, scope_code, active_flag, created_by, updated_by)
VALUES ('t-1', 'nurse-1', 'nurse', 'TENANT', 't-1', 'Y', 'migration-v25', 'migration-v25');

INSERT INTO user_role_assignment
    (tenant_id, user_id, role_code, scope_level, scope_code, active_flag, created_by, updated_by)
VALUES ('t-1', 'med-technician-1', 'med-technician', 'TENANT', 't-1', 'Y', 'migration-v25', 'migration-v25');

INSERT INTO user_role_assignment
    (tenant_id, user_id, role_code, scope_level, scope_code, active_flag, created_by, updated_by)
VALUES ('t-1', 'pharmacist-1', 'pharmacist', 'TENANT', 't-1', 'Y', 'migration-v25', 'migration-v25');

INSERT INTO user_role_assignment
    (tenant_id, user_id, role_code, scope_level, scope_code, active_flag, created_by, updated_by)
VALUES ('t-1', 'audit-1', 'audit-compliance', 'TENANT', 't-1', 'Y', 'migration-v25', 'migration-v25');
