# SYS-06 · 安全合规与证据框架

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D5 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 S14 用户权限与合规 · 核心 §6 安全合规 · 详规 数据权限/脱敏/导出审批。

## 身份
- 卡 ID：SYS-06（引擎卡；数据权限/脱敏/导出审批框架单一归属）
- 域：D5 合规运维
- 关联场景：S14 用户、权限与合规
- 依赖卡：[BASE-02](../D0/BASE-02.md) 权限 · [BASE-04](../D0/BASE-04.md) 审计 · [EVID-01](EVID-01.md) 证据链 · [INFRA-05](../D0/INFRA-05.md) 五维 RBAC
- 工作量：5d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
建立**安全合规与证据框架**：数据权限（行/列级）+ 脱敏 + 审计统一 + **导出审批** + 证据包，作为全平台合规底座，**敏感数据不裸奔、导出可控可审、证据可验**。

## 现状（搬迁时核查 2026-05-30，以 `medkernel-backend` 为准）
框架化（底座已有）：D0 已有权限（`engine/security` `EffectivePermissionService`/`PermissionEvaluator`，[BASE-02](../D0/BASE-02.md)/[INFRA-05](../D0/INFRA-05.md) 归属）+ 审计（`compliance/audit`，[BASE-04](../D0/BASE-04.md)）+ 证据（[EVID-01](EVID-01.md)）。本卡＝在其上建**数据权限（行/列级）+ 脱敏 + 导出审批**统一框架，建在 D0 之上、不重造权限引擎。

## 功能要求（原子可测条目）
- [x] FR-1 数据权限：行/列级数据权限规则（基于 [BASE-02](../D0/BASE-02.md) 五维 + `OrgContext`），可配可审。PR1 已补策略表、配置接口、服务级门禁与审计。
- [x] FR-2 脱敏：敏感字段按角色/场景脱敏，脱敏规则统一、不前端裸奔。PR2 已补脱敏规则表、后端服务级脱敏、无规则 fail-closed、原文访问受数据范围脱敏标识控制。
- [ ] FR-3 导出审批：敏感数据导出走审批流（变更类状态机），审批留痕。
- [ ] FR-4 证据联动：合规操作生成证据（[EVID-01](EVID-01.md)）。
- [ ] FR-5 统一审计：数据访问/导出/审批统一审计（[BASE-04](../D0/BASE-04.md)）。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 端点：`GET/PUT /api/v1/compliance/data-permissions` · `POST .../exports:request`（导出申请）· `POST .../exports/{id}:approve`
- DTO：数据权限/脱敏/导出审批 Record；信封 `ApiResult`/`ProblemDetail`
- 状态机：变更类（导出：申请→审批→已导出/驳回）；配置类（数据权限）
- 幂等 / traceId：审批幂等；trace（[OBS-01](../D0/OBS-01.md)）

## 数据与迁移
- 表族：`data_permission` / `masking_rule` / `export_approval`（规则 + 状态 + 组织字段 + 审计）；五方言（[BASE-05](../D0/BASE-05.md)）。
- PR1 已落 `mk_compliance_data_permission`：租户 + 资源 + 动作唯一，含最小数据级别、允许列 JSON、七层组织范围、状态、版本、审计字段与 traceId。
- PR2 已落 `mk_compliance_masking_rule`：租户 + 资源 + 字段 + 场景唯一，含脱敏策略、保留字符数、状态、版本、审计字段与 traceId；PR3 继续补 `export_approval`。

## 视角清单（11 视角逐条）
1. 产品架构：全平台合规的"数据安全框架"。
2. 产品体验：脱敏对终端透明（页 [SECBASE-01](SECBASE-01.md)/[USERS-01](USERS-01.md)）。
3. 系统与数据架构：行/列权限下推查询；脱敏不前端做（后端落实）；P95 可控。
4. 临床医疗安全：临床数据脱敏不影响诊疗必要信息可见性（按角色）。
5. 知识与数据治理：导出审批 + 证据保证数据流向可追溯。
6. 安全合规与监管：★行/列权限 + 脱敏 + 导出审批 + 审计，满足等保/隐私法规。
7. 集团化与多租户治理：★数据权限严格租户隔离，下级不可放大权限。
8. 集成与互操作：导出经审批 + 证据（[EVID-01](EVID-01.md)）。
9. 运维 / SRE / 国产化：脱敏/审批可观测。
10. 质量与真实性审计：★敏感数据不裸奔、导出可审、脱敏后端落实。
11. AI / 模型治理与可降级：模型不可访问未脱敏敏感数据。

