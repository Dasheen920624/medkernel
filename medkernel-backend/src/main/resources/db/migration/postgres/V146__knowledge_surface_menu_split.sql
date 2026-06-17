-- MedKernel Phase 8 T8.1 · 知识审核/机构知识/知识生产菜单拆分（PostgreSQL）
-- ROLLBACK：确认没有角色授权引用后，删除新增菜单权限，并将 menu.ai-workflows 显示名恢复为“查看智能工作流”。

INSERT INTO sys_permission (permission_code, dimension, target, display_name, risk_level, created_by, updated_by)
VALUES ('menu.institution-knowledge', 'MENU', 'institution-knowledge', '查看机构知识', 'LOW', 'migration-v146', 'migration-v146');

INSERT INTO sys_permission (permission_code, dimension, target, display_name, risk_level, created_by, updated_by)
VALUES ('menu.knowledge-production', 'MENU', 'knowledge-production', '查看知识生产', 'LOW', 'migration-v146', 'migration-v146');

UPDATE sys_permission
   SET display_name = '查看模型能力',
       updated_at = CURRENT_TIMESTAMP,
       updated_by = 'migration-v146'
 WHERE permission_code = 'menu.ai-workflows';
