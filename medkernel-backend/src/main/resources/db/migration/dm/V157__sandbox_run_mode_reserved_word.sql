-- 统一沙盘运行模式列名，避免 Oracle MODE 保留字并保持五方言模型一致。
ALTER TABLE mk_sandbox_run RENAME COLUMN mode TO run_mode;

COMMENT ON COLUMN mk_sandbox_run.run_mode IS '沙盘运行模式：CURRENT、HISTORICAL_EXACT 或 COMPARE';
