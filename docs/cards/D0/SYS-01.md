# SYS-01 · 标准临床模型与事件上下文

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D0 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §7.4 标准临床模型（L1378）· 核心 §10 集成互操作（FHIR/CDS Hooks）· 落地规划 §9.3 数据域。

## 身份
- 卡 ID：SYS-01
- 域：D0 登录域 / 平台脊柱
- 关联场景：横切（所有临床域的共享类型与事件上下文）
- 依赖卡：[BASE-01](BASE-01.md)（组织/版本字段）· [BASE-05](BASE-05.md)（迁移）
- 工作量：5d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标

定义**12 类标准临床对象 + 标准事件上下文**：作为所有临床域（D3 及之后）共享的统一类型骨架，FHIR R4 兼容，唯一权威源关系库，事件上下文驱动规则/路径/CDSS 引擎。D0 只立类型与上下文，不做业务逻辑。

## 功能要求（原子可测条目）

- [x] **FR-1 12 类标准对象**：定义 Patient / Encounter / Condition / Observation / Medication / Procedure / DiagnosticReport / Document / NursingAssessment / CarePlan / FollowUp / Claim 标准对象，FHIR R4 兼容字段口径。
- [x] **FR-2 标准事件上下文**：定义临床事件触发引擎的统一上下文 `ClinicalEventContext`（患者 + 就诊 + 组织 + 时间 + 触发源），供规则/路径/CDSS 同源消费。
- [x] **FR-3 关系库权威**：12 对象唯一权威源为院内关系库（核心 #5）；图/Dify 仅投影，可重建（[SYS-03](SYS-03.md)）。
- [x] **FR-4 字典映射锚点**：12 对象的编码字段（诊断/检验/药品…）提供标准↔院内字典映射锚点（D2 TERM 卡消费）。
- [x] **FR-5 组织/版本字段**：12 对象带 `tenant_id` + `org_path` + 审计字段（呼应 [BASE-01](BASE-01.md)）。
- [x] **FR-6 事件驱动**：临床事件 → `ClinicalEventContext` → 引擎入口（规则/路径/CDSS/质控）取同一上下文。

## 接口契约 / 页面契约
### 接口契约
- 端点：本卡定义标准对象类型 + 事件上下文契约；标准上下文 API（读取患者/就诊快照）在 D2 [API-01](../D2/API-01.md) 承接。
- DTO：12 标准对象 Record + `ClinicalEventContext` Record + Bean Validation。
- 响应信封：`ApiResult`。
- 状态机：N·A —— 标准对象是临床事实主数据（其业务状态在各域）。
- 幂等 / 错误码 / traceId：事件上下文带 traceId（[OBS-01](OBS-01.md)）。

### 页面契约
N·A —— 无页面。患者主索引等页在 D3 消费本类型。

## 数据与迁移
- 表族：`mk_clinical_patient` / `mk_clinical_encounter` / `mk_clinical_condition` / `mk_clinical_observation` / `mk_clinical_medication` / `mk_clinical_procedure` / `mk_clinical_diagnostic_report` / `mk_clinical_document` / `mk_clinical_nursing_assessment` / `mk_clinical_care_plan` / `mk_clinical_follow_up` / `mk_clinical_claim`。
- 命名说明：早期裸表名已被 [BASE-05](BASE-05.md) 的迁移规约门禁替换为 `mk_<域>_<实体>`，后续实现不得新增无 `mk_` 前缀的标准临床对象权威表。
- 主键：ULID；唯一约束：`(tenant_id, source_system, source_id)`（外部来源去重）；索引：`patient_id`、`encounter_id`、`org_path`、编码字段。
- 组织字段：全 12 表带 `tenant_id` + `org_path` + 审计字段；FHIR 资源 id 映射列。
- 5 方言迁移：h2/postgres/oracle/dm/kingbase + 中文注释 + 编码字段索引。

## 视角清单（11 视角逐条）
1. **产品架构**：★12 标准对象是全临床域共享类型单一源；各域禁自定义平行患者/就诊模型。
2. **产品体验**：N·A —— 患者主索引/路径页在 D3 消费。
3. **系统与数据架构**：★本卡主战场 —— FHIR R4 兼容 12 对象 + 事件上下文 + 关系库权威；10 万级患者/就诊索引。
4. **临床医疗安全**：标准对象字段口径统一防"同字段异义"致临床误判；危急值/过敏/禁忌等关键字段标准化（核心 §6）。
5. **知识与数据治理**：编码字段挂标准字典映射锚点（核心 §7 字典语义映射），支撑唯一权威。
6. **安全合规与监管**：患者敏感字段脱敏 + 字段级加密（核心 §8 / #8）。
7. **集团化与多租户治理**：12 对象带组织维，跨院数据隔离（[BASE-01](BASE-01.md)）。
8. **集成与互操作**：★FHIR R4 兼容 + CDS Hooks 门面（核心 §10）；外部系统（HIS/EMR/LIS/PACS）经适配器映射入 12 对象（D2 INTEG 卡）。
9. **运维 / SRE / 国产化**：5 方言；高基数临床表索引与分区策略。
10. **质量与真实性审计**：无写死病种/编码（核心 #18）；标准对象不含 mock 患者。
11. **AI / 模型治理与可降级**：12 对象 + 事件上下文是 AI 增强的 B0 输入底座；无模型时规则/路径仍消费同一上下文（核心 §11/铁律#4）。

