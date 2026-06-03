# 规格增量：临床路径领域模型

> 日期：2026-06-03
> 状态：规划中
> 关联 OpenSpec：`pathway-rule-authoring-overhaul`

## ADDED Requirements

### Requirement: 路径必须支持真实入径与出径人群标准

路径的入径标准（entryCriteria）与出径标准（exitCriteria）SHALL 支持真实的纳入/排除条件树（复用规则递归条件构建器），不得为空占位。入径 SHALL 支持自动建议或人工确认。

#### Scenario: 按纳入排除标准入径

- **GIVEN** 路径定义了纳入标准（特定诊断）与排除标准（特定禁忌）
- **WHEN** 评估某患者上下文快照
- **THEN** 系统 SHALL 依据标准判断是否建议入径
- **AND** 排除标准命中的患者 SHALL NOT 被建议入径。

### Requirement: 路径必须支持阶段、里程碑与天序结构

路径 SHALL 支持在节点之上组织阶段（Phase）与里程碑（Milestone），并提供天序（如术后第 N 天）视图。里程碑 SHALL 携带预期完成时点与达成判定。

#### Scenario: 天序里程碑视图

- **GIVEN** 一条外科路径包含术前/术中/术后阶段
- **WHEN** 查看路径
- **THEN** 系统 SHALL 按阶段与天序展示节点与里程碑及其达成状态。

### Requirement: 路径必须支持富节点类型

路径节点类型 SHALL 扩展支持：决策点（DECISION，多守卫边分流）、并行（PARALLEL，fork/join 并发与汇合）、等待/计时（WAIT/TIMER）、子路径（SUBPATHWAY）、人工闸门（MANUAL_GATE，需角色确认推进）、医嘱套餐（ORDER_SET，绑定可下达医嘱/护理套餐）。各节点语义 SHALL 能被 `PathwayProgressor` 正确推进。

#### Scenario: 决策点按守卫分流

- **GIVEN** 一个 DECISION 节点有两条带守卫条件的出边
- **WHEN** 患者状态满足其中一条守卫
- **THEN** 系统 SHALL 推进到对应分支
- **AND** 守卫均不满足时 SHALL 按默认边或停留并明示。

#### Scenario: 并行汇合

- **GIVEN** 一个 PARALLEL fork 派生多条并发活动
- **WHEN** 所有并发活动到达 join
- **THEN** 系统 SHALL 在汇合后才继续推进。

### Requirement: 路径必须支持临床时钟与 SLA 升级

里程碑/时窗节点 SHALL 声明目标/最早/最晚时限与基准事件（入院、手术开始等），支撑「门球时间 <90min」「抗生素入院 1h 内」等。超时 SHALL 分级升级（提醒→上报→质控记录），并与时钟指标绑定。

#### Scenario: 时限超时升级

- **GIVEN** 节点声明「入院后 1 小时内给药」
- **WHEN** 超过时限未达成
- **THEN** 系统 SHALL 触发分级升级并记录质控时钟指标。

### Requirement: 路径流转边必须以守卫条件分支

流转边的守卫条件 SHALL 复用规则递归条件构建器进行可视化配置，产出与 `PathwayProgressor` 对齐的条件结构，替代手写 JSON。多守卫边 SHALL 按优先级与守卫求值确定推进。

#### Scenario: 可视化配置守卫边

- **GIVEN** 配置人员为条件流转边设置触发条件
- **WHEN** 编辑该边
- **THEN** 系统 SHALL 提供与规则一致的可视化条件构建器
- **AND** 产出条件 SHALL 能被 `PathwayProgressor` 正确评估。

### Requirement: 路径必须支持变异管理

患者偏离路径时系统 SHALL 捕获变异：分类（临床/系统/患者/家属）、原因码、责任角色、再入径或终止决策。变异统计 SHALL 可回流为路径优化与质控指标。

#### Scenario: 偏离路径产生变异

- **GIVEN** 患者实际处置偏离当前路径节点
- **WHEN** 记录偏离
- **THEN** 系统 SHALL 生成带分类与原因的变异记录
- **AND** SHALL 支持再入径或终止决策。

### Requirement: 路径节点必须声明角色并生成工作清单

路径节点 SHALL 声明 RACI 角色（Responsible/Accountable/Consulted/Informed）。推进时系统 SHALL 为对应角色生成工作清单，对接现有待办中心。

#### Scenario: 推进生成角色待办

- **GIVEN** 节点声明责任角色为主管护师
- **WHEN** 路径推进到该节点
- **THEN** 系统 SHALL 在待办中心为该角色生成任务。

### Requirement: 路径必须支持多级模板继承与差异合并

路径模板 SHALL 支持 STANDARD→HOSPITAL→DEPARTMENT→SPECIALTY 四级继承：下级可覆盖/新增/禁用上级节点。系统 SHALL 提供继承差异（diff）视图与合并解析，避免重复维护。

#### Scenario: 科室级覆盖标准节点

- **GIVEN** 科室级模板覆盖标准模板的某节点时窗
- **WHEN** 查看合并结果与差异
- **THEN** 系统 SHALL 正确合并并以 diff 视图明示覆盖项。

### Requirement: 路径必须绑定结局指标并支持实例状态机与多路径协调

路径/里程碑 SHALL 可绑定质量与结局指标（LOS、再入院率、并发症、成本等，对接 `EvaluationIndicator`）。系统 SHALL 维护患者路径实例状态机（当前/已完成/待办逾期/变异/完成退出），支持队列回放与历史快照时光机仿真。一名患者并发多路径时系统 SHALL 检测路径间医嘱/时窗冲突并提示协调，且 SHALL NOT 自动修改医嘱。

#### Scenario: 多路径冲突仅提示

- **GIVEN** 患者同时在两条路径且存在医嘱时窗冲突
- **WHEN** 系统检测到冲突
- **THEN** 系统 SHALL 提示需人工协调
- **AND** SHALL NOT 自动改写任一路径的医嘱。
