# TENANT-01 · 租户开通页

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D2 域简报](_brief.md) + [体验契约](../../EXPERIENCE_CONTRACT.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §S1 集团与租户开通（L478）· 落地规划 §4.1 组织层级（L191，消费 D0）· 核心 §9 集团多租户继承。
> 实化映射：占位 `D2-PAGE-租户开通` → 本卡 **TENANT-01**。

## 身份

- 卡 ID：TENANT-01（页面卡；= backlog `D2-PAGE-租户开通` 实化）
- 域：D2 试点准备
- 关联场景：S1 集团与租户开通
- 依赖卡：[SVC-PILOT-01](SVC-PILOT-01.md)（开通就绪/组织树后端）· [BASE-01](../D0/BASE-01.md)（OrgContext/组织继承）· [BASE-06](../D0/BASE-06.md)/[BASE-08](../D0/BASE-08.md)/[BASE-10](../D0/BASE-10.md)（骨架/体验/token）· [INFRA-03](../D0/INFRA-03.md)（错误反馈）
- 工作量：3d
- owner / reviewer：Codex / PR reviewer（owner ≠ reviewer）

## 目标

统一租户管理：平台主租户只负责供应客户租户与首个管理员；客户租户只配置自身组织树、品牌和实施就绪状态。组织树经 [BASE-01](../D0/BASE-01.md) 真实落地，进入试点由 [SVC-PILOT-01](SVC-PILOT-01.md) 生命周期状态机校验，不设前端假开通动作。

## 现状（搬迁时核查 2026-05-30，以 `frontend/src` 为准）

页面**已存在待真实化**：`pages/tenant/TenantOnboarding`（路由 `/tenant/onboarding` 已归入 `institution-governance`，客户名称为“服务机构”）。本卡＝接 [SVC-PILOT-01](SVC-PILOT-01.md) 真实组织树/就绪 API + 六态/五维 RBAC。

## 功能要求（原子可测条目）

- [x] **FR-1 组织树管理**：建/查核心 §9 七层组织树（[SVC-PILOT-01](SVC-PILOT-01.md) `org-units`）；层级合法校验提示；跨租户不可见。
- [x] **FR-2 实施就绪检查**：客户租户实时展示 [SVC-PILOT-01](SVC-PILOT-01.md) `onboarding-readiness`；缺项阻塞，生命周期不可进入试点。
- [x] **FR-3 一键开通**：就绪后开通，结果真实回写 + 审计；失败明确原因。
- [x] **FR-4 六态 + RBAC**：六态齐全；仅平台/医院管理员·实施工程师可操作；数据按 `OrgContext`。

## 接口契约 / 页面契约

### 接口契约（引擎/API 卡）

N·A —— 页面卡，消费 [SVC-PILOT-01](SVC-PILOT-01.md) `org-units`/`onboarding-readiness` 现有 API。

### 页面契约（页面卡）

- 路由元数据：sectionKey `institution-governance` / menuKey `tenant-onboarding` / menuLabel `服务机构` / path `/tenant/onboarding` / requiredPermissions `menu.tenant-onboarding` + `tenant.read`；开通动作由后端 `tenant.write` 守门；requiredRoles 实施运维员、平台治理管理员、机构管理员。
- 结构：PageShell（[BASE-08](../D0/BASE-08.md)）+ 组织树（树控件）+ 就绪检查面板 + 六态。
- 主按钮 ≤1（开通）/ 默认筛选 ≤3（层级/状态/院区）/ 默认角色视图。
- 五维 RBAC：菜单 / 动作（开通权）/ 数据（org）/ 资产 / 环境。
- 样式：仅引用 [BASE-10](../D0/BASE-10.md) token + [体验契约](../../EXPERIENCE_CONTRACT.md)；禁硬编码。

## 数据与迁移

N·A —— 页面卡不落库；消费 [SVC-PILOT-01](SVC-PILOT-01.md)/[BASE-01](../D0/BASE-01.md)。

## 视角清单（11 视角逐条）

1. **产品架构**：试点开通的入口页。
2. **产品体验**：组织树 + 就绪门 + 六态；国产浏览器/老年模式可读。
3. **系统与数据架构**：组织树懒加载大组织；就绪聚合多源；P95 ≤1s。
4. **临床医疗安全**：N·A（开通页）；就绪门防"空配置上线"。
5. **知识与数据治理**：N·A —— 不直接管资产。
6. **安全合规与监管**：开通/组织变更留审计（[BASE-04](../D0/BASE-04.md)）。
7. **集团化与多租户治理**：★主战场 —— 组织树与继承均按核心 §9 七层（平台→集团→医院→院区→社区服务点→科室→专病）+ 行级隔离。
8. **集成与互操作**：N·A。
9. **运维 / SRE / 国产化**：内网慢场景骨架；国产浏览器。
10. **质量与真实性审计**：★开通真实回写、无前端假开通；无演示路由（[INFRA-09](../D1/INFRA-09.md)，铁律 #1）。
11. **AI / 模型治理与可降级**：N·A。

## 适用不变量

- 命中核心约束：**§9 集团多租户继承 / 行级隔离** · **铁律 #1 真实性（不假开通）** · **依赖 [BASE-01](../D0/BASE-01.md)/[SVC-PILOT-01](SVC-PILOT-01.md)**。
- 本卡落点：把租户开通从占位页变为真实组织树 + 就绪门 + 可审计开通。

## 验收 + 验证

- [x] **AC-1（FR-1）**：建核心 §9 七层组织树；跨层提示错误；跨租户不可见。
- [x] **AC-2（FR-2/3）**：缺项时开通受阻并列阻塞；就绪后开通成功 + 审计。
- [x] **AC-3（FR-4）**：六态齐全；非授权角色无访问。
- 关联 A1–A9 剧本：A1 开通、A5 集团复用。
- T-GATE：前端真实性门禁全绿（no-page-mock）。
- B0 验收：N·A（无模型）。

## 完工证据

- 代码 permalink：`TenantOnboarding.tsx` + `useTenants` / `useProvisionTenant` / `useOnboardingReadiness` / engine org `org-units` + PageShell 六态 + 路由 RBAC。
- 测试：`TenantOnboarding.test.tsx` 覆盖平台供应与客户实施双模式；`TenantProvisioningControllerTest` 覆盖供应、登录、MFA 与重复冲突；`TenantProvisioningServiceTest` 锁定客户台账不混入 `t-1`。
- T-GATE：提交后以 `origin/main..HEAD` 复跑 changed-mode 真实性门禁（扫描 3 个触碰文件）、配置边界、迁移规约、中文注释与 diff 检查，全部通过。
- 审计员签字：待 PR reviewer（owner ≠ reviewer）。