## 适用不变量
- 命中核心约束：**§10 FHIR/CDS Hooks 互操作** · **#5 关系库权威** · **§7 字典映射锚点** · **§9 组织隔离** · **#8 敏感字段加密脱敏**。
- 本卡落点：12 标准对象 + `ClinicalEventContext` 一次立骨，使 D3+ 所有临床能力消费同一类型与事件上下文，零平行模型。

## 验收 + 验证
- [x] **AC-1（FR-1）**：12 标准对象建模完整、FHIR R4 关键字段可映射；持久化往返一致。
- [x] **AC-2（FR-2/6）**：构造一次临床事件 → `ClinicalEventContext` → 规则/路径/CDSS 三引擎入口取到同一上下文。
- [x] **AC-3（FR-3）**：12 对象权威源为关系库；图投影关闭后对象读取不受影响（核心 #5）。
- [x] **AC-4（FR-4）**：编码字段挂字典映射锚点，未映射项可追踪（指向 TERM 卡）。
- [x] **AC-5（FR-5）**：12 对象跨租户隔离 + 敏感字段脱敏/加密生效。
- 关联 A1–A9：A3 临床运行（标准对象驱动入径/推荐）、A8 护理/报告。
- T-GATE：后端门禁全绿（无 mock 患者/无写死编码）。
- B0 验收：标准模型纯确定性，无模型依赖，天然 B0。

## 完工证据
- 代码 permalink：12 标准对象类型 + `ClinicalEventContext` + 12 表迁移（×5 方言）+ FHIR 映射。
- 测试：12 对象往返测试 + 事件上下文三引擎同源测试 + 跨租户隔离测试 + 敏感字段加密测试。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。

## 大卡工序（5d，后端）
- PR1：12 标准对象类型 + 5 方言迁移 + 组织/敏感字段 → AC-1/5。
- PR2：ClinicalEventContext + 事件驱动引擎入口 + 字典映射锚点 → AC-2/4。
- PR3：关系库权威 + 投影解耦 + FHIR 映射 → AC-3。

### PR1 进度证据（本 PR 不冒领整卡完成）
- 覆盖范围：新增 12 类标准对象 Record、租户级 Repository、V38 五方言关系库权威表基础、跨租户读取测试、Patient 敏感字段密文 / 掩码字段；同步清理旧 `SYMPTOM` 口径，统一为 `NURSING_ASSESSMENT`。
- 验收口径：仅作为 **AC-1 12 对象建模 / 持久化基础** 与 **AC-5 组织字段 / 敏感字段基础** 的 PR1 证据；AC-2、AC-3、AC-4 仍由 PR2 / PR3 承接，整卡 FR / AC 不在本 PR 勾选。

### PR2 进度证据（本 PR 不冒领整卡完成）
- 覆盖范围：新增 `ClinicalEventContext`、`ClinicalEventEngineDispatcher`、规则 / 路径 / CDSS 三个真实适配入口、`clinical_event.org_scope_json` V39 五方言迁移；临床事件处理从“只推进状态”调整为先构造同一个事件上下文，再派发给三类引擎，成功后才进入 `PROCESSED`。
- 字典锚点：新增 `ClinicalCodeMappingAnchor` / `ClinicalCodeMappingAnchorRegistry`，12 类标准对象的编码字段均可生成 `anchor.key()`，`TerminologyMappingPort` 改为按锚点评估，未映射项以 `UNKNOWN` 可追踪返回。
- 验收口径：作为 **AC-2 事件上下文三引擎同源入口基础** 与 **AC-4 编码字段映射锚点基础** 的 PR2 证据；AC-3 关系库权威 / 图投影关闭 / FHIR 门面仍由 PR3 承接，整卡 FR / AC 不在本 PR 勾选。

### PR3 进度证据（SYS-01 收口，不冒领 OPT-01 / SYS-03）
- 关系库权威：新增 `StandardClinicalAuthorityService`，按 `tenantId + patientId` 从 12 个 `mk_clinical_*` 关系库权威仓库聚合标准对象；`ClinicalProjectionStatusPort` 只返回图投影诚实状态，不参与业务事实读取，投影 `NOT_SYNCED` 时仍返回 `authoritySource=RELATIONAL_DATABASE`。
- FHIR 映射：新增 `StandardClinicalFhirMappingRegistry` / `StandardClinicalFhirReference`，12 类标准对象均有 FHIR R4 资源类型锚点；已有 `fhir_resource_id` 时解析 `ResourceType/id`，缺失时以本地权威主键 fallback 并标记 `LOCAL_AUTHORITY_FALLBACK`，不伪造外部 FHIR id。
- 验收口径：本 PR 收口 **AC-3 关系库权威 / 图投影关闭不影响读取**，并补足 SYS-01 层面的 FHIR R4 映射锚点；完整 FHIR R4/R5 端点、CapabilityStatement、受控 create 与 INTEG 总线接入仍由 D2 [OPT-01](../D2/OPT-01.md) 承接，图投影重建 / 一致性校验仍由 [SYS-03](SYS-03.md) 承接。
