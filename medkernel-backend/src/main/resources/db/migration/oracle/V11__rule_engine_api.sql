-- MedKernel v1.0 GA · GA-ENG-API-05 规则引擎 API（Oracle）

CREATE TABLE rule_definition (
    id                      NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rule_id                 VARCHAR2(64)  NOT NULL,
    tenant_id               VARCHAR2(64)  NOT NULL,
    rule_code               VARCHAR2(128) NOT NULL,
    name                    VARCHAR2(256) NOT NULL,
    rule_type               VARCHAR2(32)  NOT NULL,
    authoring_mode          VARCHAR2(32)  DEFAULT 'DSL' NOT NULL,
    risk_level              VARCHAR2(16)  DEFAULT 'MEDIUM' NOT NULL,
    status                  VARCHAR2(32)  DEFAULT 'DRAFT' NOT NULL,
    active_version_id       VARCHAR2(64)  NULL,
    package_version         VARCHAR2(64)  NULL,
    applicable_org_unit_id  VARCHAR2(64)  NULL,
    created_at              TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by              VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by              VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    trace_id                VARCHAR2(128) NULL,
    CONSTRAINT uk_rule_definition_tenant_code UNIQUE (tenant_id, rule_code),
    CONSTRAINT ck_rule_definition_type CHECK (rule_type IN (
        'DIAGNOSIS','ORDER','LAB','REPORT','DISCHARGE','FOLLOWUP',
        'INSURANCE','QUALITY','RECORD','PATHWAY'
    )),
    CONSTRAINT ck_rule_definition_mode CHECK (authoring_mode IN ('TEMPLATE','VISUAL','DSL')),
    CONSTRAINT ck_rule_definition_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_rule_definition_status CHECK (status IN ('DRAFT','PUBLISHED','OFFLINE','ARCHIVED'))
);

CREATE INDEX idx_rule_definition_tenant_status ON rule_definition (tenant_id, status, updated_at);
CREATE INDEX idx_rule_definition_type_risk     ON rule_definition (tenant_id, rule_type, risk_level);

CREATE TABLE rule_version (
    id                  NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version_id          VARCHAR2(64)  NOT NULL,
    tenant_id           VARCHAR2(64)  NOT NULL,
    rule_id             VARCHAR2(64)  NOT NULL,
    version_no          NUMBER(10)    NOT NULL,
    source_ref          VARCHAR2(512) NOT NULL,
    change_summary      VARCHAR2(512) NULL,
    dsl_json            CLOB          NOT NULL,
    explanation_json    CLOB          NULL,
    status              VARCHAR2(32)  DEFAULT 'DRAFT' NOT NULL,
    published_at        TIMESTAMP WITH TIME ZONE NULL,
    published_by        VARCHAR2(64)  NULL,
    rollback_version_id VARCHAR2(64)  NULL,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by          VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by          VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    trace_id            VARCHAR2(128) NULL,
    CONSTRAINT uk_rule_version_rule_no UNIQUE (tenant_id, rule_id, version_no),
    CONSTRAINT ck_rule_version_status CHECK (status IN ('DRAFT','PUBLISHED','OFFLINE','ARCHIVED'))
);

CREATE INDEX idx_rule_version_rule_status ON rule_version (tenant_id, rule_id, status);

CREATE TABLE rule_test_case (
    id                   NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    case_id              VARCHAR2(64)  NOT NULL,
    tenant_id            VARCHAR2(64)  NOT NULL,
    rule_id              VARCHAR2(64)  NOT NULL,
    version_id           VARCHAR2(64)  NOT NULL,
    case_type            VARCHAR2(32)  NOT NULL,
    context_snapshot_id  VARCHAR2(64)  NOT NULL,
    input_payload        CLOB          NOT NULL,
    expected_hit         NUMBER(1)     NOT NULL,
    expected_severity    VARCHAR2(16)  NULL,
    expected_action_code VARCHAR2(64)  NULL,
    last_hit             NUMBER(1)     NULL,
    last_status          VARCHAR2(32)  NULL,
    last_message         VARCHAR2(512) NULL,
    last_run_at          TIMESTAMP WITH TIME ZONE NULL,
    created_at           TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by           VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at           TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by           VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    trace_id             VARCHAR2(128) NULL,
    CONSTRAINT uk_rule_test_case_id UNIQUE (case_id),
    CONSTRAINT ck_rule_test_case_type CHECK (case_type IN ('POSITIVE','NEGATIVE','BOUNDARY','CONFLICT')),
    CONSTRAINT ck_rule_test_case_status CHECK (last_status IS NULL OR last_status IN ('NOT_RUN','PASS','FAIL','ERROR'))
);

CREATE INDEX idx_rule_test_case_version_type ON rule_test_case (tenant_id, version_id, case_type);

