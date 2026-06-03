# 设计附录 I：集成与落地（医院对接 · 引擎使用 · 专病/临床决策端到端）

> 关联：`design.md`、`design-data-model.md`、`design-nfr-operations.md`。
> 既有运行时底座（**优先复用、不重造**）：
> - 接入：`engine.integration`（`IntegrationAdapter` 适配器中心 / `IntegrationWebhookConfig` 入站 / `IntegrationMessageLog`，健康状态诚实：`NOT_CONNECTED`/`MISCONFIGURED`/`HEALTHY` + 真实 RTT 心跳）。
> - 事件：`engine.context.ClinicalEvent*`（`ClinicalEventType`=DIAGNOSIS/ORDER/REPORT/DISCHARGE/FOLLOWUP/ADMISSION，**显式对应 CDS Hooks 触发点**；批量入站、幂等 `ContextIdempotencyKey`、`Outbox` 可靠分发）。
> - 分发：`ClinicalEventEngineDispatcher` → `ClinicalEventRuleEngineAdapter` / `ClinicalEventPathwayEngineAdapter` / `ClinicalEventRecommendationEngineAdapter`。
> - 上下文：`CanonicalResource` 快照 + `ClinicalCodeMappingAnchor`（投影期字典归一）+ `QualityStatus` 质量门。
> - 资产载体：`engine.pkg.KnowledgePackage`（专病包，含路径/规则/值集/字段绑定，可批量分发回滚）。

---

## I1. 端到端运行时（一张图）

```
院内系统 HIS/EMR/LIS/PACS/CIS/CDR
   │ (HL7v2 / FHIR / Webhook / 视图 / 文件)
   ▼  适配器中心 IntegrationAdapter（诚实健康自检，不造假 RTT）
临床事件 ClinicalEvent（ADMISSION/DIAGNOSIS/ORDER/REPORT/DISCHARGE/FOLLOWUP）
   │  幂等入站 + 批量
   ▼  投影：CanonicalResource 快照 + 字典对照归一(ClinicalCodeMappingAnchor) + 质量门(QualityStatus)
ClinicalEventEngineDispatcher
   ├─▶ 规则引擎(RuleEngineAdapter)      → 分级动作卡片(提醒/阻断/建议医嘱)
   ├─▶ 路径引擎(PathwayEngineAdapter)   → 入径/推进/时钟SLA/变异
   └─▶ 推荐引擎(RecommendationEngineAdapter) → 临床推荐
   │  统一 ConditionEvaluator 求值（规则 when 与路径 guard 共用）
   ▼  Outbox 可靠分发（诚实降级、可重放）
下游：待办中心 / 通知中心 / 临床提醒治理 / 院级质控驾驶舱 / 回写院内系统
```

两个引擎不是孤岛：**它们由临床事件驱动、消费同一份归一快照、共用同一条件内核**，产出经 Outbox 流向既有业务页面与院内系统。

---

## I2. 怎么对接医院系统

1. **登记适配器（适配器中心）**：为每个院内源系统建 `IntegrationAdapter`，声明 `protocolType`（HL7v2 / FHIR / Webhook / 数据库视图 / 文件）与 `configJson`；健康自检返回真实 `healthStatus` 与 `rttMs`，未接真实连接器时显示 `NOT_CONNECTED`，**不伪造连通**。
2. **入站三方式**：
   - **推送**：院内系统经 `IntegrationWebhookConfig` 推 Webhook；
   - **批量上报**：经临床事件 API（`ClinicalEventBatchRequest`）批量送事件；
   - **拉取**：适配器按配置定时拉取。
3. **按契约接入**：第三方依据 `GET /integration/data-contract?packageVersion=`（附录 B）组织 `ClinicalEventPayload`，字段/单位/值集/必填对齐当前包版本。
4. **投影归一**：入站事件投影为 `CanonicalResource` 快照，投影期用 `ClinicalCodeMappingAnchor` + terminology 把**院内编码归一为标准编码**；`QualityStatus=INVALID` 拒绝入库（`ENG-CONTEXT-003`），缺字段标 PARTIAL。
5. **幂等与可重放**：`ContextIdempotencyKey` 保证重复上报不产生重复快照；`Outbox` 保证分发可靠、可重放、可审计。
6. **回写**：规则动作、路径待办、推荐经 Outbox 回流院内系统或待办中心；目标不可达诚实降级（`NOT_CONNECTED`），不假成功。

> 落地要点：医院无需改造内部系统结构，只需通过适配器把既有 HL7/FHIR/接口/视图接入；语义对齐由字段目录 + 字典对照在平台侧完成。

---

## I3. 怎么用规则引擎（创作→运行闭环）

| 阶段 | 做什么 | 用到的能力 |
|---|---|---|
| 创作 | 向导选原型/可视化嵌套/字段目录选字段/参数化 | 附录 G、规格 rule-authoring/authoring-experience |
| 验证 | 即配即试（真实快照）+ 四类测试用例 | clinical-operators、authoring-experience |
| 治理 | 同行评审→委员会会签（高危多签）→影子 | rule-governance、engine-nfr-safety |
| 发布 | 灰度 10%→全量，保留影响摘要与回滚 | 既有发布门禁 |
| 运行 | 临床事件触发求值，产出分级动作卡片 | RuleEngineAdapter + ConditionEvaluator |
| 监测 | 命中率/越权率/影子误报/漂移 | 领域事件 + 质控驾驶舱 |

