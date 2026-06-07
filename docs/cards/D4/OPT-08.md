# OPT-08 · 价值指标与 ROI 看板

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D4 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 S11 智能评估与整改 · 详规 价值/ROI 度量 · 落地规划 §价值证明。

## 身份
- 卡 ID：OPT-08（引擎/契约卡；价值/ROI 口径单一归属）
- 域：D4 质控改进
- 关联场景：S11 智能评估与整改
- 依赖卡：[EVAL-01](EVAL-01.md) 评估 · [CDSS-01](../D3/CDSS-01.md) 采纳率 · [SVC-CLINICAL-01](../D3/SVC-CLINICAL-01.md) 路径完成 · [SVC-QUALITY-01](SVC-QUALITY-01.md) 驾驶舱
- 工作量：4d
- owner / reviewer：Codex / 待审（owner ≠ reviewer）

## 目标
定义并落地**价值/ROI 口径**：采纳率、误报率、漏报回溯、路径完成率、整改闭环率、医保违规减少，全部**真实可追溯、口径单一、不假指标**，供院级驾驶舱与 GA 价值证明消费。

## 现状（搬迁时核查 2026-05-30，以 `medkernel-backend` 为准）
基本待建：现有无统一价值/ROI 口径聚合。本卡＝新建价值指标聚合层，**口径单一归属在此**，消费 [EVAL-01](EVAL-01.md) 评估 + [CDSS-01](../D3/CDSS-01.md) 采纳/疲劳 + [SVC-CLINICAL-01](../D3/SVC-CLINICAL-01.md) 路径完成 的真实数据，非各页自算。

当前实现：后端 B0 聚合 API 使用 `recommendation_card`、`quality_finding`、`patient_pathway`、`rectification_task` 与 `mk_quality_insurance_issue` 真实事实实时复算。医保违规减少率比较查询期与等长前置基线期；缺时间窗或基线样本时返回 `NOT_AVAILABLE`。

## 功能要求（原子可测条目）
- [x] FR-1 口径定义：6 类价值指标口径受控、版本化、可解释（公式 + 数据源）。
- [x] FR-2 真实聚合：各指标从真实运行/评估数据聚合，不前端造数、不写死。
- [x] FR-3 漏报回溯：漏报可回溯到具体病例与原因。
- [x] FR-4 趋势/对比：按时间/科室/院区趋势与对比，按作用域。
- [x] FR-5 降级诚实：数据源缺失标 `NOT_AVAILABLE`，不用 0/假值充数。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 端点：`GET /api/v1/engine/value-metrics`（聚合）· `GET .../value-metrics/{id}/drilldown`（下钻回溯）
- 查询过滤：`from` / `to` 时间窗；`departmentId` 对 `quality_finding` / `rectification_task` 真实过滤；`hospitalId` / `campusId` 在当前事实源无对应字段时返回 `NOT_AVAILABLE`，不退回租户总量冒充对比。
- DTO：价值指标 Record（口径/值/数据源/版本/作用域）；信封 `ApiResult`/`ProblemDetail`
- 状态机：N·A（只读聚合）；trace（[OBS-01](../D0/OBS-01.md)）
- 幂等 / 口径版本：口径版本化、聚合可复算

## 数据与迁移
- B0 本批不新建表：实时只读聚合现有关系库权威事实，避免在事实源未齐时写入快照假数。
- 事实源：`recommendation_card`（采纳率）、`quality_finding`（误报率 / 漏报回溯）、`patient_pathway`（路径完成率）、`rectification_task`（整改闭环率）。
- 医保违规减少：消费 [SVC-QUALITY-02](SVC-QUALITY-02.md) 的 `mk_quality_insurance_issue`，按租户、科室和时间窗计算并支持问题下钻。

## 视角清单（11 视角逐条）
1. 产品架构：全平台价值证明的"指标口径中枢"。
2. 产品体验：驾驶舱可下钻（页 [QCDASH-01](QCDASH-01.md)）。
3. 系统与数据架构：聚合可复算；大数据量预聚合；P95 ≤2s。
4. 临床医疗安全：误报/漏报口径真实，避免掩盖安全问题。
5. 知识与数据治理：口径版本化、可追溯数据源。
6. 安全合规与监管：价值数据可作监管/考核证据（[BASE-04](../D0/BASE-04.md) 审计）。
7. 集团化与多租户治理：按集团/院/科下钻；口径平台统一。
8. 集成与互操作：消费 D3/D4 多源真实数据。
9. 运维 / SRE / 国产化：聚合任务可观测、可重算。
10. 质量与真实性审计：★口径单一、聚合可复算、缺数据诚实标记、不造数。
11. AI / 模型治理与可降级：N·A（确定性聚合；模型相关指标如采纳率来自真实反馈）。

## 适用不变量
- 命中核心约束：**铁律 #1 真实性（不假指标）** · **核心 §13 真实性** · **§9 多租户下钻**。
- 本卡落点：价值/ROI 口径单一归属 + 真实可复算聚合，缺数据诚实标记。

## 验收 + 验证
- [x] AC-1（FR-1/2）：6 口径定义清晰、真实聚合可复算。
- [x] AC-2（FR-3/4）：漏报可回溯；趋势/对比按作用域。
- [x] AC-3（FR-5）：缺数据标 `NOT_AVAILABLE`，不充假值。
- 关联 A1–A9 剧本：A9 价值证明。
- T-GATE：committed-diff 真实性门禁 / 配置边界 / 迁移规约 / 中文注释 / diff 检查均已通过（口径单一/无造数）。
- B0 验收：价值聚合确定性、与模型无关。

## 完工证据
- 代码 permalink：待 PR 合并后回填；当前分支 `codex/d4-opt-08`。
- 代码范围：`ValueMetricsService` / `ValueMetricsController` / `ValueMetric*` DTO；`ServiceContractCatalog` 登记 `value-metrics` 只读契约。
- 测试：`ValueMetricsServiceTest` 覆盖 6 口径复算、漏报下钻、跨租户隔离、院区缺维度 `NOT_AVAILABLE`；`ValueMetricsControllerSecurityTest` 覆盖未认证 / 质控角色 / 医师越权。
- 已跑验证：`mvn -q -Dtest=ValueMetricsServiceTest,ValueMetricsControllerSecurityTest test`；`mvn -q -Dtest=ValueMetricsServiceTest,ValueMetricsControllerSecurityTest,ServiceContractGovernanceTest,DomainOwnershipContractTest test`；`mvn -q test`；`npm run verify`（61 文件 / 375 测试）；`npm run build`；`node scripts/authenticity-guard.mjs --mode=changed --base=origin/main`（11 文件）；`node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main`（11 文件）；`node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main`（0 文件）；`scripts/check-comment-zh.sh`（0 fail/0 warn）；`git diff --check origin/main..HEAD`（无输出）。
- 审计员签字：@待审（owner ≠ reviewer）。
