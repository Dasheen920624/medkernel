# ADAPTER-01 · 适配器中心页

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D2 域简报](_brief.md) + [体验契约](../../EXPERIENCE_CONTRACT.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §S2 院内系统接入（L489）· 落地规划 §11.3 院内系统对接（L741）· 核心 §10 集成边界 / 铁律 #2 断连不伪造。
> 实化映射：占位 `D2-PAGE-适配器中心` → 本卡 **ADAPTER-01**。

## 身份

- 卡 ID：ADAPTER-01（页面卡；= backlog `D2-PAGE-适配器中心` 实化）
- 域：D2 试点准备
- 关联场景：S2 院内系统接入
- 依赖卡：[INTEG-01](INTEG-01.md)（对接总线）· [SVC-PILOT-02](SVC-PILOT-02.md)（接入与数据质量）· [SVC-INTEGRATION-01](SVC-INTEGRATION-01.md)（业务接口）· [OPT-01](OPT-01.md)（FHIR 门面）· [BASE-06](../D0/BASE-06.md)/[BASE-08](../D0/BASE-08.md)/[BASE-10](../D0/BASE-10.md)
- 工作量：3d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标

把适配器中心页**真实化**：适配器目录 + 连通/健康状态 + 字段映射 + 重试死信 + **数据质量看板** + 接入向导。**接 [INTEG-01](INTEG-01.md)/[SVC-PILOT-02](SVC-PILOT-02.md) 真实状态，断连诚实标 `NOT_CONNECTED`，不伪造连接**。

## 现状（搬迁时核查 2026-05-30，以 `frontend/src` 为准）

页面**已存在待真实化**：`pages/tenant/AdapterHub`（路由 `/adapter/hub` 已注册 sectionKey `pilot-setup`，占位）。本卡＝接 [INTEG-01](INTEG-01.md)/[SVC-PILOT-02](SVC-PILOT-02.md) 真实适配器/健康/数据质量 + 六态/RBAC。

## 功能要求（原子可测条目）

- [x] **FR-1 适配器目录**：列出 HIS/EMR/LIS/PACS/医保/病案/随访适配器 + 连通态；启停（[INTEG-01](INTEG-01.md)）。
- [x] **FR-2 健康状态**：实时健康 + 断连诚实标 `NOT_CONNECTED`，不伪造在线。
- 普通配置模式已提供服务地址、探活路径、投递路径和连接/请求超时；HTTP 类协议无需手写 JSON 即可完成真实连接配置，专家模式仅承载请求头等高级配置。
- [x] **FR-3 字段映射 + 死信**：字段映射配置（接 [TERM-01](TERM-01.md)）；死信队列查看 + 重放。
- [x] **FR-4 数据质量看板**：必填率/编码映射率/时效（[SVC-PILOT-02](SVC-PILOT-02.md) `DataQualityReport`），缺口诚实暴露。
- [x] **FR-5 接入向导**：向导式新增适配器（[SVC-INTEGRATION-01](SVC-INTEGRATION-01.md) 接入生命周期）。
- [x] **FR-6 六态 + RBAC**：六态齐全；仅信息科·实施工程师可操作；数据按 `OrgContext`。

## 接口契约 / 页面契约

### 接口契约（引擎/API 卡）

N·A —— 页面卡，消费 [INTEG-01](INTEG-01.md) / [SVC-PILOT-02](SVC-PILOT-02.md) / [SVC-INTEGRATION-01](SVC-INTEGRATION-01.md) 现有 API：`/api/v1/engine/integration/adapters`、`/adapter-hub/status`、`/logs`、`/dead-letter/{id}/replay`、`/data-quality/reports`、`/onboardings`、`/onboardings/{id}/advance`。本卡不新增后端端点。

### 页面契约（页面卡）

- 路由元数据：sectionKey `pilot-setup` / menuKey `adapter-hub` / menuLabel `适配器中心` / path `/adapter/hub` / requiredPermissions `menu.adapter-hub + integration.read/write/execute` / requiredRoles `it-ops`、`implementation`。
- 结构：PageShell（[BASE-08](../D0/BASE-08.md)）+ 适配器目录 + 健康面板 + 字段映射 + 死信队列 + 数据质量看板 + 六态。
- 主按钮 ≤1（新增适配器）/ 默认筛选 ≤3（类型/状态/院区）/ 默认角色视图。
- 五维 RBAC：菜单 / 动作（启停/重放权）/ 数据（org）/ 资产（适配器）/ 环境。
- 样式：仅引用 [BASE-10](../D0/BASE-10.md) token + [体验契约](../../EXPERIENCE_CONTRACT.md)；禁硬编码。

## 数据与迁移

N·A —— 页面卡不落库；消费 [INTEG-01](INTEG-01.md)/[SVC-PILOT-02](SVC-PILOT-02.md) 表族。

## 视角清单（11 视角逐条）

1. **产品架构**：院内系统接入的运维中枢页。
2. **产品体验**：适配器目录 + 健康 + 死信 + 数据质量看板 + 六态；国产浏览器可读。
3. **系统与数据架构**：状态实时取数、独立降级；大消息日志分页；P95 ≤1s。
4. **临床医疗安全**：外部数据经标准上下文不绕引擎直写；同步异常不阻断临床。
5. **知识与数据治理**：字段映射经 [TERM-01](TERM-01.md) 归一可溯。
6. **安全合规与监管**：接入/启停/重放/质量留审计（[BASE-04](../D0/BASE-04.md)）。
7. **集团化与多租户治理**：适配器按 org 隔离；集团协议 + 院内实例。
8. **集成与互操作**：★主战场 —— 适配器目录 + 健康 + 死信 + FHIR 门面双路（核心 §10）。
9. **运维 / SRE / 国产化**：死信重放；国产中间件；内外网；离线接入。
10. **质量与真实性审计**：★断连标 `NOT_CONNECTED` 不伪造、数据质量真实统计；无演示页（[INFRA-09](../D1/INFRA-09.md)，铁律 #1/#2）。
11. **AI / 模型治理与可降级**：N·A —— 接入页无模型。

## 适用不变量

- 命中核心约束：**§10 集成边界 / 不阻断主流程** · **铁律 #2 断连不伪造** · **§13 数据质量不伪造** · **依赖 [INTEG-01](INTEG-01.md)/[SVC-PILOT-02](SVC-PILOT-02.md)**。
- 本卡落点：把适配器中心从占位页变为真实状态、诚实降级、含数据质量看板的接入运维页。

## 验收 + 验证

- [x] **AC-1（FR-1/2）**：适配器目录 + 健康；断连显示 `NOT_CONNECTED`（不伪造在线）。
- [x] **AC-2（FR-3/4）**：字段映射配置 + 死信重放；数据质量看板真实统计、缺口暴露。
- [x] **AC-3（FR-5/6）**：接入向导新增适配器；六态齐全；非授权角色无访问。
- 关联 A1–A9 剧本：A1 接入、A6 合规（数据质量证据）。
- T-GATE：前端真实性门禁全绿（no-page-mock、断连不伪造）。
- B0 验收：确定性接入运维，**天然 B0**。

## 完工证据

- 代码 permalink：待 PR 合并后补 GitHub permalink；本地改动覆盖 `frontend/src/pages/tenant/AdapterHub.tsx`、`AdapterHub.module.css`、`frontend/src/shared/api/hooks.ts`、`frontend/src/shared/config/routes.ts`。
- 测试：新增 `AdapterHub.test.tsx` 覆盖适配器目录、健康诊断、`NOT_CONNECTED` 诚实展示、死信重试 / 重放、数据质量报告、接入申请推进、六态；`hooks.test.ts` 覆盖接入生命周期 / 数据质量 / 死信重放 endpoint；`routes.test.ts` 覆盖 RBAC；`RulePathwayCleanliness.test.ts` 阻断旧 Webhook / Launch Token 控制台回流。
- 本地证据：聚焦测试 4 files / 67 tests、页面 smoke 22 tests、前端 `npm run verify` 51 files / 309 tests、生产依赖审计 0、`npm run build`、T-GATE 规则测试 34/34、中文注释 0 fail / 0 warn、`git diff --check`。浏览器打开 `/adapter/hub` 在无登录会话时正确重定向 `/login` 且 console errors 0；受保护页面完整交互由组件级红绿测试覆盖，未伪造登录会话。
- 未冒领：`DEFER-003` 前端测试 / 构建噪声、`DEFER-004` 浏览器截图链路、真实院方连接器 / 大规模压测仍保持 open；本卡不新增后端 / 迁移。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
