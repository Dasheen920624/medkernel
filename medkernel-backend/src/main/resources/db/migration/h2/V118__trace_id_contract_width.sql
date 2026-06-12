-- MedKernel v1.0 GA · 链路追踪标识持久化宽度统一
-- 回滚：确认不存在超过 64 字符的追踪标识后，方可将两个字段缩回 VARCHAR(64)。
ALTER TABLE platform_credential ALTER COLUMN trace_id SET DATA TYPE VARCHAR(128);
ALTER TABLE mk_security_bootstrap_init_token ALTER COLUMN trace_id SET DATA TYPE VARCHAR(128);

COMMENT ON COLUMN platform_credential.trace_id IS '链路追踪标识，与入站最长 128 字符契约一致';
COMMENT ON COLUMN mk_security_bootstrap_init_token.trace_id IS '链路追踪标识，与入站最长 128 字符契约一致';
