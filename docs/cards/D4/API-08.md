# API-08 · 评估质控 API

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D4 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 S9 病历内涵质控 · S11 智能评估与整改 · 详规 §1.4 评估入参。

## 身份
- 卡 ID：API-08（引擎/API 卡）
- 域：D4 质控改进
- 关联场景：S9 病历内涵质控 · S11 智能评估与整改
- 依赖卡：[EVAL-01](EVAL-01.md) 评估引擎（单一归属）· [SVC-QUALITY-03](SVC-QUALITY-03.md) 整改 · [BASE-03](../D0/BASE-03.md) API 契约 · [API-13](../D0/API-13.md) 大列表
- 工作量：3d
- owner / reviewer：Codex / 待审（owner ≠ reviewer）

## 目标
把评估质控能力**契约化**：指标配置、评估运行、结果查询、问题列表、整改派发、复核闭环，全部真实、可追溯、可降级。

## 现状（搬迁时核查 2026-05-30，以 `medkernel-backend` 为准）
已有实质基础：`engine/evaluation/` 下 `EvaluationEngineController` + `EvaluationIndicator{+CreateRequest/Filter/Status}` + `EvaluationResult{+Filter/Level/Request}` + `EvaluationEvaluateSnapshotRequest` + 整改 `RectificationTask/Review`。本卡＝把控制器契约化为统一 API（指标/运行/结果/问题/整改/复核），命中/闭环逻辑归 [EVAL-01](EVAL-01.md)/[SVC-QUALITY-03](SVC-QUALITY-03.md)。

## 功能要求（原子可测条目）
- [x] FR-1 指标 CRUD：配置/查询评估指标（[API-13](../D0/API-13.md) 分页）。
- [x] FR-2 评估运行：对病例快照（`EvaluationEvaluateSnapshotRequest`）跑评估，幂等（`EvaluationIdempotencyKey`）。
- [x] FR-3 结果/问题：查评估结果（`EvaluationResult` + level）与生成的问题列表。
- [x] FR-4 整改/复核：派整改、提交、复核（[SVC-QUALITY-03](SVC-QUALITY-03.md)）契约暴露。
- [x] FR-5 降级：关模型只跑确定性指标命中 + `MODEL_DISABLED`。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 端点：`GET/POST /api/v1/engine/evaluation/indicators` · `POST /api/v1/engine/evaluation:evaluate` · `GET /api/v1/engine/evaluation/results` · `GET /api/v1/engine/evaluation/issues` · `POST /api/v1/engine/evaluation/rectifications` · `POST /api/v1/engine/evaluation/rectifications/{findingId}/review`
- DTO：`EvaluationIndicatorCreateRequest` / `EvaluationEvaluateSnapshotRequest` / `EvaluationResultRequest` / `RectificationSubmitRequest`；信封 `ApiResult`/`ProblemDetail`
- 状态机：待办类（问题：待整改→整改中→已复核/已豁免）；评估幂等
- 幂等 / 错误码 / traceId：`EvaluationIdempotencyKey`；trace（[OBS-01](../D0/OBS-01.md)）

## 数据与迁移
- 复用 `evaluation_indicator` / `evaluation_result` / `rectification_*` 表族（[EVAL-01](EVAL-01.md)/[SVC-QUALITY-03](SVC-QUALITY-03.md) 归属）；本卡不另立模型。

## 视角清单（11 视角逐条）
1. 产品架构：质控评估对外契约层（命中归 [EVAL-01](EVAL-01.md)）。
2. 产品体验：N·A（页面在 [EVALSET-01](EVALSET-01.md)/[EVALRES-01](EVALRES-01.md)）。
3. 系统与数据架构：评估批量、结果大表分页；P95 ≤1s；幂等。
4. 临床医疗安全：评估只判定不改医嘱；问题可追溯病历证据。
5. 知识与数据治理：指标/评估绑规则版本（[SYS-08](../D2/SYS-08.md)）可追溯。
6. 安全合规与监管：评估/整改/复核留审计（[BASE-04](../D0/BASE-04.md)）。
7. 集团化与多租户治理：指标/结果按 `OrgContext`/科室作用域。
8. 集成与互操作：评估消费 D3 运行数据（[CDSS-01](../D3/CDSS-01.md)）。
9. 运维 / SRE / 国产化：评估任务可观测、可重跑。
10. 质量与真实性审计：★评估命中真实、无伪造问题/闭环率。
11. AI / 模型治理与可降级：模型增强为挂点，关闭确定性指标 `MODEL_DISABLED`。

## 适用不变量
- 命中核心约束：**铁律 #1 真实性** · **核心 §5 状态机闭环** · **§1.4 统一入参** · **§11 B0**。
- 本卡落点：评估/问题/整改/复核统一契约，命中归 [EVAL-01](EVAL-01.md)。

## 验收 + 验证
- [x] AC-1（FR-1/2/3）：指标/评估/结果契约正确；评估幂等。
- [x] AC-2（FR-4）：整改/复核闭环契约可达。
- [x] AC-3（FR-5）：关模型确定性评估仍可用。
- 关联 A1–A9 剧本：A9 评估与整改。
- T-GATE：后端真实性门禁全绿（无伪造问题/闭环率）。
- B0 验收：关模型评估契约可用。

## 完工证据
- 代码 permalink：`engine/evaluation` 控制器契约化 + 整改契约（PR 合并后回填）。
- 测试：红灯先失败（canonical 单数路径 / `evaluation:evaluate` / `issues` / 整改复核 / `MODEL_DISABLED` 缺失；坏资源 JSON 与坏指标 DSL 静默降级）；绿灯已跑 `mvn -q -Dtest=EvaluationEngineServiceTest,EvaluationEngineApiContractTest,EvaluationEngineIntegrationTest,EvaluationEngineControllerSecurityTest,EvaluationRepositoryTest test`、`mvn -q -Dtest=EvaluationEngineApiContractTest,ServiceContractGovernanceTest,OpenApiContractConfigurationTest test`、`cd medkernel-backend && mvn -q test`、`npm test -- --run src/shared/api/hooks.test.ts`（43 测试通过）、`cd frontend && npm run verify`（61 文件 / 375 测试通过）。
- T-GATE：committed-diff 已跑 `node scripts/authenticity-guard.mjs --mode=changed --base=origin/main`（扫描 7 文件）、`node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main`（扫描 6 文件）、`node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main`（无迁移文件）、`scripts/check-comment-zh.sh`（0 fail / 0 warn）、`git diff --check origin/main..HEAD`（无输出）。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
