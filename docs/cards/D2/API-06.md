# API-06 · 路径引擎 API

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D2 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §1.5.2 当前/后续 API 清单·路径行（L185）· §5.4 路径运行状态（L1168）· 落地规划 §8.3 路径引擎（L468）· 核心 §1.4 统一入参。

## 身份
- 卡 ID：API-06（= backlog 任务 ID）
- 域：D2 试点准备
- 关联场景：S6 路径引擎配置（客户面 API）
- 依赖卡：[PATH-01](PATH-01.md)（路径引擎）· [SYS-04](SYS-04.md)（发布）· [API-01](API-01.md)（推进输入）· [BASE-03](../D0/BASE-03.md)（契约）· [API-13](../D0/API-13.md)（大列表）
- 工作量：3d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
提供路径引擎**统一 REST 客户面**：模板 · 路径知识包 · 患者路径 · 节点推进 · 变异 · 关键时钟。本卡只立 **API 契约**，能力在 [PATH-01](PATH-01.md)、发布在 [SYS-04](SYS-04.md)。

## 现状（搬迁时核查 2026-05-30，以 `medkernel-backend/src` 为准）
`engine/pathway` **控制器已建**，本卡＝**契约化 + 统一入参/分页对齐**：
- 已有：`PathwayEngineController`(+ 安全测试)、`PathwayEngineService`、`PathwayTemplateCreateRequest`/`Detail`/`Filter`/`PublishResponse`、`PatientPathwayEnterRequest`/`Detail`、`PathwayAdvanceRequest`/`Response`、`PathwaySimulateRequest`/`Response`、`PathwayPackageBuildRequest`、`SpecialtyMetricBindingRequest`、`ClinicalClock`。
- 2026-06-02 本卡补齐：① 统一 12 字段入参 + `ApiResult`/`ProblemDetail`；② `/api/v1/engine/pathway/**` 客户面；③ `patient-pathways/{id}/advance` 路径参数合同；④ `variances`/`clocks` 独立查询；⑤ 前端共享 hooks 和消费页清理旧 `/engine/rules/**`、`/engine/pathways/**` 口径。
- 2026-06-02 PATH-01 PR1 追加：`POST /pathway-templates/{id}/simulate` 与 `POST /patient-pathways/{id}/advance` 支持 `snapshotId` 读取 [API-01](API-01.md) 真实上下文快照；响应追加快照质量 / 缺失字段 / 映射状态 / 资源计数，推进响应追加 `edgeCode`、`edgeType`、`decisionEvidence`。条件边消费快照事实证据；无条件默认边仅在条件边均未命中时执行确定性兜底。

## 功能要求（原子可测条目）
- [x] **FR-1 模板/路径知识包**：`GET/POST /pathway-templates`；`POST /api/v1/engine/pkg/packages/pathway` 创建草稿，`GET /api/v1/engine/pkg/packages?assetType=PATHWAY` 分页查询（[API-13](../D0/API-13.md)）。
- [x] **FR-2 患者路径**：`POST /patient-pathways/enter`、`GET /patient-pathways/{id}`（节点/变异/时钟状态）。
- [x] **FR-3 节点推进**：`POST /patient-pathways/{id}/advance`（幂等，返回 `PathwayProgressDecision` + 解释）。
- [x] **FR-4 变异/时钟**：`GET /patient-pathways/{id}/variances`、`GET /patient-pathways/{id}/clocks`（超时状态）。
- [x] **FR-5 仿真 + 发布**：`POST /pathway-templates/{id}/simulate`、`POST /pathway-templates/{id}/publish`（7 步流，[SYS-04](SYS-04.md)）。
- [x] **FR-6 统一入参/信封**：12 字段入参 + `ApiResult`/`ProblemDetail`（[BASE-03](../D0/BASE-03.md)）。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 端点：`/api/v1/engine/pathway/**`（pathway-templates、patient-pathways、advance、variances、clocks、simulate、publish）+ `/api/v1/engine/pkg/packages/pathway`（路径知识包草稿）。
- DTO：复用 `PathwayTemplateCreateRequest`/`Detail`/`PatientPathwayEnterRequest`/`PathwayAdvanceRequest`/`Response`/`PathwaySimulateRequest`。
- 响应信封：`ApiResult` / `ProblemDetail`；大列表 `PageResult`（[API-13](../D0/API-13.md)）。
- 状态机：路径版本核心 §3；患者路径运行态 `PatientPathwayStatus`。
- 幂等 / 错误码 / traceId：推进按 `(patient_pathway, node, event)` 幂等；时钟缺失 → `PATHWAY_CLOCK_MISSING`；traceId（[OBS-01](../D0/OBS-01.md)）。
### 页面契约（页面卡）
N·A —— 本卡无页面。被 [PATH-01](PATH-01.md) 路径配置页 + D3 患者路径页消费。

