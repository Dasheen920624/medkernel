# 设计附录 B：数据模型（DDL）与 API 面（可实现版）

> 关联：`design.md`、`design-dsl-grammar.md`。
> 列约定对齐既有实体（如 `rule_definition`）：业务主键用字符串 `*_id`，统一携带 `tenant_id`、`package_version`、`created_at/by`、`updated_at/by`、`trace_id`；持久层为 Spring Data JDBC（`@Table`/`@Column`）。所有新表 SHALL 带 `tenant_id` 并纳入租户隔离；退役/下线用状态位封存，不物理删除。
>
> **多方言迁移（重要落地约束）**：迁移目录为 `medkernel-backend/src/main/resources/db/migration/{dm,h2,kingbase,oracle,postgres}`，当前最新为 `V58`。本变更新增 DDL SHALL 从 **`V59` 起为五种方言各提供一份**（达梦 DM、人大金仓 KingBase、Oracle、Postgres、H2），类型按方言适配（如 `CLOB`/`TEXT`/`NCLOB`、`TIMESTAMP`、`NUMBER`/`NUMERIC`/`BOOLEAN`）。下方 DDL 为方言中立示意，落地需逐方言落版本号一致的脚本。
>
> **错误码约定**：沿用 `ENG-<域>-NNN`（如 `ENG-RULE-001`/`ENG-PATHWAY-006`/`ENG-CONTEXT-003`）。本变更新增建议：`ENG-RULE-007` 临床算子/公式入参非法、`ENG-RULE-008` 单位不可换算、`ENG-CONTEXT-005` 字段目录字段不存在于 canonical、`ENG-CONTEXT-006` 值集展开失败/版本缺失、`ENG-TERM-003` 对照覆盖率不达标（发布门禁）。新增码 SHALL 登记到 `ErrorCode` 枚举单一真相。
>
> **版本一致性**：规则/路径引用的字段目录、值集、CodeSystem、对外契约 SHALL 同属一个 `package_version`；运行期与发布门禁 SHALL 校验版本一致，跨版本引用 SHALL 拒绝（`ENG-CONTEXT-002` 包版本不存在 / 自定义校验）。

---

## B1. 上下文字段目录与术语值集

