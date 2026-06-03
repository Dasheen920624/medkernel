# 规格增量：引擎基础（统一条件内核与上下文资源）

> 日期：2026-06-03
> 状态：规划中
> 关联 OpenSpec：`pathway-rule-authoring-overhaul`；详见 `design.md §13`、`design-dsl-grammar.md`

## ADDED Requirements

### Requirement: 规则与路径必须共用统一条件求值内核

系统 SHALL 提供共享条件求值组件 `ConditionEvaluator`，以统一 `Group`（all/any/not + 叶子）文法对 canonical 上下文求值。规则 `when` 与路径边 `guard` SHALL 共用该内核，保证「可视化能构建的条件，规则与路径求值语义一致」。路径推进流程仍由 `PathwayProgressor` 决策，但边条件 SHALL 由统一内核求值，上下文来源 SHALL 与规则一致（canonical 上下文，而非独立 facts map）。

#### Scenario: 同一条件在规则与路径求值一致

- **GIVEN** 同一段 `Group` 条件分别用于规则 `when` 与路径边 `guard`
- **WHEN** 对同一 canonical 上下文快照求值
- **THEN** 两处 SHALL 得到一致的命中结果与证据链。

#### Scenario: 既有扁平边条件向后兼容

- **GIVEN** 线上路径边存在旧扁平条件 `{fact,operator,value}`
- **WHEN** 统一内核加载该边
- **THEN** 系统 SHALL 将其适配为单叶子 `Group` 并正确求值
- **AND** 既有边 SHALL NOT 需要数据迁移即可运行。

### Requirement: 上下文模型必须覆盖过敏/不耐受资源

系统 SHALL 在 canonical 上下文模型中提供过敏/不耐受资源 `CanonicalAllergyIntolerance`（含编码、编码系统、致敏物质、类别、严重性、反应、发生时间、质量状态等），并纳入字段目录、对外数据契约与字典对照，以支持药物-过敏核查类规则。

#### Scenario: 药物-过敏核查可表达

- **GIVEN** 患者上下文含既往青霉素类过敏记录
- **WHEN** 配置「开具与已知过敏交叉反应药物时阻断」规则
- **THEN** 字段目录 SHALL 提供过敏资源字段供选择
- **AND** 规则 SHALL 能基于过敏资源与值集交叉反应判断命中。

### Requirement: 新增引擎工件必须满足确定性、租户隔离与可重放

本变更新增的所有表与服务 SHALL 携带 `tenant_id` 并纳入租户隔离，携带 `package_version` 以支持版本锁定与回滚。规则/路径求值 SHALL 在给定快照、`package_version` 与 DSL 下确定性可重放，并复用既有求值幂等键与审计链。

#### Scenario: 求值可确定性重放

- **GIVEN** 一次求值的快照、packageVersion、DSL 与幂等键已留存
- **WHEN** 以相同输入重放
- **THEN** 系统 SHALL 产出一致的结果与证据链。

#### Scenario: 跨租户不可见

- **GIVEN** 租户 A 的字段目录/值集/规则/路径工件
- **WHEN** 租户 B 查询
- **THEN** 系统 SHALL NOT 返回租户 A 的工件。
