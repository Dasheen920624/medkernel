# WORKBENCH-02 · 演示与校验页面

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D1 域简报](_brief.md) + [体验契约](../../EXPERIENCE_CONTRACT.md)。
> 迁移来源（覆盖矩阵锚点）：详规 S0 工作台与总览（"试点是否可验收"，L466）· 落地规划 §16 AI 全面验证与验收 / A1–A9 剧本 · 体验规范 §13 验收清单 · 宪法 §2 菜单 IA（工作台一级"工作台 · 演示与校验"合并 Tab）。
> 现状：WORKBENCH-02 已通过 #245 合入 `origin/main`（merge `07f8f77f`），生产路由 `/workbench/demo-validation` 与页面已上线到当前基线；`StepFlowDemo`（7 步流 UI 演示）**不是**本页，已由 [INFRA-09](INFRA-09.md) 清出生产。页面复用 `/security/me`、`/auth/session`、`/experience/theme-preference`、`/system/operations` 等现有底座，不新增 `/api/v1/workbench/*`。
> 卡 ID 说明：`WORKBENCH-02` 为 D1 域搬迁按"逐域回填卡 ID"规则新指派的页面卡 ID（backlog 原行 `D1-PAGE-演示校验`）。

## 身份
- 卡 ID：WORKBENCH-02（backlog D1，原 `D1-PAGE-演示校验`）
- 域：D1 工作台
- 关联场景：S0 工作台与总览（试点是否可验收 / 演示就绪度自检）
- 依赖卡：[WORKBENCH-01](WORKBENCH-01.md)（同菜单槽合并 Tab）· [BASE-06](../D0/BASE-06.md)（路由）· [BASE-08](../D0/BASE-08.md)（六态/页面壳）· [BASE-10](../D0/BASE-10.md)（设计 token）· [OBS-01](../D0/OBS-01.md)/[SYS-05](../D0/SYS-05.md)（健康/可运行状态来源）· [INFRA-03](../D0/INFRA-03.md)（错误反馈）
- 工作量：2d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标

新建**演示与校验页面**：给实施 / 信息科 / 院管一个**真实的演示就绪度自检**——本租户/本院当前"哪些能力可演示、哪些被阻塞、阻塞原因与去处"，与工作台同菜单槽合并为 Tab；结论必须真实，绝不假绿。**复用现有健康/引擎状态 API，不新增独立工作台接口**（详规 S0）。

## 功能要求（原子可测条目）

- [x] **FR-1 新建页面 + 路由**：在 `router.tsx` 新增演示与校验路由（如 `workbench/demo-validation`，登入后受保护）；新建页面文件，对接现有健康/各引擎状态 API（**不新增独立工作台接口**）。
- [x] **FR-2 就绪度自检**：展示各演示项状态（可演示 / 阻塞 / 未启用）+ 总体就绪结论（据实计数，如"3 可演示 / 2 阻塞 / 1 未启用"）。
- [x] **FR-3 阻塞可定位**：每个阻塞项给**中文阻塞原因** + 去处链接（跳对应配置/适配器/数据页修复），不只给红叉；未建域去处跳"该域未启用"诚实页。
- [x] **FR-4 合并 Tab**：与 [WORKBENCH-01](WORKBENCH-01.md) 共用"工作台"一级菜单第 0 槽，以 Tab 切换"工作台 / 演示与校验"（核心 §2.2，二级菜单仍 27、不净增）。
- [x] **FR-5 六态完整**：加载 / 空 / 错误（中文 + traceId）/ 无权限（默认临床角色无本页）/ 部分成功（部分自检项未采集到状态）/ 正常。
- [x] **FR-6 诚实结论 + 只读**：未建域/未连接依赖标"未启用 / 未连接"，**不**计入"可演示"也不假绿（铁律#1/#2）；本页只读自检 + 跳修复，**不**在本页直接改配置。

## 接口契约 / 页面契约
### 页面契约（页面卡）
- 路由元数据：path=`workbench/demo-validation` / sectionKey=`workbench` / menuKey=`demo-validation` / menuLabel=`演示与校验` / requiredPermissions=`[menu.workbench, workbench:demo:view]` / requiredRoles=实施 / 信息科 / 院管 / 平台管理员（业务临床角色默认无）。
- 结构：PageShell + 与工作台合并 Tab（同槽）+ 自检项列表（项 / 状态 / 原因 / 去处）+ 总体结论 + 六态。
- 主按钮 ≤1（重新自检）/ 默认筛选 ≤3（按状态：阻塞 / 可演示 / 未启用）/ 默认角色视图。
- 五维 RBAC 点位：菜单（演示与校验 Tab 按角色可见）/ 动作（重新自检 + 跳修复按权限）/ 数据（自检范围按 `OrgContext` 租户/组织）/ 资产 N·A / 环境（内外网一致）。
- 样式：仅引用核心 §5 设计 token 与体验契约组件（状态色走 token，**禁硬编码 hex/px**）。

### 接口契约（引擎/API 卡）
N·A（不新增业务接口，详规 S0）—— 复用现有健康（[OBS-01](../D0/OBS-01.md)/运行底座 [SYS-05](../D0/SYS-05.md)）、各引擎状态、Provider/适配器连通现有端点；仅新增页面动作权限 `workbench:demo:view` 的权限目录与迁移。

## 数据与迁移
- 不新增业务数据表、不新增工作台权威数据落库；自检实时读现有状态 API，权威在各域。
- 本轮仅为满足页面动作权限新增 `workbench:demo:view` 权限目录与五方言 V47 迁移，保持菜单权限与动作权限分离。

