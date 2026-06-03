# 规格增量：规则可视化创作

> 日期：2026-06-03
> 状态：规划中
> 关联 OpenSpec：`pathway-rule-authoring-overhaul`

## ADDED Requirements

### Requirement: 规则条件树必须支持任意深度嵌套

规则创作界面 SHALL 支持「条件组（全部满足/任一满足）」与「叶子条件」的任意深度嵌套，能表达 `A 且 (B 或 C)` 等多层级临床判断。前端产出的嵌套 `when` 结构 SHALL 与后端 `RuleDslEvaluator` 的递归 `all`/`any` 求值完全一致，保证「可视化能画出的逻辑后端必能正确求值」。

#### Scenario: 配置多层级临床规则

- **GIVEN** 配置人员在 L2 条件树需要表达「全部满足：年龄 ≥ 65 且（肌酐 > 上限 或 eGFR < 下限）」
- **WHEN** 通过「+子条件组」在顶层组内新增一个「任一满足」组并放入两个叶子
- **THEN** 系统 SHALL 生成 `when:{all:[ {fact:age...}, {any:[{fact:creatinine...},{fact:egfr...}]} ]}`
- **AND** 该 DSL 经后端 `RuleDslEvaluator` 求值 SHALL 得到与可视化一致的命中结果。

#### Scenario: 旧扁平规则向后兼容

- **GIVEN** 一条历史规则的 DSL 是单层 `when:{all:[叶子...]}`
- **WHEN** 在新版编辑器打开
- **THEN** 系统 SHALL 还原为顶层单组并可正常编辑，不丢失任何条件。

### Requirement: 规则条件字段必须来自上下文字段目录

叶子条件的字段路径 SHALL 从上下文字段目录选择，不得要求配置人员手敲裸路径。选中字段后系统 SHALL 自动带出数据类型并据此约束比较值类型；编码类字段的比较值 SHALL 从绑定的标准字典候选中选择。

#### Scenario: 选择字段自动带出类型

- **GIVEN** 配置人员在叶子条件选择字段「检验结果数值」
- **WHEN** 选中该字段
- **THEN** 比较值类型 SHALL 自动设为数值
- **AND** 算子候选 SHALL 限定为对数值有意义的集合（如大于/大于等于等）。

#### Scenario: 编码字段比较值来自标准字典

- **GIVEN** 叶子条件字段为「诊断编码」且绑定 ICD-10 字典
- **WHEN** 配置「属于集合」比较值
- **THEN** 比较值 SHALL 提供标准字典可搜索候选
- **AND** 不 SHALL 要求手敲编码字符串。

### Requirement: 规则创作必须有可解释性与复杂度护栏

系统 SHALL 限制条件树最大嵌套深度与单规则叶子总数，并禁止提交未解析的字段占位符，以保证规则可解释、可审计。

#### Scenario: 超出深度护栏

- **GIVEN** 配置人员尝试新增超过最大深度（默认 5 层）的子条件组
- **WHEN** 触发新增
- **THEN** 系统 SHALL 阻止并提示拆分规则，不 SHALL 生成不可解释的超深结构。

#### Scenario: 提交未解析字段

- **GIVEN** 条件树中存在仍为占位符 `<字段路径>` 的叶子
- **WHEN** 提交创建
- **THEN** 系统 SHALL 拦截并定位到该叶子，不 SHALL 提交。

## MODIFIED Requirements

### Requirement: L3 专家 DSL 为渐进式可选层且同步不打断流程

规则创作 SHALL 默认仅展示 L1 模板与 L2 可视化条件树；L3 专家 DSL SHALL 仅在显式开启专家模式后出现。「同步到 DSL」动作 SHALL 在后台更新 DSL 文本并轻量提示，不得强制切换到 L3 视图。L3 提交前 SHALL 仍能无损回填到 L2，禁止提交无法解释的裸 DSL。

#### Scenario: 同步不强制跳转

- **GIVEN** 配置人员在 L2 条件树点击「同步到 DSL」
- **WHEN** 同步完成
- **THEN** 系统 SHALL 保持当前 L2 视图并提示同步成功
- **AND** 不 SHALL 自动切换到 L3 专家视图。

#### Scenario: 未开专家模式时隐藏 L3

- **GIVEN** 专家模式开关处于关闭
- **WHEN** 查看创作界面
- **THEN** L3 DSL 标签 SHALL 不可见
- **AND** 同步动作 SHALL 不触发 L3 弹出。
