CREATE TABLE evaluation_indicator (
    id NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    indicator_id VARCHAR2(64 CHAR) NOT NULL,
    tenant_id VARCHAR2(64 CHAR) NOT NULL,
    indicator_code VARCHAR2(64 CHAR) NOT NULL,
    version_no NUMBER(10) NOT NULL,
    name VARCHAR2(256 CHAR) NOT NULL,
    subject_type VARCHAR2(32 CHAR) NOT NULL,
    denominator_definition CLOB NOT NULL,
    numerator_definition CLOB NOT NULL,
    exclusion_definition CLOB,
    scoring_definition CLOB,
    time_window VARCHAR2(128 CHAR) NOT NULL,
    organization_scope CLOB NOT NULL,
    responsible_department_id VARCHAR2(64 CHAR),
    source_ref VARCHAR2(256 CHAR),
    package_version VARCHAR2(64 CHAR),
    status VARCHAR2(32 CHAR) DEFAULT 'DRAFT' NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    published_by VARCHAR2(64 CHAR),
    activated_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by VARCHAR2(64 CHAR) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by VARCHAR2(64 CHAR) NOT NULL,
    trace_id VARCHAR2(64 CHAR),
    CONSTRAINT uk_eval_indicator_id UNIQUE (tenant_id, indicator_id),
    CONSTRAINT uk_eval_indicator_tenant_version UNIQUE (tenant_id, indicator_code, version_no),
    CONSTRAINT ck_eval_indicator_subject CHECK (subject_type IN ('PATIENT', 'MEDICAL_RECORD', 'DEPARTMENT', 'DOCTOR', 'DISEASE', 'PATHWAY', 'CLAIM', 'FOLLOWUP')),
    CONSTRAINT ck_eval_indicator_status CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'GRAY', 'ACTIVE', 'OFFLINE', 'ARCHIVED'))
);

CREATE INDEX idx_eval_indicator_tenant_status ON evaluation_indicator (tenant_id, status, updated_at);
CREATE INDEX idx_eval_indicator_code_status ON evaluation_indicator (tenant_id, indicator_code, status);

CREATE TABLE evaluation_run (
    id NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id VARCHAR2(64 CHAR) NOT NULL,
    tenant_id VARCHAR2(64 CHAR) NOT NULL,
    run_code VARCHAR2(64 CHAR) NOT NULL,
    run_type VARCHAR2(32 CHAR) NOT NULL,
    source_event_id VARCHAR2(64 CHAR),
    context_snapshot_id VARCHAR2(64 CHAR),
    patient_id VARCHAR2(64 CHAR),
    encounter_id VARCHAR2(64 CHAR),
    scenario_code VARCHAR2(64 CHAR),
    package_version VARCHAR2(64 CHAR),
    input_digest VARCHAR2(128 CHAR),
    status VARCHAR2(32 CHAR) DEFAULT 'RECEIVED' NOT NULL,
    error_code VARCHAR2(64 CHAR),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by VARCHAR2(64 CHAR) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by VARCHAR2(64 CHAR) NOT NULL,
    trace_id VARCHAR2(64 CHAR),
    CONSTRAINT uk_eval_run_id UNIQUE (tenant_id, run_id),
    CONSTRAINT uk_eval_run_tenant_code UNIQUE (tenant_id, run_code),
    CONSTRAINT ck_eval_run_type CHECK (run_type IN ('MANUAL_SAMPLE', 'UPSTREAM_RESULT', 'BATCH_IMPORT')),
    CONSTRAINT ck_eval_run_status CHECK (status IN ('RECEIVED', 'RECORDED', 'FAILED'))
);

CREATE INDEX idx_eval_run_tenant_time ON evaluation_run (tenant_id, occurred_at);
CREATE INDEX idx_eval_run_context ON evaluation_run (tenant_id, encounter_id, scenario_code);

