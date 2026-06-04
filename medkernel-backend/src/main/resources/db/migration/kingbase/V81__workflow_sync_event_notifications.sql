-- SVC-CLINICAL-03 PR4：通知中心接入临床同步事件通知来源。
-- ROLLBACK：如需回滚，先确认无 source_type='SYNC_EVENT' 的未归档通知，或导出审计证据后删除相关通知，再恢复旧约束。

ALTER TABLE mk_engine_notification DROP CONSTRAINT IF EXISTS ck_notification_source_type;

ALTER TABLE mk_engine_notification ADD CONSTRAINT ck_notification_source_type
    CHECK (source_type IN ('FOLLOWUP_EVENT','SAFETY_REVIEW','WORKFLOW_TODO','SYNC_EVENT'));

COMMENT ON COLUMN mk_engine_notification.source_type IS '通知来源类型，包含随访事件、安全复核、协同待办和临床同步事件';
