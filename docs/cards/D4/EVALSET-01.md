# EVALSET-01 · 评估指标库页

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D4 域简报](_brief.md) + [体验契约](../../EXPERIENCE_CONTRACT.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §3 S9 病历内涵质控 · S11 智能评估与整改 · 体验规范 §3。
> 实化映射：占位 `D4-PAGE-评估指标库` → 本卡 **EVALSET-01**。

## 身份
- 卡 ID：EVALSET-01（页面卡；= backlog `D4-PAGE-评估指标库` 实化）
- 域：D4 质控改进
- 关联场景：S9 病历内涵质控 · S11 智能评估与整改
- 依赖卡：[EVAL-01](EVAL-01.md)（指标后端）· [API-08](API-08.md)（契约）· [RULE-01](../D2/RULE-01.md)（规则）· [BASE-08](../D0/BASE-08.md)/[BASE-10](../D0/BASE-10.md) · [INFRA-09](../D1/INFRA-09.md)
- 工作量：3d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
把评估指标库页**真实化**：配置/管理质控评估指标（基于规则引擎条件树），版本化、可仿真，全部接 [EVAL-01](EVAL-01.md)，**不前端写死指标**。

## 现状（搬迁时核查 2026-05-30，以 `frontend/src` 为准）
页面**已存在待真实化**：`pages/quality/QcEvalSets.tsx`（路由 `/qc/eval/sets` 已注册 `app/router.tsx`）。本卡＝去占位/mock + 接评估指标 CRUD/版本 API（[EVAL-01](EVAL-01.md) `EvaluationIndicator`）+ 六态/五维 RBAC 齐全。

2026-06-06 Codex 实现：`QcEvalSets.tsx` 已改为真实 PageShell 页面，默认筛选仅指标编码 / 指标状态 / 评估主体 3 个，列表通过 `useEvaluationIndicators` 按 API-13 `page=1&size=20&sort=updatedAt,desc` 读取真实指标；新建指标用 `ConditionTreeEditor` 编辑分母 / 分子条件树并提交 DSL JSON；详情抽屉展示 7 步 `StepFlow`、真实版本 / 状态 / `traceId` 和生命周期 submit / publish / activate；仿真抽屉读取 ACTIVE 临床快照并调用 canonical `/engine/evaluation:evaluate`。页面不再前端写死指标、命中事实或 trace。

## 功能要求（原子可测条目）
- [x] FR-1 指标列表：列评估指标（[API-13](../D0/API-13.md) 分页），状态/版本真实。
- [x] FR-2 指标编辑：基于规则引擎（[RULE-01](../D2/RULE-01.md)）条件树配置指标，不写死。
- [x] FR-3 版本/发布：指标走配置类 7 步流（[SYS-04](../D2/SYS-04.md)），版本化。
- [x] FR-4 仿真：指标对历史病例仿真命中预览（与规则仿真一致）。
- [x] FR-5 六态 + 五维 RBAC：齐全；质控办/医务处可配；数据按 `OrgContext`。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
N·A —— 消费 [API-08](API-08.md)/[EVAL-01](EVAL-01.md) 指标 API。
### 页面契约（页面卡）
- 路由元数据：sectionKey `quality` / menuKey `qc-eval-sets` / menuLabel `评估指标库` / path `/qc/eval/sets` / requiredPermissions 指标配置 / requiredRoles 质控办·医务处。
- 结构：PageShell（[BASE-08](../D0/BASE-08.md)）+ 指标列表 + 条件树编辑器 + 仿真预览 + 7 步流（StepFlow [INFRA-09](../D1/INFRA-09.md)）+ 六态。
- 主按钮 ≤1（保存/发布）/ 默认筛选 ≤3 / 默认角色视图（质控办）。
- 五维 RBAC：菜单 / 动作（配置/发布）/ 数据（org）/ 资产（指标版本）/ 环境。
- 样式：仅引用 [BASE-10](../D0/BASE-10.md) token + [体验契约](../../EXPERIENCE_CONTRACT.md)；禁硬编码 hex/px。

## 数据与迁移
N·A —— 页面卡不落库；消费 [EVAL-01](EVAL-01.md) 后端。

## 视角清单（11 视角逐条）
1. 产品架构：质控指标的"配置库"页。
2. 产品体验：条件树可视、仿真即时；国产浏览器可读。
3. 系统与数据架构：仿真预览 P95 ≤1.5s。
4. 临床医疗安全：指标变更走审核发布、不直接生效。
5. 知识与数据治理：★指标版本化、不写死（[SYS-08](../D2/SYS-08.md)）。
6. 安全合规与监管：配置/发布留审计（[BASE-04](../D0/BASE-04.md)）。
7. 集团化与多租户治理：指标按集团/院/科继承；安全指标下级不可关。
8. 集成与互操作：复用规则引擎条件树（[RULE-01](../D2/RULE-01.md)）。
9. 运维 / SRE / 国产化：内网慢场景骨架。
10. 质量与真实性审计：★无前端写死指标、仿真真实；无演示路由（[INFRA-09](../D1/INFRA-09.md)）。
11. AI / 模型治理与可降级：N·A（确定性配置页）。

## 适用不变量
- 命中核心约束：**铁律 #1 真实性** · **核心 §5 配置 7 步流** · **§7 版本** · **§9 多租户作用域**。
- 本卡落点：把评估指标库页变为接真实指标 CRUD/版本/仿真、不写死的配置库。

## 验收 + 验证
- [x] AC-1（FR-1/2）：指标列表/编辑真实、不写死。
- [x] AC-2（FR-3/4）：版本 7 步流；仿真命中预览真实。
- [x] AC-3（FR-5）：六态齐全；按作用域。
- 关联 A1–A9 剧本：A9 指标配置。
- T-GATE：changed-mode 通过（no-page-mock、无写死指标）：真实性门禁扫描 1 文件，配置边界扫描 0 文件，中文注释 0 fail / 0 warn，`git diff --check origin/main...HEAD` 无输出。
- B0 验收：N·A（确定性页面）。

## 完工证据
- 代码 permalink：`pages/quality/QcEvalSets` 真实化 + 接 [EVAL-01](EVAL-01.md) + 六态（PR 合并后回填永久链接）。
- 测试：`cd frontend && npm test -- QcEvalSets.test.tsx pages.smoke.test.tsx hooks.test.ts` 74 测试通过；`cd frontend && npm run verify` 66 文件 / 396 测试通过；`cd frontend && npm run build` 通过；changed-mode T-GATE 通过（真实性 1 文件、配置边界 0 文件、中文注释 0 fail / 0 warn、diff check 无输出）。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
