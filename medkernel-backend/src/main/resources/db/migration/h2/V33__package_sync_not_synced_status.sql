-- ROLLBACK：若需回滚，先将 release_plan/sync_log 中 NOT_SYNCED 记录按业务审计转为 FAILED，再恢复旧 CHECK 约束。
ALTER TABLE release_plan DROP CONSTRAINT ck_release_plan_status;
ALTER TABLE release_plan ADD CONSTRAINT ck_release_plan_status CHECK (status IN ('DRAFT','EXECUTING','SUCCESS','FAILED','NOT_SYNCED','ROLLBACKED'));
ALTER TABLE sync_log DROP CONSTRAINT ck_sync_log_status;
ALTER TABLE sync_log ADD CONSTRAINT ck_sync_log_status CHECK (status IN ('RUNNING','SUCCESS','FAILED','NOT_SYNCED','RETRYING'));

COMMENT ON COLUMN release_plan.status IS '发布计划状态：DRAFT 草稿 / EXECUTING 执行中 / SUCCESS 成功 / FAILED 失败 / NOT_SYNCED 未接入真实同步通道 / ROLLBACKED 已回滚';
COMMENT ON COLUMN sync_log.status IS '同步日志状态：RUNNING 运行中 / SUCCESS 成功 / FAILED 失败 / NOT_SYNCED 未接入真实同步通道 / RETRYING 重试中';
