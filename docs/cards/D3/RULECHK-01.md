# RULECHK-01 · 规则校验页

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D3 域简报](_brief.md) + [体验契约](../../EXPERIENCE_CONTRACT.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §3 S5 规则引擎配置（运行校验侧）· 详规 S8 临床嵌入运行 · 体验规范 §3。
> 实化映射：占位 `D3-PAGE-规则校验` → 本卡 **RULECHK-01**。

## 身份
- 卡 ID：RULECHK-01（页面卡；= backlog `D3-PAGE-规则校验` 实化）
- 域：D3 临床运行
- 关联场景：S8 临床嵌入运行
- 依赖卡：[SVC-CLINICAL-02](SVC-CLINICAL-02.md)（规则校验后端）· [RULE-01](../D2/RULE-01.md)（规则引擎 `RuleDslEvaluator`）· [BASE-08](../D0/BASE-08.md)/[BASE-10](../D0/BASE-10.md) · [INFRA-09](../D1/INFRA-09.md)
- 工作量：3d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
把规则校验页**真实化**：对医嘱/病历/处方实时跑规则校验（[RULE-01](../D2/RULE-01.md)），展示命中规则、级别、解释，**不前端写死校验结果**。

## 现状（搬迁时核查 2026-05-30，以 `frontend/src` 为准）
页面**已存在待真实化**：`pages/clinical/RuleValidate.tsx`（路由 `/rule/validate` 已注册 `app/router.tsx`）。本卡＝去占位/mock + 接规则校验 API（`engine/rule` `RuleDslEvaluator`）+ 六态/五维 RBAC 齐全。

## 功能要求（原子可测条目）
- [x] FR-1 校验执行：提交医嘱/病历 → 跑规则校验，命中结果真实来自 [RULE-01](../D2/RULE-01.md)。PR1 默认使用 CDS Hooks `order-sign`，调用真实 `/engine/rule/rules/evaluate`。
- [x] FR-2 结果解释：每条命中显示规则 ID/版本/级别/解释（可追溯）。PR1 列表改读后端 `ruleId/versionId/actions/explanation`。
- [ ] FR-3 红线突出：红线（[OPT-04](OPT-04.md)）命中强突出、不可忽略。
- [ ] FR-4 仿真对比：可对历史用例回放校验（与规则仿真一致）。
- [ ] FR-5 六态 + 五维 RBAC：齐全；仅医生/专科专家/质控可见；数据按 `OrgContext`。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
N·A —— 消费 [SVC-CLINICAL-02](SVC-CLINICAL-02.md) 规则校验端点（`POST /api/v1/engine/rule/rules/evaluate`）与执行解释端点（`GET /api/v1/engine/rule/rules/executions/{executionId}/explain`）。
### 页面契约（页面卡）
- 路由元数据：sectionKey `clinical-run` / menuKey `rule-validate` / menuLabel `规则校验` / path `/rule/validate` / requiredPermissions 规则校验 / requiredRoles 临床医生·专科专家·质控。
- 结构：PageShell（[BASE-08](../D0/BASE-08.md)）+ 输入区（医嘱/病历）+ 校验结果列表（级别色阶 token）+ 解释抽屉 + 六态。
- 主按钮 ≤1（执行校验）/ 默认筛选 ≤3 / 默认角色视图（临床医生）。
- 五维 RBAC：菜单 / 动作（校验）/ 数据（org）/ 资产（规则版本）/ 环境。
- 样式：仅引用 [BASE-10](../D0/BASE-10.md) token + [体验契约](../../EXPERIENCE_CONTRACT.md)；禁硬编码 hex/px。

## 数据与迁移
N·A —— 页面卡不落库；消费 [SVC-CLINICAL-02](SVC-CLINICAL-02.md) / [RULE-01](../D2/RULE-01.md) 后端。

## 视角清单（11 视角逐条）
1. 产品架构：规则在临床运行侧的"校验工作台"。
2. 产品体验：结果级别色阶清晰、解释可追溯；国产浏览器可读。
3. 系统与数据架构：校验 P95 ≤1s；回放大用例分页。
4. 临床医疗安全：★红线强突出；校验只提示不自动改医嘱。
5. 知识与数据治理：命中追溯规则版本（[SYS-08](../D2/SYS-08.md)）。
6. 安全合规与监管：校验留审计（[BASE-04](../D0/BASE-04.md)）。
7. 集团化与多租户治理：规则按 `OrgContext`/科室继承生效。
8. 集成与互操作：N·A（页面）。
9. 运维 / SRE / 国产化：内网慢场景骨架。
10. 质量与真实性审计：★无前端写死校验结果；命中可复现；无演示路由（[INFRA-09](../D1/INFRA-09.md)）。
11. AI / 模型治理与可降级：N·A（规则校验确定性）。

## 适用不变量
- 命中核心约束：**铁律 #1 真实性** · **§临床安全（红线突出）** · **§2 菜单 IA** · **§9 多租户作用域**。
- 本卡落点：把规则校验页变为接真实规则引擎、结果可追溯、红线强突出的校验工作台。

## 验收 + 验证
- [x] AC-1（FR-1/2）：校验结果真实、可追溯版本。
- [ ] AC-2（FR-3/4）：红线强突出；历史回放一致。
- [ ] AC-3（FR-5）：六态齐全；非授权不可校验。
- 关联 A1–A9 剧本：A5 规则校验。
- T-GATE：前端真实性门禁全绿（no-page-mock、无写死结果、无演示路由）。
- B0 验收：N·A（确定性页面）。

## 完工证据
- 代码 permalink：PR1 `frontend/src/pages/clinical/RuleValidate.tsx` 修正真实规则引擎 DTO 与 CDS Hooks 触发点；红线突出 / 回放 / 完整六态 / RBAC 留本页面后续 PR。
- 测试：`npm test -- src/pages/clinical/RuleValidate.test.tsx` 覆盖真实 `ruleId/versionId/actions/explanation` 渲染和提交载荷；同轮 `npm run verify`（57 文件 / 323 测试）、`npm run build`、`npm audit --omit=dev --audit-level=moderate` 通过。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