**运行触发映射（ClinicalEventType ↔ CDS Hooks）**：`ORDER`→开/签医嘱(order-select/sign)、`ADMISSION`→就诊开始(encounter-start)、`DIAGNOSIS`→患者视图(patient-view)、`REPORT`→检验报告（危急值回报）、`FOLLOWUP`→随访、`DISCHARGE`→出院核查。求值先判适用域再求值，超时按 NFR 诚实降级。

---

## I4. 怎么用路径引擎（建模→结局闭环）

| 阶段 | 做什么 | 触发/能力 |
|---|---|---|
| 建模 | 阶段/里程碑/富节点/临床时钟/守卫边 | pathway-clinical-model |
| 入径 | 诊断/入院事件命中入径标准→建议入径 | DIAGNOSIS/ADMISSION + entryCriteria |
| 推进 | 报告/医嘱事件 + 守卫求值选下一节点 | REPORT/ORDER + ConditionEvaluator + PathwayProgressor |
| 时钟 | 里程碑 target/min/max + 超时分级升级 | ClinicalClock + ClockSlaBreached 事件 |
| 任务 | 节点 RACI 角色生成工作清单 | 待办中心 |
| 变异 | 偏离捕获/分类/再入径或终止 | pathway_variance |
| 出径 | 出径标准 + 结局指标（LOS/再入院/达标） | exitCriteria + EvaluationIndicator |

---

## I5. 专病诊疗端到端示例：慢性肾病（CKD）专病

**专病包（KnowledgePackage「CKD 专病」）** 内含：CKD 路径模板 + 规则集 + 值集（肾毒性药 ATC、肌酐 LOINC）+ 字段目录绑定 + 受控公式（eGFR/CrCl）+ 条件片段「肾功能受限」。一次分发到多院（复用 SyncTarget）。

1. **入径**：HIS 下达诊断 `N18.x` → `DIAGNOSIS` 事件 → 投影归一 → 入径标准命中 → 建议入径 CKD 路径（医生确认）。
2. **分期里程碑**：派生 `eGFR`（附录 E1）→ 决策节点按 G1–G5 分期分流到对应阶段。
3. **运行规则**（事件触发，全部命中同一快照）：
   - 开肾毒性药（`ORDER`）→ 规则「eGFR<30 且 在用肾毒性药」→ **BLOCK 卡片**（附录 A5），复用「肾功能受限」片段；
   - 按 `CrCl` 剂量调整提醒；贫血/钙磷阈值（参考范围算子）；
   - 随访时窗（`FOLLOWUP` 路径节点时钟）。
4. **变异**：患者拒绝转诊/透析 → 变异捕获、再入径或调整。
5. **结局**：绑定 eGFR 下降速率、住院率、血压/血糖达标率指标 → 质控驾驶舱。
6. **复用**：肾功能受限片段、肾毒性药值集、eGFR/CrCl 公式跨规则与路径复用；CKD 专病包可被各院订阅后本地覆盖（如院内剂量阈值）。

## I5b. 专病示例：房颤（AF）抗凝管理

**专病包「房颤抗凝」**：路径模板 + 规则集 + 值集（房颤诊断 ICD `I48`、口服抗凝药 OAC ATC `B01A`、抗血小板/NSAID、出血高危诊断）+ 受控公式（CHA₂DS₂-VASc、HAS-BLED、CrCl）+ 条件片段「高出血风险」「肾功能受限」。

1. **入径**：诊断 `I48.x`（`DIAGNOSIS` 事件）→ 入径标准命中 → 建议入径房颤抗凝路径。
2. **风险评估决策点**：派生 `CHA₂DS₂-VASc` 与 `HAS-BLED`（附录 E8/E8b）。
   - 男 ≥1 / 女 ≥2 → 进入「建议抗凝」分支；评分不足 → 进入「暂不抗凝随访」分支（守卫边按评分分流）。
3. **运行规则**（事件触发，同一归一快照）：
   - 已达抗凝指征但**未开 OAC**（`PATIENT_VIEW`/查房）→ 提醒「漏抗凝」（STRONG_REMINDER，来源指南 A 级）；
   - 开具 OAC（`ORDER`）时若 **HAS-BLED ≥3** → 强提醒纠正可逆出血因素 + 加强监测（非阻断）；
   - **OAC 与抗血小板/NSAID 联用**（值集 `count where ≥1`）→ 出血风险叠加提醒，复用「高出血风险」片段；
   - **肾功能调量**：NOAC 按 `CrCl` 分档剂量（区间算子 `between`）给出建议剂量；CrCl 极低 → 禁用相应 NOAC（BLOCK + 越权理由）；
   - **INR 监测**（华法林路径）：`REPORT` 事件 → INR 超治疗窗 `not_between [2,3]` → 调量提醒；INR 危急 `is_critical` → 危急值回报时钟。
