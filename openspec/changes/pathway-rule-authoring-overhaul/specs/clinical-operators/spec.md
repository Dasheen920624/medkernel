# 规格增量：临床算子与受控函数库

> 日期：2026-06-03
> 状态：规划中
> 关联 OpenSpec：`pathway-rule-authoring-overhaul`

## ADDED Requirements

### Requirement: 规则必须支持区间与参考范围比较

规则叶子条件 SHALL 支持区间比较（`between`/`not_between`）与参考范围比较（`above_ref`/`below_ref`/`within_ref`）。参考范围 SHALL 取自上下文字段（如 `CanonicalObservation.referenceRange`），不要求配置人员手填阈值。这些算子 SHALL 可在 L2 可视化结构化配置，L3 DSL 仅供专家核查。

#### Scenario: 按检验参考范围判断异常

- **GIVEN** 配置人员需要表达「检验结果高于该检验项参考范围上限」
- **WHEN** 在 L2 选择字段并选用 `above_ref` 算子
- **THEN** 系统 SHALL 基于该 Observation 的参考范围求值，无需手填阈值
- **AND** 参考范围缺失时 SHALL 按缺失数据策略处理而非误判。

### Requirement: 数值比较必须单位感知

涉及带单位字段的数值比较，系统 SHALL 在比较前按 UCUM 子集与字段元数据换算因子归一单位。单位不可换算时系统 SHALL 拒绝求值并明示，不得在单位不一致时静默比较。

#### Scenario: 跨单位比较自动归一

- **GIVEN** 字段单位为 mmol/L，配置的比较值单位为 mg/dL
- **WHEN** 执行比较
- **THEN** 系统 SHALL 按物质摩尔质量换算后比较
- **AND** 若两单位无换算关系，系统 SHALL 拒绝求值并提示单位不匹配。

### Requirement: 规则必须支持聚合、量词与时间窗表达式

规则表达式 SHALL 支持对资源集合的聚合与量词（`latest`/`first`/`max`/`min`/`avg`/`sum`/`count`，可带 `where` 过滤）以及时间窗 `over(window)`（相对就诊或评估时刻的相对时间）。多记录求值顺序 SHALL 确定（按 `eventTime`），保证可重放。

#### Scenario: 取最近一次指定检验值

- **GIVEN** 患者有多条肌酐 Observation
- **WHEN** 表达式为「最近一次 code=肌酐 的数值」
- **THEN** 系统 SHALL 按 eventTime 取最新一条求值
- **AND** 集合为空时 SHALL 返回 UNKNOWN 并按缺失策略处理。

#### Scenario: 时间窗内计数

- **GIVEN** 需要「24 小时内抗生素使用次数 > 2」
- **WHEN** 用 `count` + `where(类别=抗生素)` + `over(PT24H)`
- **THEN** 系统 SHALL 仅统计窗口内记录并比较。

### Requirement: 规则必须支持时序趋势与持续性算子

系统 SHALL 支持时序算子：`trend(rising|falling|stable, n)`（连续 n 次趋势）、`sustained(op,value,window)`（窗口内持续满足）、`delta(op,value,window)`（变化量）、`frequency(op,n,window)`（窗口内次数），以表达临床的连续监测判断。

#### Scenario: 连续上升趋势

- **GIVEN** 需要「肌酐连续 3 次上升」
- **WHEN** 选用 `trend(rising,3)`
- **THEN** 系统 SHALL 基于按时间排序的序列判定趋势
- **AND** 有效记录不足 3 次时 SHALL 返回 UNKNOWN。

### Requirement: 受控临床公式必须来自白名单函数库

系统 SHALL 提供受控临床公式库（如 eGFR、CrCl(Cockcroft-Gault)、BSA、BMI 等），每个公式声明入参字段、单位要求、适用人群与文献来源，并经金标准单测覆盖。公式 SHALL 通过白名单 `ClinicalFunctionRegistry` 注册，系统 SHALL NOT 允许运行期注入任意计算表达式或脚本。

#### Scenario: 用 eGFR 作为比较操作数

- **GIVEN** 配置「eGFR < 60」作为肾功能受限判断
- **WHEN** 选择受控公式 eGFR 并提供所需入参字段
- **THEN** 系统 SHALL 用注册公式计算结果参与比较
- **AND** 入参缺失或单位不符时 SHALL 拒绝计算并明示，不 SHALL 用默认值估算。

### Requirement: 缺失与陈旧数据必须按三值逻辑诚实处理

规则求值 SHALL 采用 TRUE/FALSE/UNKNOWN 三值逻辑：字段缺失、`QualityStatus=INVALID` 或超龄（is_stale）得 UNKNOWN；`QualityStatus=PARTIAL` 仍参与求值但 SHALL 在证据链标注。每条规则 SHALL 声明缺失数据策略（`UNKNOWN_AS_FALSE` 默认不误报，或 `UNKNOWN_AS_BLOCK` 高危场景缺数据即提示人工核查）。系统 SHALL NOT 以默认值或随机值伪造命中，缺失/陈旧/部分质量 SHALL 在证据链明示。

#### Scenario: 高危规则缺关键数据

- **GIVEN** 一条高危用药规则声明 `UNKNOWN_AS_BLOCK`，且关键检验缺失
- **WHEN** 求值
- **THEN** 系统 SHALL 产出人工核查动作而非静默放过
- **AND** 证据链 SHALL 标明缺失字段。
