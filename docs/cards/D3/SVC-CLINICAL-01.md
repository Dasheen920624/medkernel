# SVC-CLINICAL-01 · 患者与路径运行服务包

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D3 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 S1 集团与租户开通（患者主索引）· 详规 S8 临床嵌入运行 · 落地规划 §服务包。

## 身份
- 卡 ID：SVC-CLINICAL-01（服务包卡；`MpiPatient`/患者路径运行单一归属）
- 域：D3 临床运行
- 关联场景：S8 临床嵌入运行
- 依赖卡：[PATH-01](../D2/PATH-01.md) 路径引擎 · [API-01](../D2/API-01.md) 上下文 · [SYS-01](../D0/SYS-01.md) 标准模型 · 页 [PMI-01](PMI-01.md)/[PPATH-01](PPATH-01.md)
- 工作量：4d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
把**患者主索引（MPI）+ 患者路径运行 + 关键时钟**做成服务包：建/合并患者、查患者 360、入径、节点推进、关键时钟驱动到期提醒，全部真实数据、可追溯。

## 现状（搬迁时核查 2026-05-30，以 `medkernel-backend` 为准）
已有实质基础：`engine/mpi/` 下 `MpiService`/`MpiController` + `MpiPatient(+Repository)` + `MpiMergeRequest` + `MpiStatsResponse` + 测试；`engine/pathway/` 下 `ClinicalClock(+Repository)` 关键时钟。本卡＝把患者主索引 + 路径实例运行 + 时钟编排为服务包契约，路径模型归 [PATH-01](../D2/PATH-01.md)。

## PR1 收口核查（2026-06-04）
- MPI 已补齐建患者、查患者 360、合并与拆分归并；建患者按租户作用域、`mpiId` 幂等，合并/拆分均留状态迁移审计。当前 B0 以 `mpiId` 作为跨源归一后的业务键；外部来源标识映射归 [INTEG-01](../D2/INTEG-01.md) / 适配器进入 MPI，本卡不伪造外部主数据。
- 患者 360 聚合最新标准上下文快照、上下文详情和在径路径摘要；MPI 统计新增真实在径数量。
- 患者路径运行新增按租户 + 患者 + 状态筛选的服务端分页列表；入径、节点推进、关键时钟、变异追溯沿用既有真实路径运行服务并纳入本轮聚焦回归。
- 页面只做服务包消费侧必要接入：PMI 新增真实建患者 / 拆分交互，PPATH 清理本地 `sessionPathways`，改为后端分页列表。页面卡 [PMI-01](PMI-01.md) / [PPATH-01](PPATH-01.md) 的完整六态、RBAC、E2E 仍按各自页面卡验收。

## 功能要求（原子可测条目）
- [x] FR-1 患者主索引：建/查/合并患者（`MpiMergeRequest`），跨源唯一标识、合并可追溯可拆。
- [x] FR-2 患者 360：聚合标准上下文（[API-01](../D2/API-01.md)）呈现患者全景。
- [x] FR-3 入径：患者按已发布路径（[PATH-01](../D2/PATH-01.md)）入径，实例化路径节点。
- [x] FR-4 节点推进：节点状态推进 + 关键时钟（`ClinicalClock`）到期触发提醒/任务。
- [x] FR-5 统计：`MpiStatsResponse` 患者/在径统计真实、按作用域。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 端点：`POST /api/v1/engine/mpi/patients` · `GET .../mpi/patients` · `GET .../mpi/patients/{id}` · `GET .../mpi/stats` · `POST .../mpi/patients:merge` · `POST .../mpi/patients/{id}:split` · `GET .../pathway/patient-pathways` · `POST .../pathway/patient-pathways/enter`（入径）· `POST .../pathway/patient-pathways/{id}/advance`（推进）
- DTO：`MpiPatientCreateRequest` / `MpiMergeRequest` / `MpiSplitRequest` / `MpiPatientDetailResponse` / `MpiStatsResponse` + 路径实例 Record；信封 `ApiResult`/`ProblemDetail`
- 状态机：待办类（路径实例：未开始→进行中→完成/退出）+ 关键时钟到期
- 幂等 / traceId：建患者/入径幂等键；trace（[OBS-01](../D0/OBS-01.md)）

## 数据与迁移
- 复用 `mpi_patient` 表族（本卡归属）+ 路径实例/节点/时钟表（路径模型归 [PATH-01](../D2/PATH-01.md)）；本 PR 不新增迁移，继续遵守 [BASE-05](../D0/BASE-05.md) 五方言治理。

## 视角清单（11 视角逐条）
1. 产品架构：临床运行的"患者 + 路径"运行底座。
2. 产品体验：页 [PMI-01](PMI-01.md)/[PPATH-01](PPATH-01.md) 消费；患者 360 一屏。
3. 系统与数据架构：患者 10万级、合并幂等；时钟调度；P95 查询 ≤1s。
4. 临床医疗安全：合并不丢病史、可拆；入径只用 `ACTIVE` 路径版本。
5. 知识与数据治理：路径实例绑定路径版本（[SYS-08](../D2/SYS-08.md)）可追溯。
6. 安全合规与监管：建/合并/入径/推进留审计（[BASE-04](../D0/BASE-04.md)）。
7. 集团化与多租户治理：患者与路径实例按 `OrgContext`/院区作用域。
8. 集成与互操作：患者来自外部经适配器（[INTEG-01](../D2/INTEG-01.md)）+ 标准上下文。
9. 运维 / SRE / 国产化：时钟/调度可观测、可补偿。
10. 质量与真实性审计：★患者/在径统计真实、合并可追溯可拆。
11. AI / 模型治理与可降级：N·A（确定性运行；模型在 CDSS）。

## 适用不变量
- 命中核心约束：**铁律 #1 真实性** · **核心 §5 状态机** · **§7 权威版本** · **§9 多租户作用域**。
- 本卡落点：患者主索引 + 路径实例运行 + 关键时钟服务包，路径模型归 [PATH-01](../D2/PATH-01.md)。

## 验收 + 验证
- [x] AC-1（FR-1/2）：建/合并/查患者正确；患者 360 聚合真实。
- [x] AC-2（FR-3/4）：入径实例化、节点推进、时钟到期触发。
- [x] AC-3（FR-5）：统计真实、按作用域。
- 关联 A1–A9 剧本：A2 入径 · A3 节点推进。
- T-GATE：后端真实性门禁全绿（合并不丢病史 / 统计真实）。
- B0 验收：关模型患者/路径运行全可用。

## 完工证据
- 代码 permalink：`engine/mpi` + `engine/pathway` 实例/时钟服务包。
- 测试：合并幂等 / 入径 / 节点推进 / 时钟到期 / 统计。
- PR1 本地证据：`mvn -q -Dtest=ServiceContractGovernanceTest,MpiServiceTest,MpiControllerContractTest,PathwayEngineServiceTest,PathwayRepositoryTest test`；`mvn -q test`（含 H2、PostgreSQL 15.18、Oracle 21.3 迁移到 V76 并二次 no-op）；`npm test -- src/pages/clinical/Mpi.test.tsx src/pages/clinical/PatientPathways.test.tsx`；`npm run verify`（55 文件 / 320 测试）；`npm run build`；`npm audit --omit=dev --audit-level=moderate`；changed-mode T-GATE（真实性 / 配置边界 / 迁移规约 / 中文注释 / diff check）；Playwright `/login` 与 `/pathway/patients` 未登录回登录 smoke。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