CREATE TABLE rule_execution_log (
    id               NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    execution_id     VARCHAR2(64)  NOT NULL,
    tenant_id        VARCHAR2(64)  NOT NULL,
    rule_id          VARCHAR2(64)  NOT NULL,
    version_id       VARCHAR2(64)  NOT NULL,
    trigger_point    VARCHAR2(64)  NOT NULL,
    event_id         VARCHAR2(64)  NULL,
    actor_user_id    VARCHAR2(64)  NULL,
    input_digest     VARCHAR2(128) NOT NULL,
    hit              NUMBER(1)     NOT NULL,
    severity         VARCHAR2(16)  NULL,
    actions_json     CLOB          NULL,
    explanation_json CLOB          NULL,
    status           VARCHAR2(32)  DEFAULT 'SUCCESS' NOT NULL,
    error_code       VARCHAR2(64)  NULL,
    error_class      VARCHAR2(32)  NULL,
    executed_at      TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    trace_id         VARCHAR2(128) NULL,
    CONSTRAINT uk_rule_execution_id UNIQUE (execution_id),
    CONSTRAINT ck_rule_execution_status CHECK (status IN ('SUCCESS','MISS','FAILED')),
    CONSTRAINT ck_rule_execution_severity CHECK (severity IS NULL OR severity IN ('LOW','MEDIUM','HIGH','CRITICAL'))
);

CREATE INDEX idx_rule_execution_tenant_time ON rule_execution_log (tenant_id, executed_at);
CREATE INDEX idx_rule_execution_rule_time   ON rule_execution_log (tenant_id, rule_id, executed_at);
CREATE INDEX idx_rule_execution_trigger     ON rule_execution_log (tenant_id, trigger_point, executed_at);

-- ===== 表与列中文注释（GA-ENG-API-05）=====

COMMENT ON TABLE rule_definition IS '规则定义：受控规则资产的稳定身份；状态机 DRAFT→PUBLISHED→OFFLINE→ARCHIVED；tenant_id + rule_code 唯一';
COMMENT ON COLUMN rule_definition.rule_id                IS '规则 ID（业务键，跨租户唯一）';
COMMENT ON COLUMN rule_definition.tenant_id              IS '租户 ID';
COMMENT ON COLUMN rule_definition.rule_code              IS '规则业务编码（同租户内唯一）';
COMMENT ON COLUMN rule_definition.name                   IS '规则展示名称';
COMMENT ON COLUMN rule_definition.rule_type              IS '规则业务类型：DIAGNOSIS 诊断 / ORDER 医嘱 / LAB 检验 / REPORT 报告 / DISCHARGE 出院 / FOLLOWUP 随访 / INSURANCE 医保 / QUALITY 质控 / RECORD 病历 / PATHWAY 路径';
COMMENT ON COLUMN rule_definition.authoring_mode         IS '规则编写模式：TEMPLATE 模板 / VISUAL 可视化 / DSL JSON DSL（首版默认 DSL）';
COMMENT ON COLUMN rule_definition.risk_level             IS '规则风险级别：LOW 低 / MEDIUM 中 / HIGH 高 / CRITICAL 红线（HIGH/CRITICAL 必须医师确认）';
COMMENT ON COLUMN rule_definition.status                 IS '规则状态机：DRAFT 草稿 / PUBLISHED 已发布 / OFFLINE 已下线 / ARCHIVED 归档';
COMMENT ON COLUMN rule_definition.active_version_id      IS '当前激活版本 ID → rule_version.version_id';
COMMENT ON COLUMN rule_definition.package_version        IS '规则包版本（预留 GA-ENG-RULE-01 多版本灰度/回滚）';
COMMENT ON COLUMN rule_definition.applicable_org_unit_id IS '适用组织单元 ID（限定部门/科室范围，空表示全租户）';

COMMENT ON TABLE rule_version IS '规则版本：规则 JSON DSL 与解释模板的版本化载体；tenant_id + rule_id + version_no 唯一';
COMMENT ON COLUMN rule_version.version_id          IS '版本 ID（业务键，跨租户唯一）';
COMMENT ON COLUMN rule_version.tenant_id           IS '租户 ID';
COMMENT ON COLUMN rule_version.rule_id             IS '关联规则 ID → rule_definition.rule_id';
COMMENT ON COLUMN rule_version.version_no          IS '同规则下递增版本号';
COMMENT ON COLUMN rule_version.source_ref          IS '规则来源引用（指南/制度/路径/医保/院内规范；发布门禁必填）';
COMMENT ON COLUMN rule_version.change_summary      IS '版本变更摘要';
COMMENT ON COLUMN rule_version.dsl_json            IS '规则 JSON DSL 内容（含 trigger/when/then/explain）';
COMMENT ON COLUMN rule_version.explanation_json    IS '规则解释模板 JSON 快照';
COMMENT ON COLUMN rule_version.status              IS '版本状态机：DRAFT 草稿 / PUBLISHED 已发布 / OFFLINE 已下线 / ARCHIVED 归档';
COMMENT ON COLUMN rule_version.published_at        IS '发布时间';
COMMENT ON COLUMN rule_version.published_by        IS '发布人 user_id';
COMMENT ON COLUMN rule_version.rollback_version_id IS '回滚指向版本 ID（GA-ENG-RULE-01 预留）';