CREATE TABLE evaluation_result (
    id NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    result_id VARCHAR2(64 CHAR) NOT NULL,
    tenant_id VARCHAR2(64 CHAR) NOT NULL,
    run_id VARCHAR2(64 CHAR) NOT NULL,
    indicator_id VARCHAR2(64 CHAR) NOT NULL,
    indicator_code VARCHAR2(64 CHAR) NOT NULL,
    indicator_version NUMBER(10) NOT NULL,
    subject_type VARCHAR2(32 CHAR) NOT NULL,
    subject_ref_id VARCHAR2(64 CHAR) NOT NULL,
    score_value NUMBER(18,4),
    result_level VARCHAR2(32 CHAR) NOT NULL,
    hit_flag NUMBER(1) DEFAULT 0 NOT NULL,
    evidence_summary VARCHAR2(2048 CHAR),
    source_ref VARCHAR2(256 CHAR),
    responsible_department_id VARCHAR2(64 CHAR),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by VARCHAR2(64 CHAR) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by VARCHAR2(64 CHAR) NOT NULL,
    trace_id VARCHAR2(64 CHAR),
    CONSTRAINT uk_eval_result_id UNIQUE (tenant_id, result_id),
    CONSTRAINT ck_eval_result_subject CHECK (subject_type IN ('PATIENT', 'MEDICAL_RECORD', 'DEPARTMENT', 'DOCTOR', 'DISEASE', 'PATHWAY', 'CLAIM', 'FOLLOWUP')),
    CONSTRAINT ck_eval_result_level CHECK (result_level IN ('PASS', 'ATTENTION', 'NON_COMPLIANT', 'CRITICAL'))
);

CREATE INDEX idx_eval_result_run ON evaluation_result (tenant_id, run_id);
CREATE INDEX idx_eval_result_indicator ON evaluation_result (tenant_id, indicator_code, created_at);

CREATE TABLE quality_finding (
    id NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    finding_id VARCHAR2(64 CHAR) NOT NULL,
    tenant_id VARCHAR2(64 CHAR) NOT NULL,
    run_id VARCHAR2(64 CHAR) NOT NULL,
    result_id VARCHAR2(64 CHAR) NOT NULL,
    indicator_id VARCHAR2(64 CHAR) NOT NULL,
    finding_code VARCHAR2(64 CHAR) NOT NULL,
    title VARCHAR2(256 CHAR) NOT NULL,
    description VARCHAR2(2048 CHAR) NOT NULL,
    severity VARCHAR2(8 CHAR) NOT NULL,
    status VARCHAR2(32 CHAR) DEFAULT 'NEW' NOT NULL,
    evidence_summary VARCHAR2(2048 CHAR) NOT NULL,
    responsible_department_id VARCHAR2(64 CHAR),
    due_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by VARCHAR2(64 CHAR) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by VARCHAR2(64 CHAR) NOT NULL,
    trace_id VARCHAR2(64 CHAR),
    CONSTRAINT uk_quality_finding_id UNIQUE (tenant_id, finding_id),
    CONSTRAINT uk_quality_finding_result_code UNIQUE (tenant_id, result_id, finding_code),
    CONSTRAINT ck_quality_finding_severity CHECK (severity IN ('P0', 'P1', 'P2', 'P3')),
    CONSTRAINT ck_quality_finding_status CHECK (status IN ('NEW', 'ASSIGNED', 'REMEDIATING', 'CLOSED', 'WAIVED'))
);

CREATE INDEX idx_quality_finding_status ON quality_finding (tenant_id, status, severity);
CREATE INDEX idx_quality_finding_department ON quality_finding (tenant_id, responsible_department_id, due_at);

CREATE TABLE rectification_task (
    id NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id VARCHAR2(64 CHAR) NOT NULL,
    tenant_id VARCHAR2(64 CHAR) NOT NULL,
    finding_id VARCHAR2(64 CHAR) NOT NULL,
    responsible_department_id VARCHAR2(64 CHAR) NOT NULL,
    assignee_user_id VARCHAR2(64 CHAR),
    status VARCHAR2(32 CHAR) DEFAULT 'ASSIGNED' NOT NULL,
    due_at TIMESTAMP WITH TIME ZONE NOT NULL,
    rectification_summary VARCHAR2(2048 CHAR),
    evidence_ref VARCHAR2(256 CHAR),
    submitted_at TIMESTAMP WITH TIME ZONE,
    submitted_by VARCHAR2(64 CHAR),
    closed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by VARCHAR2(64 CHAR) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by VARCHAR2(64 CHAR) NOT NULL,
    trace_id VARCHAR2(64 CHAR),
    CONSTRAINT uk_rect_task_id UNIQUE (tenant_id, task_id),
    CONSTRAINT uk_rect_task_finding UNIQUE (tenant_id, finding_id),
    CONSTRAINT ck_rect_task_status CHECK (status IN ('ASSIGNED', 'SUBMITTED', 'RETURNED', 'CLOSED', 'WAIVED'))
);

CREATE INDEX idx_rect_task_finding ON rectification_task (tenant_id, finding_id, status);
CREATE INDEX idx_rect_task_department_status ON rectification_task (tenant_id, responsible_department_id, status, due_at);

