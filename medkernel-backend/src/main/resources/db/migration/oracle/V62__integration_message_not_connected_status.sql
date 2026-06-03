-- MedKernel v1.0 GA · INTEG-01 集成消息断连降级状态（Oracle）
-- ROLLBACK：确认无 NOT_CONNECTED 集成消息后，删除 ck_integration_message_status 并恢复为 SUCCESS/FAILED/RETRYING/DEAD_LETTER。
-- 允许出站同步断连时诚实标记 NOT_CONNECTED，等待异步补偿或死信重放，不伪造 SUCCESS。

ALTER TABLE integration_message_log DROP CONSTRAINT ck_integration_message_status;
ALTER TABLE integration_message_log ADD CONSTRAINT ck_integration_message_status
    CHECK (status IN ('SUCCESS','FAILED','RETRYING','NOT_CONNECTED','DEAD_LETTER'));

COMMENT ON COLUMN integration_message_log.status IS '数据流审计及重试队列状态（SUCCESS、FAILED、RETRYING、NOT_CONNECTED、DEAD_LETTER）';
