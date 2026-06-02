# API-10 · 包发布 API

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D2 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §1.5.2 当前/后续 API 清单·包发布行（L185）· 落地规划 §12.2 发布流程（L811）· 核心 §1.4 统一入参 / 铁律 #2 不伪造同步。

## 身份
- 卡 ID：API-10（= backlog 任务 ID）
- 域：D2 试点准备
- 关联场景：S13 包发布与院内同步（客户面 API）
- 依赖卡：[PKG-01](PKG-01.md)（包发布引擎）· [SYS-04](SYS-04.md)（发布流）· [BASE-03](../D0/BASE-03.md)（契约）· [API-13](../D0/API-13.md)（大列表）
- 工作量：3d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
提供包发布**统一 REST 客户面**：知识包/配置包 CRUD · 校验 · 灰度 · 全量 · 同步 · 回滚。本卡只立 **API 契约**，能力在 [PKG-01](PKG-01.md)、发布流在 [SYS-04](SYS-04.md)。

## 现状（搬迁时核查 2026-05-30，以 `medkernel-backend/src` 为准）
`engine/pkg` **控制器已建**，本卡＝**契约化 + 统一入参/同步证据对齐**：
- 已有：`PackageEngineController`(+ 安全测试)、`PackageCreateRequest`/`Detail`/`Response`、`PackageItemRequest`/`Response`、`PackageSyncRequest`/`Response`、`PackageDiffResponse`、`SyncLogResponse`、`ReleasePlan`。
- 2026-06-02 本卡补齐：① 统一 12 字段入参 + 信封；② `/api/v1/engine/pkg/**` 客户面；③ 校验/发布/同步日志端点；④ 同步端点返回真实证据 + `NOT_SYNCED`；⑤ 前端共享 hooks 与配置包中心清理旧 `/engine/packages` 口径和“物理投影/长链接”旧文案。

## 功能要求（原子可测条目）
- [x] **FR-1 包 CRUD**：`GET/POST /packages`、`POST /packages/{id}/items`；列表分页（[API-13](../D0/API-13.md)）。
- [x] **FR-2 校验/差异**：`POST /packages/{id}/validate`、`GET /packages/{id}/diff`（`PackageDiffResponse`）。
- [x] **FR-3 发布**：`POST /packages/{id}/release`（灰度/全量，[SYS-04](SYS-04.md)）；`POST /packages/{id}/rollback`。
- [x] **FR-4 同步**：`POST /packages/{id}/sync`、`GET /packages/{id}/sync-logs`（水位/失败站点）；无通道 → `NOT_SYNCED`。
- [x] **FR-5 统一入参/信封**：12 字段入参 + `ApiResult`/`ProblemDetail`（[BASE-03](../D0/BASE-03.md)）。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 端点：`/api/v1/engine/pkg/**`（packages、items、validate、diff、release、rollback、sync、sync-logs）。
- DTO：复用 `PackageCreateRequest`/`Detail`/`PackageSyncRequest`/`Response`/`PackageDiffResponse`/`SyncLogResponse`。
- 响应信封：`ApiResult` / `ProblemDetail`；大列表 `PageResult`（[API-13](../D0/API-13.md)）。
- 状态机：包版本核心 §3 配置类 + 变更类（[SYS-04](SYS-04.md)）。
- 幂等 / 错误码 / traceId：发布/同步幂等键；无通道 → `NOT_SYNCED`；hash 不符 → `PACKAGE_INTEGRITY_FAILED`；traceId（[OBS-01](../D0/OBS-01.md)）。
### 页面契约（页面卡）
N·A —— 本卡无页面。被 **D2 配置包中心页**消费。

## 数据与迁移
- 无独立表族——复用 [PKG-01](PKG-01.md) 表族。不落新库（API 契约卡）。

## 视角清单（11 视角逐条）
1. **产品架构**：包能力统一对外契约口。
2. **产品体验**：N·A —— 配置包中心页（D2）消费。
3. **系统与数据架构**：统一入参/信封/分页；大包导入分块；同步证据真实。
4. **临床医疗安全**：高危包发布门禁 + 同步不破坏权威版本。
5. **知识与数据治理**：包版本/差异/回滚可溯。
6. **安全合规与监管**：发布/同步/回滚留审计（[BASE-04](../D0/BASE-04.md)）。
7. **集团化与多租户治理**：按 `OrgContext` 作用域；灰度/全量权五维 RBAC。
8. **集成与互操作**：★同步端点诚实 `NOT_SYNCED`（核心 §10）。
9. **运维 / SRE / 国产化**：离线包导入/导出端点；失败站点查询。
10. **质量与真实性审计**：无伪造同步哈希；端点真实连引擎（铁律 #1/#2）。
11. **AI / 模型治理与可降级**：包内容确定性；关模型打包/同步端点不变。

## 适用不变量
- 命中核心约束：**§1.4 统一入参** · **§10 同步不破坏权威** · **铁律 #2 不伪造同步** · **依赖 [PKG-01](PKG-01.md)/[SYS-04](SYS-04.md)/[API-13](../D0/API-13.md)**。
- 本卡落点：包能力以统一契约对外，同步诚实降级在 API 层兜底。

## 验收 + 验证
- [x] **AC-1（FR-1/2）**：包 CRUD + 校验/差异，统一信封；分页稳定。
- [x] **AC-2（FR-3）**：灰度→全量→回滚经 [SYS-04](SYS-04.md)，状态机正确。
- [x] **AC-3（FR-4）**：无通道同步 → `NOT_SYNCED`（非伪造）；有通道 → sync-logs 水位真实。
- [x] **AC-4（FR-5）**：缺统一入参 → `ProblemDetail`；越权 → 0 + 审计。
- 关联 A1–A9 剧本：A4 发布回滚、A6 同步证据。
- T-GATE：真实性门禁全绿。
- B0 验收：确定性，**天然 B0**。

## 完工证据
- 代码 permalink：`/api/v1/engine/pkg/**` 端点 + 校验/发布/回滚/同步 + `NOT_SYNCED`。
- 测试：契约 + 安全 + 灰度回滚 + 同步 `NOT_SYNCED` 测试；本地聚焦已过：`mvn -q -Dtest=PackageEngineControllerSecurityTest,PackageEngineServiceTest,LenientPackageSyncAdapterTest,ServiceContractGovernanceTest test`、`npm test -- --run src/shared/api/hooks.test.ts src/pages/tenant/ConfigPackages.test.tsx`、`npm run typecheck`；变基到 `origin/main` 396b8b15 后本地全量已过：`mvn -q test`（885 tests / 0 failures / 0 errors / 0 skipped，含 PostgreSQL 15.18 + Oracle 21.3 迁移至 V48）、`npm run verify`（41 files / 219 tests）、`npm run build`、T-GATE（真实性 21 文件、配置边界 19 文件、迁移规约 0 迁移文件、中文注释 0 fail / 0 warn、`git diff --check origin/main...HEAD`）。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
