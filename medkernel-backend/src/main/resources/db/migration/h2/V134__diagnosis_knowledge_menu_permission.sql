-- B0 第一阶段完美化 · 诊断知识维护菜单权限（H2）
-- ROLLBACK：确认没有角色授权引用后，删除 menu.diagnosis-knowledge 权限记录。

INSERT INTO sys_permission (permission_code, dimension, target, display_name, risk_level, created_by, updated_by)
VALUES ('menu.diagnosis-knowledge', 'MENU', 'diagnosis-knowledge', '查看诊断知识维护', 'LOW', 'migration-v134', 'migration-v134');
