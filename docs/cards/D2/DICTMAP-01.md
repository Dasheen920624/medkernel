# DICTMAP-01 · 字典映射页

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D2 域简报](_brief.md) + [体验契约](../../EXPERIENCE_CONTRACT.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §S4 字典映射（L511）· 落地规划 §7.2 映射闭环（L408）· 核心 §13 真实性 / 铁律 #2 高危不批量。
> 实化映射：占位 `D2-PAGE-字典映射` → 本卡 **DICTMAP-01**。

## 身份
- 卡 ID：DICTMAP-01（页面卡；= backlog `D2-PAGE-字典映射` 实化）
- 域：D2 试点准备
- 关联场景：S4 字典映射
- 依赖卡：[TERM-01](TERM-01.md)/[API-04](API-04.md)（字典映射引擎/API + 高危判别）· [SYS-04](SYS-04.md)（映射包发布）· [INFRA-09](../D1/INFRA-09.md)（StepFlow）· [BASE-06](../D0/BASE-06.md)/[BASE-08](../D0/BASE-08.md)/[BASE-10](../D0/BASE-10.md)
- 工作量：3d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
把字典映射页**真实化**：院内↔标准字典映射工作台 + 候选 + **高危近似红标 + 禁批量确认** + 冲突待裁 + 映射包 7 步流发布。**接 [TERM-01](TERM-01.md)/[API-04](API-04.md) 真实候选与高危判别，高危逐条人工二次确认**。

## 现状（搬迁时核查 2026-05-30，以 `frontend/src` 为准）
页面**已存在待真实化**：`pages/tenant/TerminologyMapping`（路由 `/terminology/mapping` 已注册 sectionKey `pilot-setup`，`routes.ts` 有 `terminologyMappingExperience` 占位：实施/信息科/医务处核查映射）。本卡＝接 [API-04](API-04.md) 真实候选/高危/发布 + 六态/RBAC。

## 功能要求（原子可测条目）
- [x] **FR-1 字典浏览**：标准（ICD-10/ICD-9-CM-3/药品本位码/LOINC）+ 院内字典分页（[API-13](../D0/API-13.md)）。
- [x] **FR-2 候选 + 高危红标**：候选含语义匹配分；**高危近似（钾/钠、肌钙蛋白 T/I、左/右、剂量量级）红标 + 禁批量确认按钮置灰**（[TERM-01](TERM-01.md) MED-C1）。
- [x] **FR-3 逐条确认**：高危映射逐条 + 二次确认弹层；普通映射可常规确认；冲突待裁可见。
- [x] **FR-4 映射包发布**：映射包 7 步流灰度/全量/回滚（[SYS-04](SYS-04.md)，StepFlow [INFRA-09](../D1/INFRA-09.md)）。
- [x] **FR-5 六态 + RBAC**：六态齐全；仅信息科/专科专家·医务处可操作；数据按 `OrgContext`。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
N·A —— 页面卡，消费 [API-04](API-04.md) `/engine/terminology/**` 现有候选/确认/发布 API。
### 页面契约（页面卡）
- 路由元数据：sectionKey `pilot-setup` / menuKey `terminology-mapping` / menuLabel `字典映射` / path `/terminology/mapping` / requiredPermissions 字典映射 / requiredRoles 信息科·专科专家·医务处。
- 结构：PageShell（[BASE-08](../D0/BASE-08.md)）+ 字典/映射列表 + 候选面板（高危红标）+ 逐条确认弹层 + 映射包 7 步流 + 六态。
- 主按钮 ≤1（确认/发布）/ 默认筛选 ≤3（标准系统/状态/风险）/ 默认角色视图。
- 五维 RBAC：菜单 / 动作（确认/发布权）/ 数据（org）/ 资产（映射包）/ 环境。
- 样式：仅引用 [BASE-10](../D0/BASE-10.md) token + [体验契约](../../EXPERIENCE_CONTRACT.md)；禁硬编码。

## 数据与迁移
N·A —— 页面卡不落库；消费 [TERM-01](TERM-01.md) 表族。

## 视角清单（11 视角逐条）
1. **产品架构**：院内↔标准码映射的工作台页。
2. **产品体验**：★候选 + 高危红标 + 逐条确认；大字典分页；国产浏览器/老年模式可读。
3. **系统与数据架构**：大字典列表分页；候选打分可解释；P95 ≤1s。
4. **临床医疗安全**：★主战场 —— 高危近似红标 + **禁批量按钮置灰** + 逐条二次确认（错映=临床误判，铁律 #2）。
5. **知识与数据治理**：标准字典来源分级（[OPT-07](OPT-07.md)）；映射版本化可回滚。
6. **安全合规与监管**：映射确认/发布留审计 + 高危确认操作人/时点（[BASE-04](../D0/BASE-04.md)）。
7. **集团化与多租户治理**：标准字典集团统一、院内本地（[SYS-04](SYS-04.md) 继承）。
8. **集成与互操作**：映射供外部编码归一（[INTEG-01](INTEG-01.md)/[API-01](API-01.md)）。
9. **运维 / SRE / 国产化**：药品本位码一等支持；离线字典包；国产浏览器。
10. **质量与真实性审计**：★候选分真实、高危禁批量前端+API 双拒；无演示页（[INFRA-09](../D1/INFRA-09.md)，铁律 #1/#2）。
11. **AI / 模型治理与可降级**：语义候选含 AI（第二波）标置信、**绝不自动确认高危**；关模型退确定性候选，页不变。

## 适用不变量
- 命中核心约束：**铁律 #2 高危不批量/不自动** · **§13 真实性** · **§4 7 步流（映射包）** · **依赖 [TERM-01](TERM-01.md)/[API-04](API-04.md)/[SYS-04](SYS-04.md)**。
- 本卡落点：把字典映射从占位页变为含高危红标、禁批量、逐条确认、可发布的安全工作台。

## 验收 + 验证
- [x] **AC-1（FR-2/3）**：高危候选红标 + 批量按钮置灰；逐条二次确认才能落；普通映射常规确认。
- [x] **AC-2（FR-1/4）**：字典分页真实；映射包 7 步流灰度→全量→回滚。
- [x] **AC-3（FR-5）**：六态齐全；非授权角色无访问。
- 关联 A1–A9 剧本：A3 字典映射。
- T-GATE：前端真实性门禁全绿（no-page-mock、高危禁批量、无伪造候选分）。
- B0 验收：确定性候选 + 人工确认，**天然 B0**。

## 完工证据
- 代码：`frontend/src/pages/tenant/TerminologyMapping.tsx` / `TerminologyMapping.module.css` 接 `/engine/terminology/**` 真实 hook；`frontend/src/shared/api/hooks.ts` 新增标准/院内字典、候选、冲突、映射包构建/发布/回滚 hook；`frontend/src/shared/config/routes.ts` 收紧 `menu.terminology-mapping + term.read/write/publish` 与 `it-ops/specialist/medical-admin`；`PageShell` / `PageExperienceShell` 修正窄屏 header action wrap。
- 测试：`npm test -- src/pages/tenant/TerminologyMapping.test.tsx src/shared/api/hooks.test.ts src/shared/config/routes.test.ts src/pages/tenant/RulePathwayCleanliness.test.ts`（4 files / 65 tests）；`npm test -- src/pages/tenant/TerminologyMapping.test.tsx src/shared/ui/PageShell.test.tsx src/shared/ui/PageExperienceShell.test.tsx src/pages/pages.smoke.test.tsx`（4 files / 33 tests）。
- 全量与门禁：`npm run verify`（50 files / 299 tests）、`npm audit --omit=dev --json`（生产依赖漏洞 0）、`npm run build`、`node --test scripts/authenticity-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/config-boundary-guard.test.mjs`（34/34）、`scripts/check-comment-zh.sh`（0 fail / 0 warn）、`git diff --check`。
- 浏览器验收：项目 Playwright 控制真实页面与 API 拦截验证 `/terminology/mapping` 桌面/移动端无运行时错误、主按钮唯一、高危批量禁用、逐条二次确认请求含标准上下文、发布请求含 10% 灰度上下文；截图 `/tmp/medkernel-dictmap-01-terminology-mapping.png`、`/tmp/medkernel-dictmap-01-terminology-mapping-mobile.png`。
- 未冒领：`DEFER-004` in-app browser 后端登录链路仍 open；`DEFER-010` 10 万级字典压测仍 open；backlog “7 页面真实化”整行仍 pending，未冒领 ADAPTER-01。
