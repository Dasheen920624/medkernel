# OPT-06 · AI 质量评测中心

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [wave2 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §AI 质量评测 · 核心 §13 真实性 · 铁律 #1。

## 身份
- 卡 ID：OPT-06（= backlog `OPT-06`）
- 域：wave2（X-LLM）
- 关联场景：S15
- 依赖卡：[LLM-07](LLM-07.md)（安全/医学维度）· [EVAL-01](../D4/EVAL-01.md)（评估引擎复用）· [LLM-01](LLM-01.md)（被评能力）
- 工作量：5d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
**AI 质量评测中心**：字典 / 规则 / 路径 / 推荐 / 解释 / 中文术语回归集 + 幻觉拦截——AI 各能力有统一回归评测平台，质量可量化、幻觉可拦。

## 现状（2026-06-17 核查）
**后端机制已建**：复用 [LLM-07](LLM-07.md) 同一评测平台，不另造旧表。V126 clean baseline 已扩展 `mk_llm_regression_case` 的 `case_domain`、`expected_terms_json`、`forbidden_assertions_json`、`min_score`，以及 `mk_llm_eval_run` 的 `capability_code`、`prompt_version`、`tool_version`、`quality_score`、`terminology_score`、`hallucination_detected`、`case_summary_json`，五方言一致且中文 COMMENT 完整。新增 `/api/v1/ai-eval/runs` 和 `/api/v1/ai-eval/trends`，支持离线 B0 输出或真实 provider 输出进入质量评分、术语专项分、幻觉拦截和版本趋势。真实字典/规则/路径/推荐/解释题库必须由真实来源导入，不在卡内编造医学题。

## 功能要求（原子可测条目）
- [x] FR-1 回归集：字典/规则/路径/推荐/解释/中文术语均可通过 `case_domain` + 真实来源用例建集，带期望、术语期望、禁用断言和最低分；不编造默认医学题。
- [x] FR-2 评测运行：`AiQualityEvalController` / `ModelEvalService.runQualityEvaluation` 对能力码批量跑回归出质量分。
- [x] FR-3 幻觉拦截：无源断言/编造编码命中 `HALLUCINATION_*`，标记 `hallucination_detected=Y` 并计入失败。
- [x] FR-4 中文术语：`expected_terms_json` 形成中文术语专项评分，输出 `terminology_score`。
- [x] FR-5 趋势：`/api/v1/ai-eval/trends` 按能力码 + 模型版本返回最近质量趋势，绑定 [LLM-04](LLM-04.md) 的 prompt/tool/model 三元组。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 端点：`POST /api/v1/ai-eval/runs`、`GET /api/v1/ai-eval/trends`；信封 [BASE-03](../D0/BASE-03.md)；趋势限定最近 20 条，后续大列表再接 [API-13](../D0/API-13.md)。
- 状态机：变更（评测运行态）。

## 数据与迁移
- 复用并扩展 `mk_llm_regression_case` / `mk_llm_eval_run`（结果/分数/幻觉标记/版本趋势），五方言 V126 clean baseline；不新增旧表。

## 视角清单（11 视角）
1. 产品架构：AI 质量量化平台。
2. 产品体验：评测结果可下钻（专家视图）。
3. 系统与数据架构：批量异步 + 趋势查询。
4. 临床医疗安全：医学红线维度归 [LLM-07](LLM-07.md)；本卡质量维度。
5. 知识与数据治理：评测集版本化、可治理。
6. 安全合规与监管：评测记录留痕。
7. 集团化与多租户治理：评测集可按 Org 扩展。
8. 集成与互操作：N·A。
9. 运维 / SRE / 国产化：可离线跑、国产浏览器可看。
10. 质量与真实性审计：★幻觉拦截、评分不注水。
11. AI / 模型治理与可降级：B0 产出也可入评测（基线分）。

## 适用不变量
- 命中核心约束：**铁律 #1 真实性**（幻觉拦截）· **核心 §13 真实性** · **#4 B0**。
- 本卡落点：AI 各能力统一回归评测 + 幻觉拦截 + 中文术语 + 版本趋势。

## 验收 + 验证
- [x] AC-1（FR-1~3）：回归出分 + 幻觉被拦。
- [x] AC-2（FR-4/5）：中文术语评测 + 版本趋势可比。
- T-GATE：后端真实性门禁全绿。
- B0 验收：★B0 产出可通过 `caseOutputs` 离线评测出基线分（不依赖 provider）。

## 完工证据
- 代码 permalink：评测集扩展 + 运行 + 幻觉拦截 + 趋势接口。
- 测试：`AiQualityEvaluatorTest`、`ModelEvalServiceTest`、`AiQualityEvalControllerSecurityTest`、`MigrationBaselineContractTest`、`H2BaselineMigrationTest`、`ServiceContractGovernanceTest`。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
