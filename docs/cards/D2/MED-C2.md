# MED-C2 · 规则 DSL 补临床算子

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D2 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §4.4 操作符库（L1048）· §4.6 规则 DSL 标准（L1077）· 核心 §13 真实性 / 铁律 #2 临床计算不伪造。

## 身份
- 卡 ID：MED-C2（= backlog 任务 ID）
- 域：D2 试点准备
- 关联场景：S5 规则引擎配置（DSL 算子扩展）
- 依赖卡：[RULE-01](RULE-01.md)（规则引擎 + DSL 求值器，本卡扩其算子库）· [TERM-01](TERM-01.md)（单位/编码标准）· [API-01](API-01.md)（标准上下文取值）
- 工作量：5d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
为规则 DSL（[RULE-01](RULE-01.md) 的 `RuleDslEvaluator`）补**临床专用算子**：`between`（区间）· `unit_compare`（**单位换算后**比较）· `temporal`（时间窗 / 连续次数）· `derived`（**受控**临床算术：eGFR / CrCl / BSA 等）。保证规则能表达真实临床判断（如"48h 内连续 2 次血钾 > 6.0 mmol/L"），且**计算确定性、单位安全、可解释**。

## 现状（搬迁时核查 2026-05-30，以 `medkernel-backend/src` 为准）
`engine/rule` **DSL 求值器已建**，本卡＝**扩算子库（新增 4 类临床算子）**：
- 已有：`RuleDslEvaluator`/`RuleDslEvaluation`(+Test)、`RuleEvaluateRequest`/`Response`/`RuleEvaluationItem`、`RuleActionResult`、基础操作符（比较/逻辑）。
- 缺口（本卡补）：① `between`；② `unit_compare`（单位换算表 + 量纲校验，接 [TERM-01](TERM-01.md) 单位标准）；③ `temporal`（时间窗、连续 N 次、趋势）；④ `derived`（受控公式白名单：eGFR/CrCl/BSA，参数缺失诚实标 `INSUFFICIENT_DATA`，不臆测）。

## 功能要求（原子可测条目）
- [x] **FR-1 between**：`value between [lo, hi]`（含/不含边界可配），类型/单位一致校验。
- [x] **FR-2 unit_compare**：跨单位比较先**换算到基准单位**（如 mg/dL ↔ mmol/L）；**无换算关系 → 报错不臆测**（`UNIT_INCOMPATIBLE`）。
- [x] **FR-3 temporal**：时间窗（"近 48h"）、连续 N 次（"连续 2 次 > X"）、趋势（升/降）；基于标准上下文时间序列。
- [x] **FR-4 derived**：受控公式**白名单**（eGFR/CrCl/BSA…），参数与单位校验；**参数缺失 → `INSUFFICIENT_DATA`**，绝不用默认值臆测。
- [x] **FR-5 可解释**：每算子输出**取值 + 单位 + 来源 + 计算式**，纳入 `RuleActionResult` 解释。
- [x] **FR-6 确定性 B0**：全算子纯确定性，无模型依赖；非法表达式编译期/求值期报错带定位。

## 接口契约 / 页面契约
### 接口契约（引擎/框架卡）
- 端点：无新增端点——扩 [RULE-01](RULE-01.md) `RuleDslEvaluator` 算子；经 [API-05](API-05.md) 求值/仿真暴露。
- DTO：扩 `RuleDslEvaluation` 算子节点类型 + `derived` 公式注册表（白名单）。
- 状态机：N·A（算子库）。
- 幂等 / 错误码 / traceId：`UNIT_INCOMPATIBLE` / `INSUFFICIENT_DATA` / `DSL_OPERATOR_INVALID`；求值 traceId（[OBS-01](../D0/OBS-01.md)）。
### 页面契约（页面卡）
N·A —— 本卡无页面。算子在 [RULE-01](RULE-01.md) 规则库页的 L3 DSL 编辑器/L2 条件树中可选用。

## 数据与迁移
- 无独立表族——算子为代码 + `derived` 公式白名单（配置外置）。`unit_compare` 换算表可入字典（[TERM-01](TERM-01.md)）或迁移种子。
- 本轮实现选择确定性代码白名单，不新增表 / 不落迁移；后续若治理到 TERM-01 字典或配置中心，必须另走 5 方言一致 + 中文注释 + 回归测试。

