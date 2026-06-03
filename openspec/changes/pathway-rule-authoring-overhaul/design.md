# 设计：路径引擎与规则引擎可视化创作与医疗级能力整治

> 日期：2026-06-03
> 状态：设计中（综合扩展版）
> 关联提案：本目录 `proposal.md`

本设计以「简单配置、表达复杂临床逻辑、可解释、可审计、可回滚、可降级」为总纲，对标国际医学信息学标准并落到既有代码：

- **CDS Hooks**：规则触发时机与卡片式动作输出模型。
- **HL7 FHIR / CQL（Clinical Quality Language）**、**Arden Syntax**：规则逻辑与质量度量的导出对标目标。
- **GLIF / PROforma / FHIR PlanDefinition**：临床路径（计划定义）领域模型对标。
- **UCUM**：单位换算与单位安全比较。
- **FHIR ValueSet / CodeSystem**：术语值集、编码系统版本化与对照。

> 对标不等于替换技术栈。底层仍为 Spring Boot + React + Antd；标准用于约束 DSL 语义边界与对外契约，保证可互操作、可审计、医学可辩护。

---

## 1. 与既有代码的事实基线

| 既有能力 | 位置 | 本次定位 |
|---|---|---|
| 规则 DSL 递归求值（`all`/`any`/leaf 任意深度） | `RuleDslEvaluator.evaluateConditionNode` 行 65-99 | 嵌套能力已具备，前端补齐产出 |
| 10 个时间点算子 exists/equals/.../in/not_in | `RuleDslEvaluator.evaluateLeaf` 行 101-124 | **受控扩展**临床算子（见 §3.2） |
| canonical 上下文模型（12 类资源） | `engine.context.canonical.*` | 字段目录与术语服务权威源 |
| Observation 带 `unit`/`referenceRange`/`criticalFlag`/`eventTime`/`qualityStatus` | `CanonicalObservation` | 支撑单位/参考范围/时序/缺失三值逻辑 |
| 路径节点/边/时窗指标绑定 | `PathwayEngineService`、`PathwayProgressor`、`PathwayEdge` | 扩展为临床路径领域模型（见 §4） |
| 术语映射（院内↔标准） | `engine.terminology`、`TerminologyMapping` | 升级为值集绑定 + 对照覆盖门禁 |
| 发布门禁四类用例 + 影响摘要 + 回滚 | 规则/路径发布链路 | 扩展为知识治理生命周期（见 §3.7） |
| 求值幂等键 | `EvaluationIdempotencyKey` | 复用，保证可重放可审计 |

**关键判断**：嵌套与可视化是纯前端缺口；临床算子、缺失数据策略、治理生命周期需要后端**加法式、受控**扩展（不重写、不替换评估内核，新增算子与服务可灰度、可回退）。

---

## 2. 能力全景矩阵（本次一次性补全的范围）

