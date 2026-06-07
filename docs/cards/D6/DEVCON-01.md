# DEVCON-01 · 开发者控制台页

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D6 域简报](_brief.md) + [体验契约](../../EXPERIENCE_CONTRACT.md)。
> 迁移来源（覆盖矩阵锚点）：详规 开发者工具 · 核心 §10 集成边界 · 体验规范 §3。
> 实化映射：占位 `D6-PAGE-开发者控制台` → 本卡 **DEVCON-01**。

## 身份
- 卡 ID：DEVCON-01（页面卡；= backlog `D6-PAGE-开发者控制台` 实化）
- 域：D6 高级工具
- 关联场景：生态扩展 / 开发者
- 依赖卡：[OPT-10](OPT-10.md)（插件边界）· [OBS-01](../D0/OBS-01.md)（可观测/traceId）· [BASE-03](../D0/BASE-03.md)（API 契约）· [BASE-08](../D0/BASE-08.md)/[BASE-10](../D0/BASE-10.md) · [INFRA-09](../D1/INFRA-09.md)
- 工作量：2d
- owner / reviewer：Codex / PR审阅人（owner ≠ reviewer）

## 目标
把开发者控制台页**真实化**：只读API契约目录、traceId诊断、运行状态和插件管理（[OPT-10](OPT-10.md)），给开发者与运维使用，**不提供绕过领域权限的通用请求编辑器，不暴露敏感配置**。

## 现状（2026-06-07）
已接入服务契约目录、Trace诊断、运行快照和插件注册 / 授权 / 禁用真实接口；目录输出已脱敏，页面不提供任意HTTP写入或跨域调试入口。

## 功能要求（原子可测条目）
- [x] FR-1 API 浏览：列可用API、权限和审计点，契约（[BASE-03](../D0/BASE-03.md)）可查。
- [x] FR-2 安全操作：不提供通用写入调试器；实际操作继续走各领域页面与权限门禁。
- [x] FR-3 trace追踪：按traceId查状态流转和Payload摘要（[OBS-01](../D0/OBS-01.md)）。
- [x] FR-4 插件管理：插件注册/授权/禁用（[OPT-10](OPT-10.md)），受控。
- [x] FR-5 RBAC + 不暴露：开发者/架构师/运维可见；敏感配置/密钥不暴露；数据按`OrgContext`；不入客户主菜单。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- `GET /api/v1/system/dev-console/api-contracts`
- `GET /api/v1/engine/diagnose/traces/{traceId}`
- `GET/POST /api/v1/plugins*`
### 页面契约（页面卡）
- 路由元数据：sectionKey `advanced` / menuKey `dev-console` / menuLabel `开发者控制台` / path `/advanced/dev-console` / requiredPermissions 开发者控制台 / requiredRoles 开发者·架构师·运维。
- 结构：PageShell（[BASE-08](../D0/BASE-08.md)）+ 运行快照 + API目录 + trace查询 + 插件管理 + 六态。
- 主按钮 ≤1（当前标签页动作）/ 默认筛选 ≤3 / 默认角色视图（开发者）。
- 五维 RBAC：菜单 / 动作（调试/插件管理）/ 数据（org）/ 资产 / 环境。
- 样式：仅引用 [BASE-10](../D0/BASE-10.md) token + [体验契约](../../EXPERIENCE_CONTRACT.md)；禁硬编码 hex/px。

## 数据与迁移
N·A —— 页面卡不落库；消费各后端。

## 视角清单（11 视角逐条）
1. 产品架构：开发者/集成的"控制台"工具页。
2. 产品体验：API/调试/trace 清晰；国产浏览器可读。
3. 系统与数据架构：调试受限频率；trace 查询 P95 ≤1s。
4. 临床医疗安全：调试不可造临床数据、不绕引擎。
5. 知识与数据治理：N·A。
6. 安全合规与监管：★调试/插件操作留审计（[BASE-04](../D0/BASE-04.md)）；不暴露密钥。
7. 集团化与多租户治理：按 `OrgContext` 作用域；调试不可跨租户。
8. 集成与互操作：★插件管理（[OPT-10](OPT-10.md)）+ API 契约（[BASE-03](../D0/BASE-03.md)）。
9. 运维 / SRE / 国产化：trace/插件可观测。
10. 质量与真实性审计：★调试不绕 RBAC、不暴露敏感、不越权造数；无演示路由（[INFRA-09](../D1/INFRA-09.md)）。
11. AI / 模型治理与可降级：N·A。

## 适用不变量
- 命中核心约束：**核心 §10 集成边界** · **§6 安全（不暴露/不绕权限）** · **铁律 #1** · **技术对象不入主路径**。
- 本卡落点：把开发者控制台页变为接真实 API/trace/插件、受权限约束、不暴露敏感的工具台。

## 验收 + 验证
- [x] AC-1（FR-1/2）：API目录按权限展示，无通用写入旁路。
- [x] AC-2（FR-3/4）：trace可查；插件管理受控（[OPT-10](OPT-10.md)）。
- [x] AC-3（FR-5）：六态齐全；不暴露敏感、不入客户主菜单。
- 关联 A1–A9 剧本：A9 开发者控制台。
- T-GATE：前端真实性门禁全绿（no-page-mock、不绕权限、不暴露敏感）。
- B0 验收：N·A（确定性页面）。

## 完工证据
- 代码：`DeveloperConsoleController` / `DeveloperConsoleService` + `pages/advanced/DevConsole.tsx`。
- 测试：`DeveloperConsoleControllerTest`、`PluginSecurityControllerTest`、`operationalControlPages.test.tsx`、`hooks.test.ts`通过。
- 浏览器：API检索、Trace空结果、插件注册 / 授权 / 禁用均真实可操作；桌面与390px无页面级横向溢出，修复后无新增控制台告警。
- 审计员签字：PR审阅人（owner ≠ reviewer）。
