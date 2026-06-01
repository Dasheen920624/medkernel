-- MedKernel v1.0 GA · SUPERADMIN-01 · 内置超级管理员种子（达梦）
-- 将系统超管角色与种子绑定写入关系库权威源；不可降权/删除由 SystemSuperAdminGuard 应用层约束阻断。

INSERT INTO sys_role
    (tenant_id, role_code, display_name, description, built_in_flag, active_flag, created_by, updated_by)
VALUES
    ('SYSTEM', 'system-superadmin', '内置超级管理员',
     '系统强制内置，启动自动满权，禁止租户用户管理降权、删除或移出超管组',
     'Y', 'Y', 'migration-v44', 'migration-v44');

INSERT INTO user_role_assignment
    (tenant_id, user_id, role_code, scope_level, scope_code, active_flag, created_by, updated_by)
VALUES
    ('t-1', 'system-superadmin-1', 'system-superadmin', 'TENANT', 't-1', 'Y',
     'migration-v44', 'migration-v44');