| 域 | 能力 | 阶段 | 后端改动 |
|---|---|---|---|
| 规则 | 递归嵌套条件树 | P1 | 无 |
| 规则 | 区间/参考范围比较 | P6 | 加算子 |
| 规则 | 单位感知比较与换算（UCUM 子集） | P6 | 加单位服务 |
| 规则 | 时间窗连续/趋势/计数/聚合（latest/max/min/avg/count where） | P6 | 加时序求值 |
| 规则 | 受控临床公式（eGFR/CrCl/BSA/BMI 等） | P6 | 加函数库 |
| 规则 | 缺失/陈旧数据三值逻辑与安全默认 | P6 | 加策略 |
| 规则 | 分级动作与卡片式输出（CDS Hooks 对齐） | P7 | 加动作模型 |
| 规则 | 规则交互治理（优先级/抑制/去重/冲突/告警疲劳/越权留痕） | P7 | 加编排 |
| 规则 | 适用域与生效（人群/科室/场景/年龄/日期） | P7 | 加适用域 |
| 规则 | 知识治理生命周期（评审→委员会→影子/静默→发布→监测→退役） | P8 | 扩展状态机 |
| 规则 | 历史回测/灵敏度特异度/漂移监测 | P8 | 加回测 |
| 路径 | 自动编码/下拉连边/校验前移 | P3 | 无 |
| 路径 | 入径/出径人群（含纳入排除标准） | P9 | 扩展 criteria |
| 路径 | 阶段/里程碑/天序结构 | P9 | 扩展模型 |
| 路径 | 富节点类型（决策/并行 fork-join/等待计时/子路径/人工闸门/医嘱套餐） | P9 | 扩展节点 |
| 路径 | 临床时钟与 SLA（目标/最早/最晚/超时升级） | P9 | 扩展时窗 |
| 路径 | 守卫式分支（复用条件构建器） | P9 | 对齐 Progressor |
| 路径 | 变异管理（捕获/分类/原因/再入径） | P10 | 加变异 |
| 路径 | 角色 RACI 与工作清单 | P10 | 加任务 |
| 路径 | 多级模板继承与差异合并 | P10 | 扩展继承 |
| 路径 | 结局指标绑定（LOS/再入院/并发症/成本） | P10 | 绑定 QI |
| 路径 | 患者路径实例状态机 + 仿真/回放 + 多路径冲突 | P10 | 扩展实例 |
| 共性 | 字段目录 + 术语值集服务 | P2 | 新增 |
| 共性 | 院内↔标准字典对照 + 覆盖门禁 | P5 | 复用 terminology |
| 共性 | 对外数据接入契约（版本化） | P4 | 新增 |
| 共性 | 标准互操作映射（CDS Hooks/CQL/PlanDefinition 导出） | P11 | 加映射器 |

---

## 3. 规则引擎精细化

### 3.1 递归条件树模型（前端）

```ts
type RuleLogic = "all" | "any";
type RuleNode = RuleGroup | RuleLeaf;
type RuleGroup = { kind:"group"; id; logic:RuleLogic; negate?:boolean; children:RuleNode[] };
type RuleLeaf  = { kind:"leaf"; id; label; expr:RuleExpression; operator; rhs?:RuleOperand; valueKind };
interface RuleConditionTree { root:RuleGroup; action:RuleActionDraft; applicability:Applicability; explanationSummary:string }
```

- `RuleGroup` ↔ `{ [logic]: children.map(toDsl) }`，递归，与后端判定顺序一致。
- 支持 `negate`（NOT 语义，映射为 `{ not: {...} }` 新增节点类型，后端加 `not` 分支）。
- 护栏：最大深度（默认 5）、叶子上限（默认 50）、禁止未解析字段占位符。

### 3.2 临床算子与受控函数库（后端加法式扩展）

叶子不再只是「字段 算子 常量」，升级为 `表达式 算子 操作数`，三者都来自受控目录：

**(a) 表达式 `RuleExpression`** —— 对字段集合的取值方式：
- 直接取值：`field(path)`。
- 聚合/量词：`latest / first / max / min / avg / sum / count` over 一个资源集合，可带 `where` 过滤（如「最近一次 code=肌酐 的 Observation」）。
- 时间窗：`over(window)`，窗口用相对时间（`PT6H`/`P2D`，相对就诊或当前评估时刻）。

**(b) 算子扩展**（在现有 10 个上加，全部确定性、可解释）：
- 区间：`between` / `not_between`（区间比较，UI 双值）。
- 参考范围：`above_ref` / `below_ref` / `within_ref`（基于 `Observation.referenceRange`，无需手填阈值）。
- 危急值：`is_critical`（基于 `criticalFlag`）。
- 时序：`trend(rising|falling|stable, n)`（连续 n 次趋势）、`sustained(op,value,window)`（窗口内持续满足）、`delta(op,value,window)`（变化量）、`frequency(op,n,window)`（窗口内次数）。
- 缺失：`is_missing` / `is_stale(maxAge)`（基于 `eventTime`/`qualityStatus`）。

