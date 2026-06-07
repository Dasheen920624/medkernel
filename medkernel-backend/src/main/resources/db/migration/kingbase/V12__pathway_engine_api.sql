-- MedKernel v1.0 GA · GA-ENG-API-06 路径引擎 API（人大金仓）

CREATE TABLE IF NOT EXISTS specialty_package (
    id                BIGSERIAL PRIMARY KEY,
    package_id        VARCHAR(64)  NOT NULL,
    tenant_id         VARCHAR(64)  NOT NULL,
    package_code      VARCHAR(128) NOT NULL,
    disease_code      VARCHAR(128) NOT NULL,
    name              VARCHAR(256) NOT NULL,
    package_version   VARCHAR(64)  NOT NULL,
    status            VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    source_ref        VARCHAR(512) NOT NULL,
    description       VARCHAR(1024) NULL,
    published_at      TIMESTAMPTZ  NULL,
    published_by      VARCHAR(64)  NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by        VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id          VARCHAR(128) NULL,
    CONSTRAINT uk_specialty_package_tenant_code UNIQUE (tenant_id, package_code, package_version),
    CONSTRAINT ck_specialty_package_status CHECK (status IN ('DRAFT','PUBLISHED','OFFLINE','ARCHIVED'))
);

CREATE INDEX IF NOT EXISTS idx_specialty_package_tenant_status ON specialty_package (tenant_id, status, updated_at);
CREATE INDEX IF NOT EXISTS idx_specialty_package_disease       ON specialty_package (tenant_id, disease_code);

CREATE TABLE IF NOT EXISTS specialty_profile (
    id                   BIGSERIAL PRIMARY KEY,
    profile_id           VARCHAR(64)  NOT NULL,
    tenant_id            VARCHAR(64)  NOT NULL,
    package_id           VARCHAR(64)  NOT NULL,
    profile_code         VARCHAR(128) NOT NULL,
    name                 VARCHAR(256) NOT NULL,
    stratification_json  TEXT         NULL,
    entry_criteria_json  TEXT         NULL,
    exit_criteria_json   TEXT         NULL,
    followup_plan_json   TEXT         NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by           VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by           VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id             VARCHAR(128) NULL,
    CONSTRAINT uk_specialty_profile_package_code UNIQUE (tenant_id, package_id, profile_code)
);

CREATE INDEX IF NOT EXISTS idx_specialty_profile_package ON specialty_profile (tenant_id, package_id);

CREATE TABLE IF NOT EXISTS pathway_template (
    id                   BIGSERIAL PRIMARY KEY,
    template_id          VARCHAR(64)  NOT NULL,
    tenant_id            VARCHAR(64)  NOT NULL,
    package_id           VARCHAR(64)  NOT NULL,
    template_code        VARCHAR(128) NOT NULL,
    name                 VARCHAR(256) NOT NULL,
    disease_code         VARCHAR(128) NOT NULL,
    template_version     INT          NOT NULL DEFAULT 1,
    template_level       VARCHAR(32)  NOT NULL DEFAULT 'STANDARD',
    status               VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    entry_mode           VARCHAR(32)  NOT NULL DEFAULT 'AUTO_SUGGEST',
    start_node_code      VARCHAR(128) NULL,
    source_ref           VARCHAR(512) NOT NULL,
    description          VARCHAR(1024) NULL,
    entry_criteria_json  TEXT         NULL,
    exit_criteria_json   TEXT         NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by           VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by           VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id             VARCHAR(128) NULL,
    CONSTRAINT uk_pathway_template_tenant_code UNIQUE (tenant_id, template_code, template_version),
    CONSTRAINT ck_pathway_template_level CHECK (template_level IN (
        'STANDARD','GROUP','HOSPITAL','DEPARTMENT','SPECIALTY'
    )),
    CONSTRAINT ck_pathway_template_status CHECK (status IN ('DRAFT','PUBLISHED','OFFLINE','ARCHIVED')),
    CONSTRAINT ck_pathway_entry_mode CHECK (entry_mode IN ('AUTO_SUGGEST','MANUAL_CONFIRM'))
);

CREATE INDEX IF NOT EXISTS idx_pathway_template_tenant_status ON pathway_template (tenant_id, status, updated_at);
CREATE INDEX IF NOT EXISTS idx_pathway_template_package       ON pathway_template (tenant_id, package_id);
CREATE INDEX IF NOT EXISTS idx_pathway_template_disease       ON pathway_template (tenant_id, disease_code);