## 数据与迁移
- 无独立表族——复用 [PATH-01](PATH-01.md) 表族。不落新库（API 契约卡）。

## 视角清单（11 视角逐条）
1. **产品架构**：路径能力统一对外契约口（配置侧 + D3 运行侧共用）。
2. **产品体验**：N·A —— 路径配置页（[PATH-01](PATH-01.md)）/ D3 患者路径页消费。
3. **系统与数据架构**：推进幂等；统一入参/分页；P95 ≤1s。
4. **临床医疗安全**：变异/时钟状态如实暴露；超时不静默。
5. **知识与数据治理**：路径版本/变异可溯。
6. **安全合规与监管**：入径/推进/变异留审计（[BASE-04](../D0/BASE-04.md)）。
7. **集团化与多租户治理**：按 `OrgContext` 作用域；路径知识包继承。
8. **集成与互操作**：推进输入为标准上下文（[API-01](API-01.md)）。
9. **运维 / SRE / 国产化**：灰度/回滚；大列表分页稳定。
10. **质量与真实性审计**：无伪造推进/时钟；端点真实连引擎（铁律 #1）。
11. **AI / 模型治理与可降级**：推进确定性；AI 路径候选经本 API 入审核，关模型不影响运行。

## 适用不变量
- 命中核心约束：**§1.4 统一入参** · **§5.4 运行状态** · **§4 7 步流** · **依赖 [PATH-01](PATH-01.md)/[SYS-04](SYS-04.md)/[API-13](../D0/API-13.md)**。
- 本卡落点：路径能力以统一契约对外，配置与运行共用一套端点。

## 验收 + 验证
- [x] **AC-1（FR-1/2）**：模板/路径知识包 CRUD + 患者入径，统一信封；分页稳定。
- [x] **AC-2（FR-3/4）**：节点推进幂等 + 解释；变异/时钟状态可查。
- [x] **AC-3（FR-5）**：仿真不写库；7 步流发布灰度→全量→回滚。
- [x] **AC-4（FR-6）**：缺统一入参 → `ProblemDetail`；越权 → 0 + 审计。
- 关联 A1–A9 剧本：A3 路径配置、A4 发布回滚。
- T-GATE：真实性门禁全绿。
- B0 验收：确定性推进，**天然 B0**。

## 完工证据
- 代码 permalink：`/api/v1/engine/pathway/**` 端点 + 推进/变异/时钟 + 发布接 [SYS-04](SYS-04.md)。
- 测试：契约 + 安全 + 推进幂等 + 变异/时钟 + 发布回滚测试；本地聚焦已过：`mvn -q -Dtest=PathwayEngineApiContractTest,PathwayEngineControllerSecurityTest,PathwayEngineServiceTest,ServiceContractGovernanceTest,OpenApiContractConfigurationTest test`、`npm run typecheck`、`npm test -- --run src/pages/tenant/RulePathwayCleanliness.test.ts`；本地全量已过：`mvn -q test`（865 tests / 0 failures / 0 errors / 0 skipped，含 PostgreSQL 15.18 + Oracle 21.3 迁移至 V48）、`npm run verify`（41 files / 206 tests）、`npm run build`、T-GATE（真实性 24/24、迁移规约 8/8、配置边界 2/2、changed 扫描 0 阻断、中文注释 0 fail / 0 warn、`git diff --check`）。PATH-01 PR1 追加验证：`PathwayEngineServiceTest` 覆盖实时推进携带 `snapshotId` 并返回 `edgeCode`、`edgeType`、快照质量与 `decisionEvidence`；`PathwayProgressorTest` 覆盖条件边优先于默认 fallback；聚焦后端套件、后端全量、前端 verify/build、T-GATE 均通过，历史噪声与历史迁移债务分别登记在 `DEFER-003`、`DEFER-006`、`DEFER-016`。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
