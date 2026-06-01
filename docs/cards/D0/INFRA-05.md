# INFRA-05 · 27 二级菜单粒度权限模型

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D0 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：质量基线 §9 13 角色矩阵 · 核心 §2.2 27 二级 + 5 高级菜单 · 详规 §7.3 五维权限。

## 身份
- 卡 ID：INFRA-05
- 域：D0 登录域 / 平台脊柱
- 关联场景：S14 用户、权限与合规（菜单粒度矩阵）
- 依赖卡：[BASE-02](BASE-02.md)（五维权限引擎）· [BASE-06](BASE-06.md)（路由元数据）
- 工作量：10d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标

交付**27 二级菜单 + 5 高级工具粒度的权限矩阵**：后端 `MenuPermissionCatalog`（菜单权限点目录）+ `DefaultPermissionPolicy`（13 角色 × 32 菜单 默认矩阵）+ 前端 `routes.ts` requiredPermissions/requiredRoles 全覆盖，使五维权限的"菜单维"真正到二级粒度生效。

## 功能要求（原子可测条目）

- [x] **FR-1 MenuPermissionCatalog**：后端定义 27 二级 + 5 高级 全部菜单的权限点目录（菜单 key → 权限点），单一权威。
- [x] **FR-2 DefaultPermissionPolicy**：13 角色（质量基线 §9）× 32 菜单 默认可见性矩阵，内置初始化。
- [x] **FR-3 前端 routes.ts 全覆盖**：每路由声明 `requiredPermissions` + `requiredRoles`（[BASE-06](BASE-06.md) 元数据 schema），无遗漏。
- [x] **FR-4 13 角色菜单呈现**：13 角色逐个登入，菜单按矩阵正确呈现（无越权可见、无应见缺失）。
- [x] **FR-5 粒度可定制**：租户/角色可在默认矩阵上局部覆盖（核心 §9 继承覆盖）。
- [x] **FR-6 前后端同源**：菜单可见性前端渲染与后端 `PermissionEvaluator`（[BASE-02](BASE-02.md)）判定一致，无"前端藏后端不校验"。

## 接口契约 / 页面契约
### 接口契约
- 端点：`GET /api/v1/security/menu-permissions/visible`（按五维返回可见菜单树）；`GET /api/v1/security/menu-permissions/catalog`（32 菜单目录 + 13 角色默认矩阵）；`PATCH /api/v1/security/menu-permissions/overrides`（D5 用户管理消费的租户级角色菜单覆盖）。
- DTO：菜单权限矩阵 Record；可见菜单树 Record。
- 响应信封：`ApiResult`。
- 状态机：N·A —— 权限矩阵是治理主数据。
- 幂等 / 错误码 / traceId：矩阵变更幂等 + 审计（[BASE-04](BASE-04.md)）。

### 页面契约
N·A —— 矩阵管理 UI 在 D5「用户管理」；菜单渲染在 [BASE-06](BASE-06.md)。

## 数据与迁移
- 采用既有五维权限权威表族，避免重复建并行权限表：`sys_permission` 保存菜单权限点目录，`role_permission` 保存租户级角色覆盖；默认 13×32 矩阵由 `DefaultPermissionPolicy` 单一初始化。
- 主键 / 唯一约束：沿用 `role_permission` 既有约束；覆盖粒度为 `(tenant_id, role_code, permission_code)`，菜单 key 由 `MenuPermissionCatalog` 映射为权限码。
- 组织字段：`role_permission.tenant_id` 承载租户覆盖；审计上下文由 `AuditRecorder` 从请求上下文补齐组织路径 / traceId / actor。
- 5 方言迁移：h2/postgres/oracle/dm/kingbase 新增 V43，将旧一级菜单权限停用，并种子 31 个新增二级 / 高级菜单权限点；`menu.workbench` 继续复用既有权限码。

