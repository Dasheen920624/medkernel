-- MedKernel v1.0 GA · INTEG-01 适配器目录租户内唯一约束（PostgreSQL）
-- 目的：同一 adapter_id 只在同一租户内唯一，避免集团多租户接入同名 HIS/LIS 时互相阻断。
-- ROLLBACK：若需回退，先确认不存在跨租户同名 adapter_id，再重建 UNIQUE (adapter_id)。

ALTER TABLE integration_adapter DROP CONSTRAINT uk_integration_adapter;
ALTER TABLE integration_adapter ADD CONSTRAINT uk_integration_adapter
    UNIQUE (tenant_id, adapter_id);
