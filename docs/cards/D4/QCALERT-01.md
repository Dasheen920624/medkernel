# QCALERT-01 · 质控预警页

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D4 域简报](_brief.md) + [体验契约](../../EXPERIENCE_CONTRACT.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §3 S11 智能评估与整改 · 详规 质控预警 · 体验规范 §3 低打扰。
> 实化映射：占位 `D4-PAGE-质控预警` → 本卡 **QCALERT-01**。

## 身份
- 卡 ID：QCALERT-01（页面卡；= backlog `D4-PAGE-质控预警` 实化）
- 域：D4 质控改进
- 关联场景：S11 智能评估与整改
- 依赖卡：[SVC-QUALITY-01](SVC-QUALITY-01.md)（预警后端）· [EVAL-01](EVAL-01.md)（评估问题）· [SVC-QUALITY-03](SVC-QUALITY-03.md)（整改）· [BASE-08](../D0/BASE-08.md)/[BASE-10](../D0/BASE-10.md) · [INFRA-09](../D1/INFRA-09.md)
- 工作量：3d
- owner / reviewer：Codex / 待审（owner ≠ reviewer）

## 目标
把质控预警页**真实化**：阈值越界质控预警实时呈现、可处置、可派整改，低打扰、按作用域，全部接 [SVC-QUALITY-01](SVC-QUALITY-01.md)，**不前端造预警**。

## 现状（搬迁时核查 2026-05-30，以 `frontend/src` 为准）
页面**已存在待真实化**：`pages/quality/QcAlerts.tsx`（路由 `/qc/alerts` 已注册 `app/router.tsx`）。本卡＝去占位/mock + 接质控预警 API + 六态/五维 RBAC/低打扰齐全。

实现更新（2026-06-05）：本分支已把 `/qc/alerts` 从旧整改工作台改为消费 [SVC-QUALITY-01](SVC-QUALITY-01.md) 真实预警 read-model；默认筛选为未处置 / 今日 / 高风险（P0/P1），列表展示真实预警级别、责任科室、阈值、来源和 traceId；处置抽屉支持 `POST /engine/quality/alerts/{alertId}/acknowledge` 真实确认，以及对 `quality_finding` 来源预警调用 [SVC-QUALITY-03](SVC-QUALITY-03.md) `POST /engine/rectifications` 派发整改。页面保留 PageShell 六态，空态/错误态诚实，不前端造预警或 trace。

## 功能要求（原子可测条目）
- [x] FR-1 预警列表：阈值越界预警真实（来源 [SVC-QUALITY-01](SVC-QUALITY-01.md)），含指标/科室/级别。
- [x] FR-2 处置：预警处置（确认/转整改），状态闭环。
- [x] FR-3 派整改：预警一键派整改（[SVC-QUALITY-03](SVC-QUALITY-03.md)）。
- [x] FR-4 低打扰：去重 + 按级别筛选；安全级预警不被静默。
- [x] FR-5 六态 + 五维 RBAC：齐全；质控办/科主任按作用域；数据按 `OrgContext`。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
N·A —— 消费 [SVC-QUALITY-01](SVC-QUALITY-01.md) 与 [SVC-QUALITY-03](SVC-QUALITY-03.md) API：
- `GET /api/v1/engine/quality/alerts?status&severity&from&to&page&size`：读取真实预警分页，默认 `status=OPEN`、`severity=HIGH_RISK`。
- `POST /api/v1/engine/quality/alerts/{alertId}/acknowledge`：确认预警，不在前端伪造状态。
- `POST /api/v1/engine/rectifications`：对质控问题来源预警派发整改，使用 `Idempotency-Key`。
### 页面契约（页面卡）
- 路由元数据：sectionKey `quality` / menuKey `qc-alerts` / menuLabel `质控预警` / path `/qc/alerts` / requiredPermissions 质控预警 / requiredRoles 质控办·科主任。
- 结构：PageShell（[BASE-08](../D0/BASE-08.md)）+ 预警列表（级别色阶 token）+ 处置抽屉 + 六态。
- 主按钮 ≤1（处置/派整改）/ 默认筛选 ≤3（未处置/今日/高级别）/ 默认角色视图（本科或全院）。
- 五维 RBAC：菜单 / 动作（处置/派整改）/ 数据（org）/ 资产 / 环境。
- 样式：仅引用 [BASE-10](../D0/BASE-10.md) token + [体验契约](../../EXPERIENCE_CONTRACT.md)；禁硬编码 hex/px。

## 数据与迁移
N·A —— 页面卡不落库；消费 [SVC-QUALITY-01](SVC-QUALITY-01.md) 后端。

## 视角清单（11 视角逐条）
1. 产品架构：质控"看异常"的预警页。
2. 产品体验：★低打扰、级别清晰、可一键处置；国产浏览器可读。
3. 系统与数据架构：预警列表分页 P95 ≤1s。
4. 临床医疗安全：安全级质控预警不被静默/降级。
5. 知识与数据治理：预警可追溯指标版本（[SYS-08](../D2/SYS-08.md)）。
6. 安全合规与监管：处置/派整改留审计（[BASE-04](../D0/BASE-04.md)）。
7. 集团化与多租户治理：按科室/院作用域。
8. 集成与互操作：预警可外发通知（[SVC-CLINICAL-03](../D3/SVC-CLINICAL-03.md)）。
9. 运维 / SRE / 国产化：内网慢场景骨架。
10. 质量与真实性审计：★无前端造预警、去重正确；无演示路由（[INFRA-09](../D1/INFRA-09.md)）。
11. AI / 模型治理与可降级：N·A（确定性页面）。

## 适用不变量
- 命中核心约束：**铁律 #1 真实性** · **核心 §13 低打扰** · **§9 多租户作用域**。
- 本卡落点：把质控预警页变为接真实预警、可处置派整改、安全级不静默的预警页。

## 验收 + 验证
- [x] AC-1（FR-1/2）：预警真实；处置闭环。
- [x] AC-2（FR-3/4）：一键派整改；低打扰且安全级不静默。
- [x] AC-3（FR-5）：六态齐全；按作用域。
- 关联 A1–A9 剧本：A9 质控预警。
- T-GATE：前端真实性门禁全绿（no-page-mock、无造预警）。
- B0 验收：N·A（确定性页面）。

## 完工证据
- 代码：本 PR 覆盖 `frontend/src/pages/quality/QcAlerts.tsx`、`frontend/src/shared/api/hooks.ts`、`QualityDashboardController` / `QualityDashboardService` / `QualityDashboardAlertFilter`、`ServiceContractCatalog`。
- 测试：`QcAlerts.test.tsx` 覆盖真实预警读取、默认高风险筛选、真实整改派发、真实确认端点与诚实空态；`QualityDashboardServiceTest` 覆盖确认状态刷新保持、租户隔离、级别过滤不受客户端分页扭曲；`QualityDashboardControllerSecurityTest` 覆盖质控角色确认权限。
- 验证：`cd frontend && npm test -- QcAlerts.test.tsx` 通过（4 测试）；`cd frontend && npm test -- pages.smoke.test.tsx` 通过（23 测试）；`cd medkernel-backend && mvn -Dtest=QualityDashboardServiceTest,QualityDashboardControllerSecurityTest test` 通过（12 测试）；`cd frontend && npm run verify` 通过（64 文件 / 385 测试）；`cd frontend && npm run build` 通过（仅既有 vendor chunk warning）；`cd medkernel-backend && mvn test` 通过（1487 测试，含 H2/PostgreSQL/Oracle v88 迁移 smoke）；changed-mode 真实性门禁扫描 6 文件通过；配置边界门禁扫描 4 文件通过；`scripts/check-comment-zh.sh` 0 fail / 0 warn；`git diff --check origin/main...HEAD` 无输出。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
