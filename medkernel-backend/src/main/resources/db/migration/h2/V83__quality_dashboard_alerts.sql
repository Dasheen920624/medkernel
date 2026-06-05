-- MedKernel v1.0 GA · SVC-QUALITY-01 质控驾驶舱预警 read-model（H2）
-- ROLLBACK：如需回滚，先导出质控预警证据与状态，再删除 mk_quality_dashboard_alert 表。

CREATE TABLE IF NOT EXISTS mk_quality_dashboard_alert (
    id               BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    alert_id         VARCHAR(256)  NOT NULL,
    tenant_id        VARCHAR(64)   NOT NULL,
    department_id    VARCHAR(64)   NULL,
    alert_type       VARCHAR(64)   NOT NULL,
    source_type      VARCHAR(64)   NOT NULL,
    source_id        VARCHAR(128)  NOT NULL,
    severity         VARCHAR(32)   NOT NULL,
    status           VARCHAR(32)   NOT NULL DEFAULT 'OPEN',
    threshold_code   VARCHAR(64)   NOT NULL,
    threshold_value  DECIMAL(18,4) NOT NULL,
    actual_value     DECIMAL(18,4) NOT NULL,
    title            VARCHAR(256)  NOT NULL,
    evidence_summary CLOB          NOT NULL,
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by       VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       VARCHAR(64)   NOT NULL DEFAULT 'system',
    trace_id         VARCHAR(128)  NULL,
    CONSTRAINT uk_quality_dashboard_alert_id UNIQUE (tenant_id, alert_id),
    CONSTRAINT uk_quality_dashboard_alert_source UNIQUE (tenant_id, alert_type, source_type, source_id),
    CONSTRAINT ck_quality_dashboard_alert_type CHECK (alert_type IN ('HIGH_RISK_FINDING','OVERDUE_RECTIFICATION')),
    CONSTRAINT ck_quality_dashboard_alert_status CHECK (status IN ('OPEN','ACKNOWLEDGED','RESOLVED'))
);

CREATE INDEX IF NOT EXISTS idx_quality_dashboard_alert_tenant_status
    ON mk_quality_dashboard_alert (tenant_id, status, updated_at);
CREATE INDEX IF NOT EXISTS idx_quality_dashboard_alert_department
    ON mk_quality_dashboard_alert (tenant_id, department_id, status);

COMMENT ON TABLE mk_quality_dashboard_alert IS 'SVC-QUALITY-01 质控驾驶舱预警 read-model 表，保存真实质控问题与整改任务阈值越界预警';
COMMENT ON COLUMN mk_quality_dashboard_alert.tenant_id IS '租户 ID';
COMMENT ON COLUMN mk_quality_dashboard_alert.department_id IS '责任科室 ID';
COMMENT ON COLUMN mk_quality_dashboard_alert.alert_type IS '预警类型';
COMMENT ON COLUMN mk_quality_dashboard_alert.source_type IS '预警来源事实类型';
COMMENT ON COLUMN mk_quality_dashboard_alert.source_id IS '预警来源事实 ID';
COMMENT ON COLUMN mk_quality_dashboard_alert.status IS '预警处理状态';
COMMENT ON COLUMN mk_quality_dashboard_alert.threshold_code IS '触发阈值编码';
COMMENT ON COLUMN mk_quality_dashboard_alert.actual_value IS '触发时真实值';
COMMENT ON COLUMN mk_quality_dashboard_alert.evidence_summary IS '预警证据摘要';
COMMENT ON COLUMN mk_quality_dashboard_alert.trace_id IS '链路追踪 ID';
