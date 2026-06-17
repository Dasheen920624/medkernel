# AIK-STD-09 · 权威知识替换、旧版失效与影响处置

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [wave2 域简报](_brief.md)。
> 迁移来源：详规 §8 权威替换 · backlog 第二波 X-AIK · 铁律 #6 唯一权威知识。

## 身份
- 卡 ID：AIK-STD-09（= backlog `AIK-STD-09`）
- 域：wave2（X-AIK）
- 关联场景：S3、S15
- 依赖卡：[SYS-08](../D2/SYS-08.md)（原子替换框架）· [MED-C3](../D3/MED-C3.md)（撤回/旧版隔离）· [AIK-STD-10](AIK-STD-10.md)
- 工作量：4d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
新版审过后**原子替换旧版 + 旧版失效 + 受影响病例/路径处置**：复用既有替换框架，AI 生成侧接入、不另起。

## 现状（核查 2026-06-17）
承载＝D2 [SYS-08](../D2/SYS-08.md) 权威原子替换 + D3 [MED-C3](../D3/MED-C3.md) 安全撤回与旧版下游隔离已建。本卡＝**AI 生成资产接入替换链**。

2026-06-16 本地进展（`codex/wave2-knowledge-model-readiness`）：AIK-STD-13 物化候选已真实进入
`KnowledgeAssetVersion(PENDING_REPLACEMENT_REVIEW)` + `CandidateClassification`；`KnowledgeVersionService.reviewCandidate(APPROVE)`
已委派 `activate(...)` 走 SYS-08 原子替换。新增 `CandidateCoexistenceService` 与
`GET /api/v1/engine/knowledge-production/candidates/coexistence?candidateRef=...`，按生产候选引用返回现行
`ACTIVE`、待审候选、分类差异、生产血缘与 `APPROVE_REPLACE_ACTIVE` 替换提醒。

2026-06-17 本地分支补齐替换处置缺口：`activate(...)` 替换已有 `ACTIVE` 时同步落
`KnowledgeInvalidationType.SUPERSEDED_REPLACEMENT`，并复用 `mk_knowledge_affected_case_task`
派发医师复核、包补同步、同步告警三类任务；`SUPERSEDED` 旧版可在同一原子替换链路回滚，lineage 写
`SupersessionType.ROLLBACK`；`WITHDRAWN` 高危版本仍返回 `ROLLBACK_SAFETY_DENIED`。不新增 AI 专属影响任务表。

## 功能要求（原子可测条目）
- [x] FR-1 原子替换：审过新版接 [SYS-08](../D2/SYS-08.md) 原子替换旧版（唯一有效约束）。AI 生产候选物化后走同一 `reviewCandidate(APPROVE) → activate(...)` 主链路。
- [x] FR-2 旧版失效：旧版隔离不再执行（接 [MED-C3](../D3/MED-C3.md)）。候选共存视图明确审核前仍由现行 `ACTIVE` 执行，审过后旧版按 SYS-08 退出新临床决策。
- [x] FR-3 影响处置：受影响患者/路径自动生成复核任务。替换旧 `ACTIVE` 时落失效记录并派三类影响处置任务；当前 B0 不伪造患者清单，按知识版本、包依赖和同步目标范围派发。
- [x] FR-4 可回滚：替换可回滚到旧版。`SUPERSEDED` 旧版可重新激活，当前 `ACTIVE` 同事务退出并写 `ROLLBACK` lineage；高危撤回版仍禁止一键回滚。
- [x] FR-5 紧急失效：召回/禁忌升级可紧急停用旧版。复用 `withdraw(...)` + MED-C3 安全撤回链。

## 接口 / 数据契约
- 复用 SYS-08/MED-C3 表：`knowledge_supersession`、`mk_knowledge_invalidation`、`mk_knowledge_affected_case_task`，五方言。替换失效类型为 `SUPERSEDED_REPLACEMENT`，影响处置统一落三类受影响任务。

## 视角清单（11 视角）
1. 产品架构：AI 资产权威化的替换层。 2. 产品体验：N·A。 3. 系统与数据架构：替换事务原子。 4. 临床医疗安全：★旧版隔离 + 受影响病例复核。 5. 知识与数据治理：★唯一有效版本（核心 §6）。 6. 安全合规与监管：替换留痕可审计。 7. 集团化与多租户治理：替换按 org 作用域。 8. 集成与互操作：N·A。 9. 运维/SRE/国产化：N·A。 10. 质量与真实性审计：影响病例真实生成。 11. AI/模型治理与可降级：替换与产出方式无关。

## 适用不变量
- 命中核心约束：**铁律 #6 唯一权威知识** · **#5 关系库权威** · **核心 §6 原子替换**。
- 本卡落点：AI 资产接原子替换 + 旧版隔离 + 影响处置 + 可回滚/紧急失效。

## 验收 + 验证
- [x] AC-1（FR-1/2）：原子替换 + 旧版隔离。证据：`KnowledgeVersionServiceTest.approveCandidateDelegatesToAtomicActivationFlow` + `CandidateCoexistenceServiceTest.pendingCandidateShowsActiveVersionAndBlocksCandidateExecution`。
- [x] AC-2（FR-3~5）：影响复核任务 + 回滚 + 紧急失效。证据：`KnowledgeVersionServiceTest.activateReplacingPriorActiveDemotesItToSuperseded` 覆盖替换影响任务；`activateSupersededVersionRollsBackThroughTheSameAtomicReplacementFlow` 覆盖旧版回滚；`withdrawHighRiskVersionCreatesInvalidationTasksAndProjectionRefresh` + `activateRejectsWithdrawnHighRiskVersionAsUnsafeRollback` 覆盖紧急失效与高危回滚护栏。
- T-GATE：后端真实性门禁全绿。
- B0 验收：★替换链不依赖模型（确定性）。

## 完工证据
- 代码 permalink：`KnowledgeVersionService.activate/withdraw` + `KnowledgeInvalidationType.SUPERSEDED_REPLACEMENT` + `mk_knowledge_invalidation` / `mk_knowledge_affected_case_task` 五方言基线。
- 测试：`KnowledgeVersionServiceTest` 替换 / 隔离 / 影响 / 回滚 / 紧急。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