CREATE TABLE rectification_review (
    id NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    review_id VARCHAR2(64 CHAR) NOT NULL,
    tenant_id VARCHAR2(64 CHAR) NOT NULL,
    finding_id VARCHAR2(64 CHAR) NOT NULL,
    task_id VARCHAR2(64 CHAR) NOT NULL,
    decision VARCHAR2(32 CHAR) NOT NULL,
    review_comment VARCHAR2(2048 CHAR),
    evidence_ref VARCHAR2(256 CHAR),
    reviewer_id VARCHAR2(64 CHAR) NOT NULL,
    reviewed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by VARCHAR2(64 CHAR) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by VARCHAR2(64 CHAR) NOT NULL,
    trace_id VARCHAR2(64 CHAR),
    CONSTRAINT uk_rect_review_id UNIQUE (tenant_id, review_id),
    CONSTRAINT ck_rect_review_decision CHECK (decision IN ('APPROVED', 'RETURNED', 'WAIVED'))
);

CREATE INDEX idx_rect_review_finding ON rectification_review (tenant_id, finding_id, reviewed_at);

CREATE TABLE evaluation_idempotency_key (
    id NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id VARCHAR2(64 CHAR) NOT NULL,
    idem_key VARCHAR2(128 CHAR) NOT NULL,
    operation_type VARCHAR2(32 CHAR) NOT NULL,
    finding_id VARCHAR2(64 CHAR) NOT NULL,
    task_id VARCHAR2(64 CHAR) NOT NULL,
    review_id VARCHAR2(64 CHAR),
    request_digest VARCHAR2(128 CHAR) NOT NULL,
    finding_status VARCHAR2(32 CHAR) NOT NULL,
    task_status VARCHAR2(32 CHAR) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by VARCHAR2(64 CHAR) NOT NULL,
    trace_id VARCHAR2(64 CHAR),
    CONSTRAINT uk_eval_idempotency_operation_key UNIQUE (tenant_id, operation_type, idem_key),
    CONSTRAINT ck_eval_idempotency_operation CHECK (operation_type IN ('RECTIFICATION_SUBMIT', 'RECTIFICATION_REVIEW')),
    CONSTRAINT ck_eval_idempotency_finding_status CHECK (finding_status IN ('NEW', 'ASSIGNED', 'REMEDIATING', 'CLOSED', 'WAIVED')),
    CONSTRAINT ck_eval_idempotency_task_status CHECK (task_status IN ('ASSIGNED', 'SUBMITTED', 'RETURNED', 'CLOSED', 'WAIVED'))
);

CREATE INDEX idx_eval_idempotency_resource
    ON evaluation_idempotency_key (tenant_id, finding_id, operation_type, created_at);

-- ===== 表与关键列中文注释（GA-ENG-API-08）=====

COMMENT ON TABLE evaluation_indicator IS '评估指标版本：保存质控指标口径、版本、来源和发布状态；tenant_id + indicator_code + version_no 唯一';
COMMENT ON COLUMN evaluation_indicator.indicator_id              IS '指标 ID（业务键，跨租户唯一）';
COMMENT ON COLUMN evaluation_indicator.tenant_id                 IS '租户 ID';
COMMENT ON COLUMN evaluation_indicator.indicator_code            IS '指标编码（同租户内按版本管理）';
COMMENT ON COLUMN evaluation_indicator.version_no                IS '指标版本号（同指标编码下递增）';
COMMENT ON COLUMN evaluation_indicator.subject_type              IS '评估对象类型：PATIENT 患者 / MEDICAL_RECORD 病历 / DEPARTMENT 科室 / DOCTOR 医生 / DISEASE 疾病 / PATHWAY 路径 / CLAIM 医保病例 / FOLLOWUP 随访';
COMMENT ON COLUMN evaluation_indicator.responsible_department_id IS '责任科室 ID';
COMMENT ON COLUMN evaluation_indicator.source_ref                IS '指标来源引用（指南、制度、评级标准或院内规范）';
COMMENT ON COLUMN evaluation_indicator.package_version           IS '指标包版本';
COMMENT ON COLUMN evaluation_indicator.status                    IS '指标状态机：DRAFT 草稿 / PENDING_REVIEW 待审核 / PUBLISHED 已发布 / GRAY 灰度 / ACTIVE 生效 / OFFLINE 下线 / ARCHIVED 归档';
COMMENT ON COLUMN evaluation_indicator.published_at              IS '发布时间';
COMMENT ON COLUMN evaluation_indicator.published_by              IS '发布人 user_id';
COMMENT ON COLUMN evaluation_indicator.activated_at              IS '激活时间';

