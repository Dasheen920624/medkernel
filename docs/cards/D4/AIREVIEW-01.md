# AIREVIEW-01 · AI 知识审核页

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D4 域简报](_brief.md) + [体验契约](../../EXPERIENCE_CONTRACT.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §3 S15 AI 验证与验收 · S3 AI 知识工厂（审核侧）· 体验规范 §3。
> 实化映射：占位 `D4-PAGE-AI 知识审核` → 本卡 **AIREVIEW-01**。

## 身份
- 卡 ID：AIREVIEW-01（页面卡；= backlog `D4-PAGE-AI 知识审核` 实化）
- 域：D4 质控改进
- 关联场景：S15 AI 验证与验收 · S3 AI 知识工厂
- 依赖卡：[KNOW-02](../D2/KNOW-02.md)（版本/审核去重）· [KNOW-01](../D2/KNOW-01.md)（知识资产）· [SYS-08](../D2/SYS-08.md)（权威替换）· [BASE-08](../D0/BASE-08.md)/[BASE-10](../D0/BASE-10.md) · [INFRA-09](../D1/INFRA-09.md)
- 工作量：3d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
把 AI 知识审核页**真实化**：对候选知识做**人工审核 → 发布/驳回**（含去重/冲突/权威替换），**本期只做"审/发"**，AI 自动生成留第二波（wave2 AIK-*/KNOWGEN-*）。全部接 [KNOW-02](../D2/KNOW-02.md)，**不前端造候选、不假审核**。

## 现状（搬迁时核查 2026-05-30，以 `frontend/src` 为准）
页面**已存在待真实化**：`pages/quality/AiReview.tsx`（路由 `/aik/review` 已注册 `app/router.tsx`）。本卡＝去占位/mock + 接知识版本审核/发布 API（[KNOW-02](../D2/KNOW-02.md) 版本状态机）+ 六态/五维 RBAC 齐全；**AI 生成不在本卡**。

2026-06-06 Codex 实现：`AiReview.tsx` 已改为 PageShell 真实页；默认筛选固定为知识域 / 身份状态 / 关键词 3 个；知识身份列表通过 `useKnowledgeIdentities` 按 `page=1&size=20&sort=updatedAt,desc` 读取 API-03 客户面；选中身份后用 `useKnowledgeCandidates(identityId)` 读取真实候选；详情抽屉用 `useKnowledgeCandidateDiff(candidateId)` 展示现行 ACTIVE 版本与候选版本、来源锚点、`contentHash`、分级和差异摘要；通过 / 驳回统一调用 `useReviewKnowledgeCandidate`，携带标准 12 字段上下文、审核上下文包版本和 `Idempotency-Key`。页面未加入 AI 生成 / 创建候选入口。

## 功能要求（原子可测条目）
- [x] FR-1 候选列表：列待审核知识候选（来源/版本/去重提示），真实。
- [x] FR-2 审核详情：看候选内容 + 与现 `ACTIVE` 版本差异 + 冲突/去重（[KNOW-02](../D2/KNOW-02.md)）。
- [x] FR-3 审/发：通过则走权威替换发布（[SYS-08](../D2/SYS-08.md)），驳回带原因。
- [x] FR-4 不造生成：本页不触发 AI 生成（生成 wave2）；候选来源真实标注。
- [x] FR-5 六态 + 五维 RBAC：齐全；医务处/专家可审；数据按 `OrgContext`。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
N·A —— 消费 [KNOW-02](../D2/KNOW-02.md) 知识版本审核/发布 API（[API-03](../D2/API-03.md) 知识契约）。
### 页面契约（页面卡）
- 路由元数据：sectionKey `quality`（或 `ai-factory`）/ menuKey `aik-review` / menuLabel `AI 知识审核` / path `/aik/review` / requiredPermissions 知识审核 / requiredRoles 医务处·专科专家。
- 结构：PageShell（[BASE-08](../D0/BASE-08.md)）+ 候选列表 + 差异/冲突对比 + 审/发操作 + 六态。
- 主按钮 ≤1（发布/驳回）/ 默认筛选 ≤3（待审/我的/高优先）/ 默认角色视图（医务处）。
- 五维 RBAC：菜单 / 动作（审/发）/ 数据（org）/ 资产（知识版本）/ 环境。
- 样式：仅引用 [BASE-10](../D0/BASE-10.md) token + [体验契约](../../EXPERIENCE_CONTRACT.md)；禁硬编码 hex/px。

## 数据与迁移
N·A —— 页面卡不落库；消费 [KNOW-02](../D2/KNOW-02.md) 后端。

## 视角清单（11 视角逐条）
1. 产品架构：知识"审/发"的人工把关页（生成在 wave2）。
2. 产品体验：差异对比清晰、审/发一键带原因；国产浏览器可读。
3. 系统与数据架构：候选列表分页 P95 ≤1s；差异计算高效。
4. 临床医疗安全：★未审知识绝不参与临床命中（[SYS-08](../D2/SYS-08.md)）。
5. 知识与数据治理：★审/发走权威替换、去重/冲突可解释、版本化。
6. 安全合规与监管：审核/发布/驳回留审计（[BASE-04](../D0/BASE-04.md)）。
7. 集团化与多租户治理：知识审/发按集团/院作用域。
8. 集成与互操作：N·A（页面）。
9. 运维 / SRE / 国产化：内网慢场景骨架。
10. 质量与真实性审计：★无前端造候选、不假审核；**本期无 AI 生成**（生成 wave2）；无演示路由（[INFRA-09](../D1/INFRA-09.md)）。
11. AI / 模型治理与可降级：★AI 生成不在本卡（B0 先于模型）；本页纯人工审/发，关模型照常可用。

## 适用不变量
- 命中核心约束：**铁律 #1 真实性** · **核心 §11 B0 先于模型（生成留 wave2）** · **§7 唯一权威** · **§6 未审不参与临床**。
- 本卡落点：把 AI 知识审核页变为接真实候选、人工审/发、走权威替换的把关页；生成留第二波。

## 验收 + 验证
- [x] AC-1（FR-1/2）：候选/差异/冲突真实可解释。
- [x] AC-2（FR-3/4）：审/发走权威替换、驳回带原因；本页不触发生成。
- [x] AC-3（FR-5）：六态齐全；按作用域。
- 关联 A1–A9 剧本：A9 AI 知识审核。
- T-GATE：changed-mode 已过；真实性扫描 2 个文件、配置边界扫描 0 个文件、中文注释 0 fail/0 warn、`git diff --check origin/main...HEAD` 无输出。
- B0 验收：★关模型人工审/发仍可用（生成不在本卡）。

## 完工证据
- 代码 permalink：`pages/quality/AiReview` 真实化 + 接 [KNOW-02](../D2/KNOW-02.md) 审/发 + 六态。
- 测试：TDD 红灯捕获旧页未调用知识 hooks、仍显示“入口暂未激活”、缺审核对照抽屉、缺 review mutation、hooks 未导出 API-03 客户面；绿灯 `cd frontend && npm test -- AiReview.test.tsx hooks.test.ts pages.smoke.test.tsx` 77 测试通过；`cd frontend && npm run verify` 67 文件 / 405 测试通过；`cd frontend && npm run build` 通过；changed-mode T-GATE 已过（真实性 2、配置边界 0、中文注释 0 fail/0 warn、空白检查干净）。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
