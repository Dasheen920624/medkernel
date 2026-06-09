# SUPERADMIN-01 · 内置超级管理员

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D0 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：核心 #20 内置超级管理员 · 超管/配置前台化设计 §B1 · 全系统核查（维护全部功能依赖手配漏配缺陷）。

## 身份
- 卡 ID：SUPERADMIN-01（backlog v8.1 D0 新增）
- 域：D0 登录域 / 平台脊柱
- 关联场景：S14 用户、权限与合规
- 依赖卡：[BASE-02](BASE-02.md)（RBAC 须支持不可撤销满权组）· [BASE-11](BASE-11.md)（种子身份 → 种子超管）· [INFRA-05](INFRA-05.md)（权限目录含超管自动授满）· [BASE-04](BASE-04.md)（独立高亮审计）
- 工作量：3d
- owner / reviewer：待派单（owner ≠ reviewer，高风险建议双签）

## 目标

交付**系统强制内置的超级管理员**：启动自动授满五维 + 系统配置中心访问，不可降权/删除/移出超管组，**不旁路**（走 RBAC 引擎、全程可审计），强制 MFA——修正"维护全部功能依赖手配、漏配即达不到、且存旁路黑洞"的缺陷。

## 功能要求（原子可测条目）

- [x] **FR-1 启动强制内置**：系统启动强制存在内置种子超管（承接 [BASE-11](BASE-11.md) 首发种子身份）；系统级约束，非配置可关。
- [x] **FR-2 自动授满**：超管自动获满五维权限（菜单/动作/数据/资产/环境）+ 系统配置中心访问（核心 #20），无需手配。
- [x] **FR-3 不可降权/删除/移出**：超管账号不可降权、不可删除、不可移出超管组（系统级约束，非配置可改），防被顶替。
- [x] **FR-4 不旁路**：超管仍走 [BASE-02](BASE-02.md) RBAC 引擎鉴权（权限被系统配满），**断言无** `if(isSuperadmin) return true` 旁路分支（核心 #20）。
- [x] **FR-5 独立高亮审计 + MFA**：超管所有操作独立高亮审计（[BASE-04](BASE-04.md)）+ 强制 MFA（[BASE-11](BASE-11.md)）。
- [x] **FR-6 高危护栏**：超管也**无法从 UI 关闭审计持久化**等高危项（核心 #19，呼应 [CONFIG-01](CONFIG-01.md)）。

## 接口契约 / 页面契约
### 接口契约
- 端点：超管管理经 RBAC 引擎与用户管理端点（D5）；本卡定义"不可撤销满权超管组"系统级约束。
- DTO：超管组定义 Record（系统内置标记）。
- 响应信封：`ApiResult` / `ProblemDetail`（尝试降权/删除超管 → 拒绝）。
- 状态机：N·A —— 超管是系统级身份约束，非四类资产。
- 幂等 / 错误码 / traceId：`SUPERADMIN_IMMUTABLE`（拒绝降权/删除）；超管动作带高亮审计 traceId。

### 页面契约
超管在用户管理页（D5 `AdminUsers`）以系统内置不可编辑形态呈现：角色名显示"内置超级管理员"，凭证与角色绑定行均只显示禁用态"系统内置"，不暴露停用、重置密码、解除绑定等租户管理操作。

## 数据与迁移
- 表族：复用 `platform_credential` / `sys_role` / `user_role_assignment`，不新增过时 `sys_user` / `user_role` / `is_system_superadmin` 模型。
- 唯一约束：沿用 `sys_role(tenant_id, role_code)` 与 `user_role_assignment(tenant_id, user_id, role_code, scope_level, scope_code)` 唯一约束；不可删除、降权、移出由 `SystemSuperAdminGuard` 在租户管理入口统一拒绝。
- 5 方言迁移：V44 `system_superadmin_seed` 在 H2 / PostgreSQL / Oracle / 达梦 / 人大金仓只建立内置角色目录，不预造无凭证的角色绑定；唯一超管身份由 BASE-11 首次接管在平台主租户创建。

