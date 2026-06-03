# 规格增量：规则动作、交互治理与知识生命周期

> 日期：2026-06-03
> 状态：规划中
> 关联 OpenSpec：`pathway-rule-authoring-overhaul`

## ADDED Requirements

### Requirement: 规则动作必须分级且对齐卡片式输出

规则命中动作 SHALL 支持按严重度分级，并以卡片式结构输出（summary、detail、indicator(info/warning/critical)、source(指南来源+证据等级)、suggestions(可执行建议)、overrideReasons(可选越权理由集合)），对齐 CDS Hooks Card 语义。动作类型 SHALL 覆盖提示/提醒/强提醒/阻断/建议医嘱/自动留痕。高危动作 SHALL 要求医师确认。

#### Scenario: 不同严重度产出不同动作

- **GIVEN** 一条规则在不同阈值下应产出提醒或阻断
- **WHEN** 命中达到阻断阈值
- **THEN** 系统 SHALL 输出 BLOCK 卡片并要求医师确认
- **AND** 卡片 SHALL 携带指南来源与证据等级。

### Requirement: 系统必须管理规则交互与告警疲劳

系统 SHALL 支持规则优先级、抑制（`suppressedBy`）、窗口内去重，并在发布前静态检测互斥/重叠规则冲突。被阻断/强提醒越权时系统 SHALL 强制捕获越权理由，并将越权率回流为质量指标。

#### Scenario: 高阶规则抑制低阶提醒

- **GIVEN** 同一触发点已产生 BLOCK，且存在一条更低阶 REMIND
- **WHEN** 编排执行
- **THEN** 系统 SHALL 抑制低阶提醒避免告警叠加。

#### Scenario: 发布前检出冲突规则

- **GIVEN** 两条规则对同一字段设置相反阈值动作
- **WHEN** 提交发布
- **THEN** 系统 SHALL 在发布门禁明示冲突并要求处置。

#### Scenario: 越权必须留痕

- **GIVEN** 医师越权一条 BLOCK 规则
- **WHEN** 执行越权
- **THEN** 系统 SHALL 强制要求选择/填写越权理由并审计留存。

### Requirement: 规则必须声明适用域并先判定再求值

每条规则 SHALL 声明适用域：人群（纳入/排除标准）、组织（集团/医院/科室）、场景（住院/门诊/急诊/随访）、生效期与灰度比例。运行期系统 SHALL 先判定适用域，不适用则不求值、不触发。

#### Scenario: 不适用人群不触发

- **GIVEN** 规则适用域排除妊娠人群
- **WHEN** 对妊娠患者评估
- **THEN** 系统 SHALL 跳过该规则不产生动作。

### Requirement: 规则必须遵循临床知识治理生命周期

系统 SHALL 在草稿与发布之间扩展临床知识治理：同行评审 → 临床委员会会签（高危规则多签）→ 影子/静默运行（monitor-only，只记录不动作）→ 灰度 → 全量 → 监测 → 退役。退役 SHALL 封存而非删除，保证医legal 可追溯。

#### Scenario: 高危规则需会签与影子验证

- **GIVEN** 一条高危规则准备上线
- **WHEN** 推进发布
- **THEN** 系统 SHALL 要求临床委员会多签
- **AND** SHALL 先在影子模式只记录不动作，达标后才进入灰度。

### Requirement: 规则上线必须可历史回测并监测漂移

系统 SHALL 支持对历史脱敏快照集回测规则，产出灵敏度/特异度等指标，并在上线后监测命中率漂移。回测与监测 SHALL 基于真实快照，不得使用伪造数据。

#### Scenario: 上线前回测

- **GIVEN** 已有标注金标准的历史快照集
- **WHEN** 对草稿规则回测
- **THEN** 系统 SHALL 报告灵敏度/特异度与误报样例供评审。