**(c) 操作数 `RuleOperand`** —— 比较值来源：
- 常量（带 `unit`，参与单位归一）。
- 字段对字段（另一个 `RuleExpression`，支持「药 A 与药 B 同开」类关系判断）。
- 受控公式结果（见下）。
- 值集成员（编码字段 `in` 一个 ValueSet，见 §5）。

**(d) 受控临床公式库** —— 命名、版本化、单测覆盖的纯函数，不允许自由表达式：
- `eGFR`（CKD-EPI/MDRD 可选）、`CrCl`（Cockcroft-Gault）、`BSA`（Mosteller/DuBois）、`BMI`、`correctedCalcium`、`anionGap`、按体重剂量 `dosePerKg` 等。
- 每个公式声明入参字段、单位要求、适用人群与文献来源；输出可作为操作数参与比较。
- 后端 `ClinicalFunctionRegistry`：白名单注册，禁止运行期注入任意计算（医疗安全红线）。

**(e) 单位服务（UCUM 子集）**：比较前对 `unit` 归一（mg/dL↔mmol/L 需物质摩尔质量，按字段元数据配置换算因子）；单位不可换算时**拒绝求值并明示**，不静默放过。

> 以上算子/函数均落到 `RuleDslEvaluator` 的 leaf 求值分支与新增 `evaluateExpression`，按算子名分派；DSL 仍是确定性 JSON，L2 可视化可完全产出，L3 仅供专家核查（兑现截图中的产品承诺）。

### 3.3 缺失/陈旧数据三值逻辑与安全默认

- 求值采用三值逻辑：`TRUE / FALSE / UNKNOWN`（字段缺失或质量不达标→UNKNOWN）。
- 每条规则声明缺失数据策略：`UNKNOWN_AS_FALSE`（默认，fail-open 不误报）或 `UNKNOWN_AS_BLOCK`（高危场景 fail-safe，缺数据即提示人工核查）。
- 陈旧数据：`is_stale(maxAge)` 与全局 `qualityStatus` 门槛；陈旧/缺失在证据链中明示，禁止用默认值伪造命中。

### 3.4 分级动作与卡片式输出（CDS Hooks 对齐）

- 动作从单一 `then` 升级为分级动作集：按命中严重度产出不同动作。
- 动作类型：`INFO` 提示 / `REMIND` 提醒 / `STRONG_REMINDER` 强提醒 / `BLOCK` 阻断 / `SUGGEST_ORDER` 建议医嘱 / `AUTO_DOCUMENT` 自动留痕。
- 输出对齐 CDS Hooks Card：summary、detail、indicator（info/warning/critical）、source（指南来源+证据等级）、suggestions（可执行建议）、overrideReasons（可选越权理由集合）。
- 高危动作沿用现有 `requiresPhysicianConfirmation` 红线。

### 3.5 规则交互治理（告警疲劳与冲突）

- **优先级**：规则带 priority，同一触发点按优先级排序。
- **抑制/去重**：相同患者+相同语义在窗口内去重；规则间可声明 `suppressedBy`（被更高阶规则抑制，如已 BLOCK 则不再 REMIND）。
- **冲突检测**：发布前静态检测互斥/重叠规则（同字段相反阈值），运行期记录共激发。
- **越权留痕**：BLOCK/STRONG_REMINDER 被医生越权时强制捕获理由，回流为规则调优与质量指标（越权率）。

### 3.6 适用域与生效（Applicability）

每条规则声明适用域，运行期先判定再求值：
- 人群：纳入/排除标准（复用条件构建器，如年龄段、性别、妊娠、特定诊断）。
- 组织：集团/医院/科室范围（对齐租户隔离）。
- 场景：住院/门诊/急诊/随访。
- 生效期：起止日期、灰度比例。

### 3.7 知识治理生命周期

