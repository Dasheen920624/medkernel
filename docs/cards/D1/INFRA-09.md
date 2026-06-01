# INFRA-09 · StepFlowDemo 处理（演示页清出生产路由）

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D1 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：N·A —— 本卡非旧文档锚点迁移，是 backlog D1 原生真实性清理项（核心 §13 真实性 / 铁律#1 / no-page-mock 精神）。
> 原现状：`frontend/src/pages/StepFlowDemo.tsx`（纯 7 步流 UI 演示、无真实数据）曾挂在生产路由 `config/packages/demo`。当前核查：最新 main 已无该页面、无该路由；本卡收口重点为保留 `StepFlow` 组件（`frontend/src/shared/ui/StepFlow.tsx`）并补生产 router 门禁防回流。

## 身份
- 卡 ID：INFRA-09（= backlog 任务 ID）
- 域：D1 工作台
- 关联场景：N·A（横切真实性清理；服务 S0 工作台域的生产路由洁净）
- 依赖卡：[INFRA-01](../D0/INFRA-01.md)（前端真实性门禁，本卡补一条门禁规则）· [BASE-06](../D0/BASE-06.md)（路由表归属）
- 工作量：0.5d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标

把无真实数据的 **7 步流演示页 StepFlowDemo 清出生产路由**：删除其路由与页面、保留可复用的 `StepFlow` 组件（供 D2 配置类页面），并加门禁防止"演示/占位页"再混入生产路由——让生产可达页面 100% 是真实功能页（铁律#1）。

## 功能要求（原子可测条目）

- [x] **FR-1 移除演示路由**：从 `frontend/src/app/router.tsx` 删除 `config/packages/demo` 子路由 + `StepFlowDemo` 的 `lazy(() => import(...))`；删后 `/config/packages/demo` 不可达（404 由路由兜底，不再渲染演示页）。
- [x] **FR-2 删除演示页文件**：删除 `frontend/src/pages/StepFlowDemo.tsx`（其逻辑无业务价值，仅组件展示）。
- [x] **FR-3 保留 StepFlow 组件**：`frontend/src/shared/ui/StepFlow.tsx` **保留不动**（核心 §4 7 步流唯一组件，D2 配置类页面复用）；删除演示页后该组件可暂无引用但必须保留。
- [x] **FR-4 组件演示转 Storybook（择一）**：如需保留"组件怎么用"的演示，迁为 `StepFlow.stories.tsx`（`.storybook` 内，**不进生产 bundle**）；若不需要则直接删演示、不留 story。
- [x] **FR-5 门禁防回流**：在前端真实性门禁（[INFRA-01](../D0/INFRA-01.md) 体系）加一条规则——生产 `router.tsx` 不得出现 `*Demo` / 演示占位页路由（再混入即 CI 拒）。

## 接口契约 / 页面契约
### 页面契约（页面卡）
- 路由元数据：**移除** `config/packages/demo` 槽（该槽本就不在核心 §2.2 的 27+5 二级菜单内，属游离演示路由）；移除后工作台一级仅保留"工作台 / 演示与校验"（[WORKBENCH-01](WORKBENCH-01.md)/[WORKBENCH-02](WORKBENCH-02.md)）。
- 结构 / 六态 / 主按钮：N·A —— 本卡是删除 + 门禁，不新增页面。
- 样式：N·A。

### 接口契约（引擎/API 卡）
N·A —— 纯前端路由/文件清理 + 门禁规则，无后端接口。

## 数据与迁移
N·A —— 无数据、无 DB 迁移；仅前端文件与路由变更 + 一条 lint/CI 门禁规则。

