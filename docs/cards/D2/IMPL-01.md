# IMPL-01 · 客户实施向导页

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D2 域简报](_brief.md) + [体验契约](../../EXPERIENCE_CONTRACT.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §S1 集团与租户开通·实施（L478）· 落地规划 §17 按业务域纵向推进（L938）· 体验规范 §3 角色体验标准（L58）。
> 实化映射：占位 `D2-PAGE-客户实施向导` → 本卡 **IMPL-01**。

## 身份
- 卡 ID：IMPL-01（页面卡；= backlog `D2-PAGE-客户实施向导` 实化）
- 域：D2 试点准备
- 关联场景：S1 集团与租户开通
- 依赖卡：[SVC-PILOT-01](SVC-PILOT-01.md)（实施步骤/就绪后端）· [BASE-06](../D0/BASE-06.md)/[BASE-08](../D0/BASE-08.md)/[BASE-10](../D0/BASE-10.md)（骨架/体验底座/token）· [INFRA-09](../D1/INFRA-09.md)（清演示页门禁）· [INFRA-03](../D0/INFRA-03.md)（错误反馈）
- 工作量：3d
- owner / reviewer：Codex / PR reviewer（owner ≠ reviewer）

## 目标
把客户实施向导页**真实化**：按步骤呈现试点准备进度（组织→用户→权限→适配器→资产→灰度），每步真实就绪状态 + 跳转对应配置页，引导实施工程师把医院"配好开起来"。**不写死步骤、不前端假就绪**。

## 现状（搬迁时核查 2026-05-30，以 `frontend/src` 为准）
页面**已存在待真实化**：`pages/tenant/ImplementationGuide`（路由 `/onboarding/guide` 已归入 `institution-governance`，客户名称为“实施与验收”）。本卡＝去占位/mock + 接 [SVC-PILOT-01](SVC-PILOT-01.md) 真实就绪 API + 六态/五维 RBAC 齐全。

## 功能要求（原子可测条目）
- [x] **FR-1 步骤真实化**：各步状态取 [SVC-PILOT-01](SVC-PILOT-01.md) `implementation-steps` 真实就绪，不前端写死/假数据。
- [x] **FR-2 跳转配置**：每步可跳对应配置页（组织→租户开通、适配器→适配器中心、资产→配置包中心）。
- [x] **FR-3 阻塞可见**：blocked 步骤明确阻塞原因 + 责任项，不显示虚假"已完成"。
- [x] **FR-4 六态**：加载/空/错误/无权限/部分成功/正常齐全（[BASE-08](../D0/BASE-08.md)）。
- [x] **FR-5 五维 RBAC**：仅实施工程师/平台·医院管理员可见可操作；数据按 `OrgContext` 作用域。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
N·A —— 本卡为页面，不新增后端；消费 [SVC-PILOT-01](SVC-PILOT-01.md) 现有就绪/步骤 API。
### 页面契约（页面卡）
- 路由元数据：sectionKey `institution-governance` / menuKey `implementation-guide` / menuLabel `实施与验收` / path `/onboarding/guide` / requiredPermissions `menu.implementation-guide` + `tenant.read` / requiredRoles 实施运维员、平台治理管理员、机构管理员。
- 结构：PageShell（[BASE-08](../D0/BASE-08.md)）+ 步骤进度（StepFlow [INFRA-09](../D1/INFRA-09.md) 组件）+ 各步就绪卡 + 六态。
- 主按钮 ≤1（继续下一步）/ 默认筛选 ≤3 / 默认角色视图（实施工程师）。
- 五维 RBAC：菜单 / 动作 / 数据（org）/ 资产 / 环境。
- 样式：仅引用 [BASE-10](../D0/BASE-10.md) token + [体验契约](../../EXPERIENCE_CONTRACT.md)；禁硬编码 hex/px。

## 数据与迁移
N·A —— 页面卡不落库；消费 [SVC-PILOT-01](SVC-PILOT-01.md) 后端。

## 视角清单（11 视角逐条）
1. **产品架构**：实施落地的"总导航"页。
2. **产品体验**：★分步向导 + 六态 + 阻塞可见；国产浏览器/老年模式可读（[BASE-10](../D0/BASE-10.md)）。
3. **系统与数据架构**：各步并行取就绪状态、独立降级；首屏 P95 ≤1s。
4. **临床医疗安全**：N·A（实施页，不触临床）；但"未就绪不可上临床"由 [SVC-PILOT-01](SVC-PILOT-01.md) 门保证。
5. **知识与数据治理**：资产就绪项链到配置包中心（[CFGPKG-01](CFGPKG-01.md)）。
6. **安全合规与监管**：操作留审计（[BASE-04](../D0/BASE-04.md)）。
7. **集团化与多租户治理**：按 `OrgContext` 作用域；集团/院内视图差异。
8. **集成与互操作**：适配器就绪项链到适配器中心（[ADAPTER-01](ADAPTER-01.md)）。
9. **运维 / SRE / 国产化**：内网慢场景骨架；国产浏览器兼容。
10. **质量与真实性审计**：★就绪状态真实、无前端假数据；无演示路由（[INFRA-09](../D1/INFRA-09.md) no-page-mock，铁律 #1）。
11. **AI / 模型治理与可降级**：N·A —— 实施页无模型。

## 适用不变量
- 命中核心约束：**铁律 #1 真实性（无前端假就绪/无演示页）** · **§2 菜单 IA** · **§9 多租户作用域** · **依赖 [SVC-PILOT-01](SVC-PILOT-01.md)**。
- 本卡落点：把实施向导从占位页变为接真实就绪、可阻塞、可跳转的导航页。

## 验收 + 验证
- [x] **AC-1（FR-1/3）**：各步状态来自真实后端；缺项显示阻塞原因，不假"已完成"。
- [x] **AC-2（FR-2）**：步骤跳转到对应配置页正确。
- [x] **AC-3（FR-4/5）**：六态齐全；非实施/管理员角色无权访问。
- 关联 A1–A9 剧本：A1 接入/开通。
- T-GATE：前端真实性门禁全绿（no-page-mock、无 Math.random 造数、无演示路由）。
- B0 验收：N·A（无模型；纯确定性页面）。

## 完工证据
- 代码 permalink：`frontend/src/pages/tenant/ImplementationGuide.tsx` 真实化 + `useImplementationSteps` 接 [SVC-PILOT-01](SVC-PILOT-01.md) `/engine/tenant/implementation-steps` + PageShell 六态 + StepFlow。
- 测试：`npm test -- src/pages/tenant/ImplementationGuide.test.tsx src/shared/api/hooks.test.ts src/shared/config/routes.test.ts src/pages/tenant/RulePathwayCleanliness.test.ts`（4 files / 47 tests）通过；`npm run verify`（49 files / 278 tests）通过；`npm run build` 通过；`npm audit --omit=dev --json` 生产依赖 0 漏洞；后端 `mvn -q test` 通过（186 reports / 1120 tests / 0 failures / 0 errors / 0 skipped，PostgreSQL 15.18 + Oracle 21.3 迁移到 V66）。
- 门禁：提交后按 `origin/main..HEAD` 复跑 changed-mode T-GATE 通过；真实性门禁扫描 3 个前端文件 0 阻断，配置边界 / 迁移规约本卡无适用文件且通过；`scripts/check-comment-zh.sh` 0 fail / 0 warn，`git diff --check origin/main..HEAD` 通过。
- 审计员签字：待 PR reviewer（owner ≠ reviewer）。
