# 变更导读：路径引擎与规则引擎可视化创作与医疗级能力整治

> OpenSpec 变更 `pathway-rule-authoring-overhaul`。状态：设计完成、待评审/开工。
> 本文件是阅读入口，供后续 AI / 评审者按序理解全貌。

## 这是什么

把试点客户「完全无法使用」的路径配置与规则库，整治为**简单可配、可表达复杂临床逻辑、可批量可复用、医学可辩护**的世界级创作平台。覆盖：规则递归条件树、临床算子与受控公式、临床路径完整领域模型、上下文字段目录与术语值集、院内↔标准字典对照、对外接入契约、五大创作体验、以及非功能与安全硬化。

## 关键判断（来自真实代码审计）

- 后端 `RuleDslEvaluator` **已支持任意深度递归** → 规则嵌套是纯前端缺口。
- L2 编辑器已承诺「区间/单位/时序/eGFR 等临床算子」但后端只有 10 个时间点算子 → 需**加法式受控扩展**评估器（不重写内核）。
- 路径边条件与规则 DSL 分叉 → **抽取统一条件内核 `ConditionEvaluator`**（P0）。
- canonical 无结构化过敏资源 → 新增 `CanonicalAllergyIntolerance`（P0）。
- `pkg`/`terminology` 域已具备批量/分发/回滚/高危治理 → 体验层**复用不重造**。

## 阅读顺序

1. `proposal.md` —— 为什么改、29 项目标、影响范围、明确不做。
2. `design.md` —— 总体架构、能力全景矩阵、§13 审计决策、§14 触发/指标/DoD、附录索引。
3. 设计附录（按需深入）：
   - `design-dsl-grammar.md`（A）DSL 文法 + 派生字段 + 临床示例
   - `design-data-model.md`（B）DDL（五方言 V59+）+ 错误码 + API
   - `design-scenario-coverage.md`（C）32 临床场景适配矩阵
   - `design-enums-glossary.md`（D）闭集枚举 + 术语表
   - `design-formula-library.md`（E）受控公式精确算式 + 金标准
   - `design-frontend-architecture.md`（F）前端组件/交互/测试
   - `design-authoring-experience.md`（G）简单·易用·易配·可批量·可复用
   - `design-nfr-operations.md`（H）NFR·安全职责分离·灰度·边界·事件·待决项
   - `design-integration-landing.md`（I）医院对接·引擎使用闭环·专病(CKD)与临床决策端到端·产品菜单落点
4. `specs/` —— 10 个能力规格（规范化需求与场景）。
5. `tasks.md` —— P0→P12 + 贯穿 P-HARDEN，每步带验证检查点与交付顺序。

## 能力规格清单（specs/）

| 规格 | 内容 |
|---|---|
| engine-foundation | 统一条件内核、结构化过敏资源、确定性/租户隔离 |
| rule-authoring | 递归条件树、字段选择器、渐进专家模式 |
| clinical-operators | 区间/参考范围/单位/时序/受控公式/三值逻辑 |
| rule-governance | 分级动作卡片、交互治理、知识生命周期、回测 |
| pathway-authoring | 自动编码、下拉连边、校验前移、守卫边 |
| pathway-clinical-model | 入径出径、阶段里程碑、富节点、时钟 SLA、变异、继承、结局、多路径 |
| context-catalog | 字段目录、派生字段、ValueSet 术语服务、字典对照、对外契约 |
| authoring-experience | 自然语言预览、向导、即配即试、参数化、片段库、批量、资产库 |
| engine-nfr-safety | 时延预算与降级、职责分离、脱敏测试数据、环检测、版本一致性 |
| engine-integration-runtime | 适配器诚实接入、临床事件触发求值、Outbox 分发、专病包跨院分发 |

## 开工建议

P0（统一内核 + 过敏资源）→ P1+P3 并行（递归树 + 路径易用性，体验基线 P12-1/2/3 同步）→ P2 字段目录 → P6 临床算子 → P5 字典/值集 → P7/P8 规则治理 → P9/P10 路径领域 → P4/P11 契约与互操作 → P12 体验强化；P-HARDEN 贯穿各阶段纳入 DoD。

## 校验

`openspec validate pathway-rule-authoring-overhaul --strict` —— 通过。
