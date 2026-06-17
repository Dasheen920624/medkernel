-- MedKernel 第二阶段 P5-T5.5 · OPT-09 数据最小化策略引擎（PostgreSQL）
-- ROLLBACK：确认无出域策略依赖后删除 mk_llm_egress_whitelist 的策略扩展列与约束。

ALTER TABLE mk_llm_egress_whitelist
    ADD COLUMN IF NOT EXISTS desensitization_rules VARCHAR(2048) NOT NULL DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS approval_threshold_level VARCHAR(16) NOT NULL DEFAULT 'HIGH',
    ADD COLUMN IF NOT EXISTS guardrail_locked_flag CHAR(1) NOT NULL DEFAULT 'Y';

ALTER TABLE mk_llm_egress_whitelist
    ADD CONSTRAINT ck_mk_llm_egress_policy_threshold
        CHECK (approval_threshold_level IN ('LOW', 'MEDIUM', 'HIGH'));

ALTER TABLE mk_llm_egress_whitelist
    ADD CONSTRAINT ck_mk_llm_egress_policy_guardrail
        CHECK (guardrail_locked_flag = 'Y');

COMMENT ON COLUMN mk_llm_egress_whitelist.desensitization_rules IS 'OPT-09 数据最小化策略：字段到脱敏规则 JSON，算子支持 MASK_ALL / MASK / GENERALIZE / NULLIFY / NONE';
COMMENT ON COLUMN mk_llm_egress_whitelist.approval_threshold_level IS 'OPT-09 数据最小化策略审批阈值：敏感级达到 LOW / MEDIUM / HIGH 时必须命中人工审批';
COMMENT ON COLUMN mk_llm_egress_whitelist.guardrail_locked_flag IS 'OPT-09 高危护栏标志：固定 Y，不允许关闭模型出域数据最小化护栏';