COMMENT ON TABLE evaluation_run IS '评估运行事实：一次人工抽检、上游结果或批量导入的受控入库批次；tenant_id + run_code 唯一';
COMMENT ON COLUMN evaluation_run.run_id              IS '运行 ID（业务键，跨租户唯一）';
COMMENT ON COLUMN evaluation_run.tenant_id           IS '租户 ID';
COMMENT ON COLUMN evaluation_run.run_code            IS '运行业务编码（同租户内唯一）';
COMMENT ON COLUMN evaluation_run.run_type            IS '运行类型：MANUAL_SAMPLE 人工抽检 / UPSTREAM_RESULT 上游结果 / BATCH_IMPORT 批量导入';
COMMENT ON COLUMN evaluation_run.source_event_id     IS '来源临床事件 ID（可空）';
COMMENT ON COLUMN evaluation_run.context_snapshot_id IS '标准上下文快照 ID（可空）';
COMMENT ON COLUMN evaluation_run.patient_id          IS '患者 ID（可空）';
COMMENT ON COLUMN evaluation_run.encounter_id        IS '就诊 ID（可空）';
COMMENT ON COLUMN evaluation_run.scenario_code       IS '业务场景编码';
COMMENT ON COLUMN evaluation_run.input_digest        IS '输入事实摘要（不保存完整病历正文）';
COMMENT ON COLUMN evaluation_run.status              IS '运行状态：RECEIVED 已接收 / RECORDED 已记录 / FAILED 入库失败';
COMMENT ON COLUMN evaluation_run.error_code          IS '失败错误码（仅 FAILED 写入）';

COMMENT ON TABLE evaluation_result IS '评估结果：绑定指标版本的一次对象判定与证据摘要；result_id 全局唯一';
COMMENT ON COLUMN evaluation_result.result_id                 IS '结果 ID（业务键，跨租户唯一）';
COMMENT ON COLUMN evaluation_result.tenant_id                 IS '租户 ID';
COMMENT ON COLUMN evaluation_result.run_id                    IS '关联运行 ID → evaluation_run.run_id';
COMMENT ON COLUMN evaluation_result.indicator_id              IS '关联指标 ID → evaluation_indicator.indicator_id';
COMMENT ON COLUMN evaluation_result.indicator_code            IS '指标编码快照';
COMMENT ON COLUMN evaluation_result.indicator_version         IS '指标版本号快照';
COMMENT ON COLUMN evaluation_result.subject_type              IS '评估对象类型：PATIENT 患者 / MEDICAL_RECORD 病历 / DEPARTMENT 科室 / DOCTOR 医生 / DISEASE 疾病 / PATHWAY 路径 / CLAIM 医保病例 / FOLLOWUP 随访';
COMMENT ON COLUMN evaluation_result.subject_ref_id            IS '评估对象业务 ID';
COMMENT ON COLUMN evaluation_result.result_level              IS '结果等级：PASS 通过 / ATTENTION 需关注 / NON_COMPLIANT 不符合 / CRITICAL 安全红线';
COMMENT ON COLUMN evaluation_result.hit_flag                  IS '是否命中不符合或关注条件';
COMMENT ON COLUMN evaluation_result.evidence_summary          IS '证据摘要';
COMMENT ON COLUMN evaluation_result.responsible_department_id IS '责任科室 ID（可空）';

COMMENT ON TABLE quality_finding IS '质控问题：评估结果中需要处置的问题事实、风险分级、责任和闭环状态；tenant_id + result_id + finding_code 唯一';
COMMENT ON COLUMN quality_finding.finding_id                IS '问题 ID（业务键，跨租户唯一）';
COMMENT ON COLUMN quality_finding.tenant_id                 IS '租户 ID';
COMMENT ON COLUMN quality_finding.run_id                    IS '关联运行 ID → evaluation_run.run_id';
COMMENT ON COLUMN quality_finding.result_id                 IS '关联结果 ID → evaluation_result.result_id';
COMMENT ON COLUMN quality_finding.indicator_id              IS '关联指标 ID → evaluation_indicator.indicator_id';
COMMENT ON COLUMN quality_finding.finding_code              IS '问题编码（同结果内唯一）';
COMMENT ON COLUMN quality_finding.severity                  IS '问题严重度：P0 安全红线 / P1 高风险 / P2 中风险 / P3 低风险';
COMMENT ON COLUMN quality_finding.status                    IS '问题状态机：NEW 新发现 / ASSIGNED 已派单 / REMEDIATING 整改中 / CLOSED 已关闭 / WAIVED 已豁免';
COMMENT ON COLUMN quality_finding.evidence_summary          IS '问题证据摘要';
COMMENT ON COLUMN quality_finding.responsible_department_id IS '责任科室 ID（P0/P1 必填）';
COMMENT ON COLUMN quality_finding.due_at                    IS '整改截止时间（P0/P1 必填）';