## 视角清单（11 视角逐条）
1. **产品架构**：让规则 DSL 能表达真实临床判断的算子底座。
2. **产品体验**：N·A —— 算子在 [RULE-01](RULE-01.md) 编辑器内呈现，含算子说明与示例。
3. **系统与数据架构**：算子确定性、可解释；时间序列取自标准上下文；求值性能可观测。
4. **临床医疗安全**：★主战场 —— 单位换算安全（不臆测）、derived 参数缺失诚实标缺、连续次数/时间窗精确（铁律 #2）。
5. **知识与数据治理**：算子与单位/公式来源可溯（[TERM-01](TERM-01.md)/[OPT-07](OPT-07.md)）。
6. **安全合规与监管**：计算式留痕可审计复算。
7. **集团化与多租户治理**：算子全局一致；公式白名单可经治理扩展。
8. **集成与互操作**：取值来自标准上下文（[API-01](API-01.md)）统一口径。
9. **运维 / SRE / 国产化**：纯计算无外部依赖；离线可用。
10. **质量与真实性审计**：★公式真实计算可复算、无伪造结果；参数缺失不造数（铁律 #1/#2）。
11. **AI / 模型治理与可降级**：算子**纯 B0 确定性**，无模型；AI 不参与临床计算（核心 §11）。

## 适用不变量
- 命中核心约束：**铁律 #2 临床计算不伪造/不臆测** · **§13 真实性** · **依赖 [RULE-01](RULE-01.md) DSL / [API-01](API-01.md) 取值 / [TERM-01](TERM-01.md) 单位**。
- 本卡落点：补 4 类临床算子，使规则可表达单位安全、时序精确、公式受控的真实判断。

## 验收 + 验证
- [x] **AC-1（FR-2）**：mg/dL 与 mmol/L 比较自动换算正确；无换算关系 → `UNIT_INCOMPATIBLE` 不臆测。
- [x] **AC-2（FR-3）**：表达"48h 内连续 2 次血钾 > 6.0" → 对时间序列判定正确（边界用例覆盖）。
- [x] **AC-3（FR-4）**：eGFR 缺肌酐 → `INSUFFICIENT_DATA`；参数齐全 → 结果与标准公式一致。
- [x] **AC-4（FR-5/6）**：求值输出取值/单位/来源/计算式；非法表达式带行列报错；关模型不影响。
- 关联 A1–A9 剧本：A3 规则配置（临床算子用例）。
- T-GATE：真实性门禁全绿（公式可复算、无伪造、缺参不造数）。
- B0 验收：纯确定性算子，**天然 B0**。

## 完工证据
- 代码 permalink：待 PR 创建后补；本轮落点为 `RuleDslEvaluator` 调度、`ClinicalRuleOperatorSupport` 临床算子支持库、`ErrorCode` 契约错误码。
- 测试：`RuleDslEvaluatorTest` 覆盖 between 边界 / 反向区间 / 单位不一致、glucose mg/dL↔mmol/L 换算、未知换算拒绝、48h 连续 2 次高钾、非数值 count、非正时间窗、趋势、趋势 count<2、非法方向、eGFR 缺参 / 0 肌酐 / 正确计算、CrCl / BSA 白名单；`ErrorCodeTest` 覆盖 `UNIT_INCOMPATIBLE` / `INSUFFICIENT_DATA` / `DSL_OPERATOR_INVALID`。
- 本地验证：`mvn -q -Dtest=RuleDslEvaluatorTest#betweenOperatorRejectsInvertedRange+temporalOperatorRejectsNonNumericCountWithoutDefaulting+temporalOperatorRejectsNonPositiveWindow+temporalTrendRejectsCountLessThanTwo+derivedFormulaRejectsNonPositiveClinicalParametersWithoutInternalError test` 红灯后转绿；`mvn -q -Dtest=RuleDslEvaluatorTest,RuleEngineServiceTest,RuleEngineApiContractTest,ErrorCodeTest test`；`mvn -q test`（Surefire XML 汇总 174 files / 1052 tests / 0 failures / 0 errors / 0 skipped，H2 + Docker PostgreSQL 15.18 / Oracle 21.3 均迁移至 v62 且二次 migrate 无新迁移）；`npm ci`（既有 dev 依赖告警归 `DEFER-002`）；`npm run verify`（44 files / 236 tests，既有 React Router / act 警告归 `DEFER-003`）；`npm audit --omit=dev --audit-level=moderate`（0 vulnerabilities）；`npm run build`（既有 `vendor-antd` 大 chunk 提示归 `DEFER-003`）；changed-mode T-GATE：真实性 / 配置边界 / 迁移规约均通过；`scripts/check-comment-zh.sh` 0 fail / 0 warn；`git diff --check` 通过。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