## 视角清单（11 视角逐条）
1. **产品架构**：生产路由表只承载真实功能页；组件级演示归 Storybook，不污染产品路由。
2. **产品体验**：移除一个会误导用户的"演示页"入口；工作台一级菜单回归"工作台 / 演示与校验"两 Tab（核心 §2.2）。
3. **系统与数据架构**：删 lazy import 后略减生产 bundle；无运行期影响。
4. **临床医疗安全**：N·A —— 演示页无临床数据。
5. **知识与数据治理**：N·A。
6. **安全合规与监管**：N·A（但减少一个无鉴权价值的可达路由面）。
7. **集团化与多租户治理**：N·A。
8. **集成与互操作**：N·A。
9. **运维 / SRE / 国产化**：N·A。
10. **质量与真实性审计**：★本卡主战场 —— 演示/占位页在生产可达违反铁律#1（假闭环/无真实数据）；删除 + 门禁防回流是真实性净化（核心 §13）。
11. **AI / 模型治理与可降级**：N·A —— 天然 B0。

## 适用不变量
- 命中核心约束：**铁律#1 真实性（生产无演示/占位假页）** · **§13 真实性门禁（[INFRA-01](../D0/INFRA-01.md) no-page-mock 精神）** · **§2 菜单 IA（不留游离演示路由）**。
- 本卡落点：删演示页 + 删路由 + 保留 StepFlow 组件 + 加门禁规则，把"生产路由 100% 真实功能页"钉成可校验事实。

## 验收 + 验证
- [x] **AC-1（FR-1/2）**：`router.tsx` 内 grep 无 `StepFlowDemo` 引用、无 `/config/packages/demo` 路由；`StepFlowDemo.tsx` 已删；访问 `/config/packages/demo` 走 404 兜底而非渲染演示页。
- [x] **AC-2（FR-3）**：`frontend/src/shared/ui/StepFlow.tsx` 仍在，构建不报缺失；（D2 配置页落地后将复用之）。
- [x] **AC-3（FR-4）**：若保留演示——`StepFlow.stories.tsx` 仅在 Storybook 可见，生产 bundle 不含；若删除——无残留 story。
- [x] **AC-4（FR-5）**：在 `router.tsx` 故意加一个 `XxxDemo` 路由 → 门禁/CI 拒（红）。
- 关联 A1–A9：N·A（真实性净化项）。
- T-GATE：前端真实性门禁全绿（含本卡新增"生产路由无 *Demo"规则）。
- B0 验收：纯前端清理，天然 B0。

## 完工证据
- 本轮现状核查：`rg --files | rg "StepFlowDemo|StepFlow\\.stories|storybook|stories"` 无结果；`frontend/src/app/router.tsx` 无 `StepFlowDemo` lazy import、无 `/config/packages/demo` 路由；`frontend/src/shared/ui/StepFlow.tsx` 保留。
- TDD 红灯：新增 `scripts/authenticity-guard.test.mjs` 反例后，`node --test scripts/authenticity-guard.test.mjs` 先失败于 `frontend.production-demo-route` 未被阻断。
- 代码 permalink：`scripts/authenticity-guard.mjs` 新增生产 router 规则；`frontend/src/app/router.test.tsx` 新增已认证访问 `/config/packages/demo` 进入 404 兜底的回归测试；`StepFlow.tsx` 保留。
- 测试：`node --test scripts/authenticity-guard.test.mjs` 23/23；`npm test -- --run src/app/router.test.tsx src/shared/ui/StepFlow.test.tsx src/shared/config/routes.test.ts` 3 个文件 / 20 个测试通过。
- 完整验证：`npm run verify` 退出 0，39 个测试文件 / 182 个测试通过；`npm run build` 退出 0（`DEFER-003` chunk 提示仍 open）；`node --test scripts/migration-convention-guard.test.mjs` 8/8；`node --test scripts/config-boundary-guard.test.mjs` 2/2；`scripts/check-comment-zh.sh --mode=full` 退出 0（历史 COMMENT GAP 继续归 `DEFER-006`）；`node scripts/authenticity-guard.mjs --mode=all` 扫描 745 文件退出 0；`git diff --check` 退出 0。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。

（本卡 0.5d 纯清理，无大卡工序拆分）
