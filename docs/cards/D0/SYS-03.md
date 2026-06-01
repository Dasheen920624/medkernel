# SYS-03 · 关系库权威源与投影同步

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D0 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §7.5 数据存储与投影（L1400）· 落地规划 §9.4 数据权威源、图谱投影与 Dify 降级（L610）· 核心 §7 / #5。

## 身份
- 卡 ID：SYS-03
- 域：D0 登录域 / 平台脊柱
- 关联场景：S7 图谱与来源追溯（提供其投影底座）；高级工具「图谱查询」
- 依赖卡：[SYS-01](SYS-01.md)（标准对象权威源）· [BASE-04](BASE-04.md)（同步审计）
- 工作量：4d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标

确立**关系库为唯一业务权威源**，图数据库（Neo4j）/Dify/缓存只能是投影或执行器：投影**可关闭、可重建、可审计、可降级**，关闭后主链路诚实返回 `NOT_SYNCED` 仍真实可用（核心 #5/§11）。

## 功能要求（原子可测条目）

- [x] **FR-1 关系库唯一权威**：业务事实唯一权威源为院内关系库（核心 #5）；图/Dify/缓存不得成为唯一存储。
- [x] **FR-2 图投影可重建**：图投影（Neo4j）由关系库**重放重建**；删除图库后可从关系库完整重投影。
- [ ] **FR-3 Dify 仅执行器**：Dify 只作可选运行器，不存业务权威数据（核心 §11）。
- [ ] **FR-4 投影可关闭 + 诚实降级**：关闭图/Dify 投影后，依赖图的功能诚实返回 `NOT_SYNCED`/降级到关系库直查，主链路不中断（核心 §11）。
- [ ] **FR-5 同步可审计**：投影同步动作留真实审计（同步对象/版本/结果），**禁假同步证据**（核心 #18）。
- [x] **FR-6 一致性校验**：关系库 ↔ 投影 diff 可检测；不一致可重建对齐。

## 接口契约 / 页面契约
### 接口契约
- 端点：投影重建触发端点 + 投影状态/一致性查询端点（供 D6 图谱查询/D5 运维消费）。
- DTO：投影同步任务 Record；一致性报告 Record。
- 响应信封：`ApiResult`；降级状态 `NOT_SYNCED` 诚实返回。
- 状态机：投影同步任务用**待办类**状态机（核心 §3）。
- 幂等 / 错误码 / traceId：重建幂等（可重复重放）；`PROJECTION_DISABLED`/`NOT_SYNCED`；同步带 traceId + 审计。

### 页面契约
N·A —— 图谱查询页在 D6 高级工具消费；同步状态在 D5 运维。

## 数据与迁移
- 表族：`mk_projection_sync` / `mk_projection_snapshot`（同步任务 / 投影事实快照 / 一致性）；权威数据在各业务表（关系库）。原 `sys_projection_sync` 口径按当前迁移命名规约收敛为 `mk_projection_*`。
- 主键：ULID；索引：`target_type`、`status`、`ts`。
- 5 方言迁移：h2/postgres/oracle/dm/kingbase + 中文注释。

## 视角清单（11 视角逐条）
1. **产品架构**：★本卡主战场 —— 关系库权威 + 投影从属的架构铁律落地（核心 §7/#5）。
2. **产品体验**：图谱查询降级时诚实提示（非空白/非假数据，核心 §11）。
3. **系统与数据架构**：投影可重建 + 一致性校验；同步异步不阻塞主链路。
4. **临床医疗安全**：临床判断不依赖可能过期的投影；权威读关系库，防投影滞后致误判（核心 §6/#5）。
5. **知识与数据治理**：★知识图谱是投影，唯一权威知识在关系库（核心 §7）；图可重建保真。
6. **安全合规与监管**：同步审计留痕（[BASE-04](BASE-04.md)）；投影不绕权限。
7. **集团化与多租户治理**：投影按租户隔离重建。
8. **集成与互操作**：Dify 作为可选外部执行器经统一链路（核心 §10/§11），断连诚实降级。
9. **运维 / SRE / 国产化**：★投影可关闭 → 国产化/内网最小形态（仅关系库）仍可运行（核心 §12）。
10. **质量与真实性审计**：★禁假同步（时间戳哈希充同步证据，核心 #18）；同步证据真实。
11. **AI / 模型治理与可降级**：★Dify/图全关后主链路真实通过（核心 §11 降级诚实）。