CREATE TABLE IF NOT EXISTS pathway_milestone (
    id                         BIGSERIAL PRIMARY KEY,
    milestone_id               VARCHAR(64)  NOT NULL,
    tenant_id                  VARCHAR(64)  NOT NULL,
    template_id                VARCHAR(64)  NOT NULL,
    phase_code                 VARCHAR(128) NOT NULL,
    phase_name                 VARCHAR(256) NOT NULL,
    milestone_code             VARCHAR(128) NOT NULL,
    name                       VARCHAR(256) NOT NULL,
    day_offset                 INT          NULL,
    expected_offset_minutes    INT          NULL,
    achievement_criteria_json  TEXT         NULL,
    sort_order                 INT          NOT NULL DEFAULT 0,
    created_at                 TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                 VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                 VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id                   VARCHAR(128) NULL,
    CONSTRAINT uk_pathway_milestone_template_code UNIQUE (tenant_id, template_id, milestone_code),
    CONSTRAINT ck_pathway_milestone_day CHECK (day_offset IS NULL OR day_offset >= 0),
    CONSTRAINT ck_pathway_milestone_expected CHECK (expected_offset_minutes IS NULL OR expected_offset_minutes >= 0)
);

CREATE INDEX IF NOT EXISTS idx_pathway_milestone_template_order
    ON pathway_milestone (tenant_id, template_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_pathway_milestone_phase_day
    ON pathway_milestone (tenant_id, template_id, phase_code, day_offset);

CREATE TABLE IF NOT EXISTS pathway_node (
    id                  BIGSERIAL PRIMARY KEY,
    node_id             VARCHAR(64)  NOT NULL,
    tenant_id           VARCHAR(64)  NOT NULL,
    template_id         VARCHAR(64)  NOT NULL,
    node_code           VARCHAR(128) NOT NULL,
    name                VARCHAR(256) NOT NULL,
    node_type           VARCHAR(32)  NOT NULL,
    milestone_code      VARCHAR(128) NULL,
    sort_order          INT          NOT NULL DEFAULT 0,
    responsible_role    VARCHAR(128) NULL,
    accountable_role    VARCHAR(128) NULL,
    consulted_roles_json TEXT        NULL,
    informed_roles_json TEXT         NULL,
    dependency_json     TEXT         NULL,
    time_window_minutes INT          NULL,
    terminal_flag       BOOLEAN      NOT NULL DEFAULT FALSE,
    config_json         TEXT         NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by          VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id            VARCHAR(128) NULL,
    CONSTRAINT uk_pathway_node_template_code UNIQUE (tenant_id, template_id, node_code),
    CONSTRAINT ck_pathway_node_type CHECK (node_type IN (
        'SCREENING','ASSESSMENT','EXAM','LAB','MEDICATION','SURGERY',
        'NURSING','REHAB','DISCHARGE','FOLLOWUP','QUALITY',
        'DECISION','PARALLEL','WAIT_TIMER','SUBPATHWAY','MANUAL_GATE','ORDER_SET'
    )),
    CONSTRAINT ck_pathway_node_terminal CHECK (terminal_flag IN (TRUE, FALSE))
);

CREATE INDEX IF NOT EXISTS idx_pathway_node_template_order ON pathway_node (tenant_id, template_id, sort_order);

CREATE TABLE IF NOT EXISTS pathway_edge (
    id             BIGSERIAL PRIMARY KEY,
    edge_id        VARCHAR(64)  NOT NULL,
    tenant_id      VARCHAR(64)  NOT NULL,
    template_id    VARCHAR(64)  NOT NULL,
    edge_code      VARCHAR(128) NOT NULL,
    from_node_code VARCHAR(128) NOT NULL,
    to_node_code   VARCHAR(128) NOT NULL,
    edge_type      VARCHAR(32)  NOT NULL DEFAULT 'DEFAULT',
    condition_json TEXT         NULL,
    priority       INT          NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by     VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by     VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id       VARCHAR(128) NULL,
    CONSTRAINT uk_pathway_edge_template_code UNIQUE (tenant_id, template_id, edge_code),
    CONSTRAINT ck_pathway_edge_type CHECK (edge_type IN (
        'DEFAULT','CONDITION','RISK_STRATIFICATION','PATIENT_CHOICE',
        'RESOURCE_UNAVAILABLE','PHYSICIAN_DECISION','ROLLBACK','JOIN'
    ))
);

CREATE INDEX IF NOT EXISTS idx_pathway_edge_template_from ON pathway_edge (tenant_id, template_id, from_node_code, priority);
CREATE INDEX IF NOT EXISTS idx_pathway_edge_template_to   ON pathway_edge (tenant_id, template_id, to_node_code);

CREATE TABLE IF NOT EXISTS patient_pathway (
    id                 BIGSERIAL PRIMARY KEY,
    patient_pathway_id VARCHAR(64)  NOT NULL,
    tenant_id          VARCHAR(64)  NOT NULL,
    patient_id         VARCHAR(128) NOT NULL,
    encounter_id       VARCHAR(128) NULL,
    template_id        VARCHAR(64)  NOT NULL,
    current_node_code  VARCHAR(128) NULL,
    status             VARCHAR(32)  NOT NULL DEFAULT 'ENTERED',
    entered_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at       TIMESTAMPTZ  NULL,
    exited_at          TIMESTAMPTZ  NULL,
    exit_reason        VARCHAR(512) NULL,
    last_event_id      VARCHAR(64)  NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by         VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by         VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id           VARCHAR(128) NULL,
    CONSTRAINT uk_patient_pathway_id UNIQUE (patient_pathway_id),
    CONSTRAINT ck_patient_pathway_status CHECK (status IN (
        'ENTERED','NODE_EXECUTING','VARIANCE','COMPLETED','EXITED'
    ))
);

CREATE INDEX IF NOT EXISTS idx_patient_pathway_patient         ON patient_pathway (tenant_id, patient_id, entered_at);
CREATE INDEX IF NOT EXISTS idx_patient_pathway_template_status ON patient_pathway (tenant_id, template_id, status);

CREATE TABLE IF NOT EXISTS pathway_variance (
    id                 BIGSERIAL PRIMARY KEY,
    variance_id        VARCHAR(64)  NOT NULL,
    tenant_id          VARCHAR(64)  NOT NULL,
    patient_pathway_id VARCHAR(64)  NOT NULL,
    node_code          VARCHAR(128) NOT NULL,
    variance_type      VARCHAR(32)  NOT NULL,
    reason_code        VARCHAR(128) NOT NULL,
    reason             VARCHAR(1024) NOT NULL,
    responsible_role   VARCHAR(128) NOT NULL,
    resolution_decision VARCHAR(32) NOT NULL,
    resolution_action  VARCHAR(512) NULL,
    continue_node_code VARCHAR(128) NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by         VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by         VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id           VARCHAR(128) NULL,
    CONSTRAINT uk_pathway_variance_id UNIQUE (variance_id),
    CONSTRAINT ck_pathway_variance_type CHECK (variance_type IN (
        'CLINICAL','SYSTEM','PATIENT','FAMILY'
    )),
    CONSTRAINT ck_pathway_variance_resolution CHECK (resolution_decision IN (
        'HOLD','REENTER','TERMINATE'
    ))
);

CREATE INDEX IF NOT EXISTS idx_pathway_variance_pathway_time ON pathway_variance (tenant_id, patient_pathway_id, created_at);

CREATE TABLE IF NOT EXISTS clinical_clock (
    id                 BIGSERIAL PRIMARY KEY,
    clock_id           VARCHAR(64)  NOT NULL,
    tenant_id          VARCHAR(64)  NOT NULL,
    patient_pathway_id VARCHAR(64)  NOT NULL,
    node_code          VARCHAR(128) NOT NULL,
    metric_code        VARCHAR(128) NULL,
    started_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    due_at             TIMESTAMPTZ  NULL,
    completed_at       TIMESTAMPTZ  NULL,
    status             VARCHAR(32)  NOT NULL DEFAULT 'RUNNING',
    baseline_event     VARCHAR(64)  NULL,
    baseline_at        TIMESTAMPTZ  NULL,
    min_due_at         TIMESTAMPTZ  NULL,
    target_due_at      TIMESTAMPTZ  NULL,
    max_due_at         TIMESTAMPTZ  NULL,
    escalation_level   VARCHAR(32)  NOT NULL DEFAULT 'NONE',
    escalation_policy_json TEXT     NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by         VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by         VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id           VARCHAR(128) NULL,
    CONSTRAINT uk_clinical_clock_id UNIQUE (clock_id),
    CONSTRAINT ck_clinical_clock_status CHECK (status IN (
        'RUNNING','COMPLETED','TIMEOUT','MISSING_DATA','VARIANCE'
    )),
    CONSTRAINT ck_clinical_clock_escalation CHECK (escalation_level IN (
        'NONE','REMINDER','REPORT','QUALITY_RECORD'
    ))
);

CREATE INDEX IF NOT EXISTS idx_clinical_clock_pathway ON clinical_clock (tenant_id, patient_pathway_id, started_at);
CREATE INDEX IF NOT EXISTS idx_clinical_clock_due     ON clinical_clock (tenant_id, status, due_at);

CREATE TABLE IF NOT EXISTS specialty_metric_binding (
    id            BIGSERIAL PRIMARY KEY,
    binding_id    VARCHAR(64)  NOT NULL,
    tenant_id     VARCHAR(64)  NOT NULL,
    package_id    VARCHAR(64)  NOT NULL,
    template_id   VARCHAR(64)  NOT NULL,
    node_code     VARCHAR(128) NOT NULL,
    metric_code   VARCHAR(128) NOT NULL,
    required_flag BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by    VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by    VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id      VARCHAR(128) NULL,
    CONSTRAINT uk_specialty_metric_binding UNIQUE (tenant_id, package_id, template_id, node_code, metric_code),
    CONSTRAINT ck_specialty_metric_required CHECK (required_flag IN (TRUE, FALSE))
);

CREATE INDEX IF NOT EXISTS idx_specialty_metric_package  ON specialty_metric_binding (tenant_id, package_id, metric_code);
CREATE INDEX IF NOT EXISTS idx_specialty_metric_template ON specialty_metric_binding (tenant_id, template_id, node_code);

-- ===== 路径引擎中文注释（GA-ENG-API-06）=====
COMMENT ON TABLE specialty_package IS '专病包主表，保存专病路径资产的病种、版本、状态和发布来源';
COMMENT ON COLUMN specialty_package.id IS '数据库自增主键';
COMMENT ON COLUMN specialty_package.package_id IS '专病包业务 ID，用于 API 返回和路径模板关联';
COMMENT ON COLUMN specialty_package.tenant_id IS '租户 ID，用于多租户数据隔离';
COMMENT ON COLUMN specialty_package.package_code IS '专病包编码，同一租户同版本唯一';
COMMENT ON COLUMN specialty_package.disease_code IS '病种编码，用于按疾病或专病范围检索路径资产';
COMMENT ON COLUMN specialty_package.name IS '专病包名称';
COMMENT ON COLUMN specialty_package.package_version IS '专病包版本号';
COMMENT ON COLUMN specialty_package.status IS '专病包状态：DRAFT 草稿、PUBLISHED 已发布、OFFLINE 已下线、ARCHIVED 已归档';
COMMENT ON COLUMN specialty_package.source_ref IS '专病包来源引用，记录指南、规范或内部版本来源';
COMMENT ON COLUMN specialty_package.description IS '专病包说明';
COMMENT ON COLUMN specialty_package.published_at IS '专病包发布时间';
COMMENT ON COLUMN specialty_package.published_by IS '专病包发布人';
COMMENT ON COLUMN specialty_package.created_at IS '创建时间';
COMMENT ON COLUMN specialty_package.created_by IS '创建人';
COMMENT ON COLUMN specialty_package.updated_at IS '更新时间';
COMMENT ON COLUMN specialty_package.updated_by IS '更新人';
COMMENT ON COLUMN specialty_package.trace_id IS '请求链路追踪 ID';

COMMENT ON TABLE specialty_profile IS '专病画像表，保存专病包下的分型、风险分层、准入退出和随访计划摘要';
COMMENT ON COLUMN specialty_profile.id IS '数据库自增主键';
COMMENT ON COLUMN specialty_profile.profile_id IS '专病画像业务 ID';
COMMENT ON COLUMN specialty_profile.tenant_id IS '租户 ID，用于多租户数据隔离';
COMMENT ON COLUMN specialty_profile.package_id IS '关联专病包业务 ID';
COMMENT ON COLUMN specialty_profile.profile_code IS '专病画像编码，同一专病包内唯一';
COMMENT ON COLUMN specialty_profile.name IS '专病画像名称';
COMMENT ON COLUMN specialty_profile.stratification_json IS '风险分层或分型摘要 JSON';
COMMENT ON COLUMN specialty_profile.entry_criteria_json IS '入径准入条件摘要 JSON';
COMMENT ON COLUMN specialty_profile.exit_criteria_json IS '退出条件摘要 JSON';
COMMENT ON COLUMN specialty_profile.followup_plan_json IS '随访计划摘要 JSON';
COMMENT ON COLUMN specialty_profile.created_at IS '创建时间';
COMMENT ON COLUMN specialty_profile.created_by IS '创建人';
COMMENT ON COLUMN specialty_profile.updated_at IS '更新时间';
COMMENT ON COLUMN specialty_profile.updated_by IS '更新人';
COMMENT ON COLUMN specialty_profile.trace_id IS '请求链路追踪 ID';

COMMENT ON TABLE pathway_template IS '路径模板主表，保存专病路径模板的编码、版本、层级、状态和起始节点';
COMMENT ON COLUMN pathway_template.id IS '数据库自增主键';
COMMENT ON COLUMN pathway_template.template_id IS '路径模板业务 ID，用于 API 返回、节点边关联和患者入径';
COMMENT ON COLUMN pathway_template.tenant_id IS '租户 ID，用于多租户数据隔离';
COMMENT ON COLUMN pathway_template.package_id IS '关联专病包业务 ID';
COMMENT ON COLUMN pathway_template.template_code IS '路径模板编码，同一租户同版本唯一';
COMMENT ON COLUMN pathway_template.name IS '路径模板名称';
COMMENT ON COLUMN pathway_template.disease_code IS '病种编码，用于按疾病或专病范围检索模板';
COMMENT ON COLUMN pathway_template.template_version IS '路径模板版本号';
COMMENT ON COLUMN pathway_template.template_level IS '模板层级：STANDARD 标准版、GROUP 集团版、HOSPITAL 医院版、DEPARTMENT 科室版、SPECIALTY 专科版';
COMMENT ON COLUMN pathway_template.status IS '模板状态：DRAFT 草稿、PUBLISHED 已发布、OFFLINE 已下线、ARCHIVED 已归档';
COMMENT ON COLUMN pathway_template.entry_mode IS '入径模式：AUTO_SUGGEST 自动建议入径、MANUAL_CONFIRM 人工确认入径';
COMMENT ON COLUMN pathway_template.start_node_code IS '起始节点编码，患者入径时默认进入该节点';
COMMENT ON COLUMN pathway_template.source_ref IS '路径模板来源引用';
COMMENT ON COLUMN pathway_template.description IS '路径模板说明';
COMMENT ON COLUMN pathway_template.entry_criteria_json IS '路径准入条件摘要 JSON';
COMMENT ON COLUMN pathway_template.exit_criteria_json IS '路径退出条件摘要 JSON';
COMMENT ON COLUMN pathway_template.created_at IS '创建时间';
COMMENT ON COLUMN pathway_template.created_by IS '创建人';
COMMENT ON COLUMN pathway_template.updated_at IS '更新时间';
COMMENT ON COLUMN pathway_template.updated_by IS '更新人';
COMMENT ON COLUMN pathway_template.trace_id IS '请求链路追踪 ID';

COMMENT ON TABLE pathway_milestone IS '路径里程碑表，保存模板阶段、天序、预期完成点和达成判定条件';
COMMENT ON COLUMN pathway_milestone.id IS '数据库自增主键';
COMMENT ON COLUMN pathway_milestone.milestone_id IS '路径里程碑业务 ID';
COMMENT ON COLUMN pathway_milestone.tenant_id IS '租户 ID，用于多租户数据隔离';
COMMENT ON COLUMN pathway_milestone.template_id IS '关联路径模板业务 ID';
COMMENT ON COLUMN pathway_milestone.phase_code IS '阶段编码，同一路径模板内用于阶段分组';
COMMENT ON COLUMN pathway_milestone.phase_name IS '阶段名称';
COMMENT ON COLUMN pathway_milestone.milestone_code IS '里程碑编码，同一路径模板内唯一';
COMMENT ON COLUMN pathway_milestone.name IS '里程碑名称';
COMMENT ON COLUMN pathway_milestone.day_offset IS '相对入径日的天序，0 表示入径当天';
COMMENT ON COLUMN pathway_milestone.expected_offset_minutes IS '相对入径时间的预期完成分钟数';
COMMENT ON COLUMN pathway_milestone.achievement_criteria_json IS '里程碑达成判定条件 JSON';
COMMENT ON COLUMN pathway_milestone.sort_order IS '阶段里程碑展示排序';
COMMENT ON COLUMN pathway_milestone.created_at IS '创建时间';
COMMENT ON COLUMN pathway_milestone.created_by IS '创建人';
COMMENT ON COLUMN pathway_milestone.updated_at IS '更新时间';
COMMENT ON COLUMN pathway_milestone.updated_by IS '更新人';
COMMENT ON COLUMN pathway_milestone.trace_id IS '请求链路追踪 ID';

COMMENT ON TABLE pathway_node IS '路径节点表，保存模板中的临床步骤、所属里程碑、RACI 角色、依赖条件、时间窗和终止标记';
COMMENT ON COLUMN pathway_node.id IS '数据库自增主键';
COMMENT ON COLUMN pathway_node.node_id IS '路径节点业务 ID';
COMMENT ON COLUMN pathway_node.tenant_id IS '租户 ID，用于多租户数据隔离';
COMMENT ON COLUMN pathway_node.template_id IS '关联路径模板业务 ID';
COMMENT ON COLUMN pathway_node.node_code IS '节点编码，同一模板内唯一';
COMMENT ON COLUMN pathway_node.name IS '节点名称';
COMMENT ON COLUMN pathway_node.node_type IS '节点类型：SCREENING 筛查、ASSESSMENT 评估、EXAM 检查、LAB 检验、MEDICATION 用药、SURGERY 手术、NURSING 护理、REHAB 康复、DISCHARGE 出院、FOLLOWUP 随访、QUALITY 质控、DECISION 决策、PARALLEL 并行、WAIT_TIMER 等待计时、SUBPATHWAY 子路径、MANUAL_GATE 人工闸门、ORDER_SET 医嘱集';
COMMENT ON COLUMN pathway_node.milestone_code IS '节点所属里程碑编码，用于阶段天序视图和达成判定';
COMMENT ON COLUMN pathway_node.sort_order IS '节点展示或执行排序';
COMMENT ON COLUMN pathway_node.responsible_role IS '节点责任角色';
COMMENT ON COLUMN pathway_node.accountable_role IS '节点签责角色';
COMMENT ON COLUMN pathway_node.consulted_roles_json IS '节点会诊角色列表 JSON';
COMMENT ON COLUMN pathway_node.informed_roles_json IS '节点知会角色列表 JSON';
COMMENT ON COLUMN pathway_node.dependency_json IS '节点依赖条件摘要 JSON';
COMMENT ON COLUMN pathway_node.time_window_minutes IS '节点建议完成时间窗，单位分钟';
COMMENT ON COLUMN pathway_node.terminal_flag IS '是否终止节点';
COMMENT ON COLUMN pathway_node.config_json IS '节点配置摘要 JSON';
COMMENT ON COLUMN pathway_node.created_at IS '创建时间';
COMMENT ON COLUMN pathway_node.created_by IS '创建人';
COMMENT ON COLUMN pathway_node.updated_at IS '更新时间';
COMMENT ON COLUMN pathway_node.updated_by IS '更新人';
COMMENT ON COLUMN pathway_node.trace_id IS '请求链路追踪 ID';

COMMENT ON TABLE pathway_edge IS '路径边表，保存模板节点之间的可达关系、分支类型、条件摘要和优先级';
COMMENT ON COLUMN pathway_edge.id IS '数据库自增主键';
COMMENT ON COLUMN pathway_edge.edge_id IS '路径边业务 ID';
COMMENT ON COLUMN pathway_edge.tenant_id IS '租户 ID，用于多租户数据隔离';
COMMENT ON COLUMN pathway_edge.template_id IS '关联路径模板业务 ID';
COMMENT ON COLUMN pathway_edge.edge_code IS '路径边编码，同一模板内唯一';
COMMENT ON COLUMN pathway_edge.from_node_code IS '源节点编码';
COMMENT ON COLUMN pathway_edge.to_node_code IS '目标节点编码';
COMMENT ON COLUMN pathway_edge.edge_type IS '路径边类型：DEFAULT 默认、CONDITION 条件、RISK_STRATIFICATION 风险分层、PATIENT_CHOICE 患者选择、RESOURCE_UNAVAILABLE 资源不可用、PHYSICIAN_DECISION 医生决策、ROLLBACK 回退、JOIN 并行汇合';
COMMENT ON COLUMN pathway_edge.condition_json IS '分支条件摘要 JSON';
COMMENT ON COLUMN pathway_edge.priority IS '同一源节点出边优先级，数值越小越优先';
COMMENT ON COLUMN pathway_edge.created_at IS '创建时间';
COMMENT ON COLUMN pathway_edge.created_by IS '创建人';
COMMENT ON COLUMN pathway_edge.updated_at IS '更新时间';
COMMENT ON COLUMN pathway_edge.updated_by IS '更新人';
COMMENT ON COLUMN pathway_edge.trace_id IS '请求链路追踪 ID';

COMMENT ON TABLE patient_pathway IS '患者路径实例表，保存患者入径后的当前节点、运行状态、完成或退出事实';
COMMENT ON COLUMN patient_pathway.id IS '数据库自增主键';
COMMENT ON COLUMN patient_pathway.patient_pathway_id IS '患者路径实例业务 ID';
COMMENT ON COLUMN patient_pathway.tenant_id IS '租户 ID，用于多租户数据隔离';
COMMENT ON COLUMN patient_pathway.patient_id IS '患者 ID，仅作路径实例引用，不保存完整病历原文';
COMMENT ON COLUMN patient_pathway.encounter_id IS '就诊 ID，用于关联一次门诊、住院或随访场景';
COMMENT ON COLUMN patient_pathway.template_id IS '关联路径模板业务 ID';
COMMENT ON COLUMN patient_pathway.current_node_code IS '当前执行节点编码';
COMMENT ON COLUMN patient_pathway.status IS '患者路径状态：ENTERED 已入径、NODE_EXECUTING 节点执行中、VARIANCE 变异待处理、COMPLETED 已完成、EXITED 已退出';
COMMENT ON COLUMN patient_pathway.entered_at IS '患者入径时间';
COMMENT ON COLUMN patient_pathway.completed_at IS '路径完成时间';
COMMENT ON COLUMN patient_pathway.exited_at IS '路径退出时间';
COMMENT ON COLUMN patient_pathway.exit_reason IS '路径退出原因';
COMMENT ON COLUMN patient_pathway.last_event_id IS '最后一次推进事件 ID，用于外部幂等或事件追溯';
COMMENT ON COLUMN patient_pathway.created_at IS '创建时间';
COMMENT ON COLUMN patient_pathway.created_by IS '创建人';
COMMENT ON COLUMN patient_pathway.updated_at IS '更新时间';
COMMENT ON COLUMN patient_pathway.updated_by IS '更新人';
COMMENT ON COLUMN patient_pathway.trace_id IS '请求链路追踪 ID';

COMMENT ON TABLE pathway_variance IS '路径变异记录表，保存患者路径执行偏离的分类、原因码、责任角色、处置决策和继续节点';
COMMENT ON COLUMN pathway_variance.id IS '数据库自增主键';
COMMENT ON COLUMN pathway_variance.variance_id IS '路径变异业务 ID';
COMMENT ON COLUMN pathway_variance.tenant_id IS '租户 ID，用于多租户数据隔离';
COMMENT ON COLUMN pathway_variance.patient_pathway_id IS '关联患者路径实例业务 ID';
COMMENT ON COLUMN pathway_variance.node_code IS '发生变异的节点编码';
COMMENT ON COLUMN pathway_variance.variance_type IS '变异分类：CLINICAL 临床、SYSTEM 系统、PATIENT 患者、FAMILY 家属';
COMMENT ON COLUMN pathway_variance.reason_code IS '变异原因码';
COMMENT ON COLUMN pathway_variance.reason IS '变异原因说明';
COMMENT ON COLUMN pathway_variance.responsible_role IS '变异责任角色';
COMMENT ON COLUMN pathway_variance.resolution_decision IS '变异处置决策：HOLD 暂停观察、REENTER 再入径、TERMINATE 终止路径';
COMMENT ON COLUMN pathway_variance.resolution_action IS '变异处置动作';
COMMENT ON COLUMN pathway_variance.continue_node_code IS '变异处理后继续进入的节点编码';
COMMENT ON COLUMN pathway_variance.created_at IS '创建时间';
COMMENT ON COLUMN pathway_variance.created_by IS '创建人';
COMMENT ON COLUMN pathway_variance.updated_at IS '更新时间';
COMMENT ON COLUMN pathway_variance.updated_by IS '更新人';
COMMENT ON COLUMN pathway_variance.trace_id IS '请求链路追踪 ID';

COMMENT ON TABLE clinical_clock IS '关键时钟表，保存患者路径节点的开始、SLA时限、完成和质控指标关联事实';
COMMENT ON COLUMN clinical_clock.id IS '数据库自增主键';
COMMENT ON COLUMN clinical_clock.clock_id IS '关键时钟业务 ID';
COMMENT ON COLUMN clinical_clock.tenant_id IS '租户 ID，用于多租户数据隔离';
COMMENT ON COLUMN clinical_clock.patient_pathway_id IS '关联患者路径实例业务 ID';
COMMENT ON COLUMN clinical_clock.node_code IS '关键时钟所属节点编码';
COMMENT ON COLUMN clinical_clock.metric_code IS '关联质控指标编码';
COMMENT ON COLUMN clinical_clock.started_at IS '关键时钟开始时间';
COMMENT ON COLUMN clinical_clock.due_at IS '关键时钟到期时间';
COMMENT ON COLUMN clinical_clock.completed_at IS '关键时钟完成时间';
COMMENT ON COLUMN clinical_clock.status IS '关键时钟状态：RUNNING 运行中、COMPLETED 已完成、TIMEOUT 已超时、MISSING_DATA 缺少数据、VARIANCE 变异暂停';
COMMENT ON COLUMN clinical_clock.baseline_event IS '临床时钟SLA基准事件：NODE_START 节点开始、PATHWAY_ENTRY 入径、ADMISSION 入院';
COMMENT ON COLUMN clinical_clock.baseline_at IS '临床时钟SLA基准时间';
COMMENT ON COLUMN clinical_clock.min_due_at IS '临床时钟SLA最早允许时间';
COMMENT ON COLUMN clinical_clock.target_due_at IS '临床时钟SLA目标时间';
COMMENT ON COLUMN clinical_clock.max_due_at IS '临床时钟SLA最晚时间';
COMMENT ON COLUMN clinical_clock.escalation_level IS '临床时钟超时升级级别：NONE 无、REMINDER 提醒、REPORT 上报、QUALITY_RECORD 质控记录';
COMMENT ON COLUMN clinical_clock.escalation_policy_json IS '临床时钟超时升级策略JSON';
COMMENT ON COLUMN clinical_clock.created_at IS '创建时间';
COMMENT ON COLUMN clinical_clock.created_by IS '创建人';
COMMENT ON COLUMN clinical_clock.updated_at IS '更新时间';
COMMENT ON COLUMN clinical_clock.updated_by IS '更新人';
COMMENT ON COLUMN clinical_clock.trace_id IS '请求链路追踪 ID';

COMMENT ON TABLE specialty_metric_binding IS '专病指标绑定表，保存专病包、路径模板、节点和质控指标之间的关联';
COMMENT ON COLUMN specialty_metric_binding.id IS '数据库自增主键';
COMMENT ON COLUMN specialty_metric_binding.binding_id IS '专病指标绑定业务 ID';
COMMENT ON COLUMN specialty_metric_binding.tenant_id IS '租户 ID，用于多租户数据隔离';
COMMENT ON COLUMN specialty_metric_binding.package_id IS '关联专病包业务 ID';
COMMENT ON COLUMN specialty_metric_binding.template_id IS '关联路径模板业务 ID';
COMMENT ON COLUMN specialty_metric_binding.node_code IS '关联路径节点编码';
COMMENT ON COLUMN specialty_metric_binding.metric_code IS '质控指标编码';
COMMENT ON COLUMN specialty_metric_binding.required_flag IS '是否必填指标';
COMMENT ON COLUMN specialty_metric_binding.created_at IS '创建时间';
COMMENT ON COLUMN specialty_metric_binding.created_by IS '创建人';
COMMENT ON COLUMN specialty_metric_binding.updated_at IS '更新时间';
COMMENT ON COLUMN specialty_metric_binding.updated_by IS '更新人';
COMMENT ON COLUMN specialty_metric_binding.trace_id IS '请求链路追踪 ID';
