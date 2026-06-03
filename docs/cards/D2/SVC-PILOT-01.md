# SVC-PILOT-01 · 租户与组织服务包

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D2 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：落地规划 §4 集团化与组织复用模型（L189，消费 D0）· 详规 §S1 集团与租户开通（L478）· FOUNDATION §4 业务服务包装（L113）· 核心 §9 集团多租户继承。

## 身份
- 卡 ID：SVC-PILOT-01（= backlog 任务 ID）
- 域：D2 试点准备
- 关联场景：S1 集团与租户开通
- 依赖卡：[BASE-01](../D0/BASE-01.md)（OrgContext / 七层组织继承 / 行级隔离）· [BASE-02](../D0/BASE-02.md)/[INFRA-05](../D0/INFRA-05.md)（五维 RBAC）· [SYS-04](SYS-04.md)（配置继承）· [BASE-04](../D0/BASE-04.md)（开通审计）
- 工作量：4d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
提供**租户开通 + 客户实施向导 + 组织树（核心 §9 七层：平台→集团→医院→院区→社区服务点→科室→专病）**的服务包：把试点医院从"空租户"带到"可配置可运行"——组织树建立、租户开通就绪检查、实施向导分步推进。本卡是**服务包组合层**（编排 D0 脊柱 + tenant/org 引擎），为 [IMPL-01](IMPL-01.md) 客户实施向导页 / [TENANT-01](TENANT-01.md) 租户开通页供后端服务。

## 现状（搬迁时核查 2026-05-30，以 `medkernel-backend/src` 为准）
`engine/tenant` + `engine/org` **已建**，本卡＝**服务包编排 + 实施向导/开通就绪补全**：
- 已有：`engine/tenant`（`TenantPilotService`、`SuccessPlan`(+Controller/Repository)、`Branding`(+Controller/Repository)）；`engine/org`（`OrgUnit`/`OrgLevel`/`OrgUnitStatus` + `OrgUnitService`/`Controller`）；组织继承底座在 [BASE-01](../D0/BASE-01.md)。
- 本卡已补：① **实施向导**分步状态机（组织树 → 用户 → 权限 → 适配器 → 资产 → 灰度 的就绪检查）；② **租户开通就绪门**（各前置项 done 才可开通）；③ 组织树 CRUD 经 `OrgUnit`/`OrgLevel` 落地 + 继承校验（核心 §9 七层：租户根→集团→医院→院区→社区服务点→科室→专病，专病为维度）；④ 前端租户开通页切到 engine tenant 路由并清理旧 `Tabs.TabPane`。

## 功能要求（原子可测条目）
- [x] **FR-1 组织树**：建核心 §9 七层 `OrgUnit`（租户根→集团→医院→院区→社区服务点→科室→专病，专病为维度）；层级合法性校验（不可跨层挂、单根无环）；经 [BASE-01](../D0/BASE-01.md) 行级隔离。
- [x] **FR-2 实施向导**：分步就绪检查（组织/用户/权限/适配器/资产/灰度），每步 done/blocked 可见、可跳转对应配置页；不伪造"已就绪"。
- [x] **FR-3 租户开通就绪门**：全部前置项 done 才允许开通；缺项明确列出阻塞原因。
- [x] **FR-4 租户成功计划**：`SuccessPlan` 记录试点里程碑/负责人/状态，供实施跟踪。
- [x] **FR-5 品牌/配置**：`Branding`（院内标识）按租户隔离配置。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 端点：`/api/v1/engine/tenant/**`、`/api/v1/engine/org/**`（branding、success-plan、onboarding-readiness、implementation-steps、org-units）；旧 `/platform/**` 与 `/tenant/org-units` 仅保留兼容，不作为新前端入口。
- DTO：复用 `OrgUnit`/`SuccessPlan`/`Branding`；新增 `OnboardingReadiness`（各前置项状态）· `ImplementationStep`（向导步骤）。
- 响应信封：`ApiResult` / `ProblemDetail`（[BASE-03](../D0/BASE-03.md)）。
- 状态机：实施向导走核心 §3 待办/任务态；组织单元 `OrgUnitStatus`。
- 幂等 / 错误码 / traceId：开通未就绪 → `TENANT_ONBOARD_NOT_READY` + 阻塞清单；跨层挂载 → `ORG_LEVEL_INVALID`；traceId（[OBS-01](../D0/OBS-01.md)）。
### 页面契约（页面卡）
N·A —— 本卡为服务包后端。页面在 [IMPL-01](IMPL-01.md)（客户实施向导）/ [TENANT-01](TENANT-01.md)（租户开通）。

