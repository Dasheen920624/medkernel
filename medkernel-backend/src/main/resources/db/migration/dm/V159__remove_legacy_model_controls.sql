-- MedKernel 模型生产控制台 · 移除旧模型控制入口（达梦 DM）
-- 旧医学回归复核菜单权限、独立复核入口与凭据维护已收敛到模型生产控制台，不保留旧授权覆盖、环境变量凭据引用或兼容入口。
-- ROLLBACK：如需紧急回退，先停止模型生产；重建旧列和权限数据仅用于恢复上一制品，凭据须由授权运维重新登记，不从加密凭据库反推。

DELETE FROM role_permission
 WHERE permission_code = 'menu.model-evaluation-review';

DELETE FROM sys_permission
 WHERE permission_code = 'menu.model-evaluation-review';

ALTER TABLE mk_llm_provider
    DROP COLUMN credential_ref;