COMMENT ON TABLE rectification_task IS '整改任务：质控问题的派单、提交、退回、关闭和豁免状态；tenant_id + finding_id 唯一';
COMMENT ON COLUMN rectification_task.task_id                   IS '整改任务 ID（业务键，跨租户唯一）';
COMMENT ON COLUMN rectification_task.tenant_id                 IS '租户 ID';
COMMENT ON COLUMN rectification_task.finding_id                IS '关联问题 ID → quality_finding.finding_id';
COMMENT ON COLUMN rectification_task.responsible_department_id IS '责任科室 ID';
COMMENT ON COLUMN rectification_task.assignee_user_id          IS '整改处理人 user_id（可空）';
COMMENT ON COLUMN rectification_task.status                    IS '整改任务状态机：ASSIGNED 已派发 / SUBMITTED 已提交 / RETURNED 已退回 / CLOSED 已关闭 / WAIVED 已豁免';
COMMENT ON COLUMN rectification_task.due_at                    IS '整改截止时间';
COMMENT ON COLUMN rectification_task.rectification_summary     IS '整改说明';
COMMENT ON COLUMN rectification_task.evidence_ref              IS '整改证据引用';
COMMENT ON COLUMN rectification_task.submitted_at              IS '整改提交时间';
COMMENT ON COLUMN rectification_task.submitted_by              IS '整改提交人 user_id';
COMMENT ON COLUMN rectification_task.closed_at                 IS '整改关闭时间';

COMMENT ON TABLE rectification_review IS '整改复核记录：保存追加式复核结论和证据，不覆写历史意见；review_id 全局唯一';
COMMENT ON COLUMN rectification_review.review_id      IS '复核记录 ID（业务键，跨租户唯一）';
COMMENT ON COLUMN rectification_review.tenant_id      IS '租户 ID';
COMMENT ON COLUMN rectification_review.finding_id     IS '关联问题 ID → quality_finding.finding_id';
COMMENT ON COLUMN rectification_review.task_id        IS '关联整改任务 ID → rectification_task.task_id';
COMMENT ON COLUMN rectification_review.decision       IS '复核结论：APPROVED 通过关闭 / RETURNED 退回继续整改 / WAIVED 豁免关闭';
COMMENT ON COLUMN rectification_review.review_comment IS '复核意见（列名避开 Oracle 保留字 comment）';
COMMENT ON COLUMN rectification_review.evidence_ref   IS '复核证据引用';
COMMENT ON COLUMN rectification_review.reviewer_id    IS '复核人 user_id';
COMMENT ON COLUMN rectification_review.reviewed_at    IS '复核时间';

COMMENT ON TABLE evaluation_idempotency_key IS '评估闭环幂等键：保存整改提交或复核请求摘要及首次成功状态；tenant_id + operation_type + idem_key 唯一';
COMMENT ON COLUMN evaluation_idempotency_key.tenant_id      IS '租户 ID';
COMMENT ON COLUMN evaluation_idempotency_key.idem_key       IS '幂等键（来自 Idempotency-Key 请求头）';
COMMENT ON COLUMN evaluation_idempotency_key.operation_type IS '幂等操作类型：RECTIFICATION_SUBMIT 整改提交 / RECTIFICATION_REVIEW 整改复核';
COMMENT ON COLUMN evaluation_idempotency_key.finding_id     IS '关联问题 ID → quality_finding.finding_id';
COMMENT ON COLUMN evaluation_idempotency_key.task_id        IS '关联整改任务 ID → rectification_task.task_id';
COMMENT ON COLUMN evaluation_idempotency_key.review_id      IS '关联复核记录 ID → rectification_review.review_id（复核操作写入）';
COMMENT ON COLUMN evaluation_idempotency_key.request_digest IS '请求摘要（用于拒绝同键异文）';
COMMENT ON COLUMN evaluation_idempotency_key.finding_status IS '首次成功后的问题状态快照';
COMMENT ON COLUMN evaluation_idempotency_key.task_status    IS '首次成功后的整改任务状态快照';
