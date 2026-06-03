-- MedKernel v1.0 GA · INTEG-01 Webhook 订阅租户内唯一约束（达梦 DM）
-- 目的：同一 webhook_id 只在同一租户内唯一，避免不同医院接入同名 Webhook 通道时互相阻断。
-- ROLLBACK：若需回退，先确认不存在跨租户同名 webhook_id，再重建 UNIQUE (webhook_id)。

ALTER TABLE integration_webhook_config DROP CONSTRAINT uk_integration_webhook;
ALTER TABLE integration_webhook_config ADD CONSTRAINT uk_integration_webhook
    UNIQUE (tenant_id, webhook_id);
