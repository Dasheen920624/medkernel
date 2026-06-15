# AIK-STD-12 · 全专业领域标准资产模板 + AiReview 审核台

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [wave2 域简报](_brief.md) + [体验契约](../../EXPERIENCE_CONTRACT.md)。
> 迁移来源：详规 §8 资产模板 + 审核台 · backlog 第二波 X-AIK · 铁律 #3 医疗安全。

## 身份
- 卡 ID：AIK-STD-12（= backlog `AIK-STD-12`；含前端审核台）
- 域：wave2（X-AIK）
- 关联场景：S3、S15 AI 验证与验收
- 依赖卡：[AIREVIEW-01](../D4/AIREVIEW-01.md)（人工审/发壳）· [AIK-STD-04](AIK-STD-04.md)/[AIK-STD-10](AIK-STD-10.md)（生成/分流）· [AIK-STD-01](AIK-STD-01.md)
- 工作量：7d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
**全医疗专业领域标准资产模板 + AiReview 前端审核台（AI 生成接入）**：各专业有标准资产模板；审核台从「人工审/发壳」升级为支持 **AI 生成候选**审核（列/详情/批准/驳回/发布）。

## 现状（核查 2026-05-31，不深调研）
承载＝D4 [AIREVIEW-01](../D4/AIREVIEW-01.md) AI 知识审核台（人工审/发，AI 生成随 wave2）+ 前端 `pages/quality/AiReview.tsx` 已存在。本卡＝**补全全专业资产模板 + AiReview 接 AI 生成候选审核**。

## 功能要求（原子可测条目）
- [x] FR-1 全专业模板：术语/规则/路径/推荐/指标/随访/护理/报告/中医/医保等各专业标准资产模板。（PR3 代码态注册表 13 专业 + 审核台对照）
- [x] FR-2 AI 候选审核：审核台列 AI 生成候选（来源 + 8 态分流 + 门禁结果）。（PR2，8 态分流复用 KNOW-02）
- [x] FR-3 审核动作：详情查看 + 来源溯源 + 批准/驳回/退修 + 发布（接替换链）。（PR1 溯源 + PR2 批准/驳回/发布 + PR3 退修 RETURN）
- [x] FR-4 AI 标识：AI 生成内容明显标识，审核人署名留痕。（PR2，AI 徽标 + reviewedBy/reviewedAt）
- [x] FR-5 六态 + RBAC：齐全；知识工程师/医学专家可见；按 OrgContext。（既有审核台 + RETURNED 态）

## 接口 / 页面契约
### 接口契约
- 复用 AIREVIEW-01 审核 API + AIK 生成/分流/门禁结果查询；大列表 [API-13](../D0/API-13.md)。
### 页面契约（页面卡）
- 路由：复用 `quality` 区 `ai-review`（[AIREVIEW-01](../D4/AIREVIEW-01.md)，不净增二级菜单）；requiredRoles 知识工程师·医学专家。
- 结构：PageShell + 候选列表/详情/审核动作 + 六态；主按钮 ≤1（发布）/ 默认筛选 ≤3。
- 五维 RBAC：菜单/动作（审/发）/数据（org）/资产/环境；样式走 [BASE-10](../D0/BASE-10.md) token，禁硬编码。

## 视角清单（11 视角）
1. 产品架构：AI 工厂的人工审核出口。 2. 产品体验：★候选审核高效、来源可溯、AI 标识。 3. 系统与数据架构：候选列表 P95；大列表分页。 4. 临床医疗安全：★AI 内容标识 + 审核人署名 + 高危强制审。 5. 知识与数据治理：审过才发、走替换链。 6. 安全合规与监管：审核留痕。 7. 集团化与多租户治理：按 org 审核范围。 8. 集成与互操作：N·A。 9. 运维/SRE/国产化：国产浏览器可用。 10. 质量与真实性审计：★无前端假审/假发、来源真实。 11. AI/模型治理与可降级：无 AI 时台仍可人工审（B0 壳，[AIREVIEW-01](../D4/AIREVIEW-01.md)）。

## 适用不变量
- 命中核心约束：**铁律 #3 医疗安全**（标识/署名/高危审）· **#6 审核后生效** · **#1 真实性**。
- 本卡落点：全专业模板 + AiReview 接 AI 候选审核（审/发真实、AI 标识、审过才发）。

## 验收 + 验证
- [x] AC-1（FR-1/2）：全专业模板 + AI 候选可审核。
- [x] AC-2（FR-3/4/5）：审/发动作 + AI 标识署名 + 六态 RBAC。

## 实现进度（多 PR，全 FR 闭卡）
- **PR1**（#623）：AI 生产来源溯源接审核台读模型（反查血缘→job→producer，旁挂只读端点）。
- **PR2**（#624）：前端审核台 AI 标识徽标 + 来源溯源 + 审核人署名（退修留待 PR3 后端补 RETURN 态）。
- **PR3**（本卡收尾）：FR-1 全专业资产模板——`engine.factory` 代码态注册表 13 专业（术语/规则/路径/推荐/指标/随访/护理/报告/中医/医保 + 指南/药品/诊断），挂 `GET /knowledge-production/asset-templates`（knowledge.read），审核台详情按候选领域对照结构清单；FR-3 退修——`RETURN` 决策 + `RETURNED` 态（候选回 DRAFT 待修订 + 必填修订意见 + 留痕），V132 五方言放宽审核 CHECK。**全 FR/AC 真实闭卡**。
- 关联 A1–A9 剧本：A9 AI 审核/发布。
- T-GATE：前后端真实性门禁全绿（no-page-mock、无假审/发）。
- B0 验收：★无 AI 时审核台人工审/发可用。

## 完工证据
- 代码 permalink：资产模板 + `pages/quality/AiReview` 接 AI 候选审核。
- 测试：候选审核 / 审发动作 / AI 标识 / 六态 / no-page-mock。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
