# SVC-QUALITY-01 · 质控驾驶舱服务包

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D4 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 S11 智能评估与整改 · 详规 院级质控总览 · 落地规划 §服务包。

## 身份
- 卡 ID：SVC-QUALITY-01（服务包卡）
- 域：D4 质控改进
- 关联场景：S11 智能评估与整改
- 依赖卡：[EVAL-01](EVAL-01.md) 评估 · [OPT-08](OPT-08.md) 价值口径 · [SVC-QUALITY-03](SVC-QUALITY-03.md) 整改 · 页 [QCDASH-01](QCDASH-01.md)/[QCALERT-01](QCALERT-01.md)
- 工作量：4d
- owner / reviewer：Codex / 待审（owner ≠ reviewer）

## 目标
把**院级质控驾驶舱**编排为服务包：院级指标、风险热力、价值指标、问题分布**可逐级下钻**到科室/病例/证据，全部真实、按作用域，并供质控预警消费。

## 现状（搬迁时核查 2026-05-30，以 `medkernel-backend` 为准）
聚合层待建（消费侧已有源）：评估（[EVAL-01](EVAL-01.md) `engine/evaluation`）、价值（[OPT-08](OPT-08.md)）、整改（[SVC-QUALITY-03](SVC-QUALITY-03.md) `RectificationTask`）数据源已成型。本卡＝建驾驶舱聚合 + 下钻编排，数据单一来自各源、不前端造数。

实现更新（2026-06-05）：已新增 `com.medkernel.engine.quality.dashboard` 服务包，按当前租户上下文读取 `quality_finding`、`rectification_task` 与 [OPT-08](OPT-08.md) 价值指标，生成院级聚合、科室热力、下钻证据包与确定性预警 read-model；预警事实落 `mk_quality_dashboard_alert`，同源预警幂等刷新、闭环后置为 `RESOLVED`，再次活跃可重新打开且不重复。QCALERT-01 本轮补齐 `POST /quality/alerts/{alertId}/acknowledge` 确认端点与 `severity` 服务端筛选，`HIGH_RISK` 映射 P0/P1；已确认预警在来源事实仍活跃时保持 `ACKNOWLEDGED`，来源闭环后由 read-model 刷新为 `RESOLVED`。

## 功能要求（原子可测条目）
- [x] FR-1 院级总览：指标/风险热力/价值/问题分布院级聚合，真实。
- [x] FR-2 逐级下钻：院 → 科 → 病例 → 证据，每层数据真实可追溯。
- [x] FR-3 风险热力：按科室/指标风险热力，颜色来自真实命中率（token 色阶）。
- [x] FR-4 预警驱动：阈值越界生成质控预警（页 [QCALERT-01](QCALERT-01.md)）。
- [x] FR-5 证据导出：任一下钻节点可导出证据包。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 端点：`GET /api/v1/engine/quality/dashboard` · `GET .../quality/dashboard/drilldown` · `GET .../quality/alerts` · `POST .../quality/alerts/{alertId}/acknowledge`
- DTO：驾驶舱聚合 Record（层级/指标/值/作用域）；信封 `ApiResult`/`ProblemDetail`
- 状态机：预警（`OPEN` → `ACKNOWLEDGED` → `RESOLVED`；来源事实重新活跃时从 `RESOLVED` 重新打开，确认态不被刷新回未处置）；trace（[OBS-01](../D0/OBS-01.md)）

## 数据与迁移
- 聚合复用 evaluation/value/rectification 表族；新增 `mk_quality_dashboard_alert` 预警 read-model（阈值 + 状态 + 组织字段 + 审计 + traceId），V83 五方言迁移（[BASE-05](../D0/BASE-05.md)）。

## 视角清单（11 视角逐条）
1. 产品架构：院级质控的"驾驶舱"服务编排。
2. 产品体验：★可下钻、热力直观（页 [QCDASH-01](QCDASH-01.md)）；老年/国产浏览器可读。
3. 系统与数据架构：聚合预计算；下钻 P95 ≤1.5s；大数据量。
4. 临床医疗安全：风险热力真实反映安全质控，不掩盖。
5. 知识与数据治理：下钻到证据可追溯版本。
6. 安全合规与监管：证据包导出留审计（[BASE-04](../D0/BASE-04.md)）。
7. 集团化与多租户治理：★集团/院/科逐级下钻按作用域。
8. 集成与互操作：消费 [EVAL-01](EVAL-01.md)/[OPT-08](OPT-08.md) 真实数据。
9. 运维 / SRE / 国产化：聚合可观测、可重算。
10. 质量与真实性审计：★无前端造数、热力来自真实命中率、下钻可追溯。
11. AI / 模型治理与可降级：N·A（确定性聚合）。

## 适用不变量
- 命中核心约束：**铁律 #1 真实性** · **核心 §9 多租户下钻** · **§13 真实可追溯**。
- 本卡落点：院级驾驶舱聚合 + 逐级下钻 + 预警，数据单一来自各源。

## 验收 + 验证
- [x] AC-1（FR-1/2）：院级聚合真实；逐级下钻到证据可追溯。
- [x] AC-2（FR-3/4）：热力真实；阈值越界生成预警。
- [x] AC-3（FR-5）：证据包可导出。
- 关联 A1–A9 剧本：A9 质控总览。
- T-GATE：前后端真实性门禁全绿（无造数/热力真实）。
- B0 验收：驾驶舱确定性聚合可用。

## 完工证据
- 代码：本 PR 覆盖 `QualityDashboardService` / `QualityDashboardController` / `QualityDashboard*` DTO、`QualityDashboardAlertRepository`、V83 五方言 `mk_quality_dashboard_alert` 迁移、`ServiceContractCatalog` 与 `DomainOwnershipCatalog`。
- 测试：`QualityDashboardServiceTest` 覆盖真实聚合、租户隔离、科室过滤、热力、价值指标消费、下钻证据包、预警幂等与同源重新打开；`QualityDashboardControllerSecurityTest` 覆盖未登录 401、质控角色可读、医生无权 403。
- 验证：`mvn -q -Dtest=QualityDashboardServiceTest,QualityDashboardControllerSecurityTest,ServiceContractGovernanceTest,DomainOwnershipContractTest,MigrationBaselineContractTest,H2BaselineMigrationTest test` 通过；`cd medkernel-backend && mvn -q test` 通过；`cd frontend && npm run verify` 通过（61 文件 / 375 测试）；`cd frontend && npm run build` 通过。
- T-GATE：真实性 changed 扫描 19 文件通过；迁移规约 changed 扫描 5 个 V83 SQL 通过；配置边界 changed 扫描 19 文件通过；`scripts/check-comment-zh.sh` 0 fail / 0 warn；`git diff --check origin/main...HEAD` 无输出。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
