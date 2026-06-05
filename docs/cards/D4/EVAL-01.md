# EVAL-01 · 评估质控引擎

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D4 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 S9 病历内涵质控 · S11 智能评估与整改 · 核心 §11 B0 先于模型。

## 身份
- 卡 ID：EVAL-01（引擎卡；`EvaluationIndicator`/`EvaluationResult` 单一归属）
- 域：D4 质控改进
- 关联场景：S9 病历内涵质控 · S11 智能评估与整改
- 依赖卡：[RULE-01](../D2/RULE-01.md) 规则引擎 · [SYS-08](../D2/SYS-08.md) 权威版本 · [CDSS-01](../D3/CDSS-01.md) 运行数据 · [API-08](API-08.md) 契约 · [SVC-QUALITY-03](SVC-QUALITY-03.md) 整改
- 工作量：5d
- owner / reviewer：Codex / 待审（owner ≠ reviewer）

## 目标
把评估质控做成 **B0 真实**：配置指标（复用规则引擎）→ 对真实病例命中 → 生成问题（分级）→ 触发整改 → 复核闭环，**不写死指标、不前端造质控数、关模型仍可跑**。

## 现状（搬迁时核查 2026-05-30，以 `medkernel-backend` 为准）
已有实质基础：`engine/evaluation/` 下 `EvaluationEngineService` + `EvaluationIndicator{+CreateRequest/Filter/Repository/Status}` + `EvaluationResult{+Filter/Level/Repository/Request}` + `EvaluationEvaluateSnapshotRequest` + `EvaluationIdempotencyKey/Operation`。本卡＝把"指标配置 + 病例命中 + 问题生成 + 整改触发"框架化为引擎核心，命中复用 [RULE-01](../D2/RULE-01.md)，非从零。

## 功能要求（原子可测条目）
- [x] FR-1 指标配置：指标基于规则引擎（[RULE-01](../D2/RULE-01.md)）条件树，版本化（[SYS-08](../D2/SYS-08.md)）、不写死。
- [x] FR-2 病例命中：对病例快照评估命中，结果带级别（`EvaluationResultLevel`）、可解释。
- [x] FR-3 问题生成：命中违规生成问题，追溯到病历证据与指标版本。
- [x] FR-4 整改触发：问题派整改任务（[SVC-QUALITY-03](SVC-QUALITY-03.md) `RectificationTask`）。
- [x] FR-5 幂等复现：同快照+指标版本评估通过稳定 `inputDigest` / `runCode` 重放可复现；整改/复核继续使用 `EvaluationIdempotencyKey`。
- [x] FR-6 B0 降级：模型语义增强为挂点，关闭只用确定性指标命中 `MODEL_DISABLED`。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 对外端点契约归 [API-08](API-08.md)；本卡负责引擎内命中算法 + `EvaluationIndicatorStatus`（配置类状态机）+ 问题生成。
- 幂等 / traceId：评估幂等可复现；命中链路 trace（[OBS-01](../D0/OBS-01.md)）。

## 数据与迁移
- 表族：`evaluation_indicator` / `evaluation_result`（命中 + 级别 + 证据引用 + 版本 + 组织字段 + 审计）；五方言（[BASE-05](../D0/BASE-05.md)）
- 唯一约束：同适用域指标唯一 `ACTIVE`；索引：病例/级别/科室

## 视角清单（11 视角逐条）
1. 产品架构：质控评估核心引擎，消费 D3 真实运行。
2. 产品体验：评估结果可解释、可下钻（页 [EVALRES-01](EVALRES-01.md)）。
3. 系统与数据架构：批量评估 10万病例级；命中可复现；P95 ≤1s。
4. 临床医疗安全：评估判定不改医嘱；问题须有病历证据、不臆造。
5. 知识与数据治理：★指标版本化、命中可追溯证据与版本（[SYS-08](../D2/SYS-08.md)）。
6. 安全合规与监管：评估/问题留审计（[BASE-04](../D0/BASE-04.md)）。
7. 集团化与多租户治理：指标按集团/院/科继承；安全质控不可下级关。
8. 集成与互操作：消费 D3 运行（[CDSS-01](../D3/CDSS-01.md)/[SVC-CLINICAL-01](../D3/SVC-CLINICAL-01.md)）。
9. 运维 / SRE / 国产化：评估可观测、可重跑复现。
10. 质量与真实性审计：★无写死指标、无伪造问题、命中可复现。
11. AI / 模型治理与可降级：★B0 确定性指标优先；模型增强挂点，关闭 `MODEL_DISABLED`。

## 适用不变量
- 命中核心约束：**核心 §11 B0** · **§13 真实性** · **§7 唯一权威** · **§5 状态机** · **铁律 #1/#2**。
- 本卡落点：确定性指标命中 + 问题生成 + 整改触发，命中复用 [RULE-01](../D2/RULE-01.md)。

## 验收 + 验证
- [x] AC-1（FR-1/2）：指标版本化不写死；命中带级别可解释。
- [x] AC-2（FR-3/4）：问题追溯证据；派整改任务。
- [x] AC-3（FR-5/6）：评估可复现；关模型确定性命中仍跑。
- 关联 A1–A9 剧本：A9 评估与整改。
- T-GATE：后端真实性门禁全绿（无写死指标/无伪造问题）。
- B0 验收：★关模型评估命中 + 问题 + 整改触发全可用。

## 大卡工序（5d）
- PR1：指标配置（复用规则）+ 命中 + 门禁 → 验收
- PR2：问题生成 + 证据追溯 + 整改触发 → 验收
- PR3：幂等复现 + B0 降级 → 验收

## 完工证据
- 代码 permalink：本分支 `codex/d4-eval-01` 变更 `medkernel-backend/src/main/java/com/medkernel/engine/evaluation/EvaluationEngineService.java` 与 `EvaluationRunRepository.java`；PR 合并后补 GitHub permalink。
- 红灯证据：`mvn -q -Dtest=EvaluationEngineServiceTest#createIndicatorRejectsNaturalLanguageRuleDefinitionBeforeDrafting+evaluateSnapshotPersistsRuleExplanationIntoResultAndFindingEvidence+evaluateSnapshotUsesStableRunCodeAndDigestForSameSnapshotAndIndicatorVersion test` 曾失败于自然语言规则未拒绝、证据摘要缺规则解释、自动 `runCode` 不稳定；`mvn -q -Dtest=EvaluationEngineServiceTest#evaluateSnapshotReplaysExistingStableRunWithoutDuplicatingFacts test` 曾因缺 `findByRunCodeAndTenantId` 编译失败。
- 绿灯证据：`mvn -q -Dtest=EvaluationEngineServiceTest,EvaluationEngineIntegrationTest,EvaluationEngineControllerSecurityTest,EvaluationRepositoryTest test` 通过；新增真实 H2 仓储集成用例覆盖自动评估首次落库后同快照重放，`evaluation_run` / `evaluation_result` / `quality_finding` / `rectification_task` 均不重复。
- 全量验证：`cd medkernel-backend && mvn -q test` 通过（含 PostgreSQL 15.18 / Oracle 21.3 Testcontainers 82 个迁移烟测）；`cd frontend && npm run verify` 通过（61 文件 / 375 测试）；committed-diff T-GATE 通过（真实性 2 文件、配置边界 2 文件、迁移规约 0 文件、中文注释 0 fail/0 warn、`git diff --check origin/main..HEAD` 无输出）。
- 审计员签字：@待审（owner ≠ reviewer）。