```sql
-- 字段目录：从 canonical 派生，前台可补充元数据
CREATE TABLE context_field_catalog (
  id              BIGINT PRIMARY KEY,
  field_id        VARCHAR(64)  NOT NULL,
  tenant_id       VARCHAR(64)  NOT NULL,
  resource_type   VARCHAR(40)  NOT NULL,   -- Observation/Condition/Medication/...
  field_path      VARCHAR(200) NOT NULL,   -- observations[].valueNumeric
  display_name    VARCHAR(200) NOT NULL,
  data_type       VARCHAR(20)  NOT NULL,   -- number/string/boolean/date/code/list
  unit            VARCHAR(40),
  ref_range_avail BOOLEAN      NOT NULL DEFAULT FALSE,
  value_set_code  VARCHAR(64),             -- 编码字段绑定的值集
  description     VARCHAR(500),
  status          VARCHAR(20)  NOT NULL,   -- ACTIVE/DEPRECATED
  package_version VARCHAR(40)  NOT NULL,
  created_at TIMESTAMP, created_by VARCHAR(64),
  updated_at TIMESTAMP, updated_by VARCHAR(64), trace_id VARCHAR(64),
  CONSTRAINT uk_field UNIQUE (tenant_id, field_path, package_version)
);

-- 值集（对齐 FHIR ValueSet：外延/内涵）
CREATE TABLE value_set (
  id BIGINT PRIMARY KEY,
  value_set_id   VARCHAR(64) NOT NULL,
  tenant_id      VARCHAR(64) NOT NULL,
  value_set_code VARCHAR(64) NOT NULL,
  name           VARCHAR(200) NOT NULL,
  definition_kind VARCHAR(20) NOT NULL,    -- EXTENSIONAL/INTENSIONAL
  code_system    VARCHAR(64),              -- 内涵：基于哪个 CodeSystem
  intensional_filter CLOB,                 -- 内涵过滤规则(JSON)：is-a / in / regex 等
  status         VARCHAR(20) NOT NULL,
  package_version VARCHAR(40) NOT NULL,
  created_at TIMESTAMP, created_by VARCHAR(64),
  updated_at TIMESTAMP, updated_by VARCHAR(64), trace_id VARCHAR(64),
  CONSTRAINT uk_vs UNIQUE (tenant_id, value_set_code, package_version)
);

-- 值集外延成员 / 展开缓存
CREATE TABLE value_set_member (
  id BIGINT PRIMARY KEY,
  value_set_id VARCHAR(64) NOT NULL,
  tenant_id    VARCHAR(64) NOT NULL,
  code         VARCHAR(64) NOT NULL,
  code_system  VARCHAR(64) NOT NULL,
  display_name VARCHAR(200),
  source       VARCHAR(20) NOT NULL,       -- EXPLICIT/EXPANDED
  package_version VARCHAR(40) NOT NULL
);

-- CodeSystem 版本（ICD-10 临床/医保版、ICD-9-CM-3、LOINC、ATC、医保编码；SNOMED 子集置于开关后）
CREATE TABLE code_system_version (
  id BIGINT PRIMARY KEY,
  code_system   VARCHAR(64) NOT NULL,
  system_version VARCHAR(40) NOT NULL,
  package_version VARCHAR(40) NOT NULL,
  status VARCHAR(20) NOT NULL,
  CONSTRAINT uk_cs UNIQUE (code_system, system_version)
);

-- 结构化参考范围（决策 H6-2；按人群分版，解析器兜底）
CREATE TABLE reference_range (
  id BIGINT PRIMARY KEY, tenant_id VARCHAR(64) NOT NULL,
  field_path VARCHAR(200) NOT NULL,      -- 关联字段目录
  population_json CLOB,                  -- 年龄/性别/妊娠等人群过滤（条件文法）
  low NUMERIC(18,6), high NUMERIC(18,6), unit VARCHAR(40),
  qualifier VARCHAR(40),                 -- 如 阴性/阳性 等非数值参考
  source VARCHAR(200), package_version VARCHAR(40) NOT NULL
);

-- 单位换算（决策 H6-3；表驱动，质量↔摩尔靠 analyte 摩尔质量）
CREATE TABLE unit_conversion (
  id BIGINT PRIMARY KEY,
  from_unit VARCHAR(40) NOT NULL, to_unit VARCHAR(40) NOT NULL,
  factor NUMERIC(24,10) NOT NULL,        -- to = from × factor（线性）
  requires_molar_mass BOOLEAN NOT NULL DEFAULT FALSE,
  package_version VARCHAR(40) NOT NULL,
  CONSTRAINT uk_uc UNIQUE (from_unit, to_unit, package_version)
);
```
> analyte 摩尔质量随字段目录/值集元数据维护（如肌酐 113.12 g/mol）；查不到换算关系即拒绝求值（`ENG-RULE-008`）。

> 院内↔标准对照**复用既有 terminology 域**真实类：`LocalTerm`、`StandardTerm`、`TermMapping`、`TermMappingPackage`/`Release`（已版本化）、`SemanticTermMatcher`、`HighRiskTermDetector`、`MappingConflict`。值集内涵展开基于 `StandardTerm` + `code_system_version`，不另起并行事实源。

---

## B2. 规则治理扩展（加法式，不改既有 rule_definition/rule_version）

