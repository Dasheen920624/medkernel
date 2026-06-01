-- MedKernel API-13 · 大规模审计列表游标分页覆盖索引（Oracle）
CREATE INDEX idx_audit_event_large_cursor
    ON audit_event (tenant_id, id);

CREATE INDEX idx_audit_event_large_action
    ON audit_event (tenant_id, action, id);

CREATE INDEX idx_audit_event_large_resource
    ON audit_event (tenant_id, resource_type, id);

CREATE INDEX idx_audit_event_large_actor
    ON audit_event (tenant_id, actor_user_id, id);
