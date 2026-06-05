# INSAUDIT-01 · 医保智能审核页

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D4 域简报](_brief.md) + [体验契约](../../EXPERIENCE_CONTRACT.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §3 S10 医保与病案质控 · 详规 医保审核 · 体验规范 §3。
> 实化映射：占位 `D4-PAGE-医保智能审核` → 本卡 **INSAUDIT-01**。

## 身份
- 卡 ID：INSAUDIT-01（页面卡；= backlog `D4-PAGE-医保智能审核` 实化）
- 域：D4 质控改进
- 关联场景：S10 医保与病案质控
- 依赖卡：[SVC-QUALITY-02](SVC-QUALITY-02.md)（病案医保后端）· [SVC-QUALITY-03](SVC-QUALITY-03.md)（整改）· [BASE-08](../D0/BASE-08.md)/[BASE-10](../D0/BASE-10.md) · [API-13](../D0/API-13.md) · [INFRA-09](../D1/INFRA-09.md)
- 工作量：3d
- owner / reviewer：Codex / 待审（owner ≠ reviewer）

## 目标
把医保智能审核页**真实化**：呈现医保违规/DRG 入组/编码费用问题，可追溯病历证据、派整改，全部接 [SVC-QUALITY-02](SVC-QUALITY-02.md)，**不前端造违规、不臆造**。

## 现状（2026-06-06，以 `frontend/src` / `medkernel-backend` 为准）
页面已由本卡真实化：`pages/quality/InsuranceAudit.tsx`（路由 `/qc/insurance` 已注册 `app/router.tsx`）消费 [SVC-QUALITY-02](SVC-QUALITY-02.md) 的真实医保病案接口，默认筛选 `未处理 / 本月 / 高金额或高风险`，列表读取当前租户真实 `mk_quality_insurance_issue`，审核动作依次调用病案内涵、DRG/DIP、医保审核三条 B0 接口；无结算事实时后端诚实返回 `INSUFFICIENT_DATA`，前端不造违规。

## 功能要求（原子可测条目）
- [x] FR-1 违规列表：医保违规/编码/费用问题真实（[SVC-QUALITY-02](SVC-QUALITY-02.md)），含规则依据。
- [x] FR-2 证据追溯：每条违规可追溯到病历证据，不臆造。
- [x] FR-3 DRG/DIP：入组结果与异常可见、可解释。
- [x] FR-4 派整改：执行医保审核时后端按命中问题联动 [SVC-QUALITY-03](SVC-QUALITY-03.md) 生成整改任务，页面展示本次整改任务数。
- [x] FR-5 六态 + 五维 RBAC：PageShell 六态；接口按 `evaluation.read`/`evaluation.execute` 与当前租户作用域读取；菜单/角色由路由元数据承接。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- `GET /api/v1/engine/quality/insurance-issues`：分页读取当前租户真实医保问题，筛选 `status/severity/departmentId/from/to/page/size`，返回 `PageResponse<InsuranceIssuePageItemResponse>`。
- `POST /api/v1/engine/quality/case-review`：病案内涵质控，复用评估运行，关模型返回确定性 `MODEL_DISABLED` 证据。
- `POST /api/v1/engine/quality/drg-grouping`：DRG/DIP 入组核对，返回期望/实际/解释。
- `POST /api/v1/engine/quality/insurance-audit`：按真实 `mk_clinical_claim` 结算事实审核，命中时生成医保问题并联动整改任务；无事实返回 `INSUFFICIENT_DATA`。
### 页面契约（页面卡）
- 路由元数据：sectionKey `quality` / menuKey `qc-insurance` / menuLabel `医保智能审核` / path `/qc/insurance` / requiredPermissions 医保审核 / requiredRoles 医保办·病案室。
- 结构：PageShell（[BASE-08](../D0/BASE-08.md)）+ 违规列表 + 证据追溯抽屉 + DRG 入组面板 + 六态。
- 主按钮 ≤1（派整改）/ 默认筛选 ≤3（未处理/本月/高金额）/ 默认角色视图（医保办）。
- 五维 RBAC：菜单 / 动作（派整改）/ 数据（org）/ 资产 / 环境。
- 样式：仅引用 [BASE-10](../D0/BASE-10.md) token + [体验契约](../../EXPERIENCE_CONTRACT.md)；禁硬编码 hex/px。

## 数据与迁移
N·A —— 本页不新增迁移；读取 [SVC-QUALITY-02](SVC-QUALITY-02.md) 已落库的 V84 `mk_quality_insurance_issue` / `mk_clinical_claim` 等真实事实表。

## 视角清单（11 视角逐条）
1. 产品架构：医保合规的"审核工作台"。
2. 产品体验：违规可追溯病历、一键派整改；国产浏览器可读。
3. 系统与数据架构：违规列表分页 P95 ≤1s。
4. 临床医疗安全：医保审核不干预临床诊疗决策。
5. 知识与数据治理：违规规则版本化可追溯（[SYS-08](../D2/SYS-08.md)）。
6. 安全合规与监管：★违规作监管证据须有病历依据、留审计（[BASE-04](../D0/BASE-04.md)）。
7. 集团化与多租户治理：按院/病案室作用域。
8. 集成与互操作：医保数据经适配器（[INTEG-01](../D2/INTEG-01.md)）入。
9. 运维 / SRE / 国产化：内网慢场景骨架。
10. 质量与真实性审计：★无前端造违规、违规追溯病历、不臆造；无演示路由（[INFRA-09](../D1/INFRA-09.md)）。
11. AI / 模型治理与可降级：编码辅助为挂点，关模型确定性规则审核。

## 适用不变量
- 命中核心约束：**铁律 #1 真实性（不臆造违规）** · **§2 菜单 IA** · **§9 多租户作用域** · **合规监管**。
- 本卡落点：把医保审核页变为接真实违规、追溯病历证据、可派整改的审核台。

## 验收 + 验证
- [x] AC-1（FR-1/2）：违规真实、追溯病历证据。
- [x] AC-2（FR-3/4）：DRG 入组可解释；审核命中后端联动整改。
- [x] AC-3（FR-5）：六态齐全；按作用域。
- 关联 A1–A9 剧本：A9 医保病案审核。
- T-GATE：前端真实性门禁全绿（no-page-mock、无造违规）。
- B0 验收：N·A（确定性页面）。

## 完工证据
- 代码 permalink：`frontend/src/pages/quality/InsuranceAudit.tsx` 真实化 + `frontend/src/shared/api/hooks.ts` 接 [SVC-QUALITY-02](SVC-QUALITY-02.md)；`InsuranceQualityController` / `InsuranceQualityService` 增加真实医保问题分页读取。
- 测试：`InsuranceAudit.test.tsx` 覆盖真实列表、证据、DRG、审核联动整改与空态；`hooks.test.ts` 覆盖 SVC-QUALITY-02 GET/POST 契约；`InsuranceQualityServiceTest` 覆盖租户作用域筛选分页；`InsuranceQualityControllerSecurityTest` 覆盖读取权限。
- 验证：`cd frontend && npm test -- hooks.test.ts InsuranceAudit.test.tsx pages.smoke.test.tsx`；`cd frontend && npm run verify`；`cd frontend && npm run build`；`cd medkernel-backend && mvn -Dtest=InsuranceQualityServiceTest,InsuranceQualityControllerSecurityTest test`；`cd medkernel-backend && mvn test`；`scripts/check-comment-zh.sh`；`git diff --check`。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
