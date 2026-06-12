# CFGPKG-01 · 配置包中心页

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D2 域简报](_brief.md) + [体验契约](../../EXPERIENCE_CONTRACT.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §S13 包发布与院内同步（L602）· §1.3 配置类 7 步流（L118）· 落地规划 §12.2 发布流程（L811）· 核心 §4 7 步流 / §3 变更类状态机。
> 实化映射：占位 `D2-PAGE-配置包中心` → 本卡 **CFGPKG-01**。

## 身份
- 卡 ID：CFGPKG-01（页面卡；= backlog `D2-PAGE-配置包中心` 实化）
- 域：D2 试点准备
- 关联场景：S13 包发布与院内同步
- 依赖卡：[PKG-01](PKG-01.md)/[API-10](API-10.md)（包发布引擎/API）· [SYS-04](SYS-04.md)（发布流）· [SVC-PILOT-03](SVC-PILOT-03.md)（资产准备）· [INFRA-09](../D1/INFRA-09.md)（StepFlow + 清演示页门禁）· [BASE-06](../D0/BASE-06.md)/[BASE-08](../D0/BASE-08.md)/[BASE-10](../D0/BASE-10.md)
- 工作量：3d
- owner / reviewer：Codex / PR reviewer（owner ≠ reviewer）

## 目标
把配置包中心页**真实化**：配置包列表 + 组包 + **7 步流发布**（选模板/导入→校验→看影响→审核→灰度→全量→留证据/回滚）+ 同步状态 + 失败站点。**接 [PKG-01](PKG-01.md)/[API-10](API-10.md) 真实发布同步，清除演示路由**。

## 现状（搬迁时核查 2026-05-30，以 `frontend/src` 为准）
页面**已存在待真实化**：`pages/tenant/ConfigPackages`（路由 `/config/packages` 已归入 `knowledge-configuration`，客户名称为“配置包与发布”）；原 `/config/packages/demo` 演示路由已按 [INFRA-09](../D1/INFRA-09.md) no-page-mock 门禁移除，StepFlow 组件保留复用。本卡＝接 [API-10](API-10.md) 真实发布/同步 + 7 步流真实化 + 六态/RBAC。

## 功能要求（原子可测条目）
- [x] **FR-1 包列表**：配置包列表（大列表 [API-13](../D0/API-13.md) 分页/筛选），状态真实。
- [x] **FR-2 组包**：选规则/路径/知识/字典组包（[SVC-PILOT-03](SVC-PILOT-03.md)）；依赖缺失提示。
- [x] **FR-3 7 步流发布**：选模板/导入→自动校验→看影响→提交审核→灰度（10%）→全量→留证据/可回滚（[SYS-04](SYS-04.md)/[PKG-01](PKG-01.md)，StepFlow [INFRA-09](../D1/INFRA-09.md)）。
- [x] **FR-4 同步状态**：同步水位 + 失败站点可见；无通道诚实 `NOT_SYNCED`，不伪造成功。
- [x] **FR-5 清演示路由**：移除 `/config/packages/demo`；生产不得有 `*Demo` 路由（[INFRA-09](../D1/INFRA-09.md) 门禁）。
- [x] **FR-6 六态 + RBAC**：六态齐全；仅实施/医务处（发布权）可操作；数据按 `OrgContext`。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
N·A —— 页面卡，消费 [API-10](API-10.md) `/engine/pkg/**` 现有发布/同步 API。
### 页面契约（页面卡）
- 路由元数据：sectionKey `knowledge-configuration` / menuKey `config-packages` / menuLabel `配置包与发布` / path `/config/packages` / requiredPermissions 配置包读取与发布 / requiredRoles 实施运维员、平台/机构知识治理员。
- 结构：PageShell（[BASE-08](../D0/BASE-08.md)）+ 包列表 + 组包 + 7 步流（StepFlow [INFRA-09](../D1/INFRA-09.md)）+ 同步/失败站点面板 + 六态。
- 主按钮 ≤1（发布）/ 默认筛选 ≤3（状态/类型/院区）/ 默认角色视图。
- 五维 RBAC：菜单 / 动作（发布/全量权）/ 数据（org）/ 资产（配置包）/ 环境。
- 样式：仅引用 [BASE-10](../D0/BASE-10.md) token + [体验契约](../../EXPERIENCE_CONTRACT.md)；禁硬编码。

## 数据与迁移
N·A —— 页面卡不落库；消费 [PKG-01](PKG-01.md) 表族。

## 视角清单（11 视角逐条）
1. **产品架构**：配置资产"发布出厂"的中枢页。
2. **产品体验**：★7 步流 + 包列表 + 同步可见 + 六态；国产浏览器/老年模式可读。
3. **系统与数据架构**：大包列表分页；同步状态真实；P95 ≤1s。
4. **临床医疗安全**：高危包（含权威知识替换）发布门禁；灰度默认 10%。
5. **知识与数据治理**：配置包版本/差异/回滚可溯。
6. **安全合规与监管**：发布/同步/回滚留审计 + 证据可导出（[BASE-04](../D0/BASE-04.md)）。
7. **集团化与多租户治理**：★发布按七层继承 + 灰度 10% + 仅院级直接全量（核心 §9）。
8. **集成与互操作**：同步失败站点 `NOT_SYNCED` 可见（核心 §10）。
9. **运维 / SRE / 国产化**：离线包导入/导出；失败站点告警；内外网。
10. **质量与真实性审计**：★无伪造同步/灰度；**清除 `/config/packages/demo` 演示路由**（[INFRA-09](../D1/INFRA-09.md)，铁律 #1）。
11. **AI / 模型治理与可降级**：关模型组包发布不变（B0）。

## 适用不变量
- 命中核心约束：**§4 7 步流** · **§3 变更类状态机** · **§9 灰度继承** · **铁律 #1 清演示页 / 不伪造同步** · **依赖 [PKG-01](PKG-01.md)/[API-10](API-10.md)/[SYS-04](SYS-04.md)**。
- 本卡落点：把配置包中心从含演示路由的占位页变为真实 7 步流发布 + 同步可见的中枢页。

## 验收 + 验证
- [x] **AC-1（FR-1/2）**：包列表分页真实；组包依赖缺失提示。
- [x] **AC-2（FR-3）**：7 步流灰度→全量→回滚真实（[PKG-01](PKG-01.md)），证据可导出。
- [x] **AC-3（FR-4）**：同步水位/失败站点真实；无通道 `NOT_SYNCED`。
- [x] **AC-4（FR-5）**：`/config/packages/demo` 已移除；CI no-page-mock 拒演示路由回流。
- 关联 A1–A9 剧本：A4 发布回滚、A6 同步证据。
- T-GATE：前端真实性门禁全绿（no-page-mock、无演示路由、无伪造同步）。
- B0 验收：确定性发布同步，**天然 B0**。

## 完工证据
- 代码 permalink：`frontend/src/pages/tenant/ConfigPackages.tsx` 真实化 + `ConfigPackages.module.css` token 化样式 + `usePackages` 服务端分页/筛选 + `/config/packages` 路由 RBAC + StepFlow 七步流；生产路由无 `/config/packages/demo`。
- 测试：红灯先失败于缺 StepFlow、缺 PageShell 六态、`usePackages` 不支持筛选参数、路由缺 `pkg.read/pkg.release` 和角色限制、源码仍有旧装饰类/旧一键草稿文案；实现后 `npm test -- src/pages/tenant/ConfigPackages.test.tsx src/shared/api/hooks.test.ts src/shared/config/routes.test.ts src/pages/tenant/RulePathwayCleanliness.test.ts`（4 files / 63 tests）通过；项目 Playwright 浏览器验收 `http://127.0.0.1:5175/config/packages` 通过（标题 1、StepFlow 1、主按钮 1、失败同步证据 1、超时弹窗 0、console errors 0、失败响应 0，截图 `/tmp/medkernel-cfgpkg-01-config-packages.png`）；`npm run verify`（50 files / 290 tests）通过；`npm run build` 通过；`npm audit --omit=dev --json` 生产依赖漏洞 0；后端 `mvn -q test` 通过（186 reports / 1120 tests / failures 0 / errors 0 / skipped 0，PostgreSQL 15.18 + Oracle 21.3 到 V66）。
- 门禁：T-GATE 规则测试 34/34、中文注释门禁 0 fail / 0 warn、`git diff --check` 通过；提交后 `origin/main..HEAD` changed-mode 真实性门禁扫描 3 个触碰文件通过，配置边界 / 迁移规约 changed-mode 无新增扫描文件并通过；以远端 CI 为最终合入证据。
- 审计员签字：待 PR reviewer（owner ≠ reviewer）。
