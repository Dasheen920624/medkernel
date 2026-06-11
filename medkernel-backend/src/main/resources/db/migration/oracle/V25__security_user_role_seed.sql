-- 平台治理空间只初始化平台职责，不预置任何客户机构岗位或历史角色。
-- 机构职责必须在具体客户服务空间内按组织范围授权。

INSERT INTO user_role_assignment
    (tenant_id, user_id, role_code, scope_level, scope_code, active_flag, created_by, updated_by)
VALUES ('t-1', 'platform-governance-admin-1', 'platform-governance-admin', 'TENANT', 't-1', 'Y', 'migration-v25', 'migration-v25');

INSERT INTO user_role_assignment
    (tenant_id, user_id, role_code, scope_level, scope_code, active_flag, created_by, updated_by)
VALUES ('t-1', 'platform-knowledge-governor-1', 'platform-knowledge-governor', 'TENANT', 't-1', 'Y', 'migration-v25', 'migration-v25');