```sql
-- 受控临床公式注册表（白名单，禁止运行期任意表达式）
CREATE TABLE clinical_function (
  id BIGINT PRIMARY KEY,
  fn_name VARCHAR(64) NOT NULL,          -- eGFR/CrCl/BSA/BMI/...
  variant VARCHAR(40),                   -- CKD-EPI/MDRD/Mosteller/...
  input_spec CLOB NOT NULL,              -- 入参字段与单位要求(JSON)
  population_note VARCHAR(500), source_ref VARCHAR(300),
  status VARCHAR(20) NOT NULL, package_version VARCHAR(40) NOT NULL,
  CONSTRAINT uk_fn UNIQUE (fn_name, variant, package_version)
);

-- 规则适用域（1:1 扩展 rule_version；DSL 内 applicability 的落库镜像，便于检索）
CREATE TABLE rule_applicability (
  id BIGINT PRIMARY KEY,
  rule_version_id VARCHAR(64) NOT NULL, tenant_id VARCHAR(64) NOT NULL,
  settings VARCHAR(120),                 -- INPATIENT,ED,...
  effective_from DATE, effective_to DATE, rollout_percent INT,
  population_json CLOB                    -- include/exclude 条件
);

-- 知识治理状态与会签
CREATE TABLE rule_governance (
  id BIGINT PRIMARY KEY,
  rule_version_id VARCHAR(64) NOT NULL, tenant_id VARCHAR(64) NOT NULL,
  governance_state VARCHAR(30) NOT NULL, -- DRAFT/PEER_REVIEW/COMMITTEE/SHADOW/CANARY/FULL/MONITOR/RETIRED
  required_signoffs INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP, updated_at TIMESTAMP, trace_id VARCHAR(64)
);
CREATE TABLE rule_signoff (
  id BIGINT PRIMARY KEY, rule_version_id VARCHAR(64) NOT NULL, tenant_id VARCHAR(64) NOT NULL,
  signer_role VARCHAR(64) NOT NULL, signer_id VARCHAR(64) NOT NULL,
  decision VARCHAR(20) NOT NULL, reason VARCHAR(500), signed_at TIMESTAMP
);

-- 影子/回测运行结果（基于真实脱敏快照）
CREATE TABLE rule_evaluation_eval_run (
  id BIGINT PRIMARY KEY, rule_version_id VARCHAR(64) NOT NULL, tenant_id VARCHAR(64) NOT NULL,
  run_mode VARCHAR(20) NOT NULL,          -- SHADOW/BACKTEST
  cohort_ref VARCHAR(120), sensitivity NUMERIC(5,4), specificity NUMERIC(5,4),
  fire_rate NUMERIC(5,4), false_positive_count INT, sample_count INT,
  created_at TIMESTAMP, trace_id VARCHAR(64)
);

-- 越权留痕（运行期，喂质量指标）
CREATE TABLE rule_override_log (
  id BIGINT PRIMARY KEY, tenant_id VARCHAR(64) NOT NULL, rule_id VARCHAR(64) NOT NULL,
  patient_id VARCHAR(64), encounter_id VARCHAR(64), action_code VARCHAR(40),
  override_reason VARCHAR(500) NOT NULL, overridden_by VARCHAR(64), overridden_at TIMESTAMP,
  trace_id VARCHAR(64)
);
```

> 分级动作卡片随 `rule_version.dsl_json` 的 `then[]` 存储（DSL 内表达），无需独立动作表。规则交互（优先级/抑制）以 `rule_definition` 新增 `priority`、`suppressed_by` 列承载。

---

## B3. 路径领域模型扩展（加法式扩展既有 pathway 表）

