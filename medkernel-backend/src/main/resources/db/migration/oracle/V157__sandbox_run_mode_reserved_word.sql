-- Oracle V153 已直接使用 run_mode；本迁移保持五方言版本序列一致并补充中文释义。
COMMENT ON COLUMN mk_sandbox_run.run_mode IS '沙盘运行模式：CURRENT、HISTORICAL_EXACT 或 COMPARE；避免 Oracle MODE 保留字';
