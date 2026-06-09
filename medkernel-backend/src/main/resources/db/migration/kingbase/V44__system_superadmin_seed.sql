-- MedKernel v1.0 GA · SUPERADMIN-01 · 内置超级管理员种子（人大金仓）
-- 只写入内置角色定义；唯一超管身份由首次部署接管创建并由 SystemSuperAdminGuard 保护。

INSERT INTO sys_role
    (tenant_id, role_code, display_name, description, built_in_flag, active_flag, created_by, updated_by)
VALUES
    ('SYSTEM', 'system-superadmin', '内置超级管理员',
     '系统强制内置，启动自动满权，禁止租户用户管理降权、删除或移出超管组',
     'Y', 'Y', 'migration-v44', 'migration-v44');