## 适用不变量
- 命中核心约束：**#5 关系库权威/投影从属** · **§11 诚实降级（NOT_SYNCED）** · **§7 知识图谱为投影** · **#18 禁假同步**。
- 本卡落点：把"图/Dify 只能是投影或执行器、可关可重建可审计可降级"做成可验证事实，关闭投影后系统仍真实可用。

## 验收 + 验证
- [x] **AC-1（FR-1/2）**：删除图库后从关系库重投影，数据完整一致。
- [ ] **AC-2（FR-4）**：关闭图/Dify 投影 → 依赖功能返回 `NOT_SYNCED`/降级关系库直查，主链路不中断。
- [ ] **AC-3（FR-3）**：断言 Dify 不存任何业务权威字段（仅执行器）。
- [ ] **AC-4（FR-5）**：投影同步产真实审计（对象/版本/结果），无 UUID/时间戳充同步证据。
- [x] **AC-5（FR-6）**：制造关系库↔投影不一致 → 一致性校验可检测并重建对齐。
- 关联 A1–A9：A2 知识工厂（图投影）、A6 合规运维（降级）。
- T-GATE：后端门禁全绿（无假同步证据）。
- B0 验收：★关闭图/Dify/模型后主链路真实通过（本卡是降级诚实的关键验收）。

## 完工证据
- 代码 permalink：投影重建服务 / 一致性校验 / 降级诚实返回 / `mk_projection_sync` 迁移。
- 测试：图重建一致性测试 + 投影关闭降级测试 + Dify 无权威断言测试 + 同步审计真实性测试。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。

## 大卡工序（4d，后端）
- PR1：关系库权威 + 图投影重建 + 一致性校验 → AC-1/5。
- PR2：投影可关闭 + 诚实降级 + Dify 执行器解耦 + 同步审计 → AC-2/3/4。

### PR1 进度证据（不冒领 PR2）
- 关系库权威投影：新增 `ClinicalGraphProjectionSource`，从 SYS-01 的 12 类 `mk_clinical_*` 权威表生成 `CLINICAL_GRAPH` 节点 / 边事实；规范载荷排除患者姓名、身份证、手机号密文字段。
- 可重建：新增 `ProjectionSyncService`、`ProjectionSnapshot`、`ProjectionSync` 与 `POST /api/v1/projections/clinical-graph/rebuild`，重建时清空当前租户投影快照后由关系库重放写入，源集合 hash 与投影 hash 一致才返回 `SUCCESS`。
- 一致性校验：新增 `GET /api/v1/projections/clinical-graph/consistency` 与 `ProjectionConsistencyReport`，可检测 missing / extra / changed 三类差异。
- 权限与契约：新增 `projection.read` / `projection.rebuild`，并登记到 `ServiceContractCatalog`，避免新增裸接口；`DomainOwnershipCatalog` 登记 `engine-projection`，确保 `mk_projection_*` 表单一归属。
- 迁移：新增 V40 五方言 `mk_projection_sync` / `mk_projection_snapshot`；当前真实运行保障仍按项目修正范围聚焦 PostgreSQL + Oracle，达梦 / 人大金仓真实环境证据继续登记 `DEFER-001`。
- 本地证据：`ClinicalGraphProjectionSourceTest`、`ProjectionSyncServiceTest`、`ServiceContractGovernanceTest`、`OpenApiContractConfigurationTest`、`MigrationBaselineContractTest`、`H2BaselineMigrationTest`、`DomainOwnershipContractTest` 已通过；`mvn -B -q test` 后端全量通过 744 tests，包含 PostgreSQL 15 + Oracle 21 Testcontainers 迁移至 V40。
