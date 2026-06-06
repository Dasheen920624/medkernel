# EVALRES-01 · 评估结果页

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D4 域简报](_brief.md) + [体验契约](../../EXPERIENCE_CONTRACT.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §3 S9 病历内涵质控 · S11 智能评估与整改 · 体验规范 §3。
> 实化映射：占位 `D4-PAGE-评估结果` → 本卡 **EVALRES-01**。

## 身份
- 卡 ID：EVALRES-01（页面卡；= backlog `D4-PAGE-评估结果` 实化）
- 域：D4 质控改进
- 关联场景：S9 病历内涵质控 · S11 智能评估与整改
- 依赖卡：[EVAL-01](EVAL-01.md)（结果后端）· [SVC-QUALITY-03](SVC-QUALITY-03.md)（整改）· [API-13](../D0/API-13.md) · [BASE-08](../D0/BASE-08.md)/[BASE-10](../D0/BASE-10.md) · [INFRA-09](../D1/INFRA-09.md)
- 工作量：3d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
把评估结果页**真实化**：呈现评估命中结果（分级）、问题详情、追溯病历证据，可派整改，全部接 [EVAL-01](EVAL-01.md)，**不前端造结果**。

## 现状（搬迁时核查 2026-05-30，以 `frontend/src` 为准）
页面**已存在待真实化**：`pages/quality/QcEvalResults.tsx`（路由 `/qc/eval/results` 已注册 `app/router.tsx`）。本卡＝去占位/mock + 接评估结果/问题 API（[EVAL-01](EVAL-01.md) `EvaluationResult`）+ 六态/五维 RBAC 齐全。

2026-06-06 Codex 实现：`QcEvalResults.tsx` 已改为 PageShell 真实页，默认筛选仅保留评估级别 / 问题状态 / 责任科室 3 个；结果列表通过 `useEvaluationResults` 按 `page=1&size=20&sort=createdAt,desc` 消费真实 `EvaluationResult`，问题列表通过 `useQualityFindings` 消费真实质控问题；详情抽屉通过 `useQualityFindingDetail` 展示命中解释、病历证据、整改任务和复核记录；`NEW` 问题通过 `useDispatchRectification` 携带幂等键派发整改；页面展示 evidence / sourceRef / traceId / indicatorVersion 等真实字段，不在前端伪造结果、问题或 trace。

## 功能要求（原子可测条目）
- [x] FR-1 结果列表：评估结果按级别（`EvaluationResultLevel`）真实，分页筛选（[API-13](../D0/API-13.md)）。
- [x] FR-2 问题详情：问题可展开看命中指标/规则/版本与解释。
- [x] FR-3 证据追溯：每条问题追溯到病历证据。
- [x] FR-4 派整改：问题一键派整改（[SVC-QUALITY-03](SVC-QUALITY-03.md)）。
- [x] FR-5 六态 + 五维 RBAC：齐全；质控办/科主任按作用域；数据按 `OrgContext`。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
N·A —— 消费 [EVAL-01](EVAL-01.md)/[API-08](API-08.md) 结果 API。
### 页面契约（页面卡）
- 路由元数据：sectionKey `quality` / menuKey `qc-eval-results` / menuLabel `评估结果` / path `/qc/eval/results` / requiredPermissions 评估结果 / requiredRoles 质控办·科主任。
- 结构：PageShell（[BASE-08](../D0/BASE-08.md)）+ 结果列表（级别色阶 token）+ 问题详情抽屉（解释+证据）+ 六态。
- 主按钮 ≤1（派整改）/ 默认筛选 ≤3（未整改/高级别/本科）/ 默认角色视图（按角色）。
- 五维 RBAC：菜单 / 动作（派整改）/ 数据（org）/ 资产 / 环境。
- 样式：仅引用 [BASE-10](../D0/BASE-10.md) token + [体验契约](../../EXPERIENCE_CONTRACT.md)；禁硬编码 hex/px。

## 数据与迁移
N·A —— 页面卡不落库；消费 [EVAL-01](EVAL-01.md) 后端。

## 视角清单（11 视角逐条）
1. 产品架构：质控"看结果"的页。
2. 产品体验：级别清晰、解释可追溯证据；国产浏览器可读。
3. 系统与数据架构：结果分页 P95 ≤1s；大数据量。
4. 临床医疗安全：评估结果不改医嘱；问题须有证据。
5. 知识与数据治理：结果追溯指标/规则版本（[SYS-08](../D2/SYS-08.md)）。
6. 安全合规与监管：派整改留审计（[BASE-04](../D0/BASE-04.md)）。
7. 集团化与多租户治理：按科/院作用域。
8. 集成与互操作：N·A（页面）。
9. 运维 / SRE / 国产化：内网慢场景骨架。
10. 质量与真实性审计：★无前端造结果、追溯证据；无演示路由（[INFRA-09](../D1/INFRA-09.md)）。
11. AI / 模型治理与可降级：N·A（确定性页面）。

## 适用不变量
- 命中核心约束：**铁律 #1 真实性** · **§2 菜单 IA** · **§9 多租户作用域** · **依赖 [EVAL-01](EVAL-01.md)**。
- 本卡落点：把评估结果页变为接真实结果/问题、追溯证据、可派整改的结果页。

## 验收 + 验证
- [x] AC-1（FR-1/2）：结果/问题真实、解释可追溯。
- [x] AC-2（FR-3/4）：问题追溯病历证据；一键派整改。
- [x] AC-3（FR-5）：六态齐全；按作用域。
- 关联 A1–A9 剧本：A9 评估结果。
- T-GATE：changed-mode 通过；真实性门禁扫描 2 文件、配置边界扫描 0 文件、中文注释 0 fail / 0 warn、`git diff --check origin/main...HEAD` 无输出。
- B0 验收：N·A（确定性页面）。

## 完工证据
- 代码 permalink：`pages/quality/QcEvalResults` 真实化 + 接 [EVAL-01](EVAL-01.md) / [SVC-QUALITY-03](SVC-QUALITY-03.md) + 六态。
- 测试：TDD 红灯捕获旧页 `size=50`、缺真实问题接口、缺详情抽屉、缺派整改；`cd frontend && npm test -- QcEvalResults.test.tsx hooks.test.ts pages.smoke.test.tsx` 74 测试通过；`cd frontend && npm run verify` 66 文件 / 399 测试通过；`cd frontend && npm run build` 通过；changed-mode T-GATE 通过。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