在现有「草稿→发布门禁→发布→回滚」基础上扩展为临床知识治理：
```
草稿 → 同行评审 → 临床委员会会签(高危多签) → 影子/静默运行(monitor-only) → 灰度发布 → 全量 → 监测 → 退役/版本升级
```
- **影子/静默模式**：规则上线前在真实流量上「只记录不动作」，对比命中率与误报，达标后才进入灰度。
- 高危规则要求多签会签（临床委员会角色），证据可审计。
- 退役不删除，封存可追溯（医legal 可辩护）。

### 3.8 字段选择器与序列化

- `expr` 字段来自字段目录（§5），选中带出 dataType/unit/referenceRange 可用性/绑定值集。
- 序列化保留 `ui` 旁注（id/label/valueKind/dictionaryCode），后端忽略，保证 L2↔L3 无损往返。

---

## 4. 路径引擎精细化（临床路径领域模型）

### 4.1 入径/出径与人群

- `entryCriteria`/`exitCriteria` 从空 `{}` 升级为真实条件树（复用规则条件构建器）：纳入标准 + 排除标准。
- 入径可自动（满足条件建议入径）或人工确认。

### 4.2 阶段 / 里程碑 / 天序结构

- 节点之上引入**阶段（Phase）**与**里程碑（Milestone）**，支持「术前/术中/术后第 1 天…」天序视图，而非扁平节点列表。
- 里程碑携带预期完成时点与达成判定。

### 4.3 富节点类型

在现有 ASSESSMENT/DIAGNOSIS/TREATMENT/NURSING/CHECK/FOLLOWUP/QUALITY 基础上扩展：
- `DECISION` 决策点：多条守卫边按条件分流。
- `PARALLEL` 并行（fork/join）：并发活动与汇合。
- `WAIT/TIMER` 等待/计时：到点或事件触发推进。
- `SUBPATHWAY` 子路径：嵌套复用。
- `MANUAL_GATE` 人工闸门：需角色确认才推进。
- `ORDER_SET` 医嘱套餐：绑定可下达医嘱集合/护理套餐。

### 4.4 临床时钟与 SLA

- 每个里程碑/时窗节点声明 `target/min/max` 时限与基准事件（入院、手术开始等），支撑「门球时间 <90min」「抗生素入院 1h 内」「出院计划第 2 天前」。
- 超时分级升级（提醒→上报→质控记录），与时钟指标绑定（现有「时钟指标编码」语义化）。

### 4.5 守卫式分支

- 条件边复用 §3 递归条件构建器，产出与 `PathwayProgressor` 对齐的守卫条件，替代手写 JSON。
- 决策点的多出边按优先级 + 守卫求值确定唯一/多路推进。

### 4.6 变异管理

- 患者偏离路径时捕获变异：分类（临床/系统/患者/家属）、原因码、责任角色、再入径或终止决策。
- 变异统计回流为路径优化与质控指标。

### 4.7 角色 RACI 与工作清单

- 每节点声明 Responsible/Accountable/Consulted/Informed 角色；推进时生成对应角色工作清单（对接现有待办中心）。

### 4.8 多级模板继承与差异合并

- STANDARD→HOSPITAL→DEPARTMENT→SPECIALTY 四级继承（现有 templateLevel）：下级可覆盖/新增/禁用上级节点；提供继承差异（diff）视图与合并解析，避免重复维护。

### 4.9 结局指标绑定

- 路径/里程碑绑定质量与结局指标（LOS、再入院率、并发症、感染率、成本），与评估引擎指标（`EvaluationIndicator`）对接，形成路径疗效闭环。

### 4.10 患者路径实例、仿真与多路径协调

- 实例状态机：当前节点、已完成、待办/逾期、变异、完成/退出。
- 仿真扩展：现有单快照试运行 → 队列回放 / 时光机（历史快照重放）/ what-if 变更影响。
- 多路径并发：一名患者多病共存时多路径并行，检测路径间医嘱/时窗冲突并提示协调。

---

## 5. 上下文字段目录与术语值集服务

### 5.1 字段目录（派生自 canonical）

