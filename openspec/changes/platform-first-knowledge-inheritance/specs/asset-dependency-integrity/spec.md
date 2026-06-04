# 资产依赖与一致性（asset-dependency-integrity）

## ADDED Requirements

### Requirement: 引用完整性
系统 SHALL 维护资产依赖图并在发布/覆盖时校验依赖在目标作用域可解析且兼容；对被在用资产依赖的资产执行 DISABLE SHALL 阻断或级联提示，不得产生悬空引用。

#### Scenario: 停用在用字典被拦截
- **WHEN** 某标准字典被一条 PUBLISHED 规则依赖，机构尝试 DISABLE 该字典
- **THEN** 操作被阻断并指明依赖来源

### Requirement: 依赖协同解析
解析某资产时，其依赖的字段/字典/子规则 SHALL 在同一解析上下文（同一权威层×组织闭包×维度）内一并解析。

#### Scenario: 规则与院内字典自洽解析
- **WHEN** 解析一条平台规则，且机构对其引用的字典有覆盖
- **THEN** 返回的有效规则与有效字典为同一上下文下自洽的组合

### Requirement: 一致性快照
单次临床决策或单次有效包合成 SHALL 在同一 resolution epoch 内完成，所有资产及其依赖取同一时点视图，不得撕裂读，并以 epoch 锚定可重放。

#### Scenario: 无撕裂读
- **WHEN** 平台在解析过程中激活了某依赖字典的新版本
- **THEN** 本次决策仍使用进入时 epoch 的字典版本，结果自洽且可重放