COMMENT ON TABLE rule_test_case IS '规则发布门禁测试用例：保存输入快照、期望命中/严重度/动作及最近一次执行结果；case_id 全局唯一';
COMMENT ON COLUMN rule_test_case.case_id              IS '用例 ID（业务键，全局唯一）';
COMMENT ON COLUMN rule_test_case.tenant_id            IS '租户 ID';
COMMENT ON COLUMN rule_test_case.rule_id              IS '关联规则 ID → rule_definition.rule_id';
COMMENT ON COLUMN rule_test_case.version_id           IS '关联规则版本 ID → rule_version.version_id';
COMMENT ON COLUMN rule_test_case.case_type            IS '用例类型：POSITIVE 阳性 / NEGATIVE 阴性 / BOUNDARY 边界 / CONFLICT 冲突（发布门禁要求四类齐备）';
COMMENT ON COLUMN rule_test_case.context_snapshot_id  IS '固化该用例时使用的 ACTIVE 标准上下文快照 ID';
COMMENT ON COLUMN rule_test_case.input_payload        IS '用例输入 JSON 快照';
COMMENT ON COLUMN rule_test_case.expected_hit         IS '期望是否命中';
COMMENT ON COLUMN rule_test_case.expected_severity    IS '期望严重度：LOW 低 / MEDIUM 中 / HIGH 高 / CRITICAL 红线（可空）';
COMMENT ON COLUMN rule_test_case.expected_action_code IS '期望动作码（如 BLOCK / STRONG_REMINDER）';
COMMENT ON COLUMN rule_test_case.last_hit             IS '最近一次实际命中结果';
COMMENT ON COLUMN rule_test_case.last_status          IS '最近一次执行状态：NOT_RUN 未执行 / PASS 通过 / FAIL 失败 / ERROR 异常';
COMMENT ON COLUMN rule_test_case.last_message         IS '最近一次执行说明';
COMMENT ON COLUMN rule_test_case.last_run_at          IS '最近一次执行时间';

COMMENT ON TABLE rule_execution_log IS '规则执行日志：仿真与真实执行的事实记录；execution_id 全局唯一；仅保存输入摘要而非完整上下文';
COMMENT ON COLUMN rule_execution_log.execution_id     IS '执行 ID（业务键，全局唯一）';
COMMENT ON COLUMN rule_execution_log.tenant_id        IS '租户 ID';
COMMENT ON COLUMN rule_execution_log.rule_id          IS '关联规则 ID → rule_definition.rule_id';
COMMENT ON COLUMN rule_execution_log.version_id       IS '执行时使用的规则版本 ID → rule_version.version_id';
COMMENT ON COLUMN rule_execution_log.trigger_point    IS '触发点（如 ORDER_SIGN、ENCOUNTER_OPEN 等）';
COMMENT ON COLUMN rule_execution_log.event_id         IS '关联业务事件 ID（可空）';
COMMENT ON COLUMN rule_execution_log.actor_user_id    IS '触发该执行的 user_id（可空）';
COMMENT ON COLUMN rule_execution_log.input_digest     IS '输入上下文 SHA-256 摘要（不落完整患者上下文）';
COMMENT ON COLUMN rule_execution_log.hit              IS '是否命中规则条件';
COMMENT ON COLUMN rule_execution_log.severity         IS '本次命中最高严重度：LOW 低 / MEDIUM 中 / HIGH 高 / CRITICAL 红线（未命中可空）';
COMMENT ON COLUMN rule_execution_log.actions_json     IS '命中动作清单 JSON 快照';
COMMENT ON COLUMN rule_execution_log.explanation_json IS '可解释性 JSON 快照（含来源引用、推理依据）';
COMMENT ON COLUMN rule_execution_log.status           IS '执行终态：SUCCESS 命中并产出 / MISS 未命中 / FAILED 异常';
COMMENT ON COLUMN rule_execution_log.error_code       IS '失败错误码（仅 FAILED 写入）';
COMMENT ON COLUMN rule_execution_log.error_class      IS '失败错误分类（仅 FAILED 写入）';
COMMENT ON COLUMN rule_execution_log.executed_at      IS '规则执行时间';