表 `context_field_catalog`：resourceType / fieldPath / displayName / dataType(number|string|boolean|date|code|list) / unit / referenceRangeAvailable / valueSetCode / description / status / packageVersion。

- 初始数据由迁移脚本从 `engine.context.canonical.*` 幂等派生；CI 校验字段路径必须真实存在于 canonical。
- 只读查询 `GET /context/field-catalog`；维护 `POST/PUT`（RBAC+审计，仅可在派生集合上补展示名/说明/单位/值集绑定）。

### 5.2 术语值集服务（FHIR ValueSet 对齐）

- 编码字段绑定**值集（ValueSet）**而非单一字典：支持外延（显式编码列表）与内涵（按 CodeSystem + 过滤规则）两种定义。
- 提供 `$expand`（展开成员）、`$validate-code`（成员校验）、`$subsumes`（上下位判定，如「头孢类」包含具体头孢）；规则 `in`/`above_ref` 等据此求值。
- CodeSystem 版本化（ICD-10/LOINC/ATC/SNOMED CT 子集），随 packageVersion 锁定，保证可回滚、可重放。

### 5.3 字段选择器（前端）

资源→字段级联可搜索下拉，选中自动带出 dataType→valueKind、unit、是否可用参考范围、绑定值集；编码字段比较值来自值集展开候选；数据源不可达诚实降级。

---

## 6. 院内 ↔ 标准字典对照

- 复用 `engine.terminology`，维护院内编码→标准编码对照，运行期上下文投影完成归一（与现有 `TerminologyMapping` 一致）。
- 医学语义匹配遵循核心 §7：同义词典 / 编码交叉表 / 来源权重 / 可选模型嵌入为主；字符 LCS/编辑距离仅低权重召回，不得自动确认（高危负样本判别）。
- **对照覆盖门禁**：规则/路径引用了绑定院内字典的编码字段时，发布前检查对照覆盖率，未对照关键项阻断上线并明示。

---

## 7. 对外数据接入契约（版本化）

- 基于字段目录 + 值集生成机器可读契约（JSON Schema 风格）：每 resourceType 的字段路径、数据类型、单位、绑定值集、是否必填 + 中文接入说明。
- 第三方按契约传入 → 投影 + 对照归一 → 规则/路径直接消费，无需逐方定制。
- 契约随 packageVersion 版本化，运行期校验版本一致，保证接入与规则/路径版本对齐、可回滚。

---

## 8. 渐进式专家模式与同步交互

- 默认仅 L1 模板 + L2 可视化；L3 专家 DSL 仅显式开启专家模式后出现。
- 「同步到 DSL」后台静默更新 + 轻提示，**不强制切 Tab**；专家模式与同步动作解耦（修复当前 `setActiveCreateLayer("l3")` 强跳）。
- L3 提交前必须可无损回填 L2，禁止提交不可解释裸 DSL。

---

## 9. 标准互操作映射（对标导出，分阶段）

- 规则 DSL ↔ **CDS Hooks**（触发点 + Card 输出）映射器；可选导出 **CQL / Arden Syntax** 以对接外部 CDS 生态与质量度量。
- 路径模型 ↔ **FHIR PlanDefinition / GLIF / PROforma** 概念映射（阶段/动作/决策/守卫）。
- 映射为加法式适配器，不改内核；用于互操作与可辩护，不强制依赖。

---

## 10. 迁移与回滚

- 新增表（字段目录、值集、对照扩展、变异、适用域、动作模型）均配套幂等迁移脚本，独立增量。
- 既有规则 DSL（单层 `when`）= 递归模型单层组，向后兼容加载。
- 临床算子为新算子名，旧规则不受影响；评估器按算子名分派，未知新算子在旧部署上诚实报错而非误算。
- 每阶段独立可回退，不影响线上已发布规则/路径运行。

---

## 11. 验证策略

