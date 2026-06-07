-- MedKernel v1.0 GA · GA-ENG-INTEG-01 适配器健康状态扩展（达梦 DM）
-- 引入诚实自检状态 NOT_CONNECTED（配置合法但未接入真实连接器、外部可达性未知）
-- 与 MISCONFIGURED（配置非法），取代"配置合法即 HEALTHY"的失真语义；
-- 保留 HEALTHY/UNHEALTHY 供接入真实外部连接器（INTEG-02 / QA-08）后使用。
-- ROLLBACK: 回退前将 NOT_CONNECTED 与 MISCONFIGURED 状态转换为 UNHEALTHY，再恢复旧健康状态约束。

ALTER TABLE integration_adapter DROP CONSTRAINT ck_integration_adapter_health;
ALTER TABLE integration_adapter ADD CONSTRAINT ck_integration_adapter_health
    CHECK (health_status IN ('HEALTHY','UNHEALTHY','NOT_CONNECTED','MISCONFIGURED'));