```sql
CREATE TABLE pathway_phase (
  id BIGINT PRIMARY KEY, phase_id VARCHAR(64) NOT NULL, tenant_id VARCHAR(64) NOT NULL,
  template_id VARCHAR(64) NOT NULL, phase_code VARCHAR(64) NOT NULL, name VARCHAR(200) NOT NULL,
  day_index INT, sort_order INT, package_version VARCHAR(40) NOT NULL
);
CREATE TABLE pathway_milestone (
  id BIGINT PRIMARY KEY, milestone_id VARCHAR(64) NOT NULL, tenant_id VARCHAR(64) NOT NULL,
  phase_id VARCHAR(64) NOT NULL, milestone_code VARCHAR(64) NOT NULL, name VARCHAR(200) NOT NULL,
  due_json CLOB,                          -- ClinicalClock
  achieve_when_json CLOB,                 -- 达成判定（条件文法）
  package_version VARCHAR(40) NOT NULL
);
-- pathway_node 扩展列：node_type 扩充枚举；新增 phase_code/roles_json/order_set_ref/sub_pathway_ref/clock_json
-- pathway_edge 扩展列：guard_json（替代/兼容既有 condition_json，向后兼容见附录 A1）
CREATE TABLE pathway_variance (
  id BIGINT PRIMARY KEY, variance_id VARCHAR(64) NOT NULL, tenant_id VARCHAR(64) NOT NULL,
  patient_pathway_id VARCHAR(64) NOT NULL, at_node_code VARCHAR(64),
  category VARCHAR(30) NOT NULL,          -- CLINICAL/SYSTEM/PATIENT/FAMILY
  reason_code VARCHAR(64), reason_text VARCHAR(500), responsible_role VARCHAR(64),
  resolution VARCHAR(30),                 -- REENTRY/TERMINATE/CONTINUE
  created_at TIMESTAMP, created_by VARCHAR(64), trace_id VARCHAR(64)
);
CREATE TABLE pathway_outcome_binding (
  id BIGINT PRIMARY KEY, tenant_id VARCHAR(64) NOT NULL, template_id VARCHAR(64) NOT NULL,
  scope VARCHAR(20) NOT NULL,             -- PHASE/MILESTONE/TEMPLATE
  ref_code VARCHAR(64), indicator_code VARCHAR(64) NOT NULL, package_version VARCHAR(40) NOT NULL
);
```

> 患者路径实例复用既有 `patient_pathway`/状态机；多级模板继承通过 `template_level` + 继承解析在服务层合并（diff 视图），不新增并行表。结局指标 `indicator_code` 对接 `EvaluationIndicator`。

---

## B4. API 面（新增/扩展）

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/context/field-catalog` | 查询字段目录（resourceType/keyword/packageVersion） |
| POST/PUT | `/context/field-catalog[/{id}]` | 维护字段元数据/单位/值集绑定（RBAC+审计，仅派生集合内补充） |
| GET | `/terminology/value-sets` | 值集列表/详情 |
| POST | `/terminology/value-sets/{code}/$expand` | 展开成员 |
| POST | `/terminology/value-sets/{code}/$validate-code` | 成员校验 |
| POST | `/terminology/value-sets/{code}/$subsumes` | 上下位判定 |
| GET | `/terminology/mappings/coverage` | 院内↔标准对照覆盖率（供发布门禁） |
| GET | `/rules/clinical-functions` | 受控公式库清单（供 L2 选择器） |
| POST | `/rules/{id}/backtest` | 对历史脱敏快照集回测（灵敏度/特异度） |
| POST | `/rules/{id}/shadow` | 进入/退出影子运行 |
| POST | `/rules/{id}/signoff` | 临床委员会会签 |
| POST | `/rules/{id}/override` | 运行期越权留痕 |
| GET | `/pathways/{id}/inheritance-diff` | 多级模板继承差异 |
| POST | `/pathways/{id}/variance` | 记录路径变异 |
| POST | `/pathways/{id}/simulate` | 单快照/队列/时光机仿真（扩展既有 simulate） |
| GET | `/integration/data-contract?packageVersion=` | 对外数据接入契约（字段+值集，JSON Schema 风格） |

> 既有规则/路径创建、发布、回滚、影响摘要、求值接口保持兼容；新增能力以加法式端点暴露，旧客户端不受影响。
