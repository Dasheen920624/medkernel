-- MedKernel v1.0 GA · 链路追踪标识持久化宽度统一
-- 回滚：确认不存在超过 64 字符的追踪标识后，方可将以下字段缩回 VARCHAR2(64)。
ALTER TABLE platform_credential MODIFY trace_id VARCHAR2(128);
ALTER TABLE mk_security_bootstrap_init_token MODIFY trace_id VARCHAR2(128);
ALTER TABLE evaluation_indicator MODIFY trace_id VARCHAR2(128);
ALTER TABLE evaluation_run MODIFY trace_id VARCHAR2(128);
ALTER TABLE evaluation_result MODIFY trace_id VARCHAR2(128);
ALTER TABLE quality_finding MODIFY trace_id VARCHAR2(128);
ALTER TABLE rectification_task MODIFY trace_id VARCHAR2(128);
ALTER TABLE rectification_review MODIFY trace_id VARCHAR2(128);
ALTER TABLE evaluation_idempotency_key MODIFY trace_id VARCHAR2(128);

COMMENT ON COLUMN platform_credential.trace_id IS '链路追踪标识，与入站最长 128 字符契约一致';
COMMENT ON COLUMN mk_security_bootstrap_init_token.trace_id IS '链路追踪标识，与入站最长 128 字符契约一致';
COMMENT ON COLUMN evaluation_indicator.trace_id IS '链路追踪标识，与入站最长 128 字符契约一致';
COMMENT ON COLUMN evaluation_run.trace_id IS '链路追踪标识，与入站最长 128 字符契约一致';
COMMENT ON COLUMN evaluation_result.trace_id IS '链路追踪标识，与入站最长 128 字符契约一致';
COMMENT ON COLUMN quality_finding.trace_id IS '链路追踪标识，与入站最长 128 字符契约一致';
COMMENT ON COLUMN rectification_task.trace_id IS '链路追踪标识，与入站最长 128 字符契约一致';
COMMENT ON COLUMN rectification_review.trace_id IS '链路追踪标识，与入站最长 128 字符契约一致';
COMMENT ON COLUMN evaluation_idempotency_key.trace_id IS '链路追踪标识，与入站最长 128 字符契约一致';