## 视角清单（11 视角逐条）
1. **产品架构**：菜单权限点目录单一源；32 菜单粒度锁定（核心 §2.2）。
2. **产品体验**：角色登入只见其菜单（默认角色视图，核心 §16）；无越权噪音。
3. **系统与数据架构**：矩阵可缓存（关系库权威、缓存投影）；菜单树解析高效。
4. **临床医疗安全**：N·A —— 但临床高危菜单（如发布/双签）按角色受限。
5. **知识与数据治理**：知识审核/发布菜单按角色（医务处/专家）可见（核心 §7）。
6. **安全合规与监管**：★本卡主战场 —— 五维"菜单维"到二级粒度，等保最小授权（核心 §8/#7）。
7. **集团化与多租户治理**：租户/角色矩阵覆盖（核心 §9）；集团默认下发、医院局部调整。
8. **集成与互操作**：N·A。
9. **运维 / SRE / 国产化**：默认矩阵种子随迁移；可导出审计。
10. **质量与真实性审计**：★13 角色登入菜单截图为 done 证据（质量基线 §9）；前后端同源无假权限。
11. **AI / 模型治理与可降级**：AI 知识审核/AI 工作流菜单按角色受限。

## 适用不变量
- 命中核心约束：**#7 五维权限（菜单维到 27+5 粒度）** · **§2.2 菜单锁** · **§9 矩阵继承覆盖** · **§13 13 角色截图证据**。
- 本卡落点：MenuPermissionCatalog + DefaultPermissionPolicy + routes.ts 全覆盖 + 前后端同源，让"菜单维权限"从一级 sectionKey 真正下沉到 27+5 二级粒度。

## 验收 + 验证
- [x] **AC-1（FR-1/2）**：32 菜单权限点目录完整；13 角色默认矩阵初始化与质量基线 §9 一致。
- [x] **AC-2（FR-3）**：每路由有 requiredPermissions/requiredRoles，无遗漏（构建校验）。
- [x] **AC-3（FR-4）**：13 角色逐个登入截图，菜单按矩阵呈现（无越权/无缺失）。
- [x] **AC-4（FR-5）**：租户局部覆盖某角色某菜单可见性生效。
- [x] **AC-5（FR-6）**：前端藏菜单但后端未校验的不一致用例被捕获（前后端同源）。
- 关联 A1–A9：A6 合规运维（权限矩阵）。
- T-GATE：前后端门禁全绿。
- B0 验收：纯确定性权限，天然 B0。

## 完工证据
- 代码 permalink：`MenuPermissionCatalog` / `DefaultPermissionPolicy` / `routes.ts` / `AppLayout` / 菜单权限端点 / V43 迁移 + 种子。
- 测试：`MenuPermissionCatalogTest` + `DefaultPermissionPolicyTest` + `EffectivePermissionServiceTest` + `SystemSecurityCatalogRepositoryTest` + `PermissionEvaluatorTest` + `MenuPermissionControllerTest` + `MigrationBaselineContractTest` + `ServiceContractGovernanceTest` + `OpenApiContractConfigurationTest` + `routes.test.ts` / `AppLayout.test.tsx` 前端路由授权测试。
- 全量验证：后端 `mvn -B -q test` 通过，Surefire 汇总 152 报告 / 769 tests / 0 failures / 0 errors / 0 skipped；前端 `npm run verify` 通过 39 files / 171 tests，`npm run build` 通过；真实性门禁、V43 迁移规约、配置边界、中文注释和空白检查通过。
- 角色截图：本机 in-app browser 当前不可用（见 `DEFER-004`），已用项目 Playwright 生成 13 角色菜单截图与 `summary.json`，路径 `/tmp/medkernel-infra05-role-menu-screenshots`；脚本断言 13 角色均无越权、无应见缺失、无浏览器控制台错误。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。

## 大卡工序（10d，后端为主 + 前端 routes）
- PR1：MenuPermissionCatalog + DefaultPermissionPolicy 13×32 矩阵 + 迁移种子 → AC-1。
- PR2：前端 routes.ts 全覆盖 + 可见菜单端点 + 前后端同源 → AC-2/3/5。
- PR3：租户/角色覆盖 + 矩阵管理端点（D5 消费）→ AC-4。
