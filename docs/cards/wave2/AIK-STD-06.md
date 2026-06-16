# AIK-STD-06 · 静默运行、反馈和回归评测

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [wave2 域简报](_brief.md)。
> 迁移来源：详规 §8 静默试运行 · backlog 第二波 X-AIK · 铁律 #4。

## 身份
- 卡 ID：AIK-STD-06（= backlog `AIK-STD-06`）
- 域：wave2（X-AIK）
- 关联场景：S3、S15
- 依赖卡：[AIK-STD-05](AIK-STD-05.md)（过门禁候选）· [OPT-06](OPT-06.md)（评测）· [CDSS-01](../D3/CDSS-01.md)
- 工作量：4d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
候选**静默试运行（影子模式）+ 收反馈 + 回归评测**后才提审：在不影响生产的影子环境验证候选质量，达标才进人工审核。

## 现状（核查 2026-06-16，本地分支 `codex/wave2-knowledge-model-readiness`）
[LLM-07](LLM-07.md) 已有 `mk_llm_regression_case` + `MedicalRegressionEvaluator` + provider 上线评测门禁。本地分支已把它复用于 AIK 生成期：新增 V138 五方言 `mk_knowledge_shadow_run`，记录候选提审前影子评测状态、命中/漏报/误报计数、退化标记与达标裁决；生成链路在 AIK-STD-05 门禁与 AIK-STD-10 分流后先跑影子评测，`NOT_READY/FAILED` 均阻断 `submitCandidate`，`PASSED/PENDING_REVIEW` 才允许进入人工审核；`GET /api/v1/engine/knowledge-production/jobs/{jobCode}/shadow-runs` 提供只读审计。

仍未冒领：真实事件流影子运行、人工反馈闭环、与现行权威版本逐项差异对比、前端展示和真实医学基准集独立验收仍留后续切片/P6 前置。

## 功能要求（原子可测条目）
- [ ] FR-1 影子运行：候选在影子模式跑真实事件流，**不影响生产**、不出 CDSS 卡给医师。本地分支已落提审前 B0 回归影子闸；真实事件流接入待后续。
- [ ] FR-2 反馈采集：采集影子命中/误报/漏报指标。本地分支已持久化命中/误报/漏报字段；人工反馈写入闭环待后续。
- [ ] FR-3 回归对比：与现行权威基线对比，质量退化标记。本地分支已以回归用例失败标记退化；现行权威逐项对比待 AIK-STD-09/11 联动。
- [x] FR-4 达标提审：达阈值才允许提审，未达诚实标。无基准集 `NOT_READY`、评测失败 `FAILED` 均阻断提审。
- [x] FR-5 不污染：影子数据隔离，不写生产病历/医嘱。当前仅写 `mk_knowledge_shadow_run` 影子证据，不触发 CDSS 卡、病历或医嘱。

## 接口 / 数据契约
- `mk_knowledge_shadow_run`（候选/影子结果/指标/退化/达标判定），五方言；复用 [LLM-07](LLM-07.md) `mk_llm_regression_case` 和 `MedicalRegressionEvaluator`。

## 视角清单（11 视角）
1. 产品架构：候选上线前的影子验证层。 2. 产品体验：N·A。 3. 系统与数据架构：影子隔离、不占生产链路。 4. 临床医疗安全：★影子不出医师、不进病历。 5. 知识与数据治理：质量退化拦截。 6. 安全合规与监管：影子运行留痕。 7. 集团化与多租户治理：按 org 影子。 8. 集成与互操作：N·A。 9. 运维/SRE/国产化：影子可离线跑。 10. 质量与真实性审计：★指标真实、不注水达标。 11. AI/模型治理与可降级：B0 候选也可影子评。

## 适用不变量
- 命中核心约束：**铁律 #4 B0** · **#3 医疗安全**（影子不出医师）· **#1 真实性**。
- 本卡落点：影子试运行 + 反馈 + 回归达标才提审，不污染生产。

## 验收 + 验证
- [ ] AC-1（FR-1/2）：影子运行 + 指标采集不影响生产。B0 回归影子闸已落；真实事件流/反馈闭环未完成。
- [ ] AC-2（FR-3/4）：回归对比 + 达标才提审。达标提审门已落；现行权威逐项对比未完成。
- T-GATE：后端真实性门禁全绿。
- B0 验收：★B0 候选可影子评测。

## 完工证据
- 本地代码：`KnowledgeShadowEvaluationService` + `KnowledgeShadowRun*` + V138 五方言迁移 + 生成链路 `SHADOW_EVAL` 阻断 + `shadow-runs` 只读端点。
- 测试：`KnowledgeShadowEvaluationServiceTest` 覆盖 `NOT_READY/PASSED/PENDING_REVIEW/FAILED`；`CandidateGenerationOrchestrationServiceTest` 覆盖影子未就绪阻断；`CandidateGenerationIntegrationTest` 覆盖真实 H2 提审前影子记录；`KnowledgeProductionControllerSecurityTest` 覆盖只读权限；迁移基线/H2/域归属覆盖 V138。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