## 适用不变量
- 命中核心约束：**核心 §6 安全合规** · **§9 多租户隔离** · **铁律 #1** · **§数据权限/脱敏**。
- 本卡落点：数据权限 + 脱敏 + 导出审批框架，建在 D0 权限/审计之上。

## 验收 + 验证
- [x] AC-1（FR-1/2）：行/列权限生效；脱敏后端落实不裸奔。PR1 覆盖行/列权限，PR2 覆盖后端脱敏规则、原文访问边界与缺规则 fail-closed。
- [ ] AC-2（FR-3/4/5）：导出走审批 + 证据 + 审计。
- 关联 A1–A9 剧本：A9 数据合规。
- T-GATE：后端真实性门禁全绿（脱敏落实/导出可审）。
- B0 验收：数据权限/脱敏/审批确定性，关模型可用。

## 大卡工序（5d）
- PR1：行/列数据权限 + 门禁 → 已完成本地验收，本 PR 不冒领整卡完成
- PR2：脱敏框架（后端落实）→ 已完成本地验收，本 PR 不冒领整卡完成
- PR3：导出审批 + 证据/审计联动 → 验收

## 完工证据
- 代码 permalink：数据权限 + 脱敏 + 导出审批框架。
- 测试：行列权限/脱敏/导出审批/审计 + 安全测试。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。

### PR1 阶段证据（2026-06-06，本 PR 不冒领整卡完成）
- 实现范围：`com.medkernel.compliance.datapermission` 新增数据权限策略 Record DTO / 仓储 / 服务 / 控制器，`DataPermissionService.assertAccess` 复用 D0 `DataScopeResolver` 输出执行行级范围、数据级别与列级 allow-list 门禁；策略变更写 `PERMISSION_CHANGE` 审计。
- 契约与迁移：新增 `GET/PUT /api/v1/compliance/data-permissions`，权限为 `audit.read` / `system.manage` 且要求租户数据范围；`ServiceContractCatalog` 登记 `compliance-data-permission`，`DomainOwnershipCatalog` 登记 `compliance-security`；V90 五方言新增 `mk_compliance_data_permission` 并补迁移基线合同。
- 验证：红灯测试先失败于缺数据权限实现；随后 `mvn -Dtest=DataPermissionServiceTest,DataPermissionControllerSecurityTest,MigrationBaselineContractTest,H2BaselineMigrationTest test` 通过（86 测试），`mvn -Dtest=ServiceContractGovernanceTest,DomainOwnershipContractTest,DataScopeResolverTest test` 通过，后端全量 `mvn test` 通过（1504 测试），前端 `npm run verify` 通过（67 文件 / 405 测试），V90 迁移规约 files-mode、真实性全仓、配置边界 inventory、中文注释与 `git diff --check` 均通过。

### PR2 阶段证据（2026-06-06，本 PR 不冒领整卡完成）
- 实现范围：`com.medkernel.compliance.masking` 新增脱敏规则 Record DTO / 仓储 / 服务 / 控制器，`MaskingService.mask` 按租户、资源、场景、字段匹配规则；场景规则缺失时回退 `DEFAULT`，敏感字段缺有效规则时 fail-closed；原文访问仅在当前 `ResolvedDataScope` 允许且未标记脱敏时放行。
- 契约与迁移：新增 `GET/PUT /api/v1/compliance/masking-rules`，权限为 `audit.read` / `system.manage` 且要求租户数据范围；规则变更写 `PERMISSION_CHANGE` 审计；`ServiceContractCatalog` 登记 `compliance-masking-rule`，`DomainOwnershipCatalog` 登记 `compliance-security`；V91 五方言新增 `mk_compliance_masking_rule` 并补迁移基线合同。
- 验证：红灯测试先失败于缺脱敏实现；随后 `mvn -Dtest=MaskingServiceTest,MaskingRuleControllerSecurityTest,MigrationBaselineContractTest,H2BaselineMigrationTest test` 通过（89 测试），`mvn -Dtest=ServiceContractGovernanceTest,DomainOwnershipContractTest,DataScopeResolverTest,FlywayMultiDialectSmokeTest test` 通过（13 测试，H2 / PostgreSQL 15.18 / Oracle 21.3 迁移到 V91 并二次 no-op），空值边界 `mvn -Dtest=MaskingServiceTest#maskPreservesNullSensitiveFieldWithoutLeakingOrCrashing test` 通过，扩展目标套件 100 测试通过，后端全量 `mvn test` 通过（1516 测试），前端 `npm run verify` 通过（67 文件 / 405 测试）。T-GATE changed-mode 待提交前补跑。