## 视角清单（11 视角逐条）
1. **产品架构**：超管是"系统可被完整维护"的保证；消除手配漏配缺陷。
2. **产品体验**：超管在用户管理以不可编辑系统身份呈现，避免误降权。
3. **系统与数据架构**：满权经引擎注入（非旁路）；不可删除双重约束（DB + 应用）。
4. **临床医疗安全**：N·A —— 但超管也受临床高危双签流程约束（不因满权跳过双签，核心 §6）。
5. **知识与数据治理**：超管对知识的高危操作仍留痕可审（核心 §7）。
6. **安全合规与监管**：★本卡主战场 —— 满权高危账号经"不旁路 + 高亮审计 + 强制 MFA + 不可删除"控制（等保，核心 §8/#20）。
7. **集团化与多租户治理**：平台级超管 vs 租户级管理员边界清晰（核心 §9）。
8. **集成与互操作**：N·A。
9. **运维 / SRE / 国产化**：超管是运维兜底身份；应急经 [BASE-11](BASE-11.md) CLI 受控。
10. **质量与真实性审计**：★禁旁路黑洞（核心 #20/#18）；超管走引擎、动作全审计可证。
11. **AI / 模型治理与可降级**：N·A —— 天然 B0。

## 适用不变量
- 命中核心约束：**#20 内置超管（授满/不可降权/不旁路/高亮审计/强制 MFA）** · **#19 高危护栏** · **§8 安全合规** · **#18 禁旁路黑洞**。
- 本卡落点：系统内置不可撤销满权超管组 + 走 RBAC 引擎注入满权（非 if 后门）+ 强制 MFA + 高亮审计，让"维护全部功能"不靠手配、且无旁路黑洞。

## 验收 + 验证
- [x] **AC-1（FR-1/2）**：启动后超管存在且自动满五维 + 系统配置中心可达（无需手配）。
- [x] **AC-2（FR-3）**：尝试降权/删除/移出超管 → `SUPERADMIN_IMMUTABLE` 拒绝。
- [x] **AC-3（FR-4）**：代码断言**无** `if(isSuperadmin)` 旁路；超管鉴权走 [BASE-02](BASE-02.md) 引擎（覆盖测试证明）。
- [x] **AC-4（FR-5）**：超管动作独立高亮审计可筛出；未绑 MFA 不得执行高危动作。
- [x] **AC-5（FR-6）**：超管尝试从 UI 关闭审计持久化 → 被高危护栏拦截（核心 #19）。
- 关联 A1–A9：A6 合规运维（超管 + 审计）。
- T-GATE：后端门禁全绿（无旁路黑洞）。
- B0 验收：纯确定性身份约束，天然 B0。

## 完工证据
- 代码落点：`RoleCode.SYSTEM_SUPERADMIN`、`DefaultPermissionPolicy`、`EffectivePermissionService`、`SystemSuperAdminGuard`、`ComplianceUserController/Service`、`MenuPermissionController`、五方言迁移、`AdminUsers`。
- 测试：`PermissionDimensionModelTest` / `DefaultPermissionPolicyTest` / `EffectivePermissionServiceTest` / `MfaRequirementPolicyTest` / `ComplianceUserControllerTest` / `ComplianceUserCredentialFlowTest` / `MenuPermissionControllerTest` / `BootstrapControllerTest` / `SystemConfigControllerTest` / `MigrationBaselineContractTest` / `H2BaselineMigrationTest` / `FlywayMultiDialectSmokeTest` / `AdminUsers.test.tsx`。
- 本地验证：以当前 PR 的测试、构建、迁移烟测和 T-GATE 结果为准，不在卡内固化易失真的历史数量。
- 浏览器证据：项目 Playwright 已覆盖内置超管不可编辑状态与首次部署入口关闭；截图随对应 E2E 测试产物留存。
- 审计员签字：@<reviewer>（owner ≠ reviewer，高风险双签）。

## 大卡工序（3d，后端）
- PR1：超管组系统约束 + 满权经引擎 + 不可降权/删除 → AC-1/2/3。✅
- PR2：强制 MFA + 高亮审计 + 高危护栏（不可关审计）→ AC-4/5。✅（与 PR1 合并为一个逻辑单元提交）