## 视角清单（11 视角逐条）
1. **产品架构**：演示就绪度 = 各域"可演示/可运行"自检项的只读聚合，与工作台同源（复用现有 API），不另起后端、不造独立接口。
2. **产品体验**：★本卡主战场 —— 清楚告诉实施"还差什么、去哪修"，六态 + 阻塞可定位 + 不营销化结论（体验契约）。
3. **系统与数据架构**：实时自检（各项独立取现有状态 API + 独立降级）；自检项随域增长由数据驱动，前端不硬编码项清单。
4. **临床医疗安全**：N·A —— 演示自检不触临床决策。
5. **知识与数据治理**：N·A。
6. **安全合规与监管**：自检范围按 `OrgContext` 租户/组织隔离；无权限角色不可见本页（默认临床角色无）。
7. **集团化与多租户治理**：按本租户/本院自检（不串其它租户）；平台角色可看跨院就绪度汇总。
8. **集成与互操作**：依赖外部（适配器/Provider）的演示项如实标"未连接"，给适配器中心（D2）修复去处（D1 时该去处未建则诚实指向"该域未启用"）。
9. **运维 / SRE / 国产化**：内外网双形态自检一致；国产化项（OS/JDK/DB 自检）可纳入自检项。
10. **质量与真实性审计**：★禁假绿——阻塞如实标红 + 原因；总体结论据实计数；自检项可截图举证（核心 §13 / 铁律#1）。
11. **AI / 模型治理与可降级**：AI 相关演示项关模型时标"未启用（模型已关）"而非阻塞失败；本页天然 B0。

## 适用不变量
- 命中核心约束：**详规 S0 不新增独立工作台接口** · **铁律#1 真实性 / #2 诚实降级（禁假绿）** · **§体验六态** · **#2 菜单 IA 锁（合并 Tab 不净增二级）** · **#7 五维权限** · **§5 设计 token**。
- 本卡落点：新建真实演示就绪度自检页 + 阻塞可定位 + 合并 Tab + 复用现有 API，把"演示前心里有数、阻塞有去处"做成前端事实，杜绝演示翻车与假就绪。

## 验收 + 验证
- [x] **AC-1（FR-1/2/6）**：进演示与校验 → 见各项真实状态 + 总体结论（据实计数）；制造一项阻塞 → 结论据实变化，不假绿。
- [x] **AC-2（FR-3）**：阻塞项展示中文原因 + 去处链接；点去处跳对应修复页（未建域跳"该域未启用"诚实页）。
- [x] **AC-3（FR-4）**：工作台 ↔ 演示与校验 同槽 Tab 切换；二级菜单计数不净增（仍 27）。
- [x] **AC-4（FR-5）**：六态逐态可截图（错误带 traceId；临床角色访问→无权限态）。
- [x] **AC-5（FR-1/6）**：grep 确认未新增 `/api/v1/workbench/*` 专属端点（复用现有状态 API）；本页无任何配置写操作（只读 + 跳修复）。
- 关联 A1–A9：A1–A9 全功能验收剧本（演示就绪度即"剧本可跑"判据）；A2 降级。
- T-GATE：前端门禁全绿（无 page-mock、无假就绪、无 dangling 端点、无硬编码 hex/px）。
- B0 验收：纯前端 + 现有确定性后端，天然 B0（关模型后 AI 演示项标"未启用"，自检主体可用）。

## 完工证据
- 代码：`frontend/src/pages/workbench/DemoValidation.tsx`、`frontend/src/widgets/WorkbenchTabs.tsx`、`frontend/src/app/router.tsx`、`frontend/src/shared/config/routes.ts`、`frontend/src/shared/config/menu.ts`、`medkernel-backend/src/main/java/com/medkernel/engine/security/PermissionCode.java`、`medkernel-backend/src/main/resources/db/migration/*/V47__workbench_demo_validation_permission.sql`。
- 测试：`frontend/src/pages/workbench/DemoValidation.test.tsx` 覆盖自检渲染、中文阻塞原因、去处跳转、临床角色无权限、部分成功、错误 traceId；路由 / 菜单 / 后端权限策略 / 迁移基线同步补测。
- 本地验证：`npm run verify` 41 文件 / 203 测试通过；`npm run build` 通过；`mvn -q -Dtest=DefaultPermissionPolicyTest,PermissionDimensionModelTest,SystemSecurityCatalogRepositoryTest,H2BaselineMigrationTest test` 通过并应用 47 个 H2 迁移；`mvn -q -Dtest=FlywayMultiDialectSmokeTest,MigrationBaselineContractTest,H2BaselineMigrationTest test` 通过并覆盖 H2 / PostgreSQL / Oracle 迁移到 V47；`node scripts/authenticity-guard.mjs --mode=all` 扫描 745 文件通过；`node --test scripts/authenticity-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/config-boundary-guard.test.mjs` 34/34 通过；`scripts/check-comment-zh.sh --mode=full` 本轮 V47 OK（历史 GAP 见 `DEFER-006`）；`git diff --check` 通过。
- 浏览器验收：Codex in-app browser `iab` 仍不可用（见 `DEFER-004`），已用本地 Playwright 访问 `http://127.0.0.1:5175/workbench/demo-validation`，页面渲染标题 / 真实计数 / 3 个筛选 / 单主按钮“重新自检”；网络请求仅 `/security/me`、`/experience/theme-preference`、`/auth/session`、`/system/operations`，无 `/workbench` 接口；截图 `/tmp/medkernel-workbench02-demo-validation.png`。
- 审计员签字：待 PR reviewer（owner ≠ reviewer）。

（本卡 2d 单层前端，无大卡工序拆分）
