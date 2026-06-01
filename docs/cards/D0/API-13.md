# API-13 · 大规模列表 API

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D0 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §1.2.1 大规模知识与资产列表交互（L69）· 体验规范 §6 大规模知识与资产体验（L121）· 落地规划 §11.1 API 规范。

## 身份
- 卡 ID：API-13
- 域：D0 登录域 / 平台脊柱
- 关联场景：横切（所有大列表的服务端口径）
- 依赖卡：[BASE-03](BASE-03.md)（PageResult 信封）· [BASE-08](BASE-08.md)（前端分页/导出消费）
- 工作量：3d
- owner / reviewer：Codex / 待 PR reviewer（owner ≠ reviewer）

## 目标

交付**统一大规模列表 API**：分页/游标/排序/过滤/total_estimate/异步导出标准口径，支撑 10 万级知识与资产列表服务端分页，杜绝前端全量加载（核心 #16）。

## 功能要求（原子可测条目）

- [x] **FR-1 统一分页**：offset 分页 + cursor 游标分页（深翻页用游标，稳定不漂移），统一 `PageResult<T>`（[BASE-03](BASE-03.md)）。
- [x] **FR-2 排序/过滤白名单**：排序字段 + 过滤条件走**白名单**（防 SQL 注入与任意字段排序拖垮库）。
- [x] **FR-3 total_estimate**：大表返回估算总数（避免 `count(*)` 全表扫），精确总数可选异步。
- [x] **FR-4 异步导出**：超阈值导出走异步任务（与 [BASE-08](BASE-08.md) 导出任务同源），不同步拼大响应。
- [x] **FR-5 服务端分页强制**：默认每页上限 + 禁一次性返回全集（核心 #16，门禁联动）。
- [x] **FR-6 10 万级性能**：游标分页 P95 稳定（覆盖索引），深翻页不退化。

## 接口契约 / 页面契约
### 接口契约
- 端点：`GET /api/v1/large-lists/audit-events/list?cursor=&size=&offset=&sort=&filter=` + `POST /api/v1/large-lists/exports` 异步导出触发端点。
- DTO：`PageQuery`（cursor/size/offset/sort/filters 白名单）+ `PageResult<T>`（items/nextCursor/totalEstimate/totalEstimated/hasMore）Record + Bean Validation。
- 响应信封：`ApiResult<PageResult<T>>`。
- 状态机：导出任务用**待办类**状态机（核心 §3）。
- 幂等 / 错误码 / traceId：导出任务幂等键；`SORT_FIELD_NOT_ALLOWED`/`PAGE_SIZE_EXCEEDED`；带 traceId。

### 页面契约
N·A —— 前端分页表格/导出在 [BASE-08](BASE-08.md) 复用本 API。

## 数据与迁移
- 表族：复用 `audit_event` 与 BASE-08 导出任务表；V37 为审计大列表补 `tenant_id + id/action/resource_type/actor_user_id` 覆盖索引支撑游标。
- 5 方言迁移：H2 / PostgreSQL / Oracle / DM / Kingbase 均新增 `V37__large_list_audit_event_indexes.sql`；当前运行范围只保障 PostgreSQL + Oracle，达梦 / 人大金仓真实环境适配归 [DEFER-001](../../audit/deferred-issues.md)。

## 视角清单（11 视角逐条）
1. **产品架构**：大列表查询单一口径；各资产列表 API 复用，不各自造分页。
2. **产品体验**：服务端分页 + total_estimate + 默认 ≤3 筛选（核心 #16、体验契约），列表秒开。
3. **系统与数据架构**：★本卡主战场 —— 游标分页 + 覆盖索引 + total_estimate，10 万级 P95 稳定、深翻页不退化。
4. **临床医疗安全**：N·A。
5. **知识与数据治理**：知识/资产大列表（来源/候选/规则/路径）统一服务端分页与筛选。
6. **安全合规与监管**：过滤白名单防注入；导出走审计 + 脱敏（[BASE-04](BASE-04.md)/核心 §8）。
7. **集团化与多租户治理**：列表查询自动带组织作用域（[BASE-01](BASE-01.md)），跨租户不串。
8. **集成与互操作**：N·A。
9. **运维 / SRE / 国产化**：避免 count 全表/前端全量，保护国产化库性能。
10. **质量与真实性审计**：★禁前端全量加载（核心 #16）；门禁校验列表必服务端分页。
11. **AI / 模型治理与可降级**：N·A。

## 适用不变量
- 命中核心约束：**#16 服务端分页/禁前端全量** · **§8 过滤白名单防注入/导出脱敏** · **§7 组织作用域** · **#7 Record DTO**。
- 本卡落点：游标分页 + 白名单 + total_estimate + 异步导出标准 API，让 10 万级列表服务端可承、前端禁全量。

## 验收 + 验证
- [x] **AC-1（FR-1/6）**：10 万行表游标深翻页 P95 稳定、结果无漂移/重复。
- [x] **AC-2（FR-2）**：非白名单排序字段请求 → `SORT_FIELD_NOT_ALLOWED` 拒绝。
- [x] **AC-3（FR-3）**：大表列表返回 total_estimate 不触发全表 count。
- [x] **AC-4（FR-4/5）**：超阈值导出转异步；请求超大 page size → `PAGE_SIZE_EXCEEDED`。
- [x] **AC-5（FR-5）**：前端全量加载写法被门禁/测试拒。
- 关联 A1–A9：横切（知识/质控/审计大列表）。
- T-GATE：前后端门禁全绿（无前端全量加载）。
- B0 验收：纯确定性查询，天然 B0。

## 完工证据
- 代码 permalink：待 PR 合并后回填；本分支落点为 `PageQuery` / `PageResult`、`LargeListResourceDefinition`、审计列表游标分页实现、白名单校验、异步导出触发复用、V37 覆盖索引迁移。
- 测试：
  - 后端目标：`mvn -B -q -Dtest=LargeListEngineServiceTest,LargeListControllerSecurityTest,LargeListAuditEventRepositoryTest,PageResponseTest,MigrationBaselineContractTest,H2BaselineMigrationTest test`
  - 后端全量：`mvn -B -q test`、`mvn -B -q clean test`（含 Docker 可用时 PostgreSQL + Oracle Testcontainers 迁移 smoke）
  - 前端目标 / 全量：`npm --prefix frontend test -- ServerDataTable.test.tsx`、`npm --prefix frontend run typecheck`、`npm --prefix frontend run verify`、`npm --prefix frontend run build`
  - 门禁：`node --test scripts/authenticity-guard.test.mjs`、真实性 / 配置边界 / 迁移 / 中文注释 / 空白检查
- 审计员签字：@<reviewer>（owner ≠ reviewer）。

## 大卡工序（3d，后端）
- PR1：PageQuery/PageResult + 游标分页 + 白名单 + total_estimate → AC-1/2/3。
- PR2：异步导出触发 + page size 上限 + 前端全量门禁 → AC-4/5。
