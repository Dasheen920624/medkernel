# EMR-LEVEL-01 · 电子病历评级目标与项目映射

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D4 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 S23 电子病历评级支撑 · 落地规划 §评级 · 合规监管（评级标准）。

## 身份
- 卡 ID：EMR-LEVEL-01（治理/契约卡；评级目标映射单一归属）
- 域：D4 质控改进
- 关联场景：S23 电子病历评级支撑
- 依赖卡：[EMR-LEVEL-02](EMR-LEVEL-02.md) 评级证据 · [EVAL-01](EVAL-01.md) 评估 · [SVC-QUALITY-01](SVC-QUALITY-01.md) 驾驶舱
- 工作量：5d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
建立**电子病历评级目标与项目映射**：医院目标等级（4/5/6 级）→ 评级标准项 → 系统能力差距 → 实施任务清单，可追踪进度，**不假达标、差距真实**。

## 现状（2026-06-05，以 `medkernel-backend` 为准）
后端 B0 已建：`com.medkernel.engine.emrlevel` 提供评级目标、标准项能力映射、差距清单和进度查询；V86 五方言迁移落 `mk_emr_level_target/item/gap` 表族；缺证据的“已满足”会降级为 `MISSING_EVIDENCE`，并通过 evaluation owner 内桥接服务创建真实 `quality_finding` + `rectification_task`。评级数据质量和证据包仍归 [EMR-LEVEL-02](EMR-LEVEL-02.md)。

## 功能要求（原子可测条目）
- [x] FR-1 目标等级：医院设目标等级（4/5/6 级），关联评级标准项集。
- [x] FR-2 标准项映射：评级标准项 → 系统能力点 → 当前满足/差距，差距真实。
- [x] FR-3 实施任务：差距生成实施任务（关联 [SVC-QUALITY-03](SVC-QUALITY-03.md) 整改/任务）。
- [x] FR-4 进度追踪：评级达标进度按标准项真实统计、可下钻。
- [x] FR-5 不假达标：未满足项明确标差距，不前端标"已达标"。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 端点：`GET/PUT /api/v1/engine/emr-level/targets` · `GET .../emr-level/gaps` · `GET .../emr-level/progress`
- DTO：评级目标/标准项/差距 Record；信封 `ApiResult`/`ProblemDetail`
- 状态机：配置类（草稿→已发布→生效中）；trace（[OBS-01](../D0/OBS-01.md)）
- 幂等 / 版本：评级标准项版本化

## 数据与迁移
- 表族：`mk_emr_level_target` / `mk_emr_level_item` / `mk_emr_level_gap`（标准项 + 能力点 + 差距 + 版本 + 组织字段 + 审计）；V86 五方言（[BASE-05](../D0/BASE-05.md)）

## 视角清单（11 视角逐条）
1. 产品架构：评级支撑的"目标与差距地图"。
2. 产品体验：差距/进度可下钻（驾驶舱 [QCDASH-01](QCDASH-01.md)）。
3. 系统与数据架构：差距评估可复算；P95 ≤1s。
4. 临床医疗安全：评级以真实能力为准，不为达标弱化安全项。
5. 知识与数据治理：评级标准项版本化、可追溯。
6. 安全合规与监管：★评级作监管证据，差距真实、留审计（[BASE-04](../D0/BASE-04.md)）。
7. 集团化与多租户治理：按院作用域；集团可汇总各院评级。
8. 集成与互操作：能力点对接各引擎真实能力。
9. 运维 / SRE / 国产化：差距评估可观测、可重算。
10. 质量与真实性审计：★差距/进度真实、不假达标。
11. AI / 模型治理与可降级：N·A（确定性映射）。

## 适用不变量
- 命中核心约束：**铁律 #1 真实性（不假达标）** · **合规监管** · **§9 多租户作用域**。
- 本卡落点：评级目标 → 标准项 → 差距 → 实施任务映射，差距真实可追溯。

## 验收 + 验证
- [x] AC-1（FR-1/2）：目标等级与标准项映射、差距真实。
- [x] AC-2（FR-3/4）：差距生成实施任务；进度真实可下钻。
- [x] AC-3（FR-5）：未满足项标差距、不假达标。
- 关联 A1–A9 剧本：A9 评级支撑。
- T-GATE：后端真实性门禁全绿（差距真实/不假达标）。
- B0 验收：评级映射确定性、与模型无关。

## 大卡工序（5d）
- PR1：评级标准项 + 目标等级映射 + 门禁 → 本 PR 已覆盖
- PR2：能力差距评估 + 实施任务 → 本 PR 已覆盖
- PR3：进度追踪 + 下钻 → 本 PR 已覆盖

## 完工证据
- 代码 permalink：`EmrLevelController` / `EmrLevelService` / `EmrLevelRectificationBridge` / V86 五方言迁移。
- 测试：`EmrLevelServiceTest`、`EmrLevelControllerSecurityTest`、迁移基线与领域归属治理测试覆盖差距复算 / 实施任务 / 进度 / 不假达标。
- 本地验证：`mvn -q -Dtest=EmrLevelServiceTest,EmrLevelControllerSecurityTest test`、rebase 后 V86 clean 组合 `mvn -q clean -Dtest=EmrLevelServiceTest,EmrLevelControllerSecurityTest,MigrationBaselineContractTest,H2BaselineMigrationTest,FlywayMultiDialectSmokeTest,ServiceContractGovernanceTest,DomainOwnershipContractTest test`、后端全量 `mvn -q test`、前端 `npm run verify`（61 files / 375 tests）与 `npm run build` 均通过；V86 五方言在 H2/PostgreSQL/Oracle smoke 中均迁移至 v86 且重复 migrate no-op。changed T-GATE 已通过（真实性 13 文件、迁移规约 5 文件、配置边界 13 文件、中文注释 0 fail/0 warn、空白检查通过）。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
