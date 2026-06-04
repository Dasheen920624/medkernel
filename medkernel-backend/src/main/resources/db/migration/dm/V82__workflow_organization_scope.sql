-- MedKernel v1.0 GA · SVC-CLINICAL-03 临床协同组织作用域（达梦）
-- ROLLBACK：如需回滚，先导出协同待办 / 通知组织归属证据，再删除 org_unit_id 列与组织作用域索引。

ALTER TABLE mk_engine_workflow_todo
    ADD org_unit_id VARCHAR2(64) NULL;

ALTER TABLE mk_engine_notification
    ADD org_unit_id VARCHAR2(64) NULL;

CREATE INDEX idx_workflow_todo_org_scope
    ON mk_engine_workflow_todo (tenant_id, org_unit_id, status);

CREATE INDEX idx_notification_org_scope
    ON mk_engine_notification (tenant_id, org_unit_id, status);

COMMENT ON COLUMN mk_engine_workflow_todo.org_unit_id IS '协同待办组织池归属组织单元 ID；为空表示租户级组织项';
COMMENT ON COLUMN mk_engine_notification.org_unit_id IS '通知组织池归属组织单元 ID；为空表示租户级组织项';
