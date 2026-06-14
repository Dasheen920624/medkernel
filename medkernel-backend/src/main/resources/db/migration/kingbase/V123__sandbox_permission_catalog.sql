-- MedKernel 第一阶段收官 · 全真体验沙盘权限目录（人大金仓）
-- ROLLBACK：确认没有角色授权引用后，删除 menu.sandbox 与 sandbox.run 权限记录。

INSERT INTO sys_permission (permission_code, dimension, target, display_name, risk_level, created_by, updated_by)
VALUES ('menu.sandbox', 'MENU', 'sandbox', '查看全真体验沙盘', 'LOW', 'migration-v123', 'migration-v123');

INSERT INTO sys_permission (permission_code, dimension, target, display_name, risk_level, created_by, updated_by)
VALUES ('sandbox.run', 'ACTION', 'sandbox', '运行全真体验沙盘', 'MEDIUM', 'migration-v123', 'migration-v123');
