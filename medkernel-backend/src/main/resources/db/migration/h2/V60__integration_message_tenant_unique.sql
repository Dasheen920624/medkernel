-- MedKernel v1.0 GA · INTEG-01 入站消息租户内幂等约束（H2 PostgreSQL 兼容模式）
-- 目的：同一 message_id 只在同一租户内唯一，避免不同医院外部系统复用消息号时互相阻断。
-- ROLLBACK：若需回退，先确认不存在跨租户同名 message_id，再重建 UNIQUE (message_id)。

ALTER TABLE integration_message_log DROP CONSTRAINT uk_integration_message;
ALTER TABLE integration_message_log ADD CONSTRAINT uk_integration_message
    UNIQUE (tenant_id, message_id);
