# AIK-STD-10 · 生成期知识身份识别、去重与审核分流（8 态）

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [wave2 域简报](_brief.md)。
> 迁移来源：详规 §8.13 身份识别去重 · backlog 第二波 X-AIK · 铁律 #6。

## 身份
- 卡 ID：AIK-STD-10（= backlog `AIK-STD-10`）
- 域：wave2（X-AIK）
- 关联场景：S3、S15
- 依赖卡：[KNOW-02](../D2/KNOW-02.md)（版本/审核去重）· [AIK-STD-01](AIK-STD-01.md)（资产 schema）
- 工作量：5d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
生成候选做**身份识别 + 去重 + 8 态审核分流**：识别候选与现有知识的关系，去重，按 8 态分流到对应审核处理。

## 现状（核查 2026-06-17）
承载＝D2 [KNOW-02](../D2/KNOW-02.md) 版本/审核去重（新旧识别/去重/冲突/待审/原子替换/旧版隔离）已建。本卡＝**生成期 8 态分流**，复用 KNOW-02 身份与内容 hash 事实。

本地分支已落后端 B0 能力：`mk_knowledge_generation_triage`（V137，五方言）记录生成期身份识别、去重、八态与处理动作；候选生成链路在 AIK-STD-05 门禁通过后先执行分流，同一目标身份的相同内容 hash 直接跳过不重复入审；`GET /api/v1/engine/knowledge-production/jobs/{jobCode}/triage-results` 提供只读审计。`KnowledgeGovernance` 知识生产 tab 已补 8 态队列总览与明细，八态中文标签 + 数量 + 原始状态码同屏展示，复用 `triage-results`，不新增本地假队列。

## 功能要求（原子可测条目）
- [x] FR-1 身份识别：识别候选 = 现有知识的（同一/变体/新增）。后端 B0 以目标身份、现行版本、内容 hash、显式 triage 标记与来源权威等级判定。
- [x] FR-2 去重：重复候选合并，不重复入审。同一目标身份命中相同内容 hash 时落 `DUPLICATE/SKIP_DUPLICATE` 并跳过 `submitCandidate`。
- [x] FR-3 8 态分流：新增/重复/小修订/重大升级/冲突/降级/废止/存疑 八态准确分流。八态均有单测覆盖。
- [ ] FR-4 分流去向：各态对应处理（直审/合并/冲突仲裁/升级走 [AIK-STD-09](AIK-STD-09.md)）。本地分支已落后端 action 映射与重复跳过；前端展示和专门审核队列/替换闭环仍随 AIK-STD-09/11 与生产中心收口。
- [x] FR-5 可审计：分流结果留痕、可复查。按 job 与租户只读查询，append-only 留痕。

## 接口 / 数据契约
- 复用 KNOW-02 去重 + `mk_knowledge_generation_triage`（候选/识别/8 态/去向），五方言。

## 视角清单（11 视角）
1. 产品架构：候选进审核的分流器。 2. 产品体验：N·A（分流结果在审核台）。 3. 系统与数据架构：识别批量。 4. 临床医疗安全：冲突/升级态优先处理。 5. 知识与数据治理：★8 态分流 = 审核高效不漏。 6. 安全合规与监管：分流留痕。 7. 集团化与多租户治理：按 org。 8. 集成与互操作：N·A。 9. 运维/SRE/国产化：N·A。 10. 质量与真实性审计：★分流准确、不误判合并。 11. AI/模型治理与可降级：B0 走规则/hash 识别。

## 适用不变量
- 命中核心约束：**铁律 #6 唯一权威** · **#1 真实性** · **核心 §6 去重/冲突**。
- 本卡落点：身份识别 + 去重 + 8 态分流，候选高效进审、不漏不重。

## 验收 + 验证
- [x] AC-1（FR-1/2）：识别 + 去重正确。覆盖目标身份新增、重复命中、生成链路重复跳过。
- [x] AC-2（FR-3/4）：8 态分流 + 去向正确。后端八态和 action 映射已覆盖；前端知识生产 tab 已补 8 态队列总览与明细。
- T-GATE：后端真实性门禁全绿。
- B0 验收：★规则/hash 识别（不依赖模型）。

## 完工证据
- 本地代码：`KnowledgeGenerationTriageService` + `GenerationTriage*` + V137 五方言迁移 + 生成链路接入 + `triage-results` 只读端点；前端 `KnowledgeGovernance` 8 态队列。
- 测试：`KnowledgeGenerationTriageServiceTest` 覆盖八态/去向；`CandidateGenerationOrchestrationServiceTest` 与 `CandidateGenerationIntegrationTest` 覆盖重复跳过不入审；`KnowledgeProductionControllerSecurityTest` 覆盖读权限；迁移基线/H2 覆盖 V137；`KnowledgeGovernance.test.tsx` 覆盖 8 态队列呈现。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