- **单元**：递归序列化往返（含 NOT 与 3 层嵌套）；每个临床公式/单位换算金标准用例；时序算子窗口边界用例；三值逻辑缺失/陈旧用例；值集 `$expand/$subsumes` 用例；字段目录派生幂等。
- **前端组件**：递归条件树、深度护栏、字段选择器联动、路径富节点/守卫边/变异、模板继承 diff。
- **后端契约/端到端**：嵌套+临床算子经评估器求值；CDS Hooks 卡片输出；对照覆盖门禁；第三方契约样例命中。
- **临床有效性**：发布门禁四类用例 + 历史回测（灵敏度/特异度对金标准）+ 影子模式误报率 + 上线后漂移监测。
- **回归**：路径时窗门禁、规则发布门禁、租户隔离、幂等重放不被破坏。

---

## 12. 风险与权衡

| 风险 | 缓解 |
|---|---|
| 临床算子扩大后端攻击面/不确定性 | 算子与公式白名单注册、版本化、单测金标准、禁止运行期任意表达式 |
| 单位换算错误致用药安全事故 | UCUM 子集 + 字段元数据换算因子 + 不可换算即拒绝求值并明示 |
| 缺失数据导致漏报/误报 | 三值逻辑 + 显式缺失策略（fail-open/fail-safe 按风险选择）+ 证据明示 |
| 告警疲劳 | 优先级/抑制/去重/越权率监测 + 影子模式先验证 |
| 递归 UI 与富节点过复杂难用 | 模板起步、默认折叠、深度护栏、天序/阶段视图、面包屑缩进 |
| 标准映射引入耦合 | 映射器为可选加法适配器，不进内核 |
| 值集/CodeSystem 版本漂移 | 随 packageVersion 锁定，运行期校验版本一致 |
| 多路径并发冲突误判 | 冲突仅提示协调不自动改医嘱，人工决策保留 |
| 知识治理变重拖慢上线 | 低危规则简化流程，仅高危走会签/影子，分级治理 |

---

## 13. 关键架构决策（代码审计补充）

落地前对既有代码的审计暴露三处必须在设计阶段定调的问题：

### 13.1 抽取统一条件求值内核 `ConditionEvaluator`

审计发现：规则 `RuleDslEvaluator` 从 canonical 上下文 JSON 按路径递归求值（支持 all/any/十算子）；而路径 `PathwayProgressor.matchesCondition` 只支持单层 `{fact,operator,value}`、6 个算子，且从 `facts: Map<String,Object>` 取值。两者条件语义分叉，「路径边复用规则条件构建器」无法靠前端共享组件实现。

决策：把规则求值的条件核心抽取为共享组件 `ConditionEvaluator`（输入：统一 `Group` 文法 + canonical 上下文），`RuleDslEvaluator` 与 `PathwayProgressor` 同时依赖它。路径推进仍由 `PathwayProgressor` 负责流程决策，但边 `guard` 改由 `ConditionEvaluator` 求值，上下文来源从 `facts` map 切换为 canonical 上下文（与规则一致）。**向后兼容**：既有扁平 `conditionJson` 适配为单叶子 `Group`（见附录 A1），线上边无需迁移。此为 P0 基础任务。

### 13.2 Canonical 过敏数据：从粗列表升级为结构化资源

审计发现（修正）：`CanonicalPatient` 已有 `allergies: List<String>` 与 `specialPopulations: List<String>`，但只是**粗粒度编码字符串列表**，缺少致敏物质编码系统、严重性/危急性（criticality）、反应表现（reaction）、发生时间与交叉反应判定，**不足以支撑合格的药物-过敏 CDS**（无法做交叉反应、危急分级、可解释证据）。

决策：新增结构化 `CanonicalAllergyIntolerance`（字段：code/codeSystem/substance/category/criticality/reaction/onsetTime/qualityStatus 等），与既有 `patient.allergies` 粗列表并存（粗列表保留向后兼容，结构化资源用于规则求值与交叉反应值集）。同步进字段目录、对外契约与字典对照，作为 P0 前置。`patient.specialPopulations` 直接用于适用域人群判定（妊娠/儿童/老年）。可选评估 Immunization/FamilyHistory/Coverage 为后续包版本，非阻断。