## 数据与迁移
- 表族（已有）：`org_unit`、`tenant_success_plan`、`tenant_branding`、`sys_user_role_assignment`、`integration_adapter`、`knowledge_package`、`release_plan`。
- 本卡不新增 `onboarding_readiness`/`implementation_step` 表：`OnboardingReadiness` 与 `ImplementationStep` 是确定性 DTO，按现有关系事实实时聚合，避免产生第二真相源。
- 组织树主键 / 闭包 / 唯一约束沿用 [BASE-01](../D0/BASE-01.md) 既有迁移；本卡无新增 SQL 迁移，changed 迁移规约扫描 0 个 SQL。

## 视角清单（11 视角逐条）
1. **产品架构**：把试点医院"开起来"的服务包，编排组织/用户/权限/资产就绪。
2. **产品体验**：N·A —— 向导/开通页在 [IMPL-01](IMPL-01.md)/[TENANT-01](TENANT-01.md)。
3. **系统与数据架构**：组织树继承解析（[BASE-01](../D0/BASE-01.md)）；就绪检查聚合各域只读状态。
4. **临床医疗安全**：开通就绪门确保"未配齐不可上临床"，避免空配置直达 D3。
5. **知识与数据治理**：资产就绪项接 [SVC-PILOT-03](SVC-PILOT-03.md)（资产准备）。
6. **安全合规与监管**：开通/组织变更留审计（[BASE-04](../D0/BASE-04.md)）。
7. **集团化与多租户治理**：★主战场 —— 组织树与继承均按核心 §9 七层（平台→集团→医院→院区→社区服务点→科室→专病）+ 行级隔离。
8. **集成与互操作**：适配器就绪项接 [SVC-PILOT-02](SVC-PILOT-02.md)/[INTEG-01](INTEG-01.md)。
9. **运维 / SRE / 国产化**：5 方言；离线开通；国产环境就绪检查。
10. **质量与真实性审计**：就绪状态真实聚合、无伪造"已就绪"（铁律 #1）。
11. **AI / 模型治理与可降级**：N·A —— 服务包确定性，无模型。

## 适用不变量
- 命中核心约束：**§9 集团多租户继承 / 行级隔离** · **§13 真实性（就绪不伪造）** · **依赖 [BASE-01](../D0/BASE-01.md)/[BASE-02](../D0/BASE-02.md)**。
- 本卡落点：把"开通一家试点医院"编排为可检查、可阻塞、可审计的服务包。

## 验收 + 验证
- [x] **AC-1（FR-1）**：建核心 §9 七层组织树；跨层挂载 → `ORG_LEVEL_INVALID`；跨租户不可见。
- [x] **AC-2（FR-2/3）**：向导各步状态真实；缺项时开通 → `TENANT_ONBOARD_NOT_READY` + 阻塞清单；补齐后可开通。
- [x] **AC-3（FR-4/5）**：SuccessPlan 里程碑可跟踪；Branding 按租户隔离。
- 关联 A1–A9 剧本：A1 接入/开通、A5 集团复用。
- T-GATE：真实性门禁全绿。
- B0 验收：确定性服务包，**天然 B0**。

## 完工证据
- 代码落点：`TenantEngineController`、`TenantPilotService`、`OnboardingReadiness`、`ImplementationStep`、`OrgLevel.canHaveParent`、`OrgUnitService`、`TenantOnboarding`、`frontend/src/shared/api/hooks.ts`。
- 测试：`OrgUnitServiceTest`、`OrgHierarchyIntegrationTest`、`TenantPilotServiceTest`、`TenantEngineControllerContractTest`、`RulePathwayCleanliness.test.ts`。
- 本地验证（2026-06-03）：后端聚焦套件、后端全量 `mvn -q test`、前端 `npm run verify`、`npm audit --omit=dev --audit-level=moderate`、`npm run build`、T-GATE 脚本自测、未提交变更真实性 / 配置边界扫描、`git diff --check` 均已执行；提交后需重跑 changed-mode T-GATE 并等待远端 CI。
- 审计员签字：待 PR reviewer（owner ≠ reviewer）。