4. **路径时钟**：抗凝启动后随访里程碑（如 1 周/1 月 INR 或肾功能复查）超时升级。
5. **变异**：患者拒绝抗凝/高出血事件 → 变异捕获、再评估或终止。
6. **结局指标**：抗凝达标率、TTR（华法林）、卒中事件率、大出血率 → 质控驾驶舱。
7. **复用**：CHA₂DS₂-VASc/HAS-BLED 公式、OAC 值集、「高出血风险」片段跨规则与其他心血管专病复用。

## I5c. 专病示例：脓毒症（Sepsis）1 小时集束化

**专病包「脓毒症集束化」**：路径模板（time-zero = 识别时刻，见附录 A7）+ 规则集 + 值集（乳酸/血培养 LOINC、广谱抗生素 ATC、血管活性药）+ 受控公式（无须复杂评分，主要时序与时钟）+ 条件片段「疑似脓毒症」「组织低灌注」。

1. **识别/入径**：体征报告（`REPORT`：体温/心率/呼吸/血压/乳酸）或预警 → 「疑似脓毒症」片段命中（如感染征象 + qSOFA 要素）→ 触发 `SEPSIS_RECOGNITION` 基准事件、建议入径并启动 1 小时时钟。
2. **1 小时集束化里程碑（临床时钟 SLA）**：
   - 测乳酸（`M_LACTATE` target 60min）；
   - 血培养（用药前）；
   - 经验性广谱抗生素（`M_ABX` target 60min，超时升级 ICU 主治）；
   - 乳酸 >2 或低血压 → 晶体液复苏 30mL/kg（决策节点 + 守卫边，见附录 A7）。
3. **运行规则**（事件触发）：
   - 抗生素**未在 1h 内给**（时钟超时）→ `ClockSlaBreached` 升级提醒；
   - 乳酸 `trend(falling)` 复测达标 → 推进；持续高 → 升级；
   - 复苏后仍低血压 → 建议血管活性药（SUGGEST_ORDER）；
   - 抗生素与既往**过敏交叉反应**（依赖 P0 结构化过敏资源）→ 改药提醒/阻断。
4. **变异**：转 ICU、放弃治疗、合并其他路径（多路径协调）。
5. **结局指标**：1h 集束化达标率、抗生素时限达标率、28 天病死率、ICU 转入率 → 质控驾驶舱。
6. **复用**：乳酸/抗生素值集、「疑似脓毒症」片段、时钟 SLA 模式可被其他急危重症路径复用。

> 两个示例共用同一套机制（专病包 + 事件触发 + 统一条件内核 + 时钟/守卫/变异/结局 + Outbox 下游），印证「一套引擎适配不同专病」：差异只在配置（值集/公式/时钟/守卫/片段），不在代码。

## I6. 临床决策（CDS）实时示例：医生开医嘱瞬间

```
医生开具肾毒性药并签署
  → ORDER 事件(order-sign 钩子)  [p95 ≤ 800ms 预算]
  → 投影最新快照 + 字典归一
  → RuleEngineAdapter：先判适用域(INPATIENT, 排除透析人群)，再 ConditionEvaluator 求值
  → 命中：eGFR(派生公式)<30 且 在用肾毒性药≥1
  → 产出 BLOCK 卡片：indicator=critical，来源=院内肾脏安全用药规范(A级)，
     建议=改肾安全替代，越权理由集合=[透析中/单次可接受/已会诊]
  → 医生确认或越权(强制填理由) → OverrideCaptured 事件 → 越权率指标
  （求值超时/上下文不可用 → 诚实降级，高危提示人工核查，不静默放过）
```

## I7. 落地到既有产品菜单（一一对应）

| 菜单/页面 | 本设计落点 |
|---|---|
| 适配器中心 | I2 适配器接入与健康自检 |
| 路径配置 / 规则库 | 创作体验（附录 F/G）+ 各创作规格 |
| 字典映射 | 院内↔标准对照（context-catalog）+ 投影归一 |
| 患者主索引(MPI) | 患者上下文与人群适用域 |
| 临床提醒治理 | 规则分级动作产出与告警治理（rule-governance） |
| 待办中心 / 通知中心 | 路径工作清单与动作卡片下游（Outbox 事件） |
| 智能随访 | FOLLOWUP 路径与随访时钟 |
| 院级质控驾驶舱 | 结局指标与命中/越权/SLA 监测 |
| 配置包中心 | 专病包/知识包批量导入导出与分发（pkg） |

---

## I8. 集成落地的安全与诚实边界

- 适配器健康、回写状态一律诚实（`NOT_CONNECTED`/真实 RTT），不造假连通或成功。
- 求值前必须完成投影归一与质量门；`QualityStatus=INVALID` 拒绝，不带病求值。
- 事件触发求值满足 NFR 时延预算，超时诚实降级，高危不放过。
- 入站幂等、分发经 Outbox 可靠可重放、全链路 `trace_id`/`package_version` 可审计。
- 专病包跨院分发后，下级可本地覆盖但继承关系与版本可追溯、可回滚。
