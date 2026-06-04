-- MedKernel v1.0 GA · SVC-CLINICAL-03 临床协同组织作用域（人大金仓）
-- ROLLBACK：如需回滚，先导出协同待办 / 通知组织归属证据，再删除 org_unit_id 列与组织作用域索引。

ALTER TABLE mk_engine_workflow_todo
    ADD COLUMN IF NOT EXISTS org_unit_id VARCHAR(64) NULL;

ALTER TABLE mk_engine_notification
    ADD COLUMN IF NOT EXISTS org_unit_id VARCHAR(64) NULL;

CREATE INDEX IF NOT EXISTS idx_workflow_todo_org_scope
    ON mk_engine_workflow_todo (tenant_id, org_unit_id, status);

CREATE INDEX IF NOT EXISTS idx_notification_org_scope
    ON mk_engine_notification (tenant_id, org_unit_id, status);

COMMENT ON COLUMN mk_engine_workflow_todo.org_unit_id IS '协同待办组织池归属组织单元 ID；为空表示租户级组织项';
COMMENT ON COLUMN mk_engine_notification.org_unit_id IS '通知组织池归属组织单元 ID；为空表示租户级组织项';