### 13.3 术语能力复用既有 terminology 域

审计发现：terminology 域已具备 `LocalTerm`/`StandardTerm`/`TermMapping`/`TermMappingPackage`(版本化)/`SemanticTermMatcher`/`HighRiskTermDetector`/`MappingConflict`。

决策：院内↔标准对照与高危语义匹配直接复用上述真实类（`HighRiskTermDetector` 即核心 §7 的安全闸）；值集（ValueSet）作为新概念建在 `StandardTerm` + `code_system_version` 之上，不另起并行事实源。

---

## 14. 求值触发模型、可观测性与完成定义

### 14.1 求值触发（对齐 CDS Hooks 时机）

规则求值 SHALL 支持两类触发，且同一规则二者结果一致（确定性）：
- **事件触发（on-demand）**：在临床动作点求值，对齐 CDS Hooks 钩子：`patient-view`（打开患者）、`order-select`/`order-sign`（开/签医嘱）、`encounter-start`（就诊开始）。触发点由规则 `applicability` 与触发声明限定。
- **批量触发（standing/population）**：对队列定时或按需批量求值（质控、回测、影子）。

路径推进由临床事件驱动（节点完成、时钟到点、决策、变异），由 `PathwayProgressor` + 统一 `ConditionEvaluator` 决策。

### 14.2 可观测性指标（运行后必备）

系统 SHALL 至少暴露：规则命中率/触发量、越权率（按规则）、影子-生产误报率、求值时延、SLA 时钟超时率、对照覆盖率、值集展开规模。指标用于告警疲劳治理与上线后漂移监测，复用既有 observability 基线与 `RuleExecutionLog`。

### 14.3 完成定义（Definition of Done，每阶段统一）

每阶段 SHALL 满足：规格场景全部有对应测试且通过；新增/变更接口有契约测试；新增表有五方言迁移（V59+）；无 no-page-mock / 硬编码医学常量 / 伪造数据；证据链与审计完整；向后兼容验证通过；建 PR、远端检查通过后合入远程 `main`（不直推）。

## 15. 配套设计附录

本设计的可实现细节拆分到同目录附录，供后续 AI 直接照此实现：

- `design-dsl-grammar.md`（附录 A）：规则/路径 DSL 权威 JSON 文法、统一条件内核、派生字段、证据链结构、真实临床示例。
- `design-data-model.md`（附录 B）：新增表 DDL（租户隔离/审计/版本列）、五方言迁移约束、错误码与版本一致性、API 端点清单。
- `design-scenario-coverage.md`（附录 C）：20+ 规则场景与 12 路径场景覆盖矩阵，验证适配性并暴露前置。
- `design-enums-glossary.md`（附录 D）：闭集枚举（算子/节点/动作/状态等）与术语表。
- `design-formula-library.md`（附录 E）：受控临床公式精确算式、单位、适用人群与金标准算例。
- `design-frontend-architecture.md`（附录 F）：前端创作体验组件、状态、字段选择器、画布交互与测试。
- `design-authoring-experience.md`（附录 G）：简单/易用/易配/可批量/可复用的体验设计、条件片段库、批量与分发（复用 `pkg`/`SyncTarget`）、配套表与接口。
- `design-nfr-operations.md`（附录 H）：非功能指标（CDS 时延预算）、安全与职责分离（映射真实 `RoleCode`）、灰度上线与回滚、边界失败语义、领域事件集成、决策日志与待决问题。
- `design-integration-landing.md`（附录 I）：医院系统对接（适配器/事件/投影归一，复用 `engine.integration`+`ClinicalEvent*`）、两引擎使用闭环、专病诊疗（CKD）与临床决策（开医嘱 CDS）端到端示例、落地到既有产品菜单。
