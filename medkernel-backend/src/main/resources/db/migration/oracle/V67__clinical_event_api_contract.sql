-- MedKernel v1.0 GA · API-02 临床事件客户面契约补强（Oracle）

ALTER TABLE clinical_event ADD trigger_point VARCHAR2(64) NULL;
ALTER TABLE clinical_event ADD idempotency_key VARCHAR2(128) NULL;
ALTER TABLE clinical_event ADD callback_webhook_id VARCHAR2(64) NULL;

ALTER TABLE clinical_event ADD CONSTRAINT ck_clinical_event_trigger_point
    CHECK (trigger_point IS NULL OR trigger_point IN (
        'PATIENT_VIEW','ORDER_SIGN','MEDICATION_PRESCRIBE',
        'RESULT_REVIEW','DISCHARGE_SIGN','FOLLOWUP_ALERT'
    ));

CREATE UNIQUE INDEX uk_clinical_event_idempotency
    ON clinical_event (tenant_id, idempotency_key);
CREATE INDEX idx_clinical_event_trigger
    ON clinical_event (tenant_id, trigger_point, received_at);
CREATE INDEX idx_clinical_event_callback
    ON clinical_event (tenant_id, callback_webhook_id, received_at);

COMMENT ON COLUMN clinical_event.trigger_point IS '临床事件触发点：患者打开、医嘱签署、用药开立、结果查看、出院签署或随访提醒';
COMMENT ON COLUMN clinical_event.idempotency_key IS '客户面 API 幂等键，同租户内同键同 payload 只接收一次';
COMMENT ON COLUMN clinical_event.callback_webhook_id IS '处理完成后回调客户系统的 Webhook 配置 ID';
